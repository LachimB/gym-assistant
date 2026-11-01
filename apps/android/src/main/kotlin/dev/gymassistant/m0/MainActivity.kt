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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gymassistant.engine.EngineCommand
import dev.gymassistant.engine.EngineState
import dev.gymassistant.engine.SessionStatus
import dev.gymassistant.engine.compileTemplate
import dev.gymassistant.engine.initialState
import dev.gymassistant.engine.reduce
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
                    val stateHolder = remember { mutableStateOf(initialState()) }
                    val state = stateHolder.value
                    val view = snapshot(plan, state)
                    LaunchedEffect(Unit) {
                        var lastTick = SystemClock.elapsedRealtime()
                        while (true) {
                            kotlinx.coroutines.delay(50)
                            val now = SystemClock.elapsedRealtime()
                            val delta = now - lastTick
                            lastTick = now
                            val current = stateHolder.value
                            if (current.status == SessionStatus.Running && delta > 0) {
                                stateHolder.value = reduce(plan, current, EngineCommand.Advance(delta))
                            }
                        }
                    }
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { stateHolder.value = reduce(plan, stateHolder.value, EngineCommand.Start) }) { Text("Start") }
                            Button(onClick = { stateHolder.value = reduce(plan, stateHolder.value, EngineCommand.Pause) }) { Text("Pause") }
                            Button(onClick = { stateHolder.value = reduce(plan, stateHolder.value, EngineCommand.Resume) }) { Text("Resume") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { stateHolder.value = reduce(plan, stateHolder.value, EngineCommand.StepBackward) }) { Text("Back") }
                            Button(onClick = { stateHolder.value = reduce(plan, stateHolder.value, EngineCommand.StepForward) }) { Text("Forward") }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val seconds = ceil(ms / 1000.0).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
