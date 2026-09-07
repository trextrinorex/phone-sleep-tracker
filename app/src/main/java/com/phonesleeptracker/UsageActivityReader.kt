package com.phonesleeptracker

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class UsageActivityReader(private val context: Context) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasUsageAccess(): Boolean {
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - 60_000, now)
        return events != null
    }

    fun openUsageAccessSettings(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Returns timestamps for foreground app transitions in the requested window. */
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
