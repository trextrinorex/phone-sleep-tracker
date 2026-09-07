package com.phonesleeptracker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic background worker that:
 * 1. Reads recent phone activity signals
 * 2. Runs the sleep inference engine
 * 3. Persists high-confidence sessions while avoiding duplicates
 */
class SleepTrackingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val reader = UsageActivityReader(applicationContext)
        if (!reader.hasUsageAccess()) {
            Log.d(TAG, "Usage access not granted – skipping")
            return Result.success()
        }

        val dao = SleepTrackerDatabase.getInstance(applicationContext).sleepSessionDao()
        val recentSessions = dao.getRecent(14)
        val personalStats = SleepTrackerRepository.computePersonalStats(recentSessions)

        val candidate = SleepTrackerRepository(reader)
            .inferRecentSleep(personalStats = personalStats)
            ?.takeIf { it.confidence >= 65 }
            ?: return Result.success()

        val zone = java.time.ZoneId.systemDefault()
        val startTime = candidate.start.atZone(zone).toInstant().toEpochMilli()
        val endTime = candidate.end.atZone(zone).toInstant().toEpochMilli()

        // Avoid inserting near-duplicates (substantial temporal overlap)
        val overlapping = dao.findOverlapping(startTime, endTime)
        val isDuplicate = overlapping.any { existing ->
            val overlapStart = maxOf(existing.startEpochMillis, startTime)
            val overlapEnd = minOf(existing.endEpochMillis, endTime)
            val overlapMs = (overlapEnd - overlapStart).coerceAtLeast(0)
            val shorter = minOf(
                existing.endEpochMillis - existing.startEpochMillis,
                endTime - startTime
            )
            shorter > 0 && overlapMs.toDouble() / shorter > 0.5
        }

        if (!isDuplicate) {
            dao.insert(
                SleepSessionEntity(
                    startEpochMillis = startTime,
                    endEpochMillis = endTime,
                    confidence = candidate.confidence
                )
            )
            Log.d(TAG, "Saved estimated sleep session confidence=${candidate.confidence}")
        } else {
            Log.d(TAG, "Skipped overlapping session")
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "SleepTrackingWorker"
    }
}
