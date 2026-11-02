package dev.gymassistant.m0

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gymassistant.engine.SessionStatus
import dev.gymassistant.engine.snapshot
import dev.gymassistant.session.WorkoutModel
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    private val workout by viewModels<WorkoutModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val ui by workout.ui.collectAsState()
            var confirmation by remember { mutableStateOf<String?>(null) }
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Gym Assistant", style = MaterialTheme.typography.headlineSmall)
                        when {
                            ui.loading -> Text("Loading saved workout…")
                            ui.error != null -> {
                                Text(checkNotNull(ui.error))
                                WorkoutButton("Retry saved checkpoint") { workout.retry() }
                            }
                            ui.saved == null -> {
                                Text("Training Template", style = MaterialTheme.typography.headlineMedium)
                                Text("Synthetic alternating pyramid · 50 seconds")
                                Text("Starts an ungrouped Workout Session. Saved on this phone; cloud sync is not connected.")
                                WorkoutButton("Start Workout") { workout.command("start") }
                            }
                            else -> {
                                val saved = checkNotNull(ui.saved)
                                val state = saved.state
                                val view = snapshot(saved.start.plan, state)
                                Text("Session ${saved.start.sessionId.take(8)} · saved locally")
                                Text("Template revision ${saved.start.plan.revisionId}")
                                if (saved.checkpoint.outcome != "inProgress") {
                                    Text(if (saved.checkpoint.outcome == "discarded") "Workout discarded; partial record retained" else "Workout saved",
                                        style = MaterialTheme.typography.headlineMedium)
                                } else {
                                    Text(view.phaseName ?: "Complete", style = MaterialTheme.typography.displaySmall)
                                    Text(formatMs(view.phaseRemainingMs), style = MaterialTheme.typography.displayMedium)
                                    Text("${view.exerciseId ?: "Rest"} · ${view.variationId ?: ""}")
                                    Text("Set ${view.setId ?: "—"} · rep ${view.repetitionIndex ?: "—"}")
                                    Text(view.resistanceLabel.ifBlank { "No resistance" })
                                }
                                Text("Scheduled ${state.scheduledElapsedMs} ms · observed ${state.observedRunMs} ms")
                                Text("Pause ${state.pauseMs} ms · unobserved ${state.unobservedGapMs} ms")
                                if (saved.checkpoint.unknownGapCount > 0) {
                                    Text("${saved.checkpoint.unknownGapCount} interruption(s) with unknown duration. No exercise was inferred from them.")
                                }
                                Text("Sync pending · Drive is not connected")
                                when {
                                    saved.checkpoint.outcome != "inProgress" ->
                                        WorkoutButton("New Workout") { workout.command("new") }
                                    state.status == SessionStatus.Completed -> {
                                        Text("Review the saved workout, then finish. Skipped phases were not marked complete.")
                                        WorkoutButton("Complete Workout") { workout.command("finish") }
                                        if (ui.needsRecovery) {
                                            WorkoutButton("Discard workout…") { confirmation = "discard" }
                                        }
                                    }
                                    ui.needsRecovery -> {
                                        Text("Unfinished workout recovered from its saved plan. Resume from this checkpoint or discard it. Unobserved time was not counted as exercise.")
                                        WorkoutButton("Resume saved workout") { workout.command("resume") }
                                        WorkoutButton("Discard workout…") { confirmation = "discard" }
                                        WorkoutButton("Save partial workout…") { confirmation = "end" }
                                    }
                                    else -> {
                                        Text("${state.completedPhaseIndexes.size} phases completed · ${state.status.name}")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (state.status == SessionStatus.Running) {
                                                WorkoutButton("Pause") { workout.command("pause") }
                                            } else {
                                                WorkoutButton("Resume") { workout.command("resume") }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            WorkoutButton("Back") { workout.command("back") }
                                            WorkoutButton("Forward") { workout.command("forward") }
                                        }
                                        WorkoutButton("End Workout…") { confirmation = "end" }
                                    }
                                }
                            }
                        }
                    }
                    if (confirmation != null) {
                        val discard = confirmation == "discard"
                        AlertDialog(onDismissRequest = { confirmation = null },
                            title = { Text(if (discard) "Discard this workout?" else "End this workout?") },
                            text = { Text("The recorded partial progress will be retained. This does not change the Training Template.") },
                            confirmButton = { TextButton(onClick = {
                                workout.command(checkNotNull(confirmation)); confirmation = null
                            }) { Text(if (discard) "Discard" else "Save partial workout") } },
                            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFFFFFDF8), contentColor = Color(0xFF1C1915))) {
        Text(label)
    }
}

private fun formatMs(ms: Long): String {
    val seconds = ceil(ms / 1000.0).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
