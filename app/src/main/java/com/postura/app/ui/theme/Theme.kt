package com.postura.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val EmeraldGreen = Color(0xFF10B981)
val AmberYellow = Color(0xFFF59E0B)
val CrimsonRed = Color(0xFFEF4444)

val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate100 = Color(0xFFF1F5F9)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    secondary = AmberYellow,
    error = CrimsonRed,
    background = Slate900,
    surface = Slate800,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Slate100,
    onSurface = Slate100
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldGreen,
    secondary = AmberYellow,
    error = CrimsonRed,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Slate900,
    onSurface = Slate900
)

@Composable
fun PosturaTheme(
    themeSetting: String = "SISTEMA",
    content: @Composable () -> Unit
) {
    val isDark = when (themeSetting) {
        "OSCURO" -> true
        "CLARO" -> false
        else -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
