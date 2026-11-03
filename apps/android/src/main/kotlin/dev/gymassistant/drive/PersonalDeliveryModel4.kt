package dev.gymassistant.drive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gymassistant.m0.BuildConfig
import dev.gymassistant.session.*
import dev.gymassistant.engine.v2.ImportContracts2
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*

internal data class PersonalSessionChoice4(val id:String,val revision:Long,val hash:String,val label:String,val bytes:Int)
class PersonalDeliveryModel4(application:Application):AndroidViewModel(application) {
    private val preferences=application.getSharedPreferences("personal-session-delivery-v1",0)
    private val db by lazy {WorkoutDatabases.open(application)}
    private val store by lazy {
        fun schema(n:String)=Json.parseToJsonElement(application.assets.open(n).bufferedReader().use {it.readText()}).jsonObject
        PersonalDeliveryStore4(db,DeliveryPayloadFiles4(File(application.filesDir,"delivery-payloads")),ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json")))
    }
    internal val choices=MutableStateFlow<List<PersonalSessionChoice4>>(emptyList())
    private val mutableBusy=MutableStateFlow(false);val busy:StateFlow<Boolean> = mutableBusy
    private val mutableStatus=MutableStateFlow("No session selected. Nothing uploads automatically.");val status:StateFlow<String> = mutableStatus
    private var selection:PersonalSessionChoice4?=null
    private var sending=false
    fun refresh() {if(mutableBusy.value)return;viewModelScope.launch(Dispatchers.IO){try {
        choices.value=store.eligible().map {h->val (revision,current)=store.preview(h.sessionId);val canonical=current.toCanonicalJson()
            val time=Instant.parse(h.startedAt).atZone(java.time.ZoneId.of(h.timeZone)).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
            val title=dev.gymassistant.engine.v2.planJson2.decodeFromString<dev.gymassistant.engine.v2.Plan2>(h.sourcePlanSnapshot.compiledPlanJson).templateName
            PersonalSessionChoice4(h.sessionId,revision,sha256Utf8(canonical),"${h.localTrainingDate} $time ${h.timeZone} · $title · ${if(h.completionKind=="partial")"Partial" else "Completed"}",canonical.toByteArray(Charsets.UTF_8).size)}
        mutableStatus.value=if(choices.value.isEmpty())"No eligible finished local workout. Unfinished and discarded tests cannot be sent." else "Choose a finished session to review."
    }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){mutableStatus.value="Session list could not be verified. Nothing sent."}}}
    internal fun select(choice:PersonalSessionChoice4){if(!mutableBusy.value)selection=choice}
    internal fun selected()=selection
    fun awaitingAuthorization()=mutableBusy.value && !sending
    fun begin():Boolean {
        if(mutableBusy.value || selection==null)return false
        if(checkNotNull(selection).bytes !in 1..DeliveryPayloadFiles4.LIMIT){mutableStatus.value="This recording exceeds the 8 MB delivery limit. Every local fact is retained; nothing will be truncated or sent.";return false}
        if(BuildConfig.SYNTHETIC_DRIVE_ACCOUNT.isBlank() || !BuildConfig.PERSONAL_DELIVERY_BUDGET_RESERVED){mutableStatus.value="Personal delivery is disabled until its request allowance is reserved.";return false}
        val attempts=preferences.getInt("authorization-attempts",0)
        if(attempts !in 0 until 4 || !preferences.edit().putInt("authorization-attempts",attempts+1).commit()){mutableStatus.value="Authorization allowance exhausted. Stop for review; do not clear data.";return false}
        mutableBusy.value=true;mutableStatus.value="Waiting for authorization for the selected PERSONAL session…";return true
    }
    fun failed(){if(sending)return;mutableBusy.value=false;mutableStatus.value="Authorization cancelled. Session and pending delivery retained."}
    fun authorized(scopes:Collection<String>,token:String?) {
        if(sending)return
        if(!awaitingAuthorization() || !exactDriveScope(scopes) || token.isNullOrBlank()){failed();return}
        val chosen=checkNotNull(selection);sending=true
        viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO) {
                    // Revalidate the reviewed bytes before any folder or upload request.
                    val (revision,h)=store.preview(chosen.id)
                    check(revision==chosen.revision && sha256Utf8(h.toCanonicalJson())==chosen.hash)
                    GoogleDeliveryHttp(token){synchronized(preferences){val used=preferences.getInt("direct-requests",0)
                        if(used !in 0 until 50 || !preferences.edit().putInt("direct-requests",used+1).commit())throw DeliveryFailure("budget")}}.use {http->
                        val (setup,remote)=ensurePersonalRoot4(PersonalSetupFiles4(File(getApplication<Application>().filesDir,"personal-drive/setup.json")),http,BuildConfig.SYNTHETIC_DRIVE_ACCOUNT)
                        val at=Instant.now().toString()
                        val binding=RoomDeliveryStore(db).bind(DriveConnectionRow(UUID.randomUUID().toString(),"googleDrive",setup.permissionId,setup.email,setup.folderId,setup.marker,at,at))
                        val job=store.enqueue(binding.connectionId,chosen.id,UUID.randomUUID().toString(),chosen.revision,chosen.hash)
                        SessionDeliverySender(store,remote,BuildConfig.SYNTHETIC_DRIVE_ACCOUNT,personalTarget=PersonalDeliveryTarget4(binding)).send(job.deliveryId)
                    }
                }
                mutableStatus.value=when(result){"delivered"->"Personal session delivered and read back exactly. Retry reuses the same saved file.";"conflict"->"Remote content differs. Both copies retained; no overwrite.";"awaitingAuthorization"->"Authorization expired. Session retained; authorize again to retry.";else->"Delivery pending. Session and retry identity retained."}
            }catch(cancelled:CancellationException){throw cancelled}
            catch(f:DeliveryFailure){mutableStatus.value=when(f.reason){"conflict"->"Remote content differs. Local recording and remote file are retained; no overwrite or replacement.";"accountMismatch"->"Account mismatch. Nothing sent to that account.";"missingRoot"->"Saved personal folder is missing or inaccessible. No replacement created.";"budget"->"Request allowance exhausted. Stop for review; do not reset counters.";else->"Delivery stopped safely. Local session and saved retry identity retained."}}
            catch(_:Exception){mutableStatus.value="Delivery could not be verified. Local session and saved retry identity retained."}
            finally {sending=false;mutableBusy.value=false}
        }
    }
}
