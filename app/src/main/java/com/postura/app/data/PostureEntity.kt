package com.postura.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posture_logs")
data class PostureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val dateString: String, // Formato "YYYY-MM-DD"
    val zone: String,       // "VERDE", "AMARILLO", "ROJO"
    val angle: Float,
    val secondsInZone: Int
)
