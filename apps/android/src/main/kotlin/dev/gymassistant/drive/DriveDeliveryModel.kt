package dev.gymassistant.drive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gymassistant.m0.BuildConfig
import dev.gymassistant.session.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DriveDeliveryModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("synthetic-session-delivery-v1",0)
    private val sample by lazy {
        val assets=getApplication<Application>().assets
        SyntheticDeliverySample.create(assets.open("composite-pyramid.yaml").bufferedReader().use { it.readText() },
            listOf("bridge-band-family.yaml","plank-family.yaml").map { assets.open(it).bufferedReader().use { r -> r.readText() } })
    }
    private val mutableBusy=MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy
    private val mutableStatus=MutableStateFlow("No synthetic session sent. Your workout is unchanged.")
    val status: StateFlow<String> = mutableStatus
    private var sending=false
    @Volatile private var generation=0
    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state=RoomDeliveryStore(WorkoutDatabases.open(getApplication())).syntheticStatus(sample)
                if (!mutableBusy.value && generation == 0) mutableStatus.value=when(state) {
                    "delivered" -> "Previously delivered synthetic example retained. Retrying reuses its saved delivery identity. No real workout uploaded."
                    "conflict" -> "Synthetic delivery has a conflict. Local and remote versions remain preserved."
                    null -> "No synthetic session sent. Your workout is unchanged."
                    else -> "Pending synthetic delivery recovered. Authorize to retry the same saved file."
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if (!mutableBusy.value && generation == 0) mutableStatus.value="Saved delivery status could not be read. Local data is retained." }
        }
    }
    fun preview(): String = runCatching { "Generated example: Synthetic alternating pyramid · 50 seconds · reached planned end. " +
        "${sample.canonicalJson.toByteArray(Charsets.UTF_8).size} bytes. Destination: ${projectDriveLayout5(BuildConfig.PROJECT_DRIVE_LAYOUT)?.let {"Gym Assistant/test/SYNTHETIC M1"}?:"labelled SYNTHETIC M1 TEST folder"}. " +
        "This sample contains no activity recorded from you. Your unfinished workout stays paused." }.getOrDefault("Synthetic example unavailable. Nothing will be sent.")
    fun awaitingAuthorization() = mutableBusy.value && !sending
    fun begin(): Boolean {
        if (mutableBusy.value) return false
        if (!syntheticResourcesConfigured() || BuildConfig.SYNTHETIC_DRIVE_ACCOUNT.isBlank() || !BuildConfig.SYNTHETIC_DELIVERY_BUDGET_RESERVED) {
            mutableStatus.value="Synthetic delivery is disabled until its dedicated request allowance is reserved."; return false
        }
        if (runCatching { sample }.isFailure) { mutableStatus.value="Synthetic example could not be verified. Nothing sent."; return false }
        val attempts=preferences.getInt("authorization-attempts",0)
        if (attempts !in 0 until 6 || !preferences.edit().putInt("authorization-attempts",attempts+1).commit()) {
            mutableStatus.value="Synthetic authorization allowance exhausted. Stop for review; do not clear app data."; return false
        }
        generation++
        mutableBusy.value=true
        mutableStatus.value="Waiting for authorization for this synthetic example only…"
        return true
    }
    fun failed() {
        if (sending) return
        mutableBusy.value=false
        mutableStatus.value="Authorization cancelled. Local workout and pending delivery are retained."
    }
    fun authorized(scopes: Collection<String>, token: String?) {
        if (sending) return
        if (!mutableBusy.value || !exactDriveScope(scopes) || token.isNullOrBlank()) { failed(); return }
        sending=true
        viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO) {
                    GoogleDeliveryHttp(token) {
                        synchronized(preferences) {
                            val used=preferences.getInt("direct-requests",0)
                            if (used !in 0 until 20 || !preferences.edit().putInt("direct-requests",used+1).commit()) throw DeliveryFailure("budget")
                        }
                    }.use { http ->
                        val database=WorkoutDatabases.open(getApplication())
                        val project=ProjectDriveAccess5(database,projectDriveLayout5(BuildConfig.PROJECT_DRIVE_LAYOUT))
                        val remote=GoogleDeliveryRemote(http,project.gate("synthetic"),project::identity)
                        val identity=remote.identity()
                        if (!identity.email.equals(BuildConfig.SYNTHETIC_DRIVE_ACCOUNT,ignoreCase=true) || identity.permissionId.isBlank()) throw DeliveryFailure("accountMismatch")
                        val folder=remote.folder(SYNTHETIC_PARENT)
                        val marker=SYNTHETIC_MARKER
                        val names=if(folder.projectVerified)setOf("Gym Assistant — SYNTHETIC M1 TEST — $marker","SYNTHETIC M1") else setOf("Gym Assistant — SYNTHETIC M1 TEST — $marker")
                        if (folder.marker != marker || folder.name !in names ||
                            folder.mimeType != "application/vnd.google-apps.folder") throw DeliveryFailure("missingRoot")
                        val local=RoomDeliveryStore(database)
                        val at=Instant.now().toString()
                        val binding=local.bind(DriveConnectionRow(UUID.randomUUID().toString(),"googleDrive",identity.permissionId,
                            identity.email,SYNTHETIC_PARENT,marker,at,at))
                        local.prepareSynthetic(sample)
                        val job=local.enqueue(binding.connectionId,sample.saved.start.sessionId,UUID.randomUUID().toString())
                        SessionDeliverySender(local,remote,BuildConfig.SYNTHETIC_DRIVE_ACCOUNT,allowSynthetic=sample::permits).send(job.deliveryId)
                    }
                }
                mutableStatus.value=when(result) {
                    "delivered" -> "Synthetic example delivered and read back exactly. Retrying this delivery reuses its saved identity. No real workout uploaded."
                    "conflict" -> "Remote content differs. Both versions are retained; no overwrite attempted."
                    "awaitingAuthorization" -> "Google authorization expired. Delivery remains pending; authorize again to retry."
                    else -> "Synthetic delivery remains pending. Check the connection/account and retry when ready; no real workout uploaded."
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: DeliveryFailure) {
                mutableStatus.value=when(failure.reason) {
                    "accountMismatch" -> "The authorized account does not match the approved personal account. Delivery stopped."
                    "budget" -> "Synthetic request allowance exhausted. Stop for review; do not reset counters."
                    else -> "Synthetic delivery stopped safely. Check authorization and connection; local data is retained."
                }
            } catch(_: Exception) {
                mutableStatus.value="Synthetic delivery could not be verified. Local data and saved retry identity are retained."
            } finally { sending=false; mutableBusy.value=false }
        }
    }
}
