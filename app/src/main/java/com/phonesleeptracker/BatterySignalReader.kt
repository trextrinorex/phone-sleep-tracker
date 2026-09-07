package com.phonesleeptracker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Lightweight battery / charging signal helper.
 *
 * Charging is treated only as *supporting evidence* for a sleep candidate.
 * Presence of charging never forces a sleep classification by itself.
 *
 * Current limitation: we only observe the *present* charging state at the
 * moment of inference. A future enhancement can persist POWER_CONNECTED /
 * POWER_DISCONNECTED events via a BroadcastReceiver into Room for true
 * historical overlap detection.
 */
class BatterySignalReader(private val context: Context) {

    data class Snapshot(
        val isCharging: Boolean,
        val isPlugged: Boolean,
        val observedAt: LocalDateTime
    )

    fun currentSnapshot(): Snapshot {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val isPlugged = plugged != 0

        return Snapshot(
            isCharging = isCharging,
            isPlugged = isPlugged,
            observedAt = LocalDateTime.now(ZoneId.systemDefault())
        )
    }

    /**
     * Produce a synthetic CHARGING_START signal if the device is currently
     * charging or plugged in. The timestamp is placed a few minutes after the
     * candidate start so it falls inside the nearby-signal window used by scoring.
     */
    fun chargingSignalsForWindow(candidateStart: LocalDateTime): List<PhoneSignal> {
        val snap = currentSnapshot()
        if (!snap.isCharging && !snap.isPlugged) return emptyList()

        // Place the signal a little after the inactivity start so it is considered
        // "during" the candidate window by the nearby filter (±45 min).
        val signalTime = candidateStart.plusMinutes(10)
        return listOf(PhoneSignal(signalTime, PhoneSignal.Type.CHARGING_START))
    }
}
