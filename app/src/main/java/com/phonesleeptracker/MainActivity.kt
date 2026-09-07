package com.phonesleeptracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val usageReader by lazy { UsageActivityReader(this) }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshAccess()
    }

    private var accessState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accessState = usageReader.hasUsageAccess()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SleepTrackerHome(
                        hasUsageAccess = accessState,
                        onOpenSettings = { settingsLauncher.launch(usageReader.openUsageAccessSettings()) },
                        onRefreshAccess = { refreshAccess() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccess()
    }

    private fun refreshAccess() {
        accessState = usageReader.hasUsageAccess()
    }
}

@androidx.compose.runtime.Composable
private fun SleepTrackerHome(
    hasUsageAccess: Boolean,
    onOpenSettings: () -> Unit,
    onRefreshAccess: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("tracking", 0) }
    var tracking by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    val sessions by remember {
        SleepTrackerDatabase.getInstance(context).sleepSessionDao().observeAll()
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(hasUsageAccess, tracking) {
        if (hasUsageAccess && tracking) TrackingScheduler.start(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Phone Sleep Tracker", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Estimate sleep from passive smartphone activity. No smartwatch or wearable required.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Permanent disclaimer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    "Sleep duration is estimated from smartphone activity and may be inaccurate. " +
                        "This app is not a medical device and does not measure sleep stages, " +
                        "heart rate, or blood oxygen.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (!hasUsageAccess) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("One permission needed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Usage Access lets the app see lightweight activity timestamps " +
                                "(when apps come to the foreground and screen interactive state). " +
                                "It does not read messages, passwords, photos, or microphone audio.",
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                        )
                        Button(onClick = onOpenSettings) { Text("Grant Usage Access") }
                        OutlinedButton(
                            onClick = onRefreshAccess,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("I granted it – check again")
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Automatic tracking", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (tracking) {
                            "Background checks are enabled. The app will periodically look for " +
                                "likely sleep periods using on-device inference."
                        } else {
                            "Turn this on once. The app will periodically process activity " +
                                "timestamps and estimate sleep windows locally."
                        },
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                    )
                    Button(
                        enabled = hasUsageAccess,
                        onClick = {
                            tracking = !tracking
                            prefs.edit().putBoolean("enabled", tracking).apply()
                            if (tracking) TrackingScheduler.start(context)
                            else TrackingScheduler.stop(context)
                        }
                    ) {
                        Text(if (tracking) "Stop tracking" else "Start tracking")
                    }
                }
            }
        }

        item {
            Text("Estimated sleep history", style = MaterialTheme.typography.titleLarge)
            if (sessions.isNotEmpty()) {
                val avg = sessions.take(7).map { it.durationMinutes }.average()
                val hours = (avg / 60).toInt()
                val mins = (avg % 60).toInt()
                Text(
                    "7-night average (estimated): ${hours}h ${mins}m",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    "No sleep session estimated yet. Leave tracking on overnight and check back " +
                        "in the morning. Estimates appear after the phone detects a sufficiently " +
                        "long inactivity window that scores as likely sleep."
                )
            }
        } else {
            items(sessions.take(14), key = { it.id }) { session ->
                SleepSessionCard(session)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "All processing happens on your device. Raw activity events are not uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun SleepSessionCard(session: SleepSessionEntity) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, d MMM • h:mm a") }
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(session.startEpochMillis).atZone(zone).format(formatter)
    val end = Instant.ofEpochMilli(session.endEpochMillis).atZone(zone).format(formatter)
    val hours = session.durationMinutes / 60
    val minutes = session.durationMinutes % 60

    val confidenceLabel = when {
        session.confidence >= 80 -> "High"
        session.confidence >= 60 -> "Moderate"
        else -> "Low"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Estimated ${hours}h ${minutes}m",
                style = MaterialTheme.typography.headlineSmall
            )
            Text("$start → $end", modifier = Modifier.padding(top = 4.dp))
            Text(
                "Confidence: $confidenceLabel (${session.confidence}%)",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
