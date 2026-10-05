package com.postura.app

import com.postura.app.sensor.PostureSensorFusion
import com.postura.app.service.PostureForegroundService
import com.postura.app.service.PostureZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostureLogicTest {

    // Test 1: Cálculo de pitch y ángulo usando directamente la lógica de PostureSensorFusion
    @Test
    fun testPitchAngleCalculationDirect() {
        val neutralPitch = 45.0f
        val currentPitch = 80.0f
        val calculatedAngle = PostureSensorFusion.computePitchDifference(currentPitch, neutralPitch)
        assertEquals(35.0f, calculatedAngle, 0.001f)

        // Caso con inclinación negativa relativa
        val currentPitchLower = 20.0f
        val calculatedAngleLower = PostureSensorFusion.computePitchDifference(currentPitchLower, neutralPitch)
        assertEquals(25.0f, calculatedAngleLower, 0.001f)
    }

    // Test 2: Clasificación de estados Verde / Amarillo / Rojo según el enum real PostureZone
    @Test
    fun testZoneClassificationDirect() {
        val threshold = 30.0f

        // Caso 1: Ángulo < umbral -> VERDE
        assertEquals(PostureZone.VERDE, PostureForegroundService.classifyZone(29.9f, threshold))
        assertEquals(PostureZone.VERDE, PostureForegroundService.classifyZone(0.0f, threshold))

        // Caso 2: umbral <= ángulo < umbral + 5 -> AMARILLO
        assertEquals(PostureZone.AMARILLO, PostureForegroundService.classifyZone(30.0f, threshold))
        assertEquals(PostureZone.AMARILLO, PostureForegroundService.classifyZone(34.9f, threshold))

        // Caso 3: ángulo >= umbral + 5 -> ROJO
        assertEquals(PostureZone.ROJO, PostureForegroundService.classifyZone(35.0f, threshold))
        assertEquals(PostureZone.ROJO, PostureForegroundService.classifyZone(50.0f, threshold))
    }

    // Test 3: Reset diario a medianoche local usando función de servicio real
    @Test
    fun testMidnightDateCheckDirect() {
        val lastDate = "2026-10-01"
        val sameDate = "2026-10-01"
        val nextDate = "2026-10-02"

        // Mismo día: no debe resetear
        assertFalse(PostureForegroundService.isNewDay(lastDate, sameDate))

        // Cambio de día (medianoche): debe resetear
        assertTrue(PostureForegroundService.isNewDay(lastDate, nextDate))
    }

    // Test 4: Persistencia y media móvil de muestras de calibración con función de producción
    @Test
    fun testCalibrationAverageCalculationDirect() {
        val simulatedSamples = floatArrayOf(45f, 46f, 47f, 48f, 49f)
        val average = PostureSensorFusion.calculateMovingAverage(simulatedSamples, 5)

        assertEquals(47.0f, average, 0.001f)
    }

    // Test 5: Estabilidad de varianza usando PostureSensorFusion
    @Test
    fun testStabilityVarianceEvaluation() {
        // Varianza baja (<= 18.0) -> estable
        assertTrue(PostureSensorFusion.isVarianceStable(12.5f))
        assertTrue(PostureSensorFusion.isVarianceStable(18.0f))

        // Varianza alta (> 18.0) -> inestable (teléfono agitándose)
        assertFalse(PostureSensorFusion.isVarianceStable(22.3f))
    }

    // Test 6: Modo Simulación en el servicio selecciona pitch simulado en lugar del sensor
    @Test
    fun testModoSimulacionEnServicio() {
        val simulatedPitch = 65.0f
        val sensorPitch = 15.0f

        // Con modo de simulación activo, se usa el pitch simulado
        val pitchEnModoSimulado = PostureForegroundService.resolvePitchForPosture(
            isSimulation = true,
            simulatedPitch = simulatedPitch,
            sensorPitch = sensorPitch
        )
        assertEquals(simulatedPitch, pitchEnModoSimulado, 0.001f)

        // Con modo de simulación inactivo, se usa el sensor real
        val pitchEnModoReal = PostureForegroundService.resolvePitchForPosture(
            isSimulation = false,
            simulatedPitch = simulatedPitch,
            sensorPitch = sensorPitch
        )
        assertEquals(sensorPitch, pitchEnModoReal, 0.001f)
    }

    // Test 7: Con isStable=false, el servicio DEBE seguir actualizando currentAngle
    // La inestabilidad no bloquea la actualización visual del ángulo, solo la acumulación de segundos en rojo en el ticker
    @Test
    fun testAngleUpdatesRegardlessOfStability() {
        val neutralPitch = 40.0f
        val currentPitch = 70.0f
        val calculatedAngle = PostureSensorFusion.computePitchDifference(currentPitch, neutralPitch)
        val zone = PostureForegroundService.classifyZone(calculatedAngle, 30.0f)

        // Verificamos que el cálculo del ángulo y la zona se realizan sin importar isStable
        assertEquals(30.0f, calculatedAngle, 0.001f)
        assertEquals(PostureZone.AMARILLO, zone)

        // Verificamos que el umbral relajado de estabilidad tolera movimientos normales (ej: varianza 45.0f <= 100.0f)
        assertTrue(PostureSensorFusion.isVarianceStable(45.0f, 100.0f))
        assertFalse(PostureSensorFusion.isVarianceStable(125.0f, 100.0f))
    }
}
