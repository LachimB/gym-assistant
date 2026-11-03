package dev.gymassistant.session

import dev.gymassistant.engine.EngineCommand
import dev.gymassistant.engine.SessionStatus
import dev.gymassistant.engine.v2.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable data class HistorySource2(val sourceSnapshotJson:String,val sourceHash:String,val compiledPlanJson:String,val compiledPlanHash:String,
    val planFormatVersion:String="2.0.0",val hashFormat:String="sha256-utf8")
@Serializable data class EffectiveHistoryPlan2(val compiledPlanJson:String,val compiledPlanHash:String,val planFormatVersion:String="2.0.0")
@Serializable data class HistoryTiming2(val plannedDurationMs:Long,val adjustedPlannedDurationMs:Long,val adjustedScheduledElapsedMs:Long,
    val scheduledElapsedMs:Long?,val observedRunMs:Long,val pauseMs:Long,val unobservedGapMs:Long,val unknownGapCount:Int)
@Serializable data class HistoryEvent2(val eventId:String,val type:String,val recordedAt:String,val adjustedScheduledElapsedMs:Long,
    val scheduledElapsedMs:Long?,val observedRunMs:Long,val pauseMs:Long,val unobservedGapMs:Long,val unknownGapCount:Int,
    val phaseInstanceId:String?=null,val adjustmentId:String?=null,val setId:String?=null,val detail:HistoryEventDetail?=null)
@Serializable data class SessionHistory2(val sessionId:String,val templateId:String,val revisionId:String,val engineVersion:String,
    val startedAt:String,val timeZone:String,val localTrainingDate:String,val status:String,val completionKind:String,
    val sourcePlanSnapshot:HistorySource2,val effectivePlanSnapshot:EffectiveHistoryPlan2,val adjustments:List<Adjustment2>,
    val timing:HistoryTiming2,val events:List<HistoryEvent2>,val finishedAt:String?=null,val schemaVersion:String="1.2.0")
fun SessionHistory2.toCanonicalJson():String {
    val encoded=historyJson.encodeToJsonElement(this).jsonObject
    return buildJsonObject {
        for((key,value) in encoded)put(key,when(key) {
            "timing"->JsonObject(value.jsonObject+ ("scheduledElapsedMs" to (timing.scheduledElapsedMs?.let {JsonPrimitive(it)}?:JsonNull)))
            "events"->JsonArray(value.jsonArray.mapIndexed {index,event->JsonObject(event.jsonObject+("scheduledElapsedMs" to (events[index].scheduledElapsedMs?.let {JsonPrimitive(it)}?:JsonNull)))})
            else->value
        })
    }.toString()
}

/** Replay one atomic committed prefix; private receipts remain outside the portable result. */
internal fun projectSession2(saved:SavedWorkout2,rows:List<ActionRow>,plans:List<ExecutionPlanRow>,contracts:ImportContracts2):SessionHistory2 {
    val start=saved.start;val base=start.validate(contracts)
    check(rows.isNotEmpty() && rows.size.toLong()==saved.revision)
    val started=reduce2(base,State2(),EngineCommand.Start)
    var prior=SavedWorkout2(start,1,base,Checkpoint2.from(base,started,0))
    val expectedPlans=mutableListOf(ExecutionPlanRow(start.sessionId,0,start.basePlanJson,start.basePlanHash))
    val seen=mutableSetOf<String>();val events=mutableListOf<HistoryEvent2>();var kind="unfinished"
    fun event(type:String,at:String,cp:Checkpoint2,e:Event2?=null,detail:HistoryEventDetail?=null) {
        events+=HistoryEvent2(identity2("event",start.sessionId,"history-2",events.size.toString()),type,at,
            e?.adjustedScheduledElapsedMs?:cp.adjustedScheduledElapsedMs,
            if(e!=null)e.baseScheduledElapsedMs else baseCursor2(prior.plan,cp.adjustedScheduledElapsedMs),
            e?.observedRunMs?:cp.observedRunMs,e?.pauseMs?:cp.pauseMs,e?.unobservedGapMs?:cp.unobservedGapMs,cp.unknownGapCount,
            if(type in setOf("phaseStarted","phaseCompleted","skip","rewind"))e?.phaseInstanceId else null,e?.adjustmentId,e?.setId,detail)
    }
    rows.forEachIndexed { index,row ->
        val sequence=index.toLong()+1
        check(row.sessionId==start.sessionId && row.sequence==sequence && row.expectedRevision==sequence-1 && row.operationId.isNotBlank() && seen.add(row.operationId))
        val cp=sessionJson.decodeFromString<Checkpoint2>(row.checkpointJson)
        val payload=sessionJson.decodeFromString<ActionPayload2>(row.payloadJson)
        val action:SessionAction2?;val expected:Transition2
        if(index==0) {
            check(sessionJson.decodeFromString<SessionStart2>(row.intentJson)==start)
            action=null;expected=Transition2(base,prior.checkpoint,ActionPayload2(null,started.events))
        }else {action=sessionJson.decodeFromString(row.intentJson);expected=transition2(prior,checkNotNull(action))}
        check(cp==expected.checkpoint && payload==expected.payload)
        if(expected.checkpoint.structuralRevision!=prior.checkpoint.structuralRevision) {
            val json=planJson2.encodeToString(expected.plan)
            expectedPlans+=ExecutionPlanRow(start.sessionId,cp.structuralRevision,json,hashUtf8_2(json),action?.adjustment?.adjustmentId)
        }
        val old=prior;prior=SavedWorkout2(start,sequence,expected.plan,cp)
        val at=action?.recordedAt?:start.startedAt
        for(e in payload.events) {
            val type=when(e.type) {
                "sessionStarted","phaseStarted","phaseCompleted","pause","resume","skip","rewind","repetitionAdded"->e.type
                "resumeAfterGap"->"resume";"unobservedGap"->"interruption";else->error("Unsupported semantic event")
            }
            val detail=when(e.type) {
                "resumeAfterGap"->HistoryEventDetail(resumedAfterInterruption=true)
                "unobservedGap"->HistoryEventDetail(gapDurationKnown=action?.gapMs!=null,gapDurationMs=action?.gapMs)
                else->null
            }
            event(type,at,cp,e,detail)
        }
        if(index>0 && cp.unknownGapCount>old.checkpoint.unknownGapCount && payload.events.none {it.type=="unobservedGap"})
            event("interruption",at,cp,detail=HistoryEventDetail(gapDurationKnown=false))
        if(index>0 && cp.status==SessionStatus.Completed.name && old.checkpoint.status!=SessionStatus.Completed.name && cp.outcome=="inProgress")event("planEndReached",at,cp)
        if(cp.outcome!="inProgress") {
            kind=when(action?.kind) {"finish"->"plannedEnd";"end"->"partial";"discard"->"discarded";else->error("Invalid finalization")}
            event(if(kind=="discarded")"sessionDiscarded" else "sessionEnded",at,cp,detail=HistoryEventDetail(completionKind=kind))
        }
    }
    check(prior==saved && plans==expectedPlans)
    val cp=saved.checkpoint
    val status=when {
        cp.outcome=="discarded"->"discarded";cp.outcome=="completed"->"completed"
        cp.status==SessionStatus.Completed.name->"awaitingCompletion"
        cp.status in setOf(SessionStatus.Paused.name,SessionStatus.RecoveredPaused.name)->"paused"
        cp.status==SessionStatus.Running.name->"inProgress";else->error("Unsupported state")
    }
    val effective=planJson2.encodeToString(saved.plan)
    return SessionHistory2(start.sessionId,base.templateId,base.revisionId,start.engineVersion,start.startedAt,start.timeZone,start.localTrainingDate,status,kind,
        HistorySource2(start.sourceSnapshotJson,start.sourceHash,start.basePlanJson,start.basePlanHash),EffectiveHistoryPlan2(effective,hashUtf8_2(effective)),saved.plan.adjustments,
        HistoryTiming2(base.originalDurationMs,saved.plan.expectedDurationMs,cp.adjustedScheduledElapsedMs,baseCursor2(saved.plan,cp.adjustedScheduledElapsedMs),
            cp.observedRunMs,cp.pauseMs,cp.unobservedGapMs,cp.unknownGapCount),events,cp.finishedAt)
}
