package dev.gymassistant.engine

import dev.gymassistant.engine.v2.*
import java.io.File
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class Runtime2Test {
    private val root=File("../..")
    private val source=root.resolve("examples/templates/timed-runtime-v2.yaml").readText()
    private val exercises=listOf(root.resolve("examples/exercises/bridge-band-family.yaml").readText())
    private val contracts=ImportContracts2(listOf("1.0.0" to "v1","1.1.0" to "v1.1").associate { (v,f)->v to Json.parseToJsonElement(root.resolve("schemas/template.$f.schema.json").readText()).jsonObject },
        Json.parseToJsonElement(root.resolve("schemas/exercise.v1.schema.json").readText()).jsonObject)
    private fun plan(text:String=source)=compileTemplate2(text,exercises,contracts)
    private fun running(p:Plan2,ms:Long)=reduce2(p,reduce2(p,State2(),EngineCommand.Start),EngineCommand.Advance(ms))
    private fun extra(id:String="addition_left_1",set:String="set_left")=Adjustment2(id,set,"2026-11-03T10:00:00Z")
    @Test fun sideWeightAndSourceIdentitySurviveCompilation() {
        val p=plan();assertEquals(80000,p.expectedDurationMs);assertEquals("2.0.0",p.planFormatVersion)
        val left=p.phases.first();assertEquals("left",left.side);assertEquals(12.5,left.resistance?.weight?.value)
        assertEquals("band_yellow",left.resistance?.bands?.single()?.equipmentId)
        assertEquals("occurrence_runtime_hip",left.occurrenceId);assertEquals("block_sides",left.blockId)
        assertEquals(listOf("set_left","set_right"),p.policies.map { it.setId })
        assertEquals(10,p.phases.size)
        val output=root.resolve("packages/engine/build/outputs/runtime2-base.json");output.parentFile.mkdirs();output.writeText(planJson2.encodeToString(p))
    }
    @Test fun appendPreservesCurrentCountdownAndExistingResetBeforeLongHold() {
        val p=plan();val s=running(p,7000);val before=activePhase2(p,s.cursorMs)!!
        val change=appendRepetition2(p,s,extra());val q=change.plan
        assertEquals(90000,q.expectedDurationMs);assertEquals(80000,p.expectedDurationMs)
        assertEquals(s.cursorMs,change.state.cursorMs);assertEquals(s.observedRunMs,change.state.observedRunMs)
        assertEquals(before,activePhase2(q,s.cursorMs))
        val inserted=q.phases.filter { it.adjustmentId!=null }
        assertEquals(listOf("work","reset"),inserted.map { it.role });assertEquals(listOf(6000L,4000L),inserted.map { it.endMs-it.startMs })
        assertEquals(20000,inserted.first().startMs);assertEquals(30000,q.phases.single { it.setId=="set_long_hold" }.startMs)
        assertEquals(1,q.phases.count { it.setId=="set_long_hold" });assertEquals("left",inserted.first().side)
        for(original in p.phases) {val changed=q.phases.single { it.instanceId==original.instanceId };assertEquals(original.ordinal,changed.ordinal);assertEquals(original.baseStartMs,changed.baseStartMs)}
        assertEquals(s.completedInstanceIds,change.state.completedInstanceIds)
        assertEquals(p,plan())
    }
    @Test fun appendBeforeLongRestAddsResetBeforeWorkAndDoesNotDuplicateRest() {
        val p=plan();val c=appendRepetition2(p,running(p,45000),extra("addition_right_1","set_right"))
        assertEquals(listOf("reset","work"),c.plan.phases.filter { it.adjustmentId!=null }.map { it.role })
        assertEquals(90000,c.plan.expectedDurationMs);assertEquals(1,c.plan.phases.count { it.sourceStepId=="step_final_rest" })
        assertEquals(30000,c.plan.phases.last().endMs-c.plan.phases.last().startMs)
        assertEquals("right",c.plan.phases.first { it.adjustmentId!=null }.side)
    }
    @Test fun repeatedTapsAreOrderedWhileDuplicateDomainActionIsIdempotent() {
        val p=plan();val s=running(p,1000);val first=appendRepetition2(p,s,extra());val second=appendRepetition2(first.plan,first.state,extra("addition_left_2"))
        assertEquals(100000,second.plan.expectedDurationMs)
        assertEquals(listOf("addition_left_1","addition_left_2"),second.plan.adjustments.map { it.adjustmentId })
        assertEquals(listOf(3,4),second.plan.phases.filter { it.adjustmentId!=null && it.role=="work" }.map { it.repetitionIndex })
        assertEquals(second,appendRepetition2(second.plan,second.state,extra()))
        assertFails { appendRepetition2(second.plan,second.state,extra().copy(recordedAt="2026-11-03T11:00:00Z")) }
        for(at in listOf("2026-11-03","2026-02-30T10:00:00Z"))assertFails { appendRepetition2(p,s,extra("invalid_date").copy(recordedAt=at)) }
    }
    @Test fun pauseRecoverySkipAndAddedProgressRemainDistinct() {
        val p=plan();val paused=reduce2(p,running(p,3000),EngineCommand.Pause)
        val c=appendRepetition2(p,paused,extra());assertEquals(SessionStatus.Paused,c.state.status)
        val waiting=reduce2(c.plan,c.state,EngineCommand.Advance(1200));assertEquals(1200,waiting.pauseMs);assertEquals(3000,waiting.observedRunMs)
        val resumed=reduce2(c.plan,waiting,EngineCommand.Resume)
        val intoExtra=reduce2(c.plan,resumed,EngineCommand.Advance(19000));assertNull(baseCursor2(c.plan,intoExtra.cursorMs))
        val skipped=reduce2(c.plan,intoExtra,EngineCommand.StepForward)
        assertEquals(intoExtra.observedRunMs,skipped.observedRunMs)
        assertFalse(activePhase2(c.plan,intoExtra.cursorMs)!!.instanceId in skipped.completedInstanceIds)
        val recovered=reduce2(c.plan,skipped,EngineCommand.NoteUnobserved(900))
        assertEquals(900,recovered.unobservedGapMs);assertNull(additionAvailable2(c.plan,recovered))
        assertFails { appendRepetition2(c.plan,recovered,extra("not_allowed")) }
    }
    @Test fun additionsRejectWrongSideCompletedSetAndAmbiguousSource() {
        val p=plan();assertFails { appendRepetition2(p,running(p,1000),extra("wrong_side","set_right")) }
        assertNull(additionAvailable2(p,running(p,22000))) // separate longer hold
        assertNull(additionAvailable2(p,running(p,51000))) // final rest
        assertFails { plan(source.replace("resetForStepId: step_left_1","resetForStepId: step_right_1")) }
        assertFails { plan(source.replace("resetMs: 4000","resetMs: 3000")) }
        assertFails { plan(source.replace("side: left","side: alternating")) }
    }
    @Test fun strictYamlAndContractBoundaryRejectsUnsafeAndQuotedNumbers() {
        for(raw in listOf("a: 1\na: 2", "a: &a [*a]", "a: !!str 1", "---\na: 1\n---\nb: 2", "[".repeat(70)+"]".repeat(70),"a: .nan", "a: 2026-11-03", "a: yes"))assertFails { readYaml2(raw) }
        assertFails { plan(source.replace("repetitions: 2","repetitions: '2'")) }
        assertFails { plan(source.replace("durationMs: 6000","durationMs: -1")) }
        assertFails { plan(source.replace("notes: Synthetic fixed target","unknown: hidden_source_marker")) }
        try { plan("private source marker: [") } catch(e:Exception) {assertFalse(e.message.orEmpty().contains("private source marker"))}
    }
    @Test fun overflowAndSourceDefaultMutationAreNeverAccepted() {
        val p=plan();val s=running(p,1).copy(observedRunMs=MAX_SAFE_2)
        assertFails { reduce2(p,s,EngineCommand.Advance(1)) }
        assertFails { reduce2(p,running(p,1),EngineCommand.Advance(-1)) }
        val c=appendRepetition2(p,running(p,1),extra())
        assertEquals(source,root.resolve("examples/templates/timed-runtime-v2.yaml").readText())
        assertEquals(p.originalDurationMs,c.plan.originalDurationMs)
    }
    @Test fun sharedGoldenRetainsEverySemanticBoundaryAcrossAddsRewindAndRecovery() {
        var p=plan();var state=State2();val trace=mutableListOf<JsonObject>()
        fun capture(label:String) {trace+=buildJsonObject {
            put("label",label);put("status",state.status.name.replaceFirstChar {it.lowercase()})
            put("cursorMs",state.cursorMs);put("observedRunMs",state.observedRunMs);put("pauseMs",state.pauseMs);put("unobservedGapMs",state.unobservedGapMs)
            put("baseCursorMs",baseCursor2(p,state.cursorMs)?.let {JsonPrimitive(it)}?:JsonNull)
            put("completedInstanceIds",JsonArray(state.completedInstanceIds.sorted().map {JsonPrimitive(it)}))
            put("events",planJson2.encodeToJsonElement(state.events));put("plan",planJson2.encodeToJsonElement(p))
        }}
        fun send(label:String,cmd:EngineCommand) {state=reduce2(p,state,cmd);capture(label)}
        fun add(id:String) {val change=appendRepetition2(p,state,extra(id));p=change.plan;state=change.state;capture(id)}
        send("start",EngineCommand.Start);send("tick",EngineCommand.Advance(7000));add("addition_left_1");add("addition_left_2")
        send("pause",EngineCommand.Pause);send("paused",EngineCommand.Advance(1200));send("resume",EngineCommand.Resume)
        send("into_extra",EngineCommand.Advance(16000));send("skip",EngineCommand.StepForward);send("rewind",EngineCommand.StepBackward)
        send("gap",EngineCommand.NoteUnobserved(900));send("explicit_resume",EngineCommand.Resume);send("finish_time",EngineCommand.Advance(200000))
        send("acknowledge",EngineCommand.Acknowledge)
        val encoded=JsonArray(trace).toString()
        val output=root.resolve("packages/engine/build/outputs/runtime2-trace.json");output.parentFile.mkdirs();output.writeText(encoded)
        val golden=root.resolve("examples/expected/timed-runtime-v2.trace.json")
        if(golden.exists())assertEquals(Json.parseToJsonElement(golden.readText()),Json.parseToJsonElement(encoded))
    }

}
