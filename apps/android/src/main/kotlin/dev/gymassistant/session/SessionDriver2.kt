package dev.gymassistant.session

import dev.gymassistant.engine.EngineCommand
import dev.gymassistant.engine.SessionStatus
import dev.gymassistant.engine.v2.*
import java.time.Instant
import java.util.UUID

class SessionDriver2(private val store:RoomSessionStore2,private val nowMs:()->Long,
    private val utc:()->String={Instant.now().toString()},private val id:()->String={UUID.randomUUID().toString()}) {
    var saved:SavedWorkout2?=null;private set
    var needsRecovery=false;private set
    private var anchor=nowMs()
    suspend fun restore():SavedWorkout2? {
        val prior=store.unfinished()
        saved=prior?.let { store.commit(it.start.sessionId,it.revision,id(),SessionAction2("recover",recordedAt=utc())) }
        needsRecovery=saved!=null;anchor=nowMs();return saved
    }
    suspend fun start(snapshot:SessionStart2):SavedWorkout2 {
        val next=store.start(snapshot,id());saved=next;needsRecovery=false;anchor=nowMs();return next
    }
    suspend fun send(kind:String):SavedWorkout2 {
        val prior=checkNotNull(saved)
        check(!needsRecovery || kind in setOf("resume","discard","finish","end"))
        val now=nowMs();val delta=if(now>=anchor)now-anchor else 0L
        val timed=prior.state.status in setOf(SessionStatus.Running,SessionStatus.Paused)
        val discontinuity=timed && (now<anchor || delta>SessionDriver.MAX_OBSERVED_INTERVAL_MS)
        val at=utc()
        val requested=if(kind=="add" && !discontinuity && additionAvailable2(prior.plan,reduce2(prior.plan,prior.state,EngineCommand.Advance(if(needsRecovery || !timed)0 else delta)))==null)"tick" else kind
        val action=if(discontinuity && !needsRecovery && kind!="discard")SessionAction2("recover",recordedAt=at,gapMs=if(now>=anchor)delta else null)
        else SessionAction2(requested,if(needsRecovery || !timed || kind=="discard")0 else delta,at,
            adjustment=if(requested=="add")Adjustment2(id(),checkNotNull(additionAvailable2(prior.plan,prior.state)).setId,at)else null)
        val next=store.commit(prior.start.sessionId,prior.revision,id(),action)
        saved=next;needsRecovery=action.kind=="recover" || (needsRecovery && action.kind !in setOf("resume","discard","finish","end"));anchor=now
        return next
    }
    fun shouldTick()=!needsRecovery && saved?.checkpoint?.outcome=="inProgress" && saved?.state?.status in setOf(SessionStatus.Running,SessionStatus.Paused)
}
