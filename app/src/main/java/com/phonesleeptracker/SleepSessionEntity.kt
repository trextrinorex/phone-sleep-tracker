package com.phonesleeptracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val confidence: Int
) {
    val durationMinutes: Long
        get() = (endEpochMillis - startEpochMillis) / 60_000L
}
