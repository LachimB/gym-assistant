package dev.gymassistant.drive

import dev.gymassistant.engine.v2.ImportContracts2
import dev.gymassistant.session.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Known retained base only. A missing reference is not permission for discovery or replacement. */
internal class TemplateKnownBaseExecutor5(private val store:TemplateSourceCaptureStore5,private val publications:TemplatePublicationFiles5,
    private val contracts:ImportContracts2,private val testTransport:TemplateHttp5?=null) {
    suspend fun read(lease:SyncLease5,observationId:String,priorPublication:SyncIntentFile5,token:String,reserveRequest:()->Unit,
        folders:List<TemplateFolderPin5>,revisionFolder:TemplateFolderPin5):TemplateSources3=withContext(Dispatchers.IO) {
        val context=currentCoroutineContext();val initial=store.readBinding(lease,observationId);val checked=initial.checked
        val source=TemplateSourceReadScope5(checked,folders);val n=checked.namespace
        check(source.folders.getValue(n.commonRootId).marker==initial.rootMarker && source.folders.getValue(n.areaFolderId).marker==initial.areaMarker)
        val scope=KnownImmutableBaseScope5(source,publications.load(priorPublication),revisionFolder,contracts)
        fun <R> durable(action:suspend ()->R):R=runBlocking(context){context.ensureActive();action()}
        fun current() {
            val now=durable {store.readBinding(lease,observationId)}
            check(now.checked.captureOrigin==checked.captureOrigin && now.checked.originPointer==checked.originPointer &&
                now.checked.namespace==n && now.checked.pins==checked.pins && now.rootMarker==initial.rootMarker && now.areaMarker==initial.areaMarker)
            // Immutable reference bytes are rechecked even after a prior successful read.
            KnownImmutableBaseScope5(source,publications.load(priorPublication),revisionFolder,contracts)
        }
        val reserve={current();reserveRequest()}
        val retain:(RemoteObjectCapture5)->Unit={raw->
            current();durable {store.retainRaw(lease,observationId,PublicationRawObservation5(raw.kind.name,raw.objectId,raw.status,raw.metadataBytes,raw.contentBytes))};context.ensureActive()
        }
        current()
        KnownImmutableBaseBatch5(token,reserve,scope,retain,testTransport).use {batch->val result=batch.verify();current();result}
    }
}
