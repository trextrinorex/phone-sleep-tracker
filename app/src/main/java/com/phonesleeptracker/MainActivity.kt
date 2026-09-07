package com.phonesleeptracker

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SleepTrackerHome()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SleepTrackerHome() {
    var tracking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Sleep Tracker", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = if (tracking) "Tracking is active" else "Estimate sleep using your phone — no wearable needed.",
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )
        Button(onClick = { tracking = !tracking }) {
            Text(if (tracking) "Stop tracking" else "Start tracking")
        }
    }
}
