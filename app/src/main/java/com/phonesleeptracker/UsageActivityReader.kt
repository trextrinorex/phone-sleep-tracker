package com.phonesleeptracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class UsageActivityReader(private val context: Context) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun foregroundActivityTimes(startMillis: Long, endMillis: Long): List<LocalDateTime> {
        val events = usageStatsManager.queryEvents(startMillis, endMillis)
        val result = mutableListOf<LocalDateTime>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                result += LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(event.timeStamp),
                    ZoneId.systemDefault()
                )
            }
        }
        return result
    }
}
