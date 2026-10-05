package com.postura.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow
import kotlin.math.sqrt

enum class SignalQuality {
    ALTA,
    MEDIA,
    BAJA
}

data class PostureSensorData(
    val pitchDegrees: Float = 0f,
    val isStable: Boolean = true,
    val signalQuality: SignalQuality = SignalQuality.ALTA,
    val timestamp: Long = System.currentTimeMillis()
)

class PostureSensorFusion(private val context: Context) : SensorEventListener {

    // Estrategia activa de sensor
    private enum class SensorStrategy { ROTATION_VECTOR, GAME_ROTATION_VECTOR, MANUAL_FUSION }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Detección de sensores disponibles
    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gameRotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    // Selección de estrategia en orden de preferencia
    private val strategy: SensorStrategy = when {
        rotationVectorSensor != null -> SensorStrategy.ROTATION_VECTOR
        gameRotationSensor != null   -> SensorStrategy.GAME_ROTATION_VECTOR
        else                         -> SensorStrategy.MANUAL_FUSION
    }

    private val _sensorData = MutableStateFlow(PostureSensorData())
    val sensorData: StateFlow<PostureSensorData> = _sensorData.asStateFlow()

    // Fusión manual: buffers de acelerómetro y magnetómetro
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    // Filtro: media móvil de 5 muestras para el pitch
    private val pitchMovingAverageWindow = FloatArray(5)
    private var pitchWindowIndex = 0
    private var pitchWindowCount = 0

    // Detección de interferencia magnética (solo usada en MANUAL_FUSION)
    private val magNormWindow = FloatArray(15)
    private var magNormIndex = 0
    private var magNormCount = 0
    private val magVarianceThreshold = 35.0f

    // Detección de estabilidad: ventana de 10 muestras (~0.5s a SENSOR_DELAY_UI)
    private val stabilityPitchWindow = FloatArray(10)
    private var stabilityIndex = 0
    private var stabilityCount = 0
    private val stabilityVarianceThreshold = 100.0f

    fun start() {
        resetBuffers()
        when (strategy) {
            SensorStrategy.ROTATION_VECTOR -> {
                sensorManager.registerListener(
                    this, rotationVectorSensor!!, SensorManager.SENSOR_DELAY_UI
                )
                // LOG 1 — diagnóstico temporal
                Log.d("SENSOR_START", "strategy=ROTATION_VECTOR")
            }
            SensorStrategy.GAME_ROTATION_VECTOR -> {
                sensorManager.registerListener(
                    this, gameRotationSensor!!, SensorManager.SENSOR_DELAY_UI
                )
                // LOG 1 — diagnóstico temporal
                Log.d("SENSOR_START", "strategy=GAME_ROTATION_VECTOR")
            }
            SensorStrategy.MANUAL_FUSION -> {
                accelerometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
                magnetometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
                // LOG 1 — diagnóstico temporal
                Log.d(
                    "SENSOR_START",
                    "strategy=MANUAL_FUSION accel=${accelerometer != null} mag=${magnetometer != null}"
                )
            }
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        hasGravity = false
        hasGeomagnetic = false
    }

    private fun resetBuffers() {
        pitchWindowIndex = 0
        pitchWindowCount = 0
        magNormIndex = 0
        magNormCount = 0
        stabilityIndex = 0
        stabilityCount = 0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        // LOG 2 — diagnóstico temporal
        Log.d("SENSOR_EVENT", "type=${event.sensor.type} values=${event.values.joinToString()}")

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                processRotationVector(event.values)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasGeomagnetic = true

                val norm = sqrt(
                    event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2)
                )
                magNormWindow[magNormIndex] = norm
                magNormIndex = (magNormIndex + 1) % magNormWindow.size
                if (magNormCount < magNormWindow.size) magNormCount++
            }
        }

        // LOG 3 — diagnóstico temporal
        Log.d("SENSOR_STATE", "hasGravity=$hasGravity hasGeomagnetic=$hasGeomagnetic strategy=$strategy")
        if (strategy == SensorStrategy.MANUAL_FUSION && hasGravity && hasGeomagnetic) {
            processFusion()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Manejado mediante estimación de varianza magnética directa
    }

    // ── Estrategia 1 y 2: sensores virtuales ──────────────────────────────────

    private fun processRotationVector(values: FloatArray) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)

        // LOG 4 — diagnóstico temporal (siempre success con sensores virtuales)
        Log.d("SENSOR_MATRIX", "strategy=$strategy rotationMatrix=${rotationMatrix.take(4).joinToString()}")

        val remappedMatrix = FloatArray(9)
        remapMatrix(rotationMatrix, remappedMatrix)

        val orientation = FloatArray(3)
        SensorManager.getOrientation(remappedMatrix, orientation)

        val rawPitchDegrees = Math.toDegrees(orientation[1].toDouble()).toFloat()

        // Sensores virtuales no tienen interferencia magnética → siempre calidad ALTA
        applyFilterAndEmit(rawPitchDegrees, SignalQuality.ALTA)
    }

    // ── Estrategia 3: fusión manual accel + magnet ────────────────────────────

    private fun processFusion() {
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)

        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            gravity,
            geomagnetic
        )
        // LOG 4 — diagnóstico temporal
        Log.d(
            "SENSOR_MATRIX",
            "success=$success gravity=${gravity.joinToString()} geomagnetic=${geomagnetic.joinToString()}"
        )

        if (!success) return

        val remappedMatrix = FloatArray(9)
        remapMatrix(rotationMatrix, remappedMatrix)

        val orientation = FloatArray(3)
        SensorManager.getOrientation(remappedMatrix, orientation)

        val rawPitchDegrees = Math.toDegrees(orientation[1].toDouble()).toFloat()

        // Calidad de señal por varianza de campo magnético
        val magVariance = calculateVariance(magNormWindow, magNormCount)
        val quality = when {
            magVariance > magVarianceThreshold        -> SignalQuality.BAJA
            magVariance > (magVarianceThreshold / 2f) -> SignalQuality.MEDIA
            else                                      -> SignalQuality.ALTA
        }

        applyFilterAndEmit(rawPitchDegrees, quality)
    }

    // ── Lógica compartida: filtro + estabilidad + emisión ─────────────────────

    private fun applyFilterAndEmit(rawPitchDegrees: Float, quality: SignalQuality) {
        // 1. Filtro: media móvil de 5 muestras
        pitchMovingAverageWindow[pitchWindowIndex] = rawPitchDegrees
        pitchWindowIndex = (pitchWindowIndex + 1) % pitchMovingAverageWindow.size
        if (pitchWindowCount < pitchMovingAverageWindow.size) pitchWindowCount++

        var sumPitch = 0f
        for (i in 0 until pitchWindowCount) {
            sumPitch += pitchMovingAverageWindow[i]
        }
        val filteredPitch = sumPitch / pitchWindowCount
        // LOG 5 — diagnóstico temporal
        Log.d("SENSOR_PITCH", "raw=$rawPitchDegrees filtered=$filteredPitch")

        // 2. Detección de estabilidad en ventana de 10 muestras
        stabilityPitchWindow[stabilityIndex] = filteredPitch
        stabilityIndex = (stabilityIndex + 1) % stabilityPitchWindow.size
        if (stabilityCount < stabilityPitchWindow.size) stabilityCount++

        val pitchVariance = calculateVariance(stabilityPitchWindow, stabilityCount)
        val isStable = pitchVariance <= stabilityVarianceThreshold

        _sensorData.value = PostureSensorData(
            pitchDegrees = filteredPitch,
            isStable = isStable,
            signalQuality = quality,
            timestamp = System.currentTimeMillis()
        )
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private fun getDeviceRotation(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        } catch (_: Exception) {
            Surface.ROTATION_0
        }
    }

    private fun remapMatrix(input: FloatArray, output: FloatArray) {
        when (getDeviceRotation()) {
            Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(
                input, SensorManager.AXIS_X, SensorManager.AXIS_Y, output
            )
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                input, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, output
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                input, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, output
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                input, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, output
            )
            else -> System.arraycopy(input, 0, output, 0, 9)
        }
    }

    private fun calculateVariance(array: FloatArray, count: Int): Float {
        if (count <= 1) return 0f
        var sum = 0f
        for (i in 0 until count) { sum += array[i] }
        val mean = sum / count
        var sumSquares = 0f
        for (i in 0 until count) { sumSquares += (array[i] - mean).pow(2) }
        return sumSquares / count
    }

    companion object {
        fun computePitchDifference(currentPitch: Float, neutralPitch: Float): Float {
            return kotlin.math.abs(currentPitch - neutralPitch)
        }

        fun calculateMovingAverage(samples: FloatArray, count: Int): Float {
            if (count <= 0) return 0f
            val validCount = count.coerceAtMost(samples.size)
            var sum = 0f
            for (i in 0 until validCount) { sum += samples[i] }
            return sum / validCount
        }

        fun isVarianceStable(variance: Float, threshold: Float = 18.0f): Boolean {
            return variance <= threshold
        }
    }
}
