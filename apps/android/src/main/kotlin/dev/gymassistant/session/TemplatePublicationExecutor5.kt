package dev.gymassistant.session

import dev.gymassistant.sync.*
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class PublicationRemoteSet5(val immutable:ImmutableFileRemote,val head:PublicationHeadRemote,
    val working:WorkingFileRemote?=null,private val finish:()->Unit):Closeable {
    override fun close()=finish()
}
/** A verified publication is not local adoption, a default change, or whole-job completion. */
class VerifiedPublication5 internal constructor(val operationId:String,val headObjectId:String,val publishedTag:String,val result:HeadPublicationResult)

/** Bridges synchronous provider protocols to the leased, fsynced private journal on IO. */
class TemplatePublicationExecutor5(private val store:TemplatePublicationStore5) {
    suspend fun execute(lease:SyncLease5,open:suspend (CheckedPublication5,(PublicationRawObservation5)->Unit)->PublicationRemoteSet5):VerifiedPublication5=withContext(Dispatchers.IO) {
        val context=currentCoroutineContext();val initial=store.load(lease)
        fun <T> durable(block:suspend ()->T):T=runBlocking(context){context.ensureActive();block()}
        fun sameIntent(a:PublicationHeadIntent,b:PublicationHeadIntent)=a.operationId==b.operationId && a.objectId==b.objectId &&
            a.metadataFolderId==b.metadataFolderId && a.marker==b.marker && a.expected==b.expected && a.expectedTag==b.expectedTag && a.intended==b.intended
        fun current():CheckedPublication5=durable {store.load(lease)}.also {
            check(it.working.capturedPointer==initial.working.capturedPointer && it.originPointer==initial.originPointer && it.historyFirst==initial.historyFirst &&
                it.bindingHash==initial.bindingHash && it.permissionId==initial.permissionId &&
                it.revisionFolderId==initial.revisionFolderId && sameIntent(it.intent,initial.intent)) {"Publication capture changed"}
        }
        fun sameStep(a:ImmutableFileStep,b:ImmutableFileStep)=a.objectId==b.objectId && a.folderId==b.folderId && a.marker==b.marker &&
            a.name==b.name && a.mimeType==b.mimeType && a.sha256==b.sha256 && a.byteCount==b.byteCount
        val retain:(PublicationRawObservation5)->Unit={observation->
            current();durable {store.retainRaw(lease,observation)};context.ensureActive()
        }
        current()
        open(initial,retain).use {remotes->
            check(initial.working.allFilesAcknowledged || initial.historyFirst && remotes.working!=null)
            val immutableJournal=object:ImmutableFileJournal {
                override fun requireDurable(step:ImmutableFileStep){check(current().immutableSteps.any {sameStep(it,step)})}
                override fun requireCurrentLease(){current()}
                override fun requireCreatePending(step:ImmutableFileStep){val checked=current();check(step.objectId !in checked.receipts && checked.publishedTag==null)}
                override fun acknowledge(step:ImmutableFileStep,resultTag:String){current();durable {store.acknowledgeImmutable(lease,ImmutableReceipt5(step.objectId,resultTag,step.sha256))}}
            }
            for(step in initial.immutableSteps)GuardedImmutableFile(remotes.immutable,immutableJournal).execute(step)
            check(current().allImmutableAcknowledged)
            if(initial.historyFirst) {
                val remote=checkNotNull(remotes.working) {"History-first execution requires the working transport"}
                fun sameWorking(a:WorkingFileStep,b:WorkingFileStep)=a.objectId==b.objectId && a.folderId==b.folderId && a.marker==b.marker &&
                    a.name==b.name && a.mimeType==b.mimeType && a.expectedTag==b.expectedTag && a.beforeHash==b.beforeHash && a.intendedHash==b.intendedHash
                val workingJournal=object:WorkingFileJournal {
                    override fun requireDurable(step:WorkingFileStep){val c=current();check(c.allImmutableAcknowledged && c.working.captured.steps.any {sameWorking(it,step)} && step.objectId !in c.working.receipts)}
                    override fun requireCurrentLease(){current()}
                    override fun acknowledge(step:WorkingFileStep,resultTag:String,resultHash:String){current();durable {store.acknowledgeWorking(lease,WorkingReceipt5(step.objectId,resultTag,resultHash))}}
                }
                fun verifyWorking(step:WorkingFileStep,receipt:WorkingReceipt5) {
                    current();val snapshot=checkNotNull(remote.read(step.objectId))
                    check(snapshot.objectId==step.objectId && snapshot.parents==setOf(step.folderId) && !snapshot.trashed && snapshot.marker==step.marker &&
                        snapshot.name==step.name && snapshot.mimeType==step.mimeType && snapshot.tag==receipt.tag && hashSourceBytes(snapshot.bytes)==receipt.sha256)
                }
                for(step in initial.working.captured.steps) {
                    val receipt=current().working.receipts[step.objectId]
                    if(receipt==null)GuardedWorkingFile(remote,workingJournal).execute(step)
                    else {current();remote.verifyNamespace();verifyWorking(step,receipt);current();remote.verifyNamespace()}
                }
                val completed=current();check(completed.working.allFilesAcknowledged)
                current();remote.verifyNamespace()
                for(step in completed.working.captured.steps)verifyWorking(step,completed.working.receipts.getValue(step.objectId))
                current();remote.verifyNamespace();check(current().working.receipts==completed.working.receipts)
            }
            val headJournal=object:PublicationHeadJournal {
                override fun requireDurable(intent:PublicationHeadIntent){val checked=current();check(checked.allImmutableAcknowledged && checked.working.allFilesAcknowledged && sameIntent(checked.intent,intent))}
                override fun requireCurrentLease(){current()}
                override fun observe(intent:PublicationHeadIntent,snapshot:PublicationHeadSnapshot?){requireDurable(intent);durable {store.observe(lease,snapshot)}}
                override fun acknowledgePublication(intent:PublicationHeadIntent,resultTag:String){requireDurable(intent);durable {store.acknowledgePublication(lease,resultTag)}}
            }
            val result=GuardedPublicationHead(remotes.head,headJournal).execute(initial.intent)
            val final=current();VerifiedPublication5(initial.intent.operationId,initial.intent.objectId,checkNotNull(final.publishedTag),result)
        }
    }
}
