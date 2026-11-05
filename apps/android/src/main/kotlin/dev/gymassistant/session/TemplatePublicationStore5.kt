package dev.gymassistant.session

import androidx.room.withTransaction
import dev.gymassistant.sync.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Network work is external. Only a current leased owner can commit a retained publication pointer. */
class TemplatePublicationStore5(private val db:WorkoutDatabase,private val files:TemplatePublicationFiles5,
    private val working:TemplateSyncProgressStore5,private val clock:()->Long,
    private val beforePointerTransaction:suspend ()->Unit={},private val beforePointerCommit:()->Unit={}) {
    private val leases=TemplateSyncLeases5(db)
    private data class Scope(val binding:TemplateSyncBinding5,val root:ProjectStorageRoot5,val connection:DriveConnectionRow,val hash:String)
    private suspend fun scope(job:TemplateSyncJob5):Scope {
        val b=checkNotNull(db.templateSync().binding(job.bindingId));val r=checkNotNull(db.templateSync().root(b.rootBindingId))
        val c=checkNotNull(db.deliveries().connection(b.connectionId))
        check(b.enabled && r.state=="active" && r.provider==c.provider && r.permissionId==c.permissionId)
        val identity=listOf(b.bindingId,b.connectionId,b.rootBindingId,b.templateId,b.templateFolderId,b.workingFolderId,b.historyFolderId,
            b.metadataFolderId,b.headFileId,b.marker,r.provider,r.permissionId,r.commonRootFolderId,r.marker,c.rootFolderId,c.namespaceMarker)
        return Scope(b,r,c,hashSourceBytes(JsonArray(identity.map(::JsonPrimitive)).toString().toByteArray()))
    }
    private suspend fun validate(checked:CheckedPublication5,job:TemplateSyncJob5,s:Scope) {
        checked.requireJob(job);check(job.state=="running");val b=s.binding
        check(checked.bindingHash==s.hash && checked.permissionId==s.root.permissionId &&
            checked.intent.objectId==b.headFileId && checked.intent.metadataFolderId==b.metadataFolderId && checked.intent.marker==b.marker &&
            checked.intent.intended.templateId==b.templateId && checked.intent.intended.workingFolderId==b.workingFolderId &&
            job.expectedRemoteGeneration==b.lastRemoteGeneration && job.expectedRemoteHeadTag==b.lastRemoteHeadTag)
        val expected=checked.intent.expected
        check((expected==null)==(b.baseSourceHash==null))
        if(expected!=null)check(expected.sourceHash==b.baseSourceHash)
        val namespaceIds=setOf(s.root.commonRootFolderId,s.connection.rootFolderId,b.templateFolderId,b.workingFolderId,b.historyFolderId,b.metadataFolderId,b.headFileId)
        check(checked.revisionFolderId !in namespaceIds && checked.immutableSteps.none {it.objectId in namespaceIds})
        check(checked.working.captured.steps.all {it.folderId==b.workingFolderId && it.marker==b.marker})
        val revision=checkNotNull(db.templateSync().sourceHashes(b.templateId,job.expectedLocalRevisionId))
        check(revision.sourceHash==job.sourceHash && revision.compiledPlanHash==job.compiledPlanHash)
    }
    private suspend fun commit(lease:SyncLease5,prior:TemplateSyncJob5,snapshot:Scope,next:SyncIntentFile5) {
        val checked=files.load(next) // Full file/hash/compiler work stays outside the SQL transaction.
        beforePointerTransaction()
        db.withTransaction {
            check(leases.owned(lease,clock())==prior);check(scope(prior)==snapshot)
            validate(checked,prior,snapshot)
            if(next.path!=prior.intentPath || next.sha256!=prior.intentHash)
                check(db.templateSync().advanceIntent(prior.jobId,lease.owner,lease.generation,prior.intentPath,prior.intentHash,next.path,next.sha256)==1)
            beforePointerCommit()
            leases.owned(lease,clock()) // Recheck expiry after the SQL write, while it can still roll back.
            check(scope(prior)==snapshot)
        }
    }
    suspend fun begin(lease:SyncLease5,revisionFolderId:String,intent:PublicationHeadIntent,
        initialObservation:PublicationHeadSnapshot?,headAbsenceVerified:Boolean):SyncIntentFile5=withContext(Dispatchers.IO) {
        val prior=leases.owned(lease,clock());val s=scope(prior)
        val pointer=SyncIntentFile5(prior.intentPath,prior.intentHash)
        if(files.isPublication(pointer)) {
            val existing=files.load(pointer);validate(existing,prior,s);val captured=existing.intent
            check(existing.revisionFolderId==revisionFolderId && captured.operationId==intent.operationId && captured.objectId==intent.objectId &&
                captured.metadataFolderId==intent.metadataFolderId && captured.marker==intent.marker && captured.expected==intent.expected &&
                captured.expectedTag==intent.expectedTag && captured.intended==intent.intended && headAbsenceVerified==(captured.expected==null))
            val original=existing.observations.first()
            if(original==null)check(initialObservation==null)
            else check(initialObservation!=null && initialObservation.head==original.head && initialObservation.tag==original.tag &&
                initialObservation.metadataBytes.contentEquals(original.metadataBytes) && initialObservation.contentBytes.contentEquals(original.contentBytes))
            commit(lease,prior,s,pointer);return@withContext pointer
        }
        val completed=working.load(lease);check(completed.allFilesAcknowledged)
        check(intent.operationId==prior.operationId)
        val next=files.prepare(pointer,s.hash,s.root.permissionId,revisionFolderId,intent,initialObservation,headAbsenceVerified)
        validate(files.load(next),prior,s);commit(lease,prior,s,next);next
    }
    suspend fun load(lease:SyncLease5):CheckedPublication5=withContext(Dispatchers.IO) {
        val prior=leases.owned(lease,clock());val s=scope(prior);val checked=files.load(SyncIntentFile5(prior.intentPath,prior.intentHash))
        db.withTransaction {check(leases.owned(lease,clock())==prior);check(scope(prior)==s);validate(checked,prior,s)}
        checked
    }
    private suspend fun update(lease:SyncLease5,change:(SyncIntentFile5)->SyncIntentFile5):SyncIntentFile5=withContext(Dispatchers.IO) {
        val prior=leases.owned(lease,clock());val s=scope(prior);val pointer=SyncIntentFile5(prior.intentPath,prior.intentHash)
        validate(files.load(pointer),prior,s)
        val next=change(pointer) // Immutable bytes/fsync precede the transactional pointer acknowledgement.
        commit(lease,prior,s,next);next
    }
    suspend fun acknowledgeImmutable(lease:SyncLease5,receipt:ImmutableReceipt5):SyncIntentFile5=update(lease){files.acknowledgeImmutable(it,receipt)}
    suspend fun observe(lease:SyncLease5,snapshot:PublicationHeadSnapshot?):SyncIntentFile5=update(lease){files.observe(it,snapshot)}
    suspend fun acknowledgePublication(lease:SyncLease5,tag:String):SyncIntentFile5=update(lease){files.acknowledgePublication(it,tag)}
    suspend fun retainRaw(lease:SyncLease5,observation:PublicationRawObservation5):SyncIntentFile5=update(lease){files.retainRaw(it,observation)}
    /** Confirm that a caller's pinned native folder roles belong to this exact leased binding. */
    internal suspend fun requireNativeScope(lease:SyncLease5,permissionId:String,commonRootId:String,folderIds:Set<String>,
        templateFolderId:String,workingFolderId:String,historyFolderId:String,metadataFolderId:String,revisionFolderId:String)=withContext(Dispatchers.IO) {
        val checked=load(lease)
        db.withTransaction {
            val job=leases.owned(lease,clock());val s=scope(job);validate(checked,job,s);val b=s.binding
            check(permissionId==s.root.permissionId && commonRootId==s.root.commonRootFolderId &&
                templateFolderId==b.templateFolderId && workingFolderId==b.workingFolderId && historyFolderId==b.historyFolderId &&
                metadataFolderId==b.metadataFolderId && revisionFolderId==checked.revisionFolderId &&
                folderIds.containsAll(setOf(commonRootId,s.connection.rootFolderId,templateFolderId,workingFolderId,historyFolderId,metadataFolderId,revisionFolderId)))
        }
    }
}
