package com.postura.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PostureEntity::class], version = 1, exportSchema = false)
abstract class PostureDatabase : RoomDatabase() {

    abstract fun postureDao(): PostureDao

    companion object {
        @Volatile
        private var INSTANCE: PostureDatabase? = null

        fun getDatabase(context: Context): PostureDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PostureDatabase::class.java,
                    "posture_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
