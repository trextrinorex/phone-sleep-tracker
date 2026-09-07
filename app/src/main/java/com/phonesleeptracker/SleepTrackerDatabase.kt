package com.phonesleeptracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SleepSessionEntity::class], version = 1, exportSchema = false)
abstract class SleepTrackerDatabase : RoomDatabase() {
    abstract fun sleepSessionDao(): SleepSessionDao

    companion object {
        @Volatile private var INSTANCE: SleepTrackerDatabase? = null

        fun getInstance(context: Context): SleepTrackerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SleepTrackerDatabase::class.java,
                    "sleep_tracker.db"
                ).build().also { INSTANCE = it }
            }
    }
}
