package com.postura.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DailyRedMinutes(
    val dateString: String,
    val redMinutes: Float
)

data class ZoneCount(
    val zone: String,
    val totalSeconds: Long
)

@Dao
interface PostureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PostureEntity)

    @Query("""
        SELECT dateString, (SUM(secondsInZone) / 60.0) as redMinutes 
        FROM posture_logs 
        WHERE zone = 'ROJO' AND dateString >= :startDateString 
        GROUP BY dateString 
        ORDER BY dateString ASC
    """)
    fun getRedMinutesLastDays(startDateString: String): Flow<List<DailyRedMinutes>>

    @Query("""
        SELECT zone, SUM(secondsInZone) as totalSeconds 
        FROM posture_logs 
        WHERE dateString = :dateString 
        GROUP BY zone
    """)
    fun getZoneCountsForDate(dateString: String): Flow<List<ZoneCount>>

    @Query("""
        SELECT zone, SUM(secondsInZone) as totalSeconds 
        FROM posture_logs 
        WHERE dateString >= :startDateString 
        GROUP BY zone
    """)
    fun getZoneCountsSince(startDateString: String): Flow<List<ZoneCount>>

    @Query("""
        SELECT dateString, 
               SUM(CASE WHEN zone = 'VERDE' THEN secondsInZone ELSE 0 END) * 1.0 / NULLIF(SUM(secondsInZone), 0) as greenRatio
        FROM posture_logs
        GROUP BY dateString
        ORDER BY dateString DESC
        LIMIT 30
    """)
    suspend fun getDailyGreenRatios(): List<DailyGreenRatio>

    @Query("SELECT * FROM posture_logs ORDER BY timestamp DESC")
    suspend fun getAllRecordsForExport(): List<PostureEntity>
}

data class DailyGreenRatio(
    val dateString: String,
    val greenRatio: Double?
)
