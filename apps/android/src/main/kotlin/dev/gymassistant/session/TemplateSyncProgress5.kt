package dev.gymassistant.session

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

@Serializable data class WorkingReceipt5(val objectId:String,val tag:String,val sha256:String) {
    init {require(objectId.matches(Regex("[A-Za-z0-9_-]{1,160}")))
        require(tag.length in 1..1024 && tag.none {it.isISOControl()})
        require(sha256.matches(Regex("[a-f0-9]{64}")))}
}
@Serializable private data class ProgressDocument5(val kind:String,val schemaVersion:String,val jobId:String,val operationId:String,
    val bindingId:String,val capturedPath:String,val capturedHash:String,val receipts:List<WorkingReceipt5>)

class CheckedSyncProgress5(val capturedPointer:SyncIntentFile5,val captured:CheckedSyncIntent5,records:List<WorkingReceipt5>) {
    val receipts=records.associateBy {it.objectId}
    val allFilesAcknowledged get()=this.receipts.size==captured.steps.size
    init {
        require(records.size<=16 && this.receipts.size==records.size)
        val steps=captured.steps.associateBy {it.objectId}
        for(receipt in records)check(steps[receipt.objectId]?.intendedHash==receipt.sha256) {"Receipt differs from captured source"}
    }
    fun requireJob(job:TemplateSyncJob5) {
        check(captured.jobId==job.jobId && captured.operationId==job.operationId && captured.bindingId==job.bindingId &&
            captured.expectedLocalRevisionId==job.expectedLocalRevisionId && captured.manifest.sourceHash==job.sourceHash &&
            captured.manifest.compiledPlanHash==job.compiledPlanHash) {"Job and captured source differ"}
    }
}

/** An additive private format; original captured intents/before-images and every acknowledged file are retained. */
class TemplateSyncProgressFiles5(private val files:TemplateSyncFiles5) {
    private val json=Json {encodeDefaults=true;ignoreUnknownKeys=false}
    fun load(pointer:SyncIntentFile5):CheckedSyncProgress5 {
        val raw=files.readRecord(pointer);val root=Json.parseToJsonElement(raw).jsonObject
        if("kind" !in root)return CheckedSyncProgress5(pointer,files.load(pointer),emptyList())
        val dto=json.decodeFromString<ProgressDocument5>(raw)
        check(dto.kind=="workingProgress" && dto.schemaVersion=="1.0.0")
        val capturedPointer=SyncIntentFile5(dto.capturedPath,dto.capturedHash)
        check(capturedPointer!=pointer)
        // Only original captured documents can be linked; nested progress/cycles fail the strict captured decoder.
        val captured=files.load(capturedPointer)
        check(dto.jobId==captured.jobId && dto.operationId==captured.operationId && dto.bindingId==captured.bindingId)
        return CheckedSyncProgress5(capturedPointer,captured,dto.receipts)
    }
    fun acknowledge(pointer:SyncIntentFile5,receipt:WorkingReceipt5):SyncIntentFile5 {
        val prior=load(pointer)
        check(prior.captured.steps.singleOrNull {it.objectId==receipt.objectId}?.intendedHash==receipt.sha256) {"Unexpected working-file result"}
        prior.receipts[receipt.objectId]?.let {check(it==receipt) {"Changed result needs a new observation"};return pointer}
        val dto=ProgressDocument5("workingProgress","1.0.0",prior.captured.jobId,prior.captured.operationId,prior.captured.bindingId,
            prior.capturedPointer.path,prior.capturedPointer.sha256,prior.receipts.values.toList()+receipt)
        val next=files.writeRecord(json.encodeToString(dto));load(next);return next
    }
}

/** Files/compilation finish outside the database transaction; an expired or disabled owner cannot commit their pointer. */
class TemplateSyncProgressStore5(private val db:WorkoutDatabase,private val files:TemplateSyncProgressFiles5,private val clock:()->Long,
    private val beforePointerCommit:()->Unit={}) {
    private val leases=TemplateSyncLeases5(db)
    private suspend fun requireBinding(progress:CheckedSyncProgress5,row:TemplateSyncJob5):Triple<TemplateSyncBinding5,ProjectStorageRoot5,DriveConnectionRow> {
        progress.requireJob(row);check(row.direction=="push")
        val binding=checkNotNull(db.templateSync().binding(row.bindingId))
        check(progress.captured.manifest.templateId==binding.templateId && progress.captured.manifest.revisionId==row.expectedLocalRevisionId)
        check(progress.captured.steps.all {it.folderId==binding.workingFolderId && it.marker==binding.marker})
        val revision=checkNotNull(db.library().revision(binding.templateId,row.expectedLocalRevisionId))
        check(revision.sourceHash==row.sourceHash && revision.compiledPlanHash==row.compiledPlanHash)
        return Triple(binding,checkNotNull(db.templateSync().root(binding.rootBindingId)),checkNotNull(db.deliveries().connection(binding.connectionId)))
    }
    suspend fun load(lease:SyncLease5):CheckedSyncProgress5=withContext(Dispatchers.IO) {
        val row=leases.owned(lease,clock())
        val result=files.load(SyncIntentFile5(row.intentPath,row.intentHash))
        db.withTransaction {check(leases.owned(lease,clock())==row);requireBinding(result,row)}
        result
    }
    suspend fun acknowledge(lease:SyncLease5,receipt:WorkingReceipt5):SyncIntentFile5=withContext(Dispatchers.IO) {
        val prior=leases.owned(lease,clock());val pointer=SyncIntentFile5(prior.intentPath,prior.intentHash)
        val checked=files.load(pointer);val bindingSnapshot=requireBinding(checked,prior)
        val next=files.acknowledge(pointer,receipt) // Fsync/rename before any SQL pointer mutation.
        db.withTransaction {
            check(leases.owned(lease,clock())==prior) {"Working intent changed during verification"}
            check(requireBinding(checked,prior)==bindingSnapshot) {"Sync binding changed during verification"}
            if(next!=pointer)check(db.templateSync().advanceIntent(prior.jobId,lease.owner,lease.generation,pointer.path,pointer.sha256,next.path,next.sha256)==1)
            beforePointerCommit()
        }
        next
    }
}
