package dev.gymassistant.session

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gymassistant.engine.v2.*
import kotlinx.serialization.json.*
import java.io.File
import dev.gymassistant.engine.CompiledPlan
import dev.gymassistant.engine.compileTemplate
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


data class WorkoutUi(
    val loading: Boolean = true,
    val saved: SavedWorkout? = null,
    val saved2: SavedWorkout2? = null,
    val templates: List<TemplateRevisionRow> = emptyList(),
    val candidate: TemplateCandidateRow? = null,
    val libraryMessage: String? = null,
    val libraryBusy: Boolean = false,
    val libraryPlan: CompiledPlan? = null,
    val needsRecovery: Boolean = false,
    val error: String? = null,
)

class WorkoutModel(application: Application) : AndroidViewModel(application) {
    private val database = WorkoutDatabases.open(application)
    private val driver = SessionDriver(RoomSessionStore(database), SystemClock::elapsedRealtime)
    private val contracts by lazy {
        val assets=getApplication<Application>().assets
        fun schema(name:String)=assets.open(name).bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
        ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    }
    private val library by lazy {TemplateLibrary3(database,LibraryFiles3(File(application.filesDir,"training-library")),contracts)}
    private val driver2 by lazy {SessionDriver2(RoomSessionStore2(database,contracts),SystemClock::elapsedRealtime)}
    private val gate = Mutex()
    private val mutableUi = MutableStateFlow(WorkoutUi())
    val ui: StateFlow<WorkoutUi> = mutableUi
    private var template = ""
    private var exercises = emptyList<String>()
    private var libraryPlan: CompiledPlan? = null

    init {
        retry()
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(250)
                gate.withLock {
                    if (mutableUi.value.error == null && !mutableUi.value.loading && (driver.shouldTick() || driver2.shouldTick())) {
                        guarded { if(modern())driver2.send("tick")else driver.send("tick"); publish() }
                    }
                }
            }
        }
    }

    fun retry() = viewModelScope.launch(Dispatchers.IO) {
        gate.withLock {
            if (!mutableUi.value.loading && mutableUi.value.error == null) return@withLock
            mutableUi.value = mutableUi.value.copy(loading = true, error = null)
            guarded {
                val slot=database.workouts().unfinished()
                val format=slot?.let { database.workouts().start(it.sessionId) }?.let { Json.parseToJsonElement(it.snapshotJson).jsonObject.getValue("formatVersion").jsonPrimitive.int }
                when(format) { null->readLibrary();1->driver.restore();2->{driver2.restore();mutableUi.value=mutableUi.value.copy(saved2=driver2.saved)};else->error("Unsupported saved workout") }
                mutableUi.value=mutableUi.value.copy(templates=library.published())
                publish()
            }
        }
    }

    fun command(kind: String) = viewModelScope.launch(Dispatchers.IO) {
        gate.withLock {
            if (mutableUi.value.error != null || mutableUi.value.loading) return@withLock
            guarded {
                when (kind) {
                    "start" -> {
                        if (mutableUi.value.saved != null || mutableUi.value.saved2 != null) return@guarded
                        if (template.isEmpty()) readLibrary()
                        val instant = Instant.now()
                        val zone = ZoneId.systemDefault()
                        val plan = checkNotNull(libraryPlan)
                        driver.start(SessionStart(UUID.randomUUID().toString(), template, exercises,
                            plan, hashPlan(plan), instant.toString(), zone.id, instant.atZone(zone).toLocalDate().toString()))
                        publish()
                    }
                    "new" -> {
                        check((mutableUi.value.saved2?.checkpoint?.outcome?:mutableUi.value.saved?.checkpoint?.outcome) != "inProgress")
                        readLibrary()
                        mutableUi.value = WorkoutUi(loading = false, libraryPlan = libraryPlan, templates=library.published())
                    }
                    else -> {
                        if(modern()) {
                            val additions=driver2.saved?.plan?.adjustments?.size
                            val next=driver2.send(kind)
                            if(kind=="add" && next.plan.adjustments.size==additions)mutableUi.value=mutableUi.value.copy(libraryMessage="The set ended before the tap could be saved; no extra repetition was added.")
                        }else driver.send(kind)
                        publish()
                    }
                }
            }
        }
    }

    private fun modern()=mutableUi.value.saved2!=null
    fun canImport()= !mutableUi.value.loading && mutableUi.value.error==null &&
        (mutableUi.value.saved2?.state?.status?:mutableUi.value.saved?.state?.status) !in setOf(dev.gymassistant.engine.SessionStatus.Running)
    fun importFiles(inputs:List<ImportFile3>)=viewModelScope.launch(Dispatchers.IO) {
        gate.withLock {
            if(!canImport()) {mutableUi.value=mutableUi.value.copy(libraryMessage="Pause the workout before importing.");return@withLock}
            mutableUi.value=mutableUi.value.copy(libraryBusy=true,libraryMessage=null)
            try {
                val candidate=library.stage(inputs)
                mutableUi.value=mutableUi.value.copy(candidate=candidate,libraryMessage=if(candidate.validationCode=="valid")"Review every phase before publishing."else "Import quarantined: invalid or unsupported source. Your published Templates are unchanged.")
            } catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){mutableUi.value=mutableUi.value.copy(libraryMessage="Import could not be copied or validated. Published Templates are unchanged.")}
            finally {mutableUi.value=mutableUi.value.copy(libraryBusy=false)}
        }
    }
    fun importFailed() { mutableUi.value=mutableUi.value.copy(libraryMessage="Unable to read the selected files. Choose one Template and its exercise YAML files, up to 1 MB each and 8 MB total.") }
    fun publishCandidate(id:String,sourceHash:String,planHash:String)=viewModelScope.launch(Dispatchers.IO) {
        gate.withLock {
            if(!canImport())return@withLock
            mutableUi.value=mutableUi.value.copy(libraryBusy=true)
            try {
                library.publish(id,sourceHash,planHash)
                mutableUi.value=mutableUi.value.copy(candidate=null,templates=library.published(),libraryMessage="Training Template published locally.")
            }catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){mutableUi.value=mutableUi.value.copy(libraryMessage="Publication rejected: the source, review or published revision changed. Import and review again; the previous revision remains available.")}
            finally {mutableUi.value=mutableUi.value.copy(libraryBusy=false)}
        }
    }
    fun startTemplate(templateId:String,revisionId:String)=viewModelScope.launch(Dispatchers.IO) {
        gate.withLock {
            if(mutableUi.value.loading || mutableUi.value.error!=null)return@withLock
            if(database.workouts().unfinished()!=null) {
                mutableUi.value=mutableUi.value.copy(libraryMessage="Finish or discard the unfinished workout before starting another. Its progress has been retained.");return@withLock
            }
            guarded {
                val head=checkNotNull(database.library().head(templateId));check(head.revisionId==revisionId)
                val row=checkNotNull(database.library().revision(templateId,revisionId));library.verify(row)
                val now=Instant.now();val zone=ZoneId.systemDefault()
                driver2.start(SessionStart2(UUID.randomUUID().toString(),row.sourceSnapshotJson,row.sourceHash,row.compiledPlanJson,row.compiledPlanHash,
                    now.toString(),zone.id,now.atZone(zone).toLocalDate().toString()))
                mutableUi.value=mutableUi.value.copy(saved=null,saved2=driver2.saved);publish()
            }
        }
    }
    private fun readLibrary() {
        val assets = getApplication<Application>().assets
        template = assets.open("composite-pyramid.yaml").bufferedReader().use { it.readText() }
        exercises = listOf("bridge-band-family.yaml", "plank-family.yaml").map { name ->
            assets.open(name).bufferedReader().use { it.readText() }
        }
        libraryPlan = compileTemplate(template, exercises)
    }

    private fun publish() {
        val modern=modern() || driver2.saved!=null && driver2.saved?.checkpoint?.outcome=="inProgress"
        mutableUi.value = mutableUi.value.copy(loading = false, saved = if(modern)null else driver.saved, saved2=if(modern)driver2.saved else null,
            libraryPlan = libraryPlan, needsRecovery = if(modern)driver2.needsRecovery else driver.needsRecovery)
    }

    private suspend fun guarded(action: suspend () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not log source/history/exception payloads. Timers stop until explicit reload.
            mutableUi.value = mutableUi.value.copy(loading = false, needsRecovery = true,
                error = "Unable to save or read the workout. Timing is paused. Retry loading the saved checkpoint; records have not been cleared.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Room operations may still be cancelling: no destructive cleanup or checkpoint replacement.
        // The process-owned Room instance is shared with Drive models and remains open.
    }
}
