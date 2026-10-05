package com.postura.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "posture_settings")

class PosturePreferences(private val context: Context) {

    companion object {
        val KEY_CALIBRATED = booleanPreferencesKey("is_calibrated")
        val KEY_NEUTRAL_PITCH = floatPreferencesKey("neutral_pitch")
        val KEY_THRESHOLD_DEGREES = floatPreferencesKey("threshold_degrees")
        val KEY_ALERT_DELAY_SECONDS = intPreferencesKey("alert_delay_seconds")
        val KEY_VIBRATION_INTENSITY = stringPreferencesKey("vibration_intensity")
        val KEY_SAFE_ZONE_ENABLED = booleanPreferencesKey("safe_zone_enabled")
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_MODO_SIMULACION = booleanPreferencesKey("modo_simulacion")
        val KEY_PITCH_SIMULADO = floatPreferencesKey("pitch_simulado")
    }

    val modoSimulacionFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MODO_SIMULACION] ?: false
    }

    val pitchSimuladoFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_PITCH_SIMULADO] ?: 0f
    }

    val isCalibratedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_CALIBRATED] ?: false
    }

    val neutralPitchFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_NEUTRAL_PITCH] ?: 0f
    }

    val thresholdDegreesFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_THRESHOLD_DEGREES] ?: 30.0f
    }

    val alertDelaySecondsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_ALERT_DELAY_SECONDS] ?: 3
    }

    val vibrationIntensityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_VIBRATION_INTENSITY] ?: "MEDIA"
    }

    val safeZoneEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SAFE_ZONE_ENABLED] ?: true
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: "SISTEMA"
    }

    suspend fun saveCalibration(pitch: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NEUTRAL_PITCH] = pitch
            prefs[KEY_CALIBRATED] = true
        }
    }

    suspend fun resetCalibration() {
        context.dataStore.edit { prefs ->
            prefs[KEY_CALIBRATED] = false
        }
    }

    suspend fun setThresholdDegrees(threshold: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THRESHOLD_DEGREES] = threshold
        }
    }

    suspend fun setAlertDelaySeconds(delay: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ALERT_DELAY_SECONDS] = delay
        }
    }

    suspend fun setVibrationIntensity(intensity: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VIBRATION_INTENSITY] = intensity
        }
    }

    suspend fun setSafeZoneEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SAFE_ZONE_ENABLED] = enabled
        }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME] = theme
        }
    }

    suspend fun setModoSimulacion(activo: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MODO_SIMULACION] = activo
        }
    }

    suspend fun setPitchSimulado(pitch: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PITCH_SIMULADO] = pitch.coerceIn(0f, 90f)
        }
    }
}
