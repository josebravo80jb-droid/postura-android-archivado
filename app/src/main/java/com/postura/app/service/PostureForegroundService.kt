package com.postura.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.postura.app.data.PostureDatabase
import com.postura.app.data.PostureEntity
import com.postura.app.data.PosturePreferences
import com.postura.app.sensor.PostureSensorFusion
import com.postura.app.sensor.SignalQuality
import com.postura.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class PostureZone {
    VERDE,
    AMARILLO,
    ROJO
}

data class PostureState(
    val currentAngle: Float = 0f,
    val zone: PostureZone = PostureZone.VERDE,
    val signalQuality: SignalQuality = SignalQuality.ALTA,
    val isStable: Boolean = true,
    val redSecondsToday: Long = 0L,
    val isRunning: Boolean = false,
    val correctionsToday: Int = 0
)

class PostureForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var sensorFusion: PostureSensorFusion
    private lateinit var preferences: PosturePreferences
    private lateinit var database: PostureDatabase
    private var vibrator: Vibrator? = null

    private var monitoringJob: Job? = null
    private var redSecondsTimerJob: Job? = null
    private var vibrationLoopJob: Job? = null
    private var periodicSaveJob: Job? = null

    private var previousZone: PostureZone = PostureZone.VERDE
    private var consecutiveRedSeconds = 0
    private var lastRecordedDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    // Variables de configuración reactivas sincronizadas en background desde DataStore
    private var modoSimulacion: Boolean = false
    private var pitchSimulado: Float = 0f
    private var neutralPitch: Float = 0f
    private var threshold: Float = 30f
    private var safeZoneEnabled: Boolean = true
    private var alertDelaySeconds: Int = 3
    private var vibrationIntensity: String = "MEDIA"

    companion object {
        const val CHANNEL_ID = "posture_monitoring_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        private val _serviceState = MutableStateFlow(PostureState())
        val serviceState: StateFlow<PostureState> = _serviceState.asStateFlow()

        fun isRunning(): Boolean = _serviceState.value.isRunning

        // Funciones puras de lógica para uso en servicio y en tests directos
        fun classifyZone(angle: Float, threshold: Float): PostureZone {
            return when {
                angle < threshold -> PostureZone.VERDE
                angle < (threshold + 5.0f) -> PostureZone.AMARILLO
                else -> PostureZone.ROJO
            }
        }

        fun isNewDay(lastRecordedDate: String, currentDate: String): Boolean {
            return lastRecordedDate != currentDate
        }

        fun resolvePitchForPosture(isSimulation: Boolean, simulatedPitch: Float, sensorPitch: Float): Float {
            return if (isSimulation) simulatedPitch else sensorPitch
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorFusion = PostureSensorFusion(this)
        preferences = PosturePreferences(this)
        database = PostureDatabase.getDatabase(this)
        initVibrator()
        createNotificationChannel()

        // Sincronización continua de preferencias en background
        serviceScope.launch { preferences.modoSimulacionFlow.collect { modoSimulacion = it } }
        serviceScope.launch { preferences.pitchSimuladoFlow.collect { pitchSimulado = it } }
        serviceScope.launch { preferences.neutralPitchFlow.collect { neutralPitch = it } }
        serviceScope.launch { preferences.thresholdDegreesFlow.collect { threshold = it } }
        serviceScope.launch { preferences.safeZoneEnabledFlow.collect { safeZoneEnabled = it } }
        serviceScope.launch { preferences.alertDelaySecondsFlow.collect { alertDelaySeconds = it } }
        serviceScope.launch { preferences.vibrationIntensityFlow.collect { vibrationIntensity = it } }
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoreo de Postura",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente para monitoreo continuo de postura en segundo plano"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(angle: Float, zone: PostureZone): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = when (zone) {
            PostureZone.VERDE -> "Postura adecuada (${String.format("%.1f", angle)}°)"
            PostureZone.AMARILLO -> "Atención: ligera inclinación (${String.format("%.1f", angle)}°)"
            PostureZone.ROJO -> "Inclinación excesiva (${String.format("%.1f", angle)}°)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Postura: Monitoreo activo")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        startForeground(NOTIFICATION_ID, buildNotification(0f, PostureZone.VERDE))
        _serviceState.value = _serviceState.value.copy(isRunning = true)

        sensorFusion.start()

        // Loop de recolección de muestras de sensor
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            sensorFusion.sensorData.collect { sample ->
                evaluatePosture(sample.pitchDegrees, sample.signalQuality, sample.isStable)
            }
        }

        // Ticker de 1 segundo para acumulación de tiempo en rojo y reset de medianoche
        redSecondsTimerJob?.cancel()
        redSecondsTimerJob = serviceScope.launch {
            while (true) {
                delay(1000L)
                checkMidnightReset()

                val currentState = _serviceState.value
                if (currentState.zone == PostureZone.ROJO && currentState.isStable && currentState.signalQuality != SignalQuality.BAJA) {
                    consecutiveRedSeconds++
                    val newRedTotal = currentState.redSecondsToday + 1
                    _serviceState.value = currentState.copy(redSecondsToday = newRedTotal)

                    checkVibrationTrigger()
                } else {
                    consecutiveRedSeconds = 0
                    stopVibrationLoop()
                }
            }
        }

        // Guardado periódico de telemetría a Room (cada 30s)
        periodicSaveJob?.cancel()
        periodicSaveJob = serviceScope.launch {
            while (true) {
                delay(30000L)
                val state = _serviceState.value
                val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                database.postureDao().insert(
                    PostureEntity(
                        timestamp = System.currentTimeMillis(),
                        dateString = todayStr,
                        zone = state.zone.name,
                        angle = state.currentAngle,
                        secondsInZone = 30
                    )
                )
            }
        }
    }

    private suspend fun evaluatePosture(currentPitch: Float, quality: SignalQuality, isStable: Boolean) {
        // Actualizar SIEMPRE el ángulo con el pitch filtrado,
        // independientemente de la estabilidad o calidad de señal.
        val pitchFinal = if (modoSimulacion) pitchSimulado else currentPitch
        val angle = PostureSensorFusion.computePitchDifference(pitchFinal, neutralPitch)
        val currentZone = classifyZone(angle, threshold)

        // Detección de corrección: si venía de ROJO o AMARILLO y entra a zona segura (umbral - 5°)
        if (safeZoneEnabled && (previousZone == PostureZone.ROJO || previousZone == PostureZone.AMARILLO)) {
            val safeZoneThreshold = threshold - 5.0f
            if (angle <= safeZoneThreshold) {
                triggerShortFeedbackVibration()
                _serviceState.value = _serviceState.value.copy(
                    correctionsToday = _serviceState.value.correctionsToday + 1
                )
            }
        }

        previousZone = currentZone

        // Actualizar el estado con el ángulo real
        _serviceState.value = _serviceState.value.copy(
            currentAngle = angle,
            zone = currentZone,
            signalQuality = quality,
            isStable = isStable
        )

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(angle, currentZone))
    }

    private fun checkMidnightReset() {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (isNewDay(lastRecordedDate, todayStr)) {
            lastRecordedDate = todayStr
            _serviceState.value = _serviceState.value.copy(
                redSecondsToday = 0L,
                correctionsToday = 0
            )
        }
    }

    private fun checkVibrationTrigger() {
        val delayBeforeAlert = alertDelaySeconds
        if (consecutiveRedSeconds >= delayBeforeAlert && vibrationLoopJob == null) {
            startVibrationLoop()
        }
    }

    private fun startVibrationLoop() {
        vibrationLoopJob?.cancel()
        vibrationLoopJob = serviceScope.launch {
            val intensityStr = vibrationIntensity
            val amplitude = when (intensityStr) {
                "BAJA" -> 80
                "ALTA" -> 255
                else -> 170 // MEDIA
            }

            while (true) {
                vibratePulse(500L, amplitude)
                delay(1000L)
            }
        }
    }

    private fun stopVibrationLoop() {
        vibrationLoopJob?.cancel()
        vibrationLoopJob = null
    }

    private fun vibratePulse(durationMs: Long, amplitude: Int) {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
                it.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(durationMs)
            }
        }
    }

    private fun triggerShortFeedbackVibration() {
        serviceScope.launch {
            vibratePulse(80L, 100)
        }
    }

    private fun stopMonitoring() {
        sensorFusion.stop()
        monitoringJob?.cancel()
        redSecondsTimerJob?.cancel()
        stopVibrationLoop()
        periodicSaveJob?.cancel()

        _serviceState.value = _serviceState.value.copy(isRunning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
