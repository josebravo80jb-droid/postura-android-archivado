package com.postura.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.postura.app.data.DailyGreenRatio
import com.postura.app.data.DailyRedMinutes
import com.postura.app.data.PostureDatabase
import com.postura.app.data.PosturePreferences
import com.postura.app.data.ZoneCount
import com.postura.app.sensor.PostureSensorFusion
import com.postura.app.service.PostureForegroundService
import com.postura.app.service.PostureState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PosturaViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PosturePreferences(application)
    private val database = PostureDatabase.getDatabase(application)
    private val calibrationSensor = PostureSensorFusion(application)

    val isCalibrated = preferences.isCalibratedFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val neutralPitch = preferences.neutralPitchFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0f
    )
    val thresholdDegrees = preferences.thresholdDegreesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 30f
    )
    val alertDelaySeconds = preferences.alertDelaySecondsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 3
    )
    val vibrationIntensity = preferences.vibrationIntensityFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "MEDIA"
    )
    val safeZoneEnabled = preferences.safeZoneEnabledFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )
    val appTheme = preferences.themeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "SISTEMA"
    )
    val modoSimulacion = preferences.modoSimulacionFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val pitchSimulado = preferences.pitchSimuladoFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0f
    )

    val serviceState: StateFlow<PostureState> = PostureForegroundService.serviceState

    // Estados para la pantalla de calibración
    private val _calibrationCountdown = MutableStateFlow<Int?>(null)
    val calibrationCountdown: StateFlow<Int?> = _calibrationCountdown.asStateFlow()

    private val _calibrationSamplesCount = MutableStateFlow(0)
    val calibrationSamplesCount: StateFlow<Int> = _calibrationSamplesCount.asStateFlow()

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private var calibrationJob: Job? = null
    private var pitchSimuladoJob: Job? = null

    // Estadísticas
    private val todayDate = LocalDate.now()
    private val weekAgoDate = todayDate.minusDays(6)
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    val redMinutesLast7Days: StateFlow<List<DailyRedMinutes>> = database.postureDao()
        .getRedMinutesLastDays(weekAgoDate.format(formatter))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayZoneCounts: StateFlow<List<ZoneCount>> = database.postureDao()
        .getZoneCountsForDate(todayDate.format(formatter))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekZoneCounts: StateFlow<List<ZoneCount>> = database.postureDao()
        .getZoneCountsSince(weekAgoDate.format(formatter))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _streakDays = MutableStateFlow(0)
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    init {
        calculateStreak()
    }

    fun startCalibration() {
        if (_isCalibrating.value) return
        _isCalibrating.value = true
        _calibrationSamplesCount.value = 0

        calibrationJob?.cancel()
        calibrationJob = viewModelScope.launch {
            // Cuenta regresiva de 3 segundos
            for (sec in 3 downTo 1) {
                _calibrationCountdown.value = sec
                delay(1000L)
            }
            _calibrationCountdown.value = null

            // Captura de 30 muestras en 3 segundos (1 muestra cada 100ms)
            calibrationSensor.start()
            val samples = mutableListOf<Float>()

            repeat(30) {
                delay(100L)
                val currentSample = calibrationSensor.sensorData.value.pitchDegrees
                samples.add(currentSample)
                _calibrationSamplesCount.value = samples.size
            }

            calibrationSensor.stop()

            val averagePitch = if (samples.isNotEmpty()) samples.average().toFloat() else 0f
            preferences.saveCalibration(averagePitch)
            _isCalibrating.value = false
        }
    }

    fun toggleMonitoring() {
        val app = getApplication<Application>()
        val intent = Intent(app, PostureForegroundService::class.java)
        if (serviceState.value.isRunning) {
            intent.action = PostureForegroundService.ACTION_STOP
            app.startService(intent)
        } else {
            intent.action = PostureForegroundService.ACTION_START
            app.startForegroundService(intent)
        }
    }

    fun setThreshold(threshold: Float) {
        viewModelScope.launch { preferences.setThresholdDegrees(threshold) }
    }

    fun setAlertDelay(delaySeconds: Int) {
        viewModelScope.launch { preferences.setAlertDelaySeconds(delaySeconds) }
    }

    fun setVibrationIntensity(intensity: String) {
        viewModelScope.launch { preferences.setVibrationIntensity(intensity) }
    }

    fun setSafeZoneEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setSafeZoneEnabled(enabled) }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { preferences.setTheme(theme) }
    }

    fun setModoSimulacion(activo: Boolean) {
        viewModelScope.launch { preferences.setModoSimulacion(activo) }
    }

    fun setPitchSimulado(pitch: Float) {
        pitchSimuladoJob?.cancel()
        pitchSimuladoJob = viewModelScope.launch { preferences.setPitchSimulado(pitch) }
    }

    fun resetCalibration() {
        viewModelScope.launch { preferences.resetCalibration() }
    }

    private fun calculateStreak() {
        viewModelScope.launch {
            val dailyRatios: List<DailyGreenRatio> = database.postureDao().getDailyGreenRatios()
            var currentStreak = 0
            for (item in dailyRatios) {
                val ratio = item.greenRatio ?: 0.0
                if (ratio >= 0.80) {
                    currentStreak++
                } else {
                    break
                }
            }
            _streakDays.value = currentStreak
        }
    }

    fun exportRecordsToCsv(onFileReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val records = database.postureDao().getAllRecordsForExport()
            val app = getApplication<Application>()
            val cacheFile = File(app.cacheDir, "postura_historico.csv")

            FileWriter(cacheFile).use { writer ->
                writer.append("ID,Timestamp,Fecha,Zona,Angulo,SegundosEnZona\n")
                for (r in records) {
                    writer.append("${r.id},${r.timestamp},${r.dateString},${r.zone},${r.angle},${r.secondsInZone}\n")
                }
            }

            val uri = FileProvider.getUriForFile(
                app,
                "${app.packageName}.fileprovider",
                cacheFile
            )
            onFileReady(uri)
        }
    }
}
