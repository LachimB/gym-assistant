package dev.gymassistant.session

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

enum class TemplateWakeResult5 { IDLE, WAITING_FOR_OWNER, RETRY }
fun interface TemplateWakeHandler5 {suspend fun wake(bindingId:String):TemplateWakeResult5}
interface TemplateWakeHost5 {val templateWakeHandler5:TemplateWakeHandler5}

/** Inputs contain only a local binding ID. Tokens/source/provider IDs never enter WorkManager records. */
class TemplateSyncWorker5 internal constructor(context:Context,parameters:WorkerParameters,private val handler:TemplateWakeHandler5?):CoroutineWorker(context,parameters) {
    constructor(context:Context,parameters:WorkerParameters):this(context,parameters,(context.applicationContext as? TemplateWakeHost5)?.templateWakeHandler5)
    override suspend fun doWork():Result {
        val binding=inputData.getString(BINDING)
        if(binding==null || !validBinding(binding) || inputData.keyValueMap.keys!=setOf(BINDING))return Result.failure(workDataOf("reason" to "contract"))
        val runner=handler ?: return Result.failure(workDataOf("reason" to "notConfigured"))
        return try {
            when(runner.wake(binding)) {
                TemplateWakeResult5.IDLE->Result.success(workDataOf("outcome" to "idle"))
                TemplateWakeResult5.WAITING_FOR_OWNER->Result.success(workDataOf("outcome" to "waitingForOwner"))
                TemplateWakeResult5.RETRY->Result.retry()
            }
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){Result.failure(workDataOf("reason" to "contract"))}
    }
    companion object {
        const val BINDING="bindingId"
        internal fun validBinding(id:String)=id.matches(Regex("[A-Za-z0-9_-]{3,160}"))
    }
}

/** Reconcile durable opt-in with persistent constrained wakeups. A scheduler result never acknowledges a transfer. */
class TemplateSyncScheduling5(private val db:WorkoutDatabase,private val manager:WorkManager) {
    companion object {
        const val TAG="gym-template-sync-v5"
        private const val PREFIX="gym-template-binding-"
        private fun tag(id:String):String {require(TemplateSyncWorker5.validBinding(id));return PREFIX+id}
        internal fun onceName(id:String)=tag(id)+"-once"
        internal fun periodicName(id:String)=tag(id)+"-periodic"
        private val network=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        internal fun once(id:String)=OneTimeWorkRequestBuilder<TemplateSyncWorker5>()
            .setInputData(workDataOf(TemplateSyncWorker5.BINDING to id)).addTag(TAG).addTag(tag(id))
            .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
        internal fun periodic(id:String)=PeriodicWorkRequestBuilder<TemplateSyncWorker5>(15,TimeUnit.MINUTES)
            .setInitialDelay(15,TimeUnit.MINUTES).setInputData(workDataOf(TemplateSyncWorker5.BINDING to id)).addTag(TAG).addTag(tag(id))
            .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
    }
    suspend fun reconcile(wakeNow:Boolean=true) {
        val enabled=db.templateSync().schedulableBindings().map {it.bindingId}.toSet()
        val managed=manager.getWorkInfosByTagFlow(TAG).first().filterNot {it.state.isFinished}
        val prior=managed.flatMap {it.tags}.filter {it.startsWith(PREFIX)}.map {it.removePrefix(PREFIX)}.toSet()
        for(id in prior-enabled)manager.cancelAllWorkByTag(tag(id)).await()
        for(id in enabled) {
            manager.enqueueUniquePeriodicWork(periodicName(id),ExistingPeriodicWorkPolicy.KEEP,periodic(id)).await()
            if(wakeNow)manager.enqueueUniqueWork(onceName(id),ExistingWorkPolicy.KEEP,once(id)).await()
        }
    }
    suspend fun wake(bindingId:String) {
        require(TemplateSyncWorker5.validBinding(bindingId))
        check(db.templateSync().schedulableBindings().any {it.bindingId==bindingId}) {"Template binding is not enabled and verified"}
        manager.enqueueUniqueWork(onceName(bindingId),ExistingWorkPolicy.KEEP,once(bindingId)).await()
    }
}
