package dev.gymassistant.drive

import dev.gymassistant.session.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Read-only leased batch. Caller supplies an existing token and reservation; this cannot launch OAuth or adopt a source. */
internal class TemplateSourceReadExecutor5(private val store:TemplateSourceCaptureStore5,private val testTransport:TemplateHttp5?=null) {
    suspend fun <T> read(lease:SyncLease5,observationId:String,token:String,reserveRequest:()->Unit,folders:List<TemplateFolderPin5>,
        block:(TemplateSourceReader5)->T):T=withContext(Dispatchers.IO) {
        val context=currentCoroutineContext();val initial=store.readBinding(lease,observationId);val checked=initial.checked
        val scope=TemplateSourceReadScope5(checked,folders);val n=checked.namespace
        check(scope.folders.getValue(n.commonRootId).marker==initial.rootMarker && scope.folders.getValue(n.areaFolderId).marker==initial.areaMarker)
        fun <R> durable(action:suspend ()->R):R=runBlocking(context){context.ensureActive();action()}
        fun current()=durable {store.readBinding(lease,observationId)}.also {now->
            check(now.checked.captureOrigin==checked.captureOrigin && now.checked.originPointer==checked.originPointer &&
                now.checked.namespace==n && now.checked.pins==checked.pins && now.rootMarker==initial.rootMarker && now.areaMarker==initial.areaMarker)
        }
        val reserve={current();reserveRequest()}
        val retain:(RemoteObjectCapture5)->Unit={raw->
            current();durable {store.retainRaw(lease,observationId,PublicationRawObservation5(raw.kind.name,raw.objectId,raw.status,raw.metadataBytes,raw.contentBytes))};context.ensureActive()
        }
        current()
        TemplateSourceReadBatch5(token,reserve,scope,retain,testTransport).use {batch->
            val result=block(batch.reader);current();result
        }
    }
}
