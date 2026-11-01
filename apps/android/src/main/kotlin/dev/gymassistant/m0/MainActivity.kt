package dev.gymassistant.m0

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gymassistant.engine.ClockedExecution
import dev.gymassistant.engine.EngineCommand
import dev.gymassistant.engine.SessionStatus
import dev.gymassistant.engine.compileTemplate
import dev.gymassistant.engine.snapshot
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val template = assets.open("composite-pyramid.yaml").bufferedReader().readText()
        val exercises = listOf(
            assets.open("bridge-band-family.yaml").bufferedReader().readText(),
            assets.open("plank-family.yaml").bufferedReader().readText(),
        )
        val plan = compileTemplate(template, exercises)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val clock = remember { ClockedExecution(plan, SystemClock::elapsedRealtime) }
                    val stateHolder = remember { mutableStateOf(clock.state) }
                    val state = stateHolder.value
                    val view = snapshot(plan, state)
                    LaunchedEffect(Unit) {
                        while (true) {
                            kotlinx.coroutines.delay(50)
                            stateHolder.value = clock.tick()
                        }
                    }
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.completionAcknowledged) {
                            Text("Gym Assistant", style = MaterialTheme.typography.headlineSmall)
                            Text("Workout complete", style = MaterialTheme.typography.displaySmall)
                            Text("Scheduled duration ${state.scheduledElapsedMs} ms")
                            Text("Observed execution ${state.observedRunMs} ms")
                            Text("This prototype does not save a Workout Session.")
                            PrototypeButton("Restart") { stateHolder.value = clock.reset() }
                        } else {
                            Text("Gym Assistant", style = MaterialTheme.typography.headlineSmall)
                            Text(view.phaseName ?: if (view.status == "completed") "Complete" else "Ready", style = MaterialTheme.typography.displaySmall)
                            Text(formatMs(view.phaseRemainingMs), style = MaterialTheme.typography.displayMedium)
                            Text("${view.exerciseId ?: "—"} · ${view.variationId ?: "—"}")
                            Text("Set ${view.setId ?: "—"} · rep ${view.repetitionIndex ?: "—"}")
                            Text(view.resistanceLabel.ifBlank { "No resistance" })
                            Text("Scheduled ${view.scheduledElapsedMs} ms · observed ${view.observedRunMs} ms")
                            Text("Pause ${view.pauseMs} ms · unobserved ${view.unobservedGapMs} ms")
                            Text(view.simulatedCue?.plus(". This is not device audio.") ?: "No simulated cue.")
                            if (state.status == SessionStatus.RecoveredPaused) {
                                Text("Timing was unobserved. The gap was not counted as exercise. Resume continues from the last checkpoint.")
                            }
                            if (state.status == SessionStatus.Completed) {
                                PrototypeButton("Complete Workout") {
                                    stateHolder.value = clock.send(EngineCommand.Acknowledge)
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PrototypeButton("Start") { stateHolder.value = clock.send(EngineCommand.Start) }
                                    PrototypeButton("Pause") { stateHolder.value = clock.send(EngineCommand.Pause) }
                                    PrototypeButton("Resume") { stateHolder.value = clock.send(EngineCommand.Resume) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PrototypeButton("Back") { stateHolder.value = clock.send(EngineCommand.StepBackward) }
                                    PrototypeButton("Forward") { stateHolder.value = clock.send(EngineCommand.StepForward) }
                                }
                                if (
                                    state.status == SessionStatus.Running ||
                                    state.status == SessionStatus.Paused ||
                                    state.status == SessionStatus.RecoveredPaused
                                ) {
                                    PrototypeButton("End Workout") {
                                        stateHolder.value = clock.send(EngineCommand.EndEarly)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val PrototypeButtonFill = Color(0xFFFFFDF8)
private val PrototypeButtonLabel = Color(0xFF1C1915)

@Composable
private fun PrototypeButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrototypeButtonFill,
            contentColor = PrototypeButtonLabel,
        ),
    ) {
        Text(label, color = PrototypeButtonLabel)
    }
}

private fun formatMs(ms: Long): String {
    val seconds = ceil(ms / 1000.0).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
