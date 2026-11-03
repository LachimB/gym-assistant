package dev.gymassistant.engine.v2

import dev.gymassistant.engine.*
import kotlinx.serialization.Serializable

@Serializable data class Event2(
    val type:String,val adjustedScheduledElapsedMs:Long,val observedRunMs:Long,val pauseMs:Long,val unobservedGapMs:Long,
    val baseScheduledElapsedMs:Long?=null,val phaseInstanceId:String?=null,val adjustmentId:String?=null,val setId:String?=null,
)
data class State2(
    val status:SessionStatus=SessionStatus.Loaded,val cursorMs:Long=0,val observedRunMs:Long=0,val pauseMs:Long=0,
    val unobservedGapMs:Long=0,val completedInstanceIds:Set<String> = emptySet(),val completionAcknowledged:Boolean=false,
    val events:List<Event2> = emptyList(),
)
data class Change2(val plan:Plan2,val state:State2)
fun activePhase2(plan:Plan2,cursor:Long)=plan.phases.firstOrNull { cursor>=it.startMs && cursor<it.endMs }
fun baseCursor2(plan:Plan2,cursor:Long):Long? {
    if(cursor==plan.expectedDurationMs)return plan.originalDurationMs
    val phase=activePhase2(plan,cursor) ?: return null
    return phase.baseStartMs?.let { plus2(it,cursor-phase.startMs) }
}
fun reduce2(plan:Plan2,state:State2,command:EngineCommand):State2 {
    check(state.cursorMs in 0..plan.expectedDurationMs)
    safe2(state.observedRunMs);safe2(state.pauseMs);safe2(state.unobservedGapMs)
    if(command is EngineCommand.Advance) {
        safe2(command.ms)
        if(state.status==SessionStatus.Running)plus2(state.observedRunMs,minOf(command.ms,plan.expectedDurationMs-state.cursorMs))
        if(state.status==SessionStatus.Paused)plus2(state.pauseMs,command.ms)
    }
    if(command is EngineCommand.NoteUnobserved)plus2(state.unobservedGapMs,safe2(command.ms))
    val indices=plan.phases.mapIndexed { index,p->p.instanceId to index }.toMap()
    check(state.completedInstanceIds.all { it in indices })
    val legacyPlan=CompiledPlan("2.0.0",plan.templateId,plan.revisionId,"2.0.0",plan.expectedDurationMs,
        plan.phases.mapIndexed { i,p->CompiledPhase(i,p.sourceStepId,p.name,p.startMs,p.endMs) })
    val legacyState=EngineState(state.status,state.cursorMs,state.observedRunMs,state.pauseMs,state.unobservedGapMs,
        state.completedInstanceIds.map { checkNotNull(indices[it]) }.toSet(),state.completionAcknowledged)
    val next=reduce(legacyPlan,legacyState,command)
    val events=next.events.map { e->
        val p=e.phaseIndex?.let { plan.phases[it] }
        val base=if(e.type=="phaseCompleted")p?.baseStartMs?.let { plus2(it,p.endMs-p.startMs) } else baseCursor2(plan,e.scheduledElapsedMs)
        Event2(e.type,e.scheduledElapsedMs,e.observedRunMs,e.pauseMs,e.unobservedGapMs,base,p?.instanceId)
    }
    return State2(next.status,next.scheduledElapsedMs,next.observedRunMs,next.pauseMs,next.unobservedGapMs,
        next.completedPhaseIndexes.map { plan.phases[it].instanceId }.toSet(),next.completionAcknowledged,state.events+events)
}
fun additionAvailable2(plan:Plan2,state:State2):AdditionPolicy2? {
    if(state.status !in setOf(SessionStatus.Running,SessionStatus.Paused))return null
    val active=activePhase2(plan,state.cursorMs) ?: return null
    if(active.role !in setOf("work","reset","prepare"))return null
    val policy=plan.policies.singleOrNull { it.setId==active.setId } ?: return null
    val tail=plan.phases.lastOrNull { it.setId==policy.setId && it.adjustmentId!=null }
        ?: plan.phases.single { it.instanceId==policy.anchorInstanceId }
    return policy.takeIf { state.cursorMs<tail.endMs }
}
fun appendRepetition2(plan:Plan2,state:State2,adjustment:Adjustment2):Change2 {
    require(adjustment.adjustmentId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{2,127}")))
    require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?Z").matches(adjustment.recordedAt))
    val instant=java.time.Instant.parse(adjustment.recordedAt)
    require(instant.toString().take(19)==adjustment.recordedAt.take(19))
    val prior=plan.adjustments.singleOrNull { it.adjustmentId==adjustment.adjustmentId }
    if(prior!=null) {check(prior==adjustment) { "Adjustment identity changed" };return Change2(plan,state)}
    check(plan.adjustments.size<MAX_ADDITIONS_2)
    val policy=checkNotNull(additionAvailable2(plan,state));check(policy.setId==adjustment.setId)
    val tail=plan.phases.lastOrNull { it.setId==policy.setId && it.adjustmentId!=null }
        ?: plan.phases.single { it.instanceId==policy.anchorInstanceId }
    val insertion=plan.phases.indexOf(tail)+1
    val unitId=identity2("unit","added",adjustment.adjustmentId)
    var ordinal=plan.phases.maxOf { it.ordinal }+1
    val rep=checkNotNull(policy.core.first().repetitionIndex)+plan.adjustments.count { it.setId==policy.setId }+1
    fun core()=policy.core.mapIndexed { index,p->p.copy(instanceId=identity2("phase","added",adjustment.adjustmentId,"work",index.toString()),ordinal=ordinal++,
        unitId=unitId,repetitionIndex=rep,baseStartMs=null,adjustmentId=adjustment.adjustmentId) }
    fun reset():List<Phase2> = if(policy.resetMs==0L)emptyList() else listOf(policy.core.first().copy(
        instanceId=identity2("phase","added",adjustment.adjustmentId,"reset"),ordinal=ordinal++,unitId=unitId,
        phaseId=null,repetitionIndex=rep,name="Reset",role="reset",startMs=0,endMs=policy.resetMs,baseStartMs=null,adjustmentId=adjustment.adjustmentId))
    val extra=if(policy.existingReset)core()+reset() else reset()+core()
    check(plan.phases.size+extra.size<=MAX_PHASES_2)
    val ordered=plan.phases.take(insertion)+extra+plan.phases.drop(insertion)
    var cursor=0L
    val shifted=ordered.map { p->val start=cursor;cursor=plus2(cursor,p.endMs-p.startMs);p.copy(startMs=start,endMs=cursor) }
    check(shifted.map { it.instanceId }.distinct().size==shifted.size && shifted.map { it.ordinal }.distinct().size==shifted.size)
    val changed=plan.copy(expectedDurationMs=cursor,phases=shifted,adjustments=plan.adjustments+adjustment)
    requirePlanBudget2(changed)
    check(activePhase2(plan,state.cursorMs)?.instanceId==activePhase2(changed,state.cursorMs)?.instanceId)
    val event=Event2("repetitionAdded",state.cursorMs,state.observedRunMs,state.pauseMs,state.unobservedGapMs,
        baseCursor2(changed,state.cursorMs),adjustmentId=adjustment.adjustmentId,setId=adjustment.setId)
    return Change2(changed,state.copy(events=state.events+event))
}
