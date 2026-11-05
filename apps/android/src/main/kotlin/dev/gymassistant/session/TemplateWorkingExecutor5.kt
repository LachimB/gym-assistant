package dev.gymassistant.session

import dev.gymassistant.sync.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** A verified working-file vector only. Immutable history and conditional head publication are separate steps. */
class VerifiedWorkingVector5 internal constructor(val operationId:String,val capturedPointer:SyncIntentFile5,receipts:List<WorkingReceipt5>) {
    val receipts=receipts.toList()
}

/** Runs the synchronous bounded provider protocol on IO; Room checks still finish before every network operation. */
class TemplateWorkingExecutor5(private val store:TemplateSyncProgressStore5) {
    suspend fun execute(lease:SyncLease5,remote:WorkingFileRemote):VerifiedWorkingVector5=withContext(Dispatchers.IO) {
        val context=currentCoroutineContext();val initial=store.load(lease)
        // The provider-neutral protocol is synchronous. Preserve the caller's cancellation in each Room bridge.
        fun <T> durable(block:suspend ()->T):T=runBlocking(context) {context.ensureActive();block()}
        fun current():CheckedSyncProgress5=durable {store.load(lease)}.also {
            check(it.capturedPointer==initial.capturedPointer) {"Captured transfer changed"}
        }
        fun namespace(){context.ensureActive();current();remote.verifyNamespace()}
        fun sameStep(left:WorkingFileStep,right:WorkingFileStep)=left.objectId==right.objectId && left.folderId==right.folderId &&
            left.marker==right.marker && left.name==right.name && left.mimeType==right.mimeType && left.expectedTag==right.expectedTag &&
            left.beforeHash==right.beforeHash && left.intendedHash==right.intendedHash
        fun verifyReceipt(step:WorkingFileStep,receipt:WorkingReceipt5) {
            context.ensureActive();current()
            val snapshot=checkNotNull(remote.read(step.objectId)) {"Acknowledged working file is inaccessible"}
            check(snapshot.objectId==step.objectId && snapshot.parents==setOf(step.folderId) && !snapshot.trashed &&
                snapshot.marker==step.marker && snapshot.name==step.name && snapshot.mimeType==step.mimeType &&
                snapshot.tag==receipt.tag && snapshot.bytes.size in 1..1048576 && hashSourceBytes(snapshot.bytes)==receipt.sha256) {
                "Acknowledged working file changed; preserve it for observation"
            }
        }
        val journal=object:WorkingFileJournal {
            override fun requireDurable(step:WorkingFileStep) {
                val checked=current();check(checked.captured.steps.any {sameStep(it,step)}) {"Step differs from durable source"}
                check(step.objectId !in checked.receipts) {"Acknowledged files are read-only during retry"}
            }
            override fun requireCurrentLease(){current()}
            override fun acknowledge(step:WorkingFileStep,resultTag:String,resultHash:String) {
                context.ensureActive();current()
                durable {store.acknowledge(lease,WorkingReceipt5(step.objectId,resultTag,resultHash))}
            }
        }
        for(step in initial.captured.steps) {
            val previous=current().receipts[step.objectId]
            if(previous==null)GuardedWorkingFile(remote,journal).execute(step)
            else {namespace();verifyReceipt(step,previous);namespace();current()}
        }
        val completed=current();check(completed.allFilesAcknowledged)
        // Earlier receipts may have changed while later files transferred. Never infer an atomic folder write.
        namespace()
        for(step in completed.captured.steps)verifyReceipt(step,completed.receipts.getValue(step.objectId))
        namespace();val final=current();check(final.receipts==completed.receipts)
        VerifiedWorkingVector5(final.captured.operationId,final.capturedPointer,final.receipts.values.toList())
    }
}
