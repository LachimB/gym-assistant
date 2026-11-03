package dev.gymassistant.session

import androidx.room.withTransaction
import dev.gymassistant.engine.EngineCommand
import dev.gymassistant.engine.SessionStatus
import dev.gymassistant.engine.v2.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable data class SessionStart2(
    val sessionId:String,val sourceSnapshotJson:String,val sourceHash:String,val basePlanJson:String,val basePlanHash:String,
    val startedAt:String,val timeZone:String,val localTrainingDate:String,
    val formatVersion:Int=2,val engineVersion:String="kotlin-timed-2",
) {
    fun validate(contracts:ImportContracts2):Plan2 {
        require(formatVersion==2 && engineVersion=="kotlin-timed-2")
        require(sessionId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{2,127}")))
        require(hashUtf8_2(sourceSnapshotJson)==sourceHash && hashUtf8_2(basePlanJson)==basePlanHash)
        val source=sessionJson.decodeFromString<TemplateSources3>(sourceSnapshotJson)
        val plan=compileTemplate2(source.templateYaml,source.exerciseYamls,contracts)
        require(planJson2.encodeToString(plan)==basePlanJson && plan.adjustments.isEmpty())
        require(Instant.parse(startedAt).atZone(ZoneId.of(timeZone)).toLocalDate().toString()==localTrainingDate)
        return plan
    }
}
@Serializable data class OrdinalRange2(val first:Int,val last:Int)
@Serializable data class Checkpoint2(
    val structuralRevision:Long,val status:String,val adjustedScheduledElapsedMs:Long,
    val activePhaseInstanceId:String?,val phaseOffsetMs:Long,
    val observedRunMs:Long,val pauseMs:Long,val unobservedGapMs:Long,
    val completedOrdinalRanges:List<OrdinalRange2>,val completionAcknowledged:Boolean,
    val unknownGapCount:Int=0,val outcome:String="inProgress",val finishedAt:String?=null,
) {
    fun state(plan:Plan2):State2 {
        val active=activePhase2(plan,adjustedScheduledElapsedMs)
        check(adjustedScheduledElapsedMs in 0..plan.expectedDurationMs && active?.instanceId==activePhaseInstanceId)
        check(phaseOffsetMs==if(active==null)0L else adjustedScheduledElapsedMs-active.startMs)
        check(completedOrdinalRanges.all { it.first>=0 && it.last>=it.first && it.last<MAX_PHASES_2 })
        check(completedOrdinalRanges.zipWithNext().all { (a,b)->b.first>a.last+1 })
        val ordinals=completedOrdinalRanges.flatMap { (it.first..it.last).toList() }.toSet()
        val completed=plan.phases.filter { it.ordinal in ordinals }
        check(completed.size==ordinals.size && unknownGapCount>=0 && structuralRevision>=0)
        check(listOf(observedRunMs,pauseMs,unobservedGapMs).all { it in 0..MAX_SAFE_2 })
        check(outcome in setOf("inProgress","completed","discarded"))
        return State2(SessionStatus.valueOf(status),adjustedScheduledElapsedMs,observedRunMs,pauseMs,unobservedGapMs,
            completed.map { it.instanceId }.toSet(),completionAcknowledged)
    }
    companion object {
        fun from(plan:Plan2,state:State2,structuralRevision:Long,gaps:Int=0,outcome:String="inProgress",finishedAt:String?=null):Checkpoint2 {
            val completed=plan.phases.filter { it.instanceId in state.completedInstanceIds }.map { it.ordinal }.sorted()
            check(completed.size==state.completedInstanceIds.size)
            val ranges=mutableListOf<OrdinalRange2>()
            for(ordinal in completed) {
                val last=ranges.lastOrNull()
                if(last!=null && last.last+1==ordinal)ranges[ranges.lastIndex]=last.copy(last=ordinal)
                else ranges+=OrdinalRange2(ordinal,ordinal)
            }
            val active=activePhase2(plan,state.cursorMs)
            return Checkpoint2(structuralRevision,state.status.name,state.cursorMs,active?.instanceId,
                active?.let { state.cursorMs-it.startMs }?:0,state.observedRunMs,state.pauseMs,state.unobservedGapMs,
                ranges,state.completionAcknowledged,gaps,outcome,finishedAt)
        }
    }
}
@Serializable data class SessionAction2(
    val kind:String,val elapsedMs:Long=0,val recordedAt:String,val gapMs:Long?=null,val adjustment:Adjustment2?=null,
) {
    init {
        require(kind in setOf("tick","pause","resume","back","forward","finish","end","recover","discard","add"))
        require(elapsedMs in 0..MAX_SAFE_2 && (gapMs==null || kind=="recover" && gapMs in 0..MAX_SAFE_2))
        require(kind !in setOf("recover","discard") || elapsedMs==0L)
        require((kind=="add")== (adjustment!=null))
        require(adjustment==null || adjustment.recordedAt==recordedAt)
        Instant.parse(recordedAt)
    }
}
@Serializable data class ActionPayload2(val action:SessionAction2?,val events:List<Event2>)
data class SavedWorkout2(val start:SessionStart2,val revision:Long,val plan:Plan2,val checkpoint:Checkpoint2) {
    val state get()=checkpoint.state(plan)
}
data class Transition2(val plan:Plan2,val checkpoint:Checkpoint2,val payload:ActionPayload2)
fun transition2(saved:SavedWorkout2,action:SessionAction2):Transition2 {
    check(saved.checkpoint.outcome=="inProgress")
    var plan=saved.plan;var state=saved.state
    check(action.kind!="recover" || action.gapMs==null || state.status in setOf(SessionStatus.Running,SessionStatus.Paused)) { "Known gaps require an observed running/paused interval" }
    if(action.kind !in setOf("recover","discard"))state=reduce2(plan,state,EngineCommand.Advance(action.elapsedMs))
    var structural=saved.checkpoint.structuralRevision
    when(action.kind) {
        "tick","discard"->Unit
        "add"->{val changed=appendRepetition2(plan,state,checkNotNull(action.adjustment));if(changed.plan!=plan)structural=Math.addExact(structural,1);plan=changed.plan;state=changed.state}
        else->{val command=when(action.kind) {
            "pause"->EngineCommand.Pause;"resume"->EngineCommand.Resume
            "back"->EngineCommand.StepBackward;"forward"->EngineCommand.StepForward
            "finish"->{check(state.status==SessionStatus.Completed);EngineCommand.Acknowledge}
            "end"->EngineCommand.EndEarly;"recover"->EngineCommand.NoteUnobserved(action.gapMs?:0)
            else->error("Unsupported action")
        };state=reduce2(plan,state,command)}
    }
    val outcome=when(action.kind) {"discard"->"discarded";"finish","end"->if(state.completionAcknowledged)"completed" else "inProgress";else->"inProgress"}
    val gaps=if(action.kind=="recover" && action.gapMs==null && state.status!=SessionStatus.Completed)Math.addExact(saved.checkpoint.unknownGapCount,1)else saved.checkpoint.unknownGapCount
    return Transition2(plan,Checkpoint2.from(plan,state,structural,gaps,outcome,if(outcome=="inProgress")null else action.recordedAt),ActionPayload2(action,state.events))
}

/** New-format store. Legacy rows are dispatched before decoding and never converted. */
class RoomSessionStore2(private val db:WorkoutDatabase,private val contracts:ImportContracts2,private val beforeOutbox:()->Unit={}) {
    private val dao get()=db.workouts()
    private val library get()=db.library()
    // Immutable plan cache avoids decoding a large effective plan four times per second.
    private val plans=mutableMapOf<Pair<String,Long>,Pair<String,Plan2>>()
    private suspend fun plan(id:String,revision:Long):Plan2 {
        val row=checkNotNull(library.plan(id,revision))
        val key=id to revision
        val cached=plans[key]
        if(cached!=null && cached.first==row.compiledPlanHash) {
            check(hashUtf8_2(row.compiledPlanJson)==cached.first);return cached.second
        }
        check(hashUtf8_2(row.compiledPlanJson)==row.compiledPlanHash)
        val parsed=planJson2.decodeFromString<Plan2>(row.compiledPlanJson)
        requirePlanBudget2(parsed);plans[key]=row.compiledPlanHash to parsed;return parsed
    }
    private suspend fun read(id:String,receipt:CheckpointRow?=null):SavedWorkout2? {
        val row=dao.start(id)?:return null
        val start=sessionJson.decodeFromString<SessionStart2>(row.snapshotJson)
        check(start.sessionId==id && start.formatVersion==2 && start.engineVersion=="kotlin-timed-2")
        check(hashUtf8_2(start.basePlanJson)==start.basePlanHash && hashUtf8_2(start.sourceSnapshotJson)==start.sourceHash)
        val checkpoint=receipt?:checkNotNull(dao.checkpoint(id))
        val decoded=sessionJson.decodeFromString<Checkpoint2>(checkpoint.checkpointJson)
        val effective=plan(id,decoded.structuralRevision)
        val base=planJson2.decodeFromString<Plan2>(start.basePlanJson)
        check(effective.templateId==base.templateId && effective.revisionId==base.revisionId && effective.originalDurationMs==base.expectedDurationMs)
        check(effective.planFormatVersion=="2.0.0" && effective.adjustments.size.toLong()==decoded.structuralRevision)
        check(effective.phases.isNotEmpty() && effective.phases.size<=MAX_PHASES_2)
        check(effective.phases.map {it.instanceId}.distinct().size==effective.phases.size && effective.phases.map {it.ordinal}.distinct().size==effective.phases.size)
        var edge=0L
        for(phase in effective.phases) {check(phase.startMs==edge && phase.endMs>edge);edge=phase.endMs}
        check(edge==effective.expectedDurationMs && edge<=MAX_SAFE_2)
        if(decoded.structuralRevision==0L)check(planJson2.encodeToString(effective)==start.basePlanJson)
        return SavedWorkout2(start,checkpoint.revision,effective,decoded).also { it.state }
    }
    suspend fun history(id:String):SessionHistory2?=db.withTransaction { read(id)?.let { projectSession2(it,dao.actions(id),library.plans(id),contracts) } }
    suspend fun load(id:String):SavedWorkout2?=db.withTransaction { read(id) }
    suspend fun unfinished():SavedWorkout2?=db.withTransaction { dao.unfinished()?.let { slot->read(slot.sessionId)?.also { projectSession2(it,dao.actions(slot.sessionId),library.plans(slot.sessionId),contracts) } } }
    suspend fun start(snapshot:SessionStart2,operationId:String):SavedWorkout2 {
        require(operationId.isNotBlank());val base=snapshot.validate(contracts);val encoded=sessionJson.encodeToString(snapshot)
        return db.withTransaction {
            val existing=dao.start(snapshot.sessionId)
            if(existing!=null) {
                val prior=checkNotNull(dao.operation(snapshot.sessionId,operationId))
                check(existing.snapshotJson==encoded && prior.intentJson==encoded && prior.expectedRevision==0L)
                return@withTransaction checkNotNull(read(snapshot.sessionId,CheckpointRow(snapshot.sessionId,prior.sequence,prior.checkpointJson)))
            }
            check(dao.unfinished()==null) { "An unfinished workout already exists" }
            val state=reduce2(base,State2(),EngineCommand.Start)
            val checkpoint=Checkpoint2.from(base,state,0);val checkpointJson=sessionJson.encodeToString(checkpoint)
            dao.insertStart(StartRow(snapshot.sessionId,encoded))
            library.insertPlan(ExecutionPlanRow(snapshot.sessionId,0,snapshot.basePlanJson,snapshot.basePlanHash))
            dao.insertCheckpoint(CheckpointRow(snapshot.sessionId,1,checkpointJson));dao.insertSlot(UnfinishedRow(sessionId=snapshot.sessionId))
            dao.insertAction(ActionRow(snapshot.sessionId,1,operationId,0,encoded,sessionJson.encodeToString(ActionPayload2(null,state.events)),checkpointJson))
            beforeOutbox();dao.insertOutbox(OutboxRow(snapshot.sessionId,1,operationId))
            SavedWorkout2(snapshot,1,base,checkpoint)
        }
    }
    suspend fun commit(id:String,expected:Long,operationId:String,action:SessionAction2):SavedWorkout2 {
        require(operationId.isNotBlank());val intent=sessionJson.encodeToString(action)
        return db.withTransaction {
            val current=checkNotNull(read(id));val prior=dao.operation(id,operationId)
            if(prior!=null) {
                check(prior.expectedRevision==expected && prior.intentJson==intent) { "Operation identity changed" }
                return@withTransaction checkNotNull(read(id,CheckpointRow(id,prior.sequence,prior.checkpointJson)))
            }
            check(current.revision==expected) { "Checkpoint changed" }
            val next=transition2(current,action);val revision=Math.addExact(expected,1)
            if(next.checkpoint.structuralRevision!=current.checkpoint.structuralRevision) {
                val json=planJson2.encodeToString(next.plan)
                library.insertPlan(ExecutionPlanRow(id,next.checkpoint.structuralRevision,json,hashUtf8_2(json),action.adjustment?.adjustmentId))
            }
            val json=sessionJson.encodeToString(next.checkpoint)
            dao.insertAction(ActionRow(id,revision,operationId,expected,intent,sessionJson.encodeToString(next.payload),json))
            check(dao.advanceCheckpoint(id,expected,revision,json)==1)
            beforeOutbox();dao.insertOutbox(OutboxRow(id,revision,operationId))
            if(next.checkpoint.outcome!="inProgress")dao.releaseSlot(id)
            SavedWorkout2(current.start,revision,next.plan,next.checkpoint)
        }
    }
}
