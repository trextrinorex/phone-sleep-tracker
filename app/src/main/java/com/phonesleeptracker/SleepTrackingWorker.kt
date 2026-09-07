package com.phonesleeptracker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SleepTrackingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val reader = UsageActivityReader(applicationContext)
        if (!reader.hasUsageAccess()) return Result.success()

        val candidate = SleepTrackerRepository(reader).inferRecentSleep()
            ?.takeIf { it.confidence >= 70 }
            ?: return Result.success()

        val zone = java.time.ZoneId.systemDefault()
        val startTime = candidate.start.atZone(zone).toInstant().toEpochMilli()
        val endTime = candidate.end.atZone(zone).toInstant().toEpochMilli()
        val dao = SleepTrackerDatabase.getInstance(applicationContext).sleepSessionDao()

        if (dao.findByWindow(startTime, endTime) == null) {
            dao.insert(
                SleepSessionEntity(
                    startEpochMillis = startTime,
                    endEpochMillis = endTime,
                    confidence = candidate.confidence
                )
            )
        }
        return Result.success()
    }
}
