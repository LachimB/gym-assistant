package dev.gymassistant.m0

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import android.provider.OpenableColumns
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.gymassistant.session.*
import dev.gymassistant.engine.v2.*
import android.accounts.Account
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import dev.gymassistant.drive.DriveDeliveryModel
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
    private val driveDelivery by viewModels<DriveDeliveryModel>()
    private val personalDelivery by viewModels<dev.gymassistant.drive.PersonalDeliveryModel4>()
    private val templateFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty())lifecycleScope.launch {
            try {
                val inputs=withContext(Dispatchers.IO) {
                    require(uris.size in 2..33)
                    var total=0L
                    uris.map { uri ->
                        val name=contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
                            if(cursor.moveToFirst())cursor.getString(0)else "selected.yaml"
                        }?:"selected.yaml"
                        val bytes=checkNotNull(contentResolver.openInputStream(uri)).use { stream ->
                            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                            while(true) {val count=stream.read(buffer);if(count<0)break;require(output.size()+count<=1048576);total+=count;require(total<=8388608);output.write(buffer,0,count)}
                            output.toByteArray()
                        }
                        ImportFile3(name,bytes)
                    }
                }
                workout.importFiles(inputs)
            }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
            catch(_:Exception){workout.importFailed()}
        }
    }
    private val authorization by lazy { Identity.getAuthorizationClient(this) }
    private val driveResolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) failDriveAuthorization()
        else try { completeDriveAuthorization(authorization.getAuthorizationResultFromIntent(result.data)) }
        catch (_: Exception) { failDriveAuthorization() }
    }

    private fun failDriveAuthorization() {
        if(personalDelivery.awaitingAuthorization()) personalDelivery.failed()
        else if (driveDelivery.awaitingAuthorization()) driveDelivery.failed() else driveProbe.failed()
    }

    private fun completeDriveAuthorization(result: AuthorizationResult) {
        if (result.hasResolution()) { failDriveAuthorization(); return }
        if(personalDelivery.awaitingAuthorization()) personalDelivery.authorized(result.grantedScopes,result.accessToken)
        else if (driveDelivery.awaitingAuthorization()) driveDelivery.authorized(result.grantedScopes, result.accessToken)
        else driveProbe.authorized(result.grantedScopes, result.accessToken)
    }

    private fun testDriveConnection(delivery: Boolean = false, personal: Boolean = false) {
        if (driveProbe.busy.value || driveDelivery.busy.value || personalDelivery.busy.value) return
        val email = BuildConfig.SYNTHETIC_DRIVE_ACCOUNT
        if(personal) {if(!personalDelivery.begin())return}
        else if (delivery) { if (!driveDelivery.begin()) return } else if (!driveProbe.begin(email)) return
        try {
            val request = AuthorizationRequest.builder()
                .setAccount(Account(email, "com.google"))
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .setOptOutIncludingGrantedScopes(true)
                .build()
            authorization.authorize(request).addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending == null) failDriveAuthorization()
                    else try { driveResolution.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }
                    catch (_: Exception) { failDriveAuthorization() }
                } else completeDriveAuthorization(result)
            }.addOnFailureListener { failDriveAuthorization() }
        } catch (_: Exception) { failDriveAuthorization() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val ui by workout.ui.collectAsState()
            var showDetails by remember { mutableStateOf(false) }
            var confirmation by remember { mutableStateOf<String?>(null) }
            var showLibrary by remember { mutableStateOf(false) }
            var showDriveTest by remember { mutableStateOf(false) }
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Gym Assistant", style = MaterialTheme.typography.headlineSmall)
                        if (ui.needsRecovery && ui.saved?.checkpoint?.outcome == "inProgress") {
                            TextButton(onClick = { showDriveTest = true }) { Text("Drive connection test") }
                        }
                        TextButton(onClick={showLibrary=true},enabled=workout.canImport()) {Text("Training Template library")}
                        PersonalDeliveryControls4()
                        if(!showLibrary)ui.libraryMessage?.let {Text(it)}
                        when {
                            ui.loading -> Text("Loading saved workout…")
                            ui.error != null -> {
                                Text(checkNotNull(ui.error))
                                WorkoutButton("Retry saved checkpoint") { workout.retry() }
                            }
                            ui.saved2 != null -> ModernWorkout(checkNotNull(ui.saved2),ui.needsRecovery,
                                {workout.command(it)}, {confirmation=it})
                            ui.saved == null -> {
                                Text("Training Template", style = MaterialTheme.typography.headlineMedium)
                                Text("Synthetic alternating pyramid · 50 seconds")
                                Text("Starts an ungrouped Workout Session. Saved on this phone. Finished local sessions may be sent manually after review.")
                                WorkoutButton("Start Workout") { workout.command("start") }
                                DriveTestControls()
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
                                Text("Sync pending · this workout has not been uploaded")
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
                    if(showLibrary) {
                        AlertDialog(onDismissRequest={if(!ui.libraryBusy)showLibrary=false},
                            title={Text("Training Template library")},
                            text={Column(modifier=Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text("Choose one Template and its exercise YAML files. Imports are stored privately on this phone. Review the complete plan before publishing.")
                                Button(onClick={templateFiles.launch(arrayOf("*/*"))},enabled=!ui.libraryBusy && workout.canImport()) {Text("Import selected YAML files")}
                                ui.libraryMessage?.let {Text(it)}
                                ui.candidate?.takeIf {it.validationCode=="valid"}?.let {candidate ->
                                    val plan=remember(candidate.candidateId) {planJson2.decodeFromString<Plan2>(checkNotNull(candidate.compiledPlanJson))}
                                    Text("Review: ${plan.templateName}")
                                    Text("Revision ${plan.revisionId} · ${formatMs(plan.expectedDurationMs)} · ${plan.phases.size} phases")
                                    Text("Current published revision: ${candidate.expectedHeadRevisionId?:"none"}")
                                    plan.phases.forEachIndexed {index,phase->
                                        Text("${index+1}. ${phase.exerciseName?:phase.name} · ${phase.variationName.orEmpty()} · ${phase.side?:"—"} · ${phase.name} · ${formatMs(phase.endMs-phase.startMs)}${phase.repetitionIndex?.let {" · repetition $it"}.orEmpty()}\n${resistanceText2(phase.resistance)}${if(plan.policies.any {it.setId==phase.setId})" · +1 repetition supported" else ""}")
                                    }
                                }
                                ui.templates.forEach {row ->
                                    val plan=remember(row.compiledPlanHash){planJson2.decodeFromString<Plan2>(row.compiledPlanJson)}
                                    Text("${plan.templateName} · ${formatMs(plan.expectedDurationMs)}")
                                    Button(onClick={workout.startTemplate(row.templateId,row.revisionId);showLibrary=false},enabled=!ui.libraryBusy && (ui.saved2?.checkpoint?.outcome?:ui.saved?.checkpoint?.outcome)!="inProgress") {Text("Start ${plan.templateName}")}
                                }
                                if((ui.saved2?.checkpoint?.outcome?:ui.saved?.checkpoint?.outcome)=="inProgress")Text("Your unfinished workout is retained. Finish or discard it before starting another.")
                            }},confirmButton={
                                val candidate=ui.candidate?.takeIf {it.validationCode=="valid"}
                                if(candidate!=null)Button(onClick={workout.publishCandidate(candidate.candidateId,checkNotNull(candidate.sourceHash),checkNotNull(candidate.compiledPlanHash))},enabled=!ui.libraryBusy && workout.canImport()) {Text("Publish reviewed revision")}
                                else TextButton(onClick={showLibrary=false},enabled=!ui.libraryBusy){Text("Close")}
                            },dismissButton={
                                if(ui.candidate?.validationCode=="valid")TextButton(onClick={showLibrary=false},enabled=!ui.libraryBusy){Text("Close")}
                            })
                    }
                    if (showDriveTest) {
                        AlertDialog(onDismissRequest = { showDriveTest = false },
                            title = { Text("Drive connection test") },
                            text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { DriveTestControls() } },
                            confirmButton = { TextButton(onClick = { showDriveTest = false }) { Text("Close") } })
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

    @Composable
    private fun PersonalDeliveryControls4() {
        var show by remember {mutableStateOf(false)}
        var selected by remember {mutableStateOf<dev.gymassistant.drive.PersonalSessionChoice4?>(null)}
        val choices by personalDelivery.choices.collectAsState()
        val busy by personalDelivery.busy.collectAsState()
        val status by personalDelivery.status.collectAsState()
        val reviewNote by personalDelivery.reviewNote.collectAsState()
        val otherBusy by driveDelivery.busy.collectAsState()
        val probeBusy by driveProbe.busy.collectAsState()
        TextButton(onClick={show=true;selected=null;personalDelivery.refresh()},enabled=!busy && !otherBusy && !probeBusy) {Text("Send a finished session to personal Drive…")}
        if(show) AlertDialog(onDismissRequest={if(!busy)show=false},title={Text("Personal session delivery")},
            text={Column(modifier=Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Account: ${BuildConfig.SYNTHETIC_DRIVE_ACCOUNT}")
                Text("Destination: Gym Assistant — PERSONAL M1 DEV. Created privately on the first send. No automatic uploads.")
                if(selected==null)choices.forEach {choice->TextButton(onClick={selected=choice;personalDelivery.select(choice)}) {Text(choice.label)}}
                selected?.let {choice->
                    Text(choice.label);Text(reviewNote);Text("${choice.bytes} bytes. Includes frozen workout instructions, meaningful events, dates and timing. Internal ticks and the original PDF stay off Drive.")
                    Text("Send freezes this exact recording. Retry uses the same file; conflicts stop without overwriting.")
                    TextButton(onClick={selected=null},enabled=!busy){Text("Choose another session")}
                }
                Text(status)
            }},
            confirmButton={TextButton(onClick={testDriveConnection(personal=true)},enabled=selected!=null && !busy && !otherBusy && !probeBusy && BuildConfig.PERSONAL_DELIVERY_BUDGET_RESERVED){Text(if(busy)"Sending…" else "Send or retry selected session")}},
            dismissButton={TextButton(onClick={show=false},enabled=!busy){Text("Close")}})
    }

    @Composable
    private fun DriveTestControls() {
        val probeStatus by driveProbe.status.collectAsState()
        val probeBusy by driveProbe.busy.collectAsState()
        Text("Reads the labelled synthetic test files only. No workout upload.")
        OutlinedTextField(value = BuildConfig.SYNTHETIC_DRIVE_ACCOUNT, onValueChange = {},
            readOnly = true, label = { Text("Approved personal Google account") })
        Button(onClick = { testDriveConnection() },
            enabled = !probeBusy && !driveDelivery.busy.value && BuildConfig.SYNTHETIC_DRIVE_ACCOUNT.isNotBlank()) {
            Text(if (probeBusy) "Checking…" else "Test personal Drive connection")
        }
        Text(probeStatus)
        val deliveryBusy by driveDelivery.busy.collectAsState()
        val deliveryStatus by driveDelivery.status.collectAsState()
        var preview by remember { mutableStateOf(false) }
        TextButton(onClick = { preview = true }, enabled = !probeBusy && !deliveryBusy) { Text("Preview synthetic session delivery") }
        Text(deliveryStatus)
        if (preview) AlertDialog(onDismissRequest = { if (!deliveryBusy) preview = false },
            title = { Text("Send generated synthetic example?") },
            text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Account: ${BuildConfig.SYNTHETIC_DRIVE_ACCOUNT}")
                Text(driveDelivery.preview())
                Text(deliveryStatus)
            } },
            confirmButton = { TextButton(onClick = { testDriveConnection(delivery = true) },
                enabled = !deliveryBusy && !probeBusy && BuildConfig.SYNTHETIC_DELIVERY_BUDGET_RESERVED) { Text(if (deliveryBusy) "Sending…" else "Send or retry synthetic example") } },
            dismissButton = { TextButton(onClick = { preview = false }, enabled = !deliveryBusy) { Text("Close") } })
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


private fun resistanceText2(value:Resistance2?):String = buildList {
    value?.weight?.let {add("${it.value} ${it.unit}")}
    value?.bands?.forEach {add("${it.equipmentId.replace('_',' ')} ×${it.count} ${it.placement}")}
    value?.notes?.let {add(it)}
}.joinToString(" · ").ifBlank {"No resistance"}

@Composable
private fun ModernWorkout(saved:SavedWorkout2,recovery:Boolean,command:(String)->Unit,confirm:(String)->Unit) {
    val cp=saved.checkpoint;val state=saved.state;val phase=activePhase2(saved.plan,state.cursorMs)
    if(recovery && cp.outcome=="inProgress") {
        Text("Unfinished workout recovered",style=MaterialTheme.typography.headlineMedium)
        Text("Saved progress is paused. Unobserved time was not counted as exercise.")
        WorkoutButton(if(state.status==SessionStatus.Completed)"Complete Workout" else "Resume saved workout") {command(if(state.status==SessionStatus.Completed)"finish" else "resume")}
        WorkoutButton("Save partial workout…") {confirm("end")}
        WorkoutButton("Discard workout…") {confirm("discard")}
    }
    if(cp.outcome!="inProgress")Text(if(cp.outcome=="discarded")"Workout discarded; partial record retained" else "Workout saved",style=MaterialTheme.typography.headlineMedium)
    else {
        Text(phase?.name?:"Complete",style=MaterialTheme.typography.displaySmall)
        Text(formatMs(phase?.let {it.endMs-state.cursorMs}?:0),style=MaterialTheme.typography.displayMedium)
        phase?.exerciseName?.let {Text(it)};phase?.variationName?.let {Text(it)}
        phase?.side?.let {Text("Side: ${it.replaceFirstChar {it.uppercase()}}")}
        phase?.repetitionIndex?.let {Text("Repetition $it")}
        Text(resistanceText2(phase?.resistance))
    }
    Text("Planned ${formatMs(saved.plan.originalDurationMs)} · adjusted ${formatMs(saved.plan.expectedDurationMs)}")
    Text("Observed ${formatMs(state.observedRunMs)} · pause ${formatMs(state.pauseMs)}")
    Text("Unobserved ${formatMs(state.unobservedGapMs)} · ${cp.unknownGapCount} interruption(s) of unknown duration")
    Text("${saved.plan.adjustments.size} extra repetition(s) · ${state.completedInstanceIds.size} phases completed")
    Text("Saved on this phone · check personal Drive delivery for send status")
    when {
        cp.outcome!="inProgress"->WorkoutButton("New Workout"){command("new")}
        recovery->Unit
        state.status==SessionStatus.Completed->WorkoutButton("Complete Workout"){command("finish")}
        else->{
            WorkoutButton(if(state.status==SessionStatus.Running)"Pause" else "Resume"){command(if(state.status==SessionStatus.Running)"pause" else "resume")}
            if(additionAvailable2(saved.plan,state)!=null)WorkoutButton("+1 repetition"){command("add")}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                WorkoutButton("Back"){command("back")};WorkoutButton("Forward"){command("forward")}
            }
            WorkoutButton("End Workout…"){confirm("end")}
        }
    }
}
