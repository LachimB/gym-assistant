package dev.gymassistant.session

import androidx.room.withTransaction
import dev.gymassistant.sync.hashSourceBytes
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal class SourceCaptureReadBinding5(val checked:CheckedSourceCapture5,val rootMarker:String,val areaMarker:String)

/** Incomplete observations use the existing pending/changing row; they confer no candidate or adoption authority. */
class TemplateSourceCaptureStore5(private val db:WorkoutDatabase,private val files:TemplateSourceCaptureFiles5,private val clock:()->Long,
    private val beforePointerTransaction:suspend ()->Unit={},private val beforePointerCommit:()->Unit={}) {
    private val leases=TemplateSyncLeases5(db)
    private data class Scope(val binding:TemplateSyncBinding5,val root:ProjectStorageRoot5,val connection:DriveConnectionRow,val hash:String)
    private suspend fun scope(job:TemplateSyncJob5):Scope {
        val b=checkNotNull(db.templateSync().binding(job.bindingId));val r=checkNotNull(db.templateSync().root(b.rootBindingId));val c=checkNotNull(db.deliveries().connection(b.connectionId))
        check(b.enabled && r.state=="active" && r.provider==c.provider && r.permissionId==c.permissionId)
        val identity=listOf(b.bindingId,b.connectionId,b.rootBindingId,b.templateId,b.templateFolderId,b.workingFolderId,b.historyFolderId,
            b.metadataFolderId,b.headFileId,b.marker,r.provider,r.permissionId,r.commonRootFolderId,r.marker,c.rootFolderId,c.namespaceMarker)
        return Scope(b,r,c,hashSourceBytes(JsonArray(identity.map(::JsonPrimitive)).toString().toByteArray()))
    }
    private suspend fun validate(checked:CheckedSourceCapture5,job:TemplateSyncJob5,s:Scope) {
        checked.requireJob(job);val b=s.binding;val n=checked.namespace;val local=checked.local
        check(local.templateId==b.templateId && n.bindingHash==s.hash && n.permissionId==s.root.permissionId &&
            n.commonRootId==s.root.commonRootFolderId && n.areaFolderId==s.connection.rootFolderId && n.templateFolderId==b.templateFolderId &&
            n.workingFolderId==b.workingFolderId && n.historyFolderId==b.historyFolderId && n.metadataFolderId==b.metadataFolderId && n.headFileId==b.headFileId && n.marker==b.marker)
        check(b.lastRemoteGeneration==job.expectedRemoteGeneration && b.lastRemoteHeadTag==job.expectedRemoteHeadTag &&
            b.baseRevisionId==local.baseRevisionId && b.baseSourceHash==local.baseSourceHash)
        val revision=checkNotNull(db.templateSync().sourceHashes(b.templateId,job.expectedLocalRevisionId))
        check(revision.sourceHash==local.sourceHash && revision.compiledPlanHash==local.compiledPlanHash)
    }
    private fun validateRow(row:TemplateSyncObservation5,job:TemplateSyncJob5) {
        check(row.bindingId==job.bindingId && row.expectedLocalRevisionId==job.expectedLocalRevisionId && row.candidateId==null &&
            row.remoteGeneration==null && row.remoteHeadTag==null && row.validationCode=="changing" && row.reviewState=="pending")
        require(row.capturedAt.length in 1..64);Instant.parse(row.capturedAt)
    }
    suspend fun begin(lease:SyncLease5,observationId:String,namespace:SourceCaptureNamespace5,pins:List<SourceCapturePin5>,at:String):SyncIntentFile5=withContext(Dispatchers.IO) {
        require(observationId.matches(Regex("[A-Za-z0-9_-]{3,128}")) && at.length in 1..64);Instant.parse(at)
        db.templateSync().observation(observationId)?.let {existing->
            val checked=load(lease,observationId);check(checked.namespace==namespace && checked.pins==pins && existing.capturedAt==at)
            return@withContext checked.pointer
        }
        val job=leases.owned(lease,clock());val s=scope(job)
        val pointer=files.prepare(SyncIntentFile5(job.intentPath,job.intentHash),namespace,pins,observationId);val checked=files.load(pointer)
        validate(checked,job,s)
        val row=TemplateSyncObservation5(observationId,job.bindingId,observationPath=pointer.path,observationHash=pointer.sha256,
            expectedLocalRevisionId=job.expectedLocalRevisionId,validationCode="changing",capturedAt=at)
        beforePointerTransaction()
        db.withTransaction {
            check(leases.owned(lease,clock())==job && scope(job)==s);validate(checked,job,s);validateRow(row,job)
            db.templateSync().insertObservation(row);beforePointerCommit();leases.owned(lease,clock());check(scope(job)==s)
        }
        pointer
    }
    suspend fun load(lease:SyncLease5,observationId:String):CheckedSourceCapture5=withContext(Dispatchers.IO) {
        val job=leases.owned(lease,clock());val row=checkNotNull(db.templateSync().observation(observationId));val s=scope(job)
        val checked=files.load(SyncIntentFile5(row.observationPath,row.observationHash))
        db.withTransaction {check(leases.owned(lease,clock())==job && scope(job)==s && db.templateSync().observation(observationId)==row)
            validateRow(row,job);check(checked.observationId==observationId);validate(checked,job,s)}
        checked
    }
    suspend fun retainRaw(lease:SyncLease5,observationId:String,observation:PublicationRawObservation5):SyncIntentFile5=withContext(Dispatchers.IO) {
        val job=leases.owned(lease,clock());val row=checkNotNull(db.templateSync().observation(observationId));val s=scope(job)
        validateRow(row,job);val prior=SyncIntentFile5(row.observationPath,row.observationHash);val original=files.load(prior)
        check(original.observationId==observationId);validate(original,job,s)
        val next=files.retainRaw(prior,observation);val checked=files.load(next) // File/fsync/compiler work stays outside SQL.
        beforePointerTransaction()
        db.withTransaction {
            check(leases.owned(lease,clock())==job && scope(job)==s && db.templateSync().observation(observationId)==row)
            validateRow(row,job);check(checked.observationId==observationId);validate(checked,job,s)
            check(db.templateSync().advanceObservation(observationId,prior.path,prior.sha256,next.path,next.sha256)==1)
            beforePointerCommit();leases.owned(lease,clock());check(scope(job)==s)
        }
        next
    }
    /** Marker authority comes from the same current Room binding, never from caller-supplied folder labels. */
    internal suspend fun readBinding(lease:SyncLease5,observationId:String):SourceCaptureReadBinding5=withContext(Dispatchers.IO) {
        val checked=load(lease,observationId)
        db.withTransaction {
            val job=leases.owned(lease,clock());val s=scope(job);val row=checkNotNull(db.templateSync().observation(observationId))
            validateRow(row,job);validate(checked,job,s)
            check(SyncIntentFile5(row.observationPath,row.observationHash)==checked.pointer)
            SourceCaptureReadBinding5(checked,s.root.marker,s.connection.namespaceMarker)
        }
    }
}
