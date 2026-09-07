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

/**
 * Reads lightweight, privacy-preserving activity signals from Android UsageStats.
 *
 * We only extract timestamps of:
 * - ACTIVITY_RESUMED (app came to foreground)
 * - SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE
 *
 * We never read message contents, passwords, photos, or audio.
 * Charging signals are currently limited; future versions may add BatteryManager broadcasts.
 */
class UsageActivityReader(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun phoneSignals(startMillis: Long, endMillis: Long): List<PhoneSignal> {
        if (endMillis <= startMillis) return emptyList()

        val events = usageStatsManager.queryEvents(startMillis, endMillis)
        val result = mutableListOf<PhoneSignal>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val time = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.timeStamp),
                ZoneId.systemDefault()
            )
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    result += PhoneSignal(time, PhoneSignal.Type.APP_RESUMED)
                UsageEvents.Event.SCREEN_INTERACTIVE ->
                    result += PhoneSignal(time, PhoneSignal.Type.SCREEN_ON)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE ->
                    result += PhoneSignal(time, PhoneSignal.Type.SCREEN_OFF)
                // Note: charging events are not reliably available via UsageEvents on all devices.
                // Future enhancement: register a BatteryManager / Intent.ACTION_BATTERY_CHANGED receiver.
            }
        }

        // Deduplicate identical (time, type) pairs that can appear from OEM quirks
        return result
            .distinctBy { it.time to it.type }
            .sortedBy { it.time }
    }

    fun foregroundActivityTimes(startMillis: Long, endMillis: Long): List<LocalDateTime> =
        phoneSignals(startMillis, endMillis)
            .filter { it.type == PhoneSignal.Type.APP_RESUMED }
            .map { it.time }
}
