package com.phonesleeptracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: SleepSessionEntity): Long

    @Query("SELECT * FROM sleep_sessions ORDER BY startEpochMillis DESC")
    fun observeAll(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions ORDER BY startEpochMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SleepSessionEntity>

    /** Exact window match (legacy). */
    @Query("SELECT * FROM sleep_sessions WHERE startEpochMillis = :start AND endEpochMillis = :end LIMIT 1")
    suspend fun findByWindow(start: Long, end: Long): SleepSessionEntity?

    /**
     * Detect substantial overlap with an existing session.
     * Two sessions are considered the same if they overlap by more than 50% of the shorter one.
     */
    @Query("""
        SELECT * FROM sleep_sessions
        WHERE startEpochMillis < :end
          AND endEpochMillis > :start
        LIMIT 5
    """)
    suspend fun findOverlapping(start: Long, end: Long): List<SleepSessionEntity>
}
