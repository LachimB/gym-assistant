package dev.gymassistant.session

import dev.gymassistant.engine.EngineCommand
import dev.gymassistant.engine.v2.*
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class SessionProjection2Test {
    private val root=File(checkNotNull(System.getProperty("gym.root")))
    private fun schema(name:String)=Json.parseToJsonElement(root.resolve("schemas/$name").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val at="2026-11-03T10:00:00Z"
    @Test fun humanHistoryContainsSemanticAddsAndUncertaintyWithoutPrivateTickReceipts() {
        val source=TemplateSources3(root.resolve("examples/templates/timed-runtime-v2.yaml").readText(),listOf(root.resolve("examples/exercises/bridge-band-family.yaml").readText()))
        val sourceJson=sessionJson.encodeToString(source)
        val base=compileTemplate2(source.templateYaml,source.exerciseYamls,contracts)
        val baseJson=planJson2.encodeToString(base)
        val start=SessionStart2("synthetic_runtime_session_v2",sourceJson,hashUtf8_2(sourceJson),baseJson,hashUtf8_2(baseJson),at,"Europe/Berlin","2026-11-03")
        val started=reduce2(base,State2(),EngineCommand.Start)
        var saved=SavedWorkout2(start,1,base,Checkpoint2.from(base,started,0))
        val rows=mutableListOf(ActionRow(start.sessionId,1,"private_operation_start",0,sessionJson.encodeToString(start),sessionJson.encodeToString(ActionPayload2(null,started.events)),sessionJson.encodeToString(saved.checkpoint)))
        val plans=mutableListOf(ExecutionPlanRow(start.sessionId,0,baseJson,hashUtf8_2(baseJson)))
        fun commit(action:SessionAction2) {
            val next=transition2(saved,action);val rev=saved.revision+1
            if(next.checkpoint.structuralRevision!=saved.checkpoint.structuralRevision) {
                val json=planJson2.encodeToString(next.plan);plans+=ExecutionPlanRow(start.sessionId,next.checkpoint.structuralRevision,json,hashUtf8_2(json),action.adjustment?.adjustmentId)
            }
            rows+=ActionRow(start.sessionId,rev,"private_operation_$rev",saved.revision,sessionJson.encodeToString(action),sessionJson.encodeToString(next.payload),sessionJson.encodeToString(next.checkpoint))
            saved=SavedWorkout2(start,rev,next.plan,next.checkpoint)
        }
        repeat(4){commit(SessionAction2("tick",250,at))}
        commit(SessionAction2("add",recordedAt=at,adjustment=Adjustment2("extra_left_one","set_left",at)))
        commit(SessionAction2("pause",250,at));commit(SessionAction2("tick",4000,at))
        commit(SessionAction2("recover",recordedAt=at));commit(SessionAction2("recover",recordedAt=at))
        commit(SessionAction2("resume",recordedAt=at))
        commit(SessionAction2("tick",21000,at)) // Within inserted work: base position must be null.
        commit(SessionAction2("forward",recordedAt=at));commit(SessionAction2("back",recordedAt=at))
        commit(SessionAction2("end",recordedAt=at))
        val history=projectSession2(saved,rows,plans,contracts)
        assertEquals(2,history.timing.unknownGapCount);assertEquals(4000,history.timing.pauseMs)
        assertEquals(80000,history.timing.plannedDurationMs);assertEquals(90000,history.timing.adjustedPlannedDurationMs)
        assertNull(history.timing.scheduledElapsedMs)
        assertEquals(1,history.events.count {it.type=="repetitionAdded"})
        assertEquals(2,history.events.count {it.type=="interruption"})
        assertEquals("partial",history.completionKind)
        val json=history.toCanonicalJson()
        for(secret in listOf("private_operation","structuralRevision","completedOrdinalRanges","elapsedMs","outbox"))assertFalse(json.contains(secret))
        val output=root.resolve("apps/android/build/outputs/history-projection/synthetic-runtime-v2-session.json")
        checkNotNull(output.parentFile).mkdirs();output.writeText(json)
        assertFails {projectSession2(saved,rows.dropLast(1),plans,contracts)}
        assertFails {projectSession2(saved,rows,plans.dropLast(1),contracts)}
        assertFails {projectSession2(saved,rows.mapIndexed {i,r->if(i==4)r.copy(payloadJson="{}")else r},plans,contracts)}
    }
    @Test fun completedTimelineHasNoActivePhaseAndCanBeAcknowledgedAndProjected() {
        val source=TemplateSources3(root.resolve("examples/templates/timed-runtime-v2.yaml").readText(),listOf(root.resolve("examples/exercises/bridge-band-family.yaml").readText()))
        val raw=sessionJson.encodeToString(source);val base=compileTemplate2(source.templateYaml,source.exerciseYamls,contracts);val encoded=planJson2.encodeToString(base)
        val start=SessionStart2("synthetic_runtime_completed_v2",raw,hashUtf8_2(raw),encoded,hashUtf8_2(encoded),at,"Europe/Berlin","2026-11-03")
        val state=reduce2(base,State2(),EngineCommand.Start)
        var saved=SavedWorkout2(start,1,base,Checkpoint2.from(base,state,0))
        val rows=mutableListOf(ActionRow(start.sessionId,1,"start",0,sessionJson.encodeToString(start),sessionJson.encodeToString(ActionPayload2(null,state.events)),sessionJson.encodeToString(saved.checkpoint)))
        val plans=mutableListOf(ExecutionPlanRow(start.sessionId,0,encoded,start.basePlanHash))
        fun send(action:SessionAction2) {
            val next=transition2(saved,action);val revision=saved.revision+1
            if(next.checkpoint.structuralRevision!=saved.checkpoint.structuralRevision) {
                val json=planJson2.encodeToString(next.plan);plans+=ExecutionPlanRow(start.sessionId,next.checkpoint.structuralRevision,json,hashUtf8_2(json),action.adjustment?.adjustmentId)
            }
            rows+=ActionRow(start.sessionId,revision,"operation_$revision",saved.revision,sessionJson.encodeToString(action),sessionJson.encodeToString(next.payload),sessionJson.encodeToString(next.checkpoint))
            saved=SavedWorkout2(start,revision,next.plan,next.checkpoint)
        }
        send(SessionAction2("add",recordedAt=at,adjustment=Adjustment2("completed_extra_one","set_left",at)))
        send(SessionAction2("tick",90000,at))
        assertNull(saved.checkpoint.activePhaseInstanceId);assertEquals(0L,saved.checkpoint.phaseOffsetMs)
        assertEquals(dev.gymassistant.engine.SessionStatus.Completed,saved.state.status)
        assertEquals(12,saved.state.completedInstanceIds.size)
        send(SessionAction2("finish",recordedAt=at))
        val history=projectSession2(saved,rows,plans,contracts)
        assertEquals("plannedEnd",history.completionKind);assertEquals(80000,history.timing.scheduledElapsedMs)
        assertEquals(90000,history.timing.adjustedScheduledElapsedMs);assertEquals(90000,history.timing.observedRunMs)
        assertEquals(1,history.events.count {it.type=="planEndReached"})
        val output=root.resolve("apps/android/build/outputs/history-projection/synthetic-runtime-v2-completed-session.json")
        checkNotNull(output.parentFile).mkdirs();output.writeText(history.toCanonicalJson())
    }

}
