package com.postura.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.postura.app.ui.theme.PosturaTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PosturaViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permiso gestionado
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Comprobación de permiso de notificaciones para API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            val isCalibrated by viewModel.isCalibrated.collectAsState()

            PosturaTheme(themeSetting = appTheme) {
                var selectedTab by remember { mutableStateOf(0) }
                var showOnboarding by remember { mutableStateOf(false) }

                // Mostrar onboarding si no está calibrado
                if (!isCalibrated || showOnboarding) {
                    OnboardingScreen(
                        viewModel = viewModel,
                        onCalibrationFinished = {
                            showOnboarding = false
                            selectedTab = 0
                        }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Speed, contentDescription = "Monitoreo") },
                                    label = { Text("Monitoreo") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Estadísticas") },
                                    label = { Text("Estadísticas") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Configuración") },
                                    label = { Text("Ajustes") }
                                )
                            }
                        }
                    ) { innerPadding ->
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (selectedTab) {
                                0 -> MonitoringScreen(viewModel = viewModel)
                                1 -> StatsScreen(viewModel = viewModel)
                                2 -> SettingsScreen(
                                    viewModel = viewModel,
                                    onRecalibrateRequested = {
                                        showOnboarding = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
