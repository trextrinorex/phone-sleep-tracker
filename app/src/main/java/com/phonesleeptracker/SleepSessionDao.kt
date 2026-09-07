package com.phonesleeptracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {
    @Insert
    suspend fun insert(session: SleepSessionEntity)

    @Query("SELECT * FROM sleep_sessions ORDER BY startEpochMillis DESC")
    fun observeAll(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions WHERE startEpochMillis = :start AND endEpochMillis = :end LIMIT 1")
    suspend fun findByWindow(start: Long, end: Long): SleepSessionEntity?
}
