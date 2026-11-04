package dev.gymassistant.session

import androidx.room.withTransaction
import java.util.UUID

enum class SyncJobState5(val wire:String) {
    QUEUED("queued"), RUNNING("running"), RETRY("retry"), WAITING_AUTHORIZATION("waitingAuthorization"),
    VERIFYING("verifying"), PUBLISHING("publishing"), AWAITING_REVIEW("awaitingReview"), CONFLICT("conflict"), COMPLETE("complete"), BLOCKED("blocked"),
}
data class SyncLease5(val jobId:String,val owner:String,val generation:Long,val until:Long,val bootId:String)

/** The caller supplies sync wall-clock/boot metadata, never workout timing. No network runs in these transactions. */
class TemplateSyncLeases5(private val db:WorkoutDatabase) {
    suspend fun claim(jobId:String,now:Long,bootId:String):SyncLease5? = db.withTransaction {
        require(now>=0 && bootId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        val until=Math.addExact(now,120000L);val owner=UUID.randomUUID().toString()
        if(db.templateSync().claim(jobId,owner,now,until,bootId)!=1)return@withTransaction null
        val job=checkNotNull(db.templateSync().job(jobId))
        SyncLease5(jobId,owner,job.leaseGeneration,until,bootId)
    }
    suspend fun owned(lease:SyncLease5,now:Long):TemplateSyncJob5 = db.withTransaction { checkOwned(lease,now) }
    private suspend fun checkOwned(lease:SyncLease5,now:Long):TemplateSyncJob5 {
        val row=checkNotNull(db.templateSync().job(lease.jobId))
        check(row.leaseOwner==lease.owner && row.leaseGeneration==lease.generation && row.leaseBootId==lease.bootId &&
            row.leaseUntil==lease.until && now>=0 && now<lease.until) { "Sync lease changed or expired" }
        return row
    }
    suspend fun release(lease:SyncLease5,now:Long,state:SyncJobState5,error:String?=null,nextAttemptAt:Long?=null)=db.withTransaction {
        val prior=checkOwned(lease,now)
        require(state!=SyncJobState5.RUNNING && (state==SyncJobState5.RETRY || nextAttemptAt==null))
        require(nextAttemptAt==null || nextAttemptAt>=now)
        // Validate the resulting row before SQL; corrupt state must not become an acknowledged receipt.
        prior.copy(state=state.wire,errorCode=error,nextAttemptAt=nextAttemptAt,leaseOwner=null,leaseUntil=null,leaseBootId=null)
        check(db.templateSync().release(lease.jobId,lease.owner,lease.generation,state.wire,error,nextAttemptAt)==1)
    }
}
