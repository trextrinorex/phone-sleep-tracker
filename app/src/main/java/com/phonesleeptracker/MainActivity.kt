package com.phonesleeptracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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

    LaunchedEffect(hasUsageAccess) {
        if (hasUsageAccess && tracking) TrackingScheduler.start(context)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Sleep Tracker", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Estimate sleep from passive phone activity. No smartwatch required.",
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (!hasUsageAccess) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("One permission needed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Usage access lets the app see lightweight app activity timestamps. It does not read messages or passwords.",
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                        )
                        Button(onClick = onOpenSettings) { Text("Grant usage access") }
                        OutlinedButton(onClick = onRefreshAccess, modifier = Modifier.padding(top = 8.dp)) {
                            Text("I granted it")
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
                        if (tracking) "Background checks are enabled." else "Turn this on once and the app will periodically look for likely sleep periods.",
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                    )
                    Button(
                        enabled = hasUsageAccess,
                        onClick = {
                            tracking = !tracking
                            prefs.edit().putBoolean("enabled", tracking).apply()
                            if (tracking) TrackingScheduler.start(context) else TrackingScheduler.stop(context)
                        }
                    ) { Text(if (tracking) "Stop tracking" else "Start tracking") }
                }
            }
        }

        item {
            Text("Estimated sleep history", style = MaterialTheme.typography.titleLarge)
        }

        if (sessions.isEmpty()) {
            item {
                Text("No sleep session detected yet. Leave tracking on overnight and check back in the morning.")
            }
        } else {
            items(sessions.take(7), key = { it.id }) { session ->
                SleepSessionCard(session)
            }
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("$hours h $minutes min", style = MaterialTheme.typography.headlineSmall)
            Text("$start → $end", modifier = Modifier.padding(top = 4.dp))
            Text("Confidence: ${session.confidence}%", modifier = Modifier.padding(top = 4.dp))
        }
    }
}
