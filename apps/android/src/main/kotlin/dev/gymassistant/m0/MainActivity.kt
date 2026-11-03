package dev.gymassistant.m0

import android.os.Bundle
import android.accounts.Account
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlList
import com.charleskorn.kaml.yamlScalar
import dev.gymassistant.engine.SessionStatus
import dev.gymassistant.engine.snapshot
import dev.gymassistant.session.WorkoutModel
import dev.gymassistant.drive.DriveProbeModel
import dev.gymassistant.drive.DRIVE_FILE_SCOPE
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    private val workout by viewModels<WorkoutModel>()
    private val driveProbe by viewModels<DriveProbeModel>()
    private val authorization by lazy { Identity.getAuthorizationClient(this) }
    private val driveResolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) driveProbe.failed()
        else try { completeDriveAuthorization(authorization.getAuthorizationResultFromIntent(result.data)) }
        catch (_: Exception) { driveProbe.failed() }
    }

    private fun completeDriveAuthorization(result: AuthorizationResult) {
        if (result.hasResolution()) { driveProbe.failed(); return }
        driveProbe.authorized(result.grantedScopes, result.accessToken)
    }

    private fun testDriveConnection() {
        val email = BuildConfig.SYNTHETIC_DRIVE_ACCOUNT
        if (!driveProbe.begin(email)) return
        try {
            val request = AuthorizationRequest.builder()
                .setAccount(Account(email, "com.google"))
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .setOptOutIncludingGrantedScopes(true)
                .build()
            authorization.authorize(request).addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending == null) driveProbe.failed()
                    else try { driveResolution.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }
                    catch (_: Exception) { driveProbe.failed() }
                } else completeDriveAuthorization(result)
            }.addOnFailureListener { driveProbe.failed() }
        } catch (_: Exception) { driveProbe.failed() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val ui by workout.ui.collectAsState()
            var showDetails by remember { mutableStateOf(false) }
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
                                val probeStatus by driveProbe.status.collectAsState()
                                val probeBusy by driveProbe.busy.collectAsState()
                                Text("Drive connection test", style = MaterialTheme.typography.titleMedium)
                                Text("Reads the labelled synthetic test files only. No workout upload.")
                                OutlinedTextField(value = BuildConfig.SYNTHETIC_DRIVE_ACCOUNT, onValueChange = {},
                                    readOnly = true, label = { Text("Approved personal Google account") })
                                Button(onClick = { testDriveConnection() },
                                    enabled = !probeBusy && BuildConfig.SYNTHETIC_DRIVE_ACCOUNT.isNotBlank()) {
                                    Text(if (probeBusy) "Checking…" else "Test personal Drive connection")
                                }
                                Text(probeStatus)
                            }
                            else -> {
                                val saved = checkNotNull(ui.saved)
                                val state = saved.state
                                val view = snapshot(saved.start.plan, state)
                                val labels = remember(saved.start.sessionId) { frozenExerciseLabels(saved.start.exerciseYamls) }
                                if (ui.needsRecovery && saved.checkpoint.outcome == "inProgress") {
                                    Text("Unfinished workout recovered", style = MaterialTheme.typography.headlineMedium)
                                    Text("Saved progress is paused. Unobserved time was not counted as exercise.")
                                    if (state.status == SessionStatus.Completed) {
                                        WorkoutButton("Complete Workout") { workout.command("finish") }
                                    } else {
                                        WorkoutButton("Resume saved workout") { workout.command("resume") }
                                        WorkoutButton("Save partial workout…") { confirmation = "end" }
                                    }
                                    WorkoutButton("Discard workout…") { confirmation = "discard" }
                                }
                                if (saved.checkpoint.outcome != "inProgress") {
                                    Text(if (saved.checkpoint.outcome == "discarded") "Workout discarded; partial record retained" else "Workout saved",
                                        style = MaterialTheme.typography.headlineMedium)
                                } else {
                                    Text(view.phaseName ?: "Complete", style = MaterialTheme.typography.displaySmall)
                                    Text(formatMs(view.phaseRemainingMs), style = MaterialTheme.typography.displayMedium)
                                    Text(view.exerciseId?.let { labels[it] ?: "Exercise" } ?: "Rest")
                                    view.variationId?.let { id -> Text(labels[id] ?: "Exercise variation") }
                                    view.repetitionIndex?.let { Text("Repetition $it") }
                                    Text(view.resistanceLabel.replace('_', ' ').ifBlank { "No resistance" })
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
                                    ui.needsRecovery -> Unit
                                    state.status == SessionStatus.Completed -> {
                                        Text("Review the saved workout, then finish. Skipped phases were not marked complete.")
                                        WorkoutButton("Complete Workout") { workout.command("finish") }
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
                                TextButton(onClick = { showDetails = !showDetails }) {
                                    Text(if (showDetails) "Hide source details" else "Source details")
                                }
                                if (showDetails) {
                                    Text("Session ${saved.start.sessionId}")
                                    Text("Template revision ${saved.start.plan.revisionId}")
                                    Text("Set ${view.setId ?: "—"}")
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

private fun frozenExerciseLabels(sources: List<String>): Map<String, String> = runCatching {
    buildMap<String, String> {
        for (source in sources) {
            val root = Yaml.default.parseToYamlNode(source).yamlMap
            fun label(node: com.charleskorn.kaml.YamlMap): String? {
                val names = node.get<YamlNode>("names")?.yamlMap ?: return null
                return (names.get<YamlNode>("en") ?: names.entries.values.firstOrNull())?.yamlScalar?.content
            }
            val family = root.get<YamlNode>("familyId")?.yamlScalar?.content
            if (family != null) label(root)?.let { put(family, it) }
            for (variation in root.get<YamlNode>("variations")?.yamlList?.items.orEmpty()) {
                val item = variation.yamlMap
                val id = item.get<YamlNode>("variationId")?.yamlScalar?.content
                if (id != null) label(item)?.let { put(id, it) }
            }
        }
    }
}.getOrDefault(emptyMap())
