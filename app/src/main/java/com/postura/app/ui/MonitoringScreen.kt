package com.postura.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postura.app.sensor.SignalQuality
import com.postura.app.service.PostureZone
import com.postura.app.ui.theme.AmberYellow
import com.postura.app.ui.theme.CrimsonRed
import com.postura.app.ui.theme.EmeraldGreen

@Composable
fun MonitoringScreen(
    viewModel: PosturaViewModel
) {
    val serviceState by viewModel.serviceState.collectAsState()

    // Transición suave de color del círculo en 300ms
    val targetCircleColor = when (serviceState.zone) {
        PostureZone.VERDE -> EmeraldGreen
        PostureZone.AMARILLO -> AmberYellow
        PostureZone.ROJO -> CrimsonRed
    }

    val animatedColor by animateColorAsState(
        targetValue = if (serviceState.isRunning) targetCircleColor else Color.Gray,
        animationSpec = tween(durationMillis = 300),
        label = "PostureCircleColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Indicador de Calidad de Señal
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Monitoreo en Tiempo Real",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            val (qualityText, qualityColor) = when (serviceState.signalQuality) {
                SignalQuality.ALTA -> "Señal: Alta" to EmeraldGreen
                SignalQuality.MEDIA -> "Señal: Media" to AmberYellow
                SignalQuality.BAJA -> "Interferencia" to CrimsonRed
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(qualityColor.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = qualityText,
                    color = qualityColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Círculo Central con Ángulo Grande en Grados
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(CircleShape)
                .background(animatedColor.copy(alpha = 0.15f))
                .border(6.dp, animatedColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (serviceState.isRunning) "${String.format("%.1f", serviceState.currentAngle)}°" else "--°",
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = animatedColor
                )
                Text(
                    text = if (serviceState.isRunning) {
                        when (serviceState.zone) {
                            PostureZone.VERDE -> "Correcta"
                            PostureZone.AMARILLO -> "Inclinado"
                            PostureZone.ROJO -> "Corrige postura"
                        }
                    } else "Detenido",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }

        // Control de simulación: Slider 0° a 90° si modoSimulacion está activo
        val isSimulation by viewModel.modoSimulacion.collectAsState()
        val simulatedPitch by viewModel.pitchSimulado.collectAsState()
        if (isSimulation) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Control Simulado",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                        Text(
                            text = "${simulatedPitch.toInt()}°",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    androidx.compose.material3.Slider(
                        value = simulatedPitch,
                        onValueChange = { viewModel.setPitchSimulado(it) },
                        valueRange = 0f..90f,
                        steps = 89
                    )
                }
            }
        }

        // Contadores y Métricas Rápidas
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val minutes = serviceState.redSecondsToday / 60
                    val seconds = serviceState.redSecondsToday % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed
                    )
                    Text(
                        text = "Tiempo en rojo hoy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${serviceState.correctionsToday}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                    Text(
                        text = "Correcciones hoy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Botón Grande Iniciar / Detener
        Button(
            onClick = { viewModel.toggleMonitoring() },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (serviceState.isRunning) CrimsonRed else EmeraldGreen
            )
        ) {
            Icon(
                imageVector = if (serviceState.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (serviceState.isRunning) "Detener" else "Iniciar",
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (serviceState.isRunning) "Detener Monitoreo" else "Iniciar Monitoreo",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
