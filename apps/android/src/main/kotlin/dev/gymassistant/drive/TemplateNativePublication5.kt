package dev.gymassistant.drive

import dev.gymassistant.engine.v2.ImportContracts2
import dev.gymassistant.session.*
import dev.gymassistant.sync.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking

/** Explicit caller-owned batch only; no worker, OAuth launch, allowance reset, or namespace creation. */
internal class TemplateNativePublication5(private val store:TemplatePublicationStore5,private val contracts:ImportContracts2,
    private val working:TemplateNamespace5,private val metadata:ImmutableRevisionNamespace5,private val history:ImmutableRevisionNamespace5,
    private val testTransport:((String)->TemplateHttp5)?=null) {
    suspend fun execute(lease:SyncLease5,token:String,reserveRequest:()->Unit):VerifiedPublication5=
        TemplatePublicationExecutor5(store).execute(lease){captured,retain->
            val c=captured.working.captured;val byName=c.steps.associateBy {it.name}
            val payloads=c.manifest.files.associate {it.objectId to byName.getValue(it.relativeName).intendedBytes}
            val revision=ImmutableRevisionFiles5(history,c.manifest,captured.intent.intended.revisionManifestId,captured.intent.marker,payloads,contracts)
            val scope=TemplatePublicationScope5(captured.intent,revision,working,metadata,c.steps,captured.working.receipts.values.toList(),contracts,false)
            val selected=scope.folders.getValue(working.workingFolderId).parent
            val historyId=scope.folders.getValue(history.revisionFolderId).parent
            store.requireNativeScope(lease,history.permissionId,history.commonRootId,scope.folders.keys,selected,working.workingFolderId,
                historyId,metadata.revisionFolderId,history.revisionFolderId)
            val sink:(RemoteObjectCapture5)->Unit={raw->retain(PublicationRawObservation5(raw.kind.name,raw.objectId,raw.status,raw.metadataBytes,raw.contentBytes))}
            val immutable=TemplateImmutableBatch5(token,reserveRequest,revision,sink,testTransport?.invoke("immutable"))
            var editable:TemplateStrictWorkingBatch5?=null;var published:TemplatePublicationBatch5?=null
            try {
                val context=currentCoroutineContext()
                if(captured.historyFirst)editable=TemplateStrictWorkingBatch5(token,reserveRequest,working,c.steps,sink,testTransport?.invoke("working"))
                fun head():PublicationHeadRemote {
                    context.ensureActive();published?.let {return it.remote}
                    val current=runBlocking(context){store.load(lease)}
                    check(current.originPointer==captured.originPointer && current.working.capturedPointer==captured.working.capturedPointer &&
                        current.allImmutableAcknowledged && current.working.allFilesAcknowledged)
                    val ready=TemplatePublicationScope5(current.intent,revision,working,metadata,c.steps,current.working.receipts.values.toList(),contracts)
                    return TemplatePublicationBatch5(token,reserveRequest,ready,sink,testTransport?.invoke("head")).also {published=it}.remote
                }
                val delayed=object:PublicationHeadRemote {
                    override fun verifyNamespace()=head().verifyNamespace()
                    override fun read(objectId:String)=head().read(objectId)
                    override fun replace(objectId:String,expectedTag:String,head:TemplatePublicationHead)=head().replace(objectId,expectedTag,head)
                    override fun createReserved(intent:PublicationHeadIntent)=head().createReserved(intent)
                    override fun verifyImmutablePublication(head:TemplatePublicationHead)=head().verifyImmutablePublication(head)
                    override fun workingVectorMatches()=head().workingVectorMatches()
                }
                val preflight:(()->Unit)?=if(!captured.historyFirst)null else {
                    {
                        context.ensureActive()
                        TemplateHeadReadBatch5(token,reserveRequest,scope,sink,testTransport?.invoke("preflight")).use {batch->
                            batch.remote.verifyNamespace();val observed=batch.remote.read(captured.intent.objectId);batch.remote.verifyNamespace()
                            val current=runBlocking(context){store.observe(lease,observed);store.load(lease)}
                            if(observed?.head==captured.intent.intended)check(current.allImmutableAcknowledged && current.working.allFilesAcknowledged)
                            else check(if(captured.intent.expected==null)observed==null else observed!=null && observed.head==captured.intent.expected && observed.tag==captured.intent.expectedTag) {"Head changed before editable transfer"}
                        }
                    }
                }
                PublicationRemoteSet5(immutable.remote,delayed,editable?.remote,preflight){try{published?.close()}finally{try{editable?.close()}finally{immutable.close()}}}
            }catch(error:Throwable){try{published?.close()}finally{try{editable?.close()}finally{immutable.close()}};throw error}
        }
}
