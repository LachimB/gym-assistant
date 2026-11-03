package dev.gymassistant.session

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val libraryPlan: CompiledPlan? = null,
    val needsRecovery: Boolean = false,
    val error: String? = null,
)

class WorkoutModel(application: Application) : AndroidViewModel(application) {
    private val database = WorkoutDatabases.open(application)
    private val driver = SessionDriver(RoomSessionStore(database), SystemClock::elapsedRealtime)
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
                    if (mutableUi.value.error == null && !mutableUi.value.loading && driver.shouldTick()) {
                        guarded { driver.send("tick"); publish() }
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
                val saved = driver.restore()
                if (saved == null) readLibrary()
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
                        if (mutableUi.value.saved != null) return@guarded
                        if (template.isEmpty()) readLibrary()
                        val instant = Instant.now()
                        val zone = ZoneId.systemDefault()
                        val plan = checkNotNull(libraryPlan)
                        driver.start(SessionStart(UUID.randomUUID().toString(), template, exercises,
                            plan, hashPlan(plan), instant.toString(), zone.id, instant.atZone(zone).toLocalDate().toString()))
                        publish()
                    }
                    "new" -> {
                        check(mutableUi.value.saved?.checkpoint?.outcome != "inProgress")
                        readLibrary()
                        mutableUi.value = WorkoutUi(loading = false, libraryPlan = libraryPlan)
                    }
                    else -> { driver.send(kind); publish() }
                }
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
        mutableUi.value = WorkoutUi(loading = false, saved = driver.saved,
            libraryPlan = libraryPlan, needsRecovery = driver.needsRecovery)
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
        database.close()
    }
}
