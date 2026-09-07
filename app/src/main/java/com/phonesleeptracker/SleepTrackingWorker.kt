package com.phonesleeptracker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SleepTrackingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val reader = UsageActivityReader(applicationContext)
        if (!reader.hasUsageAccess()) return Result.success()

        val now = System.currentTimeMillis()
        val start = now - 36L * 60L * 60L * 1000L
        val activity = reader.foregroundActivityTimes(start, now)
        val candidate = SleepTrackerRepository().inferFromActivity(activity)
            ?.takeIf { it.confidence >= 70 }
            ?: return Result.success()

        val dao = SleepTrackerDatabase.getInstance(applicationContext).sleepSessionDao()
        val zone = ZoneId.systemDefault()
        val startTime = candidate.start.atZone(zone).toInstant().toEpochMilli()
        val endTime = candidate.end.atZone(zone).toInstant().toEpochMilli()

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
