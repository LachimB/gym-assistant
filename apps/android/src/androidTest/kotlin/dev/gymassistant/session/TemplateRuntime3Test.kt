package dev.gymassistant.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gymassistant.engine.v2.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateRuntime3Test {
    private val context:Context=ApplicationProvider.getApplicationContext()
    private val name="library-test-${UUID.randomUUID()}.db"
    private val root=File(context.cacheDir,"library-${UUID.randomUUID()}")
    private var db=Room.databaseBuilder(context,WorkoutDatabase::class.java,name).addMigrations(DELIVERY_MIGRATION_1_2,LIBRARY_MIGRATION_2_3,PERSONAL_DELIVERY_MIGRATION_3_4).build()
    private val files=LibraryFiles3(root)
    private fun asset(name:String)=context.assets.open(name).bufferedReader().use { it.readText() }
    private val contracts=ImportContracts2(mapOf("1.0.0" to Json.parseToJsonElement(asset("template.v1.schema.json")).jsonObject,
        "1.1.0" to Json.parseToJsonElement(asset("template.v1.1.schema.json")).jsonObject),Json.parseToJsonElement(asset("exercise.v1.schema.json")).jsonObject)
    private val source=asset("timed-runtime-v2.yaml")
    private fun inputs(template:String=source)=listOf(ImportFile3("template.yaml",template.toByteArray()),ImportFile3("exercise.yaml",asset("bridge-band-family.yaml").toByteArray()))
    private val at="2026-11-03T10:00:00Z"
    @After fun cleanup() {db.close();context.deleteDatabase(name);root.deleteRecursively()}
    private suspend fun publish(library:TemplateLibrary3,candidate:TemplateCandidateRow)=library.publish(candidate.candidateId,checkNotNull(candidate.sourceHash),checkNotNull(candidate.compiledPlanHash),at)
    private suspend fun rejects(action:suspend()->Unit) { var failed=false;try {action()}catch(_:IllegalStateException){failed=true}catch(_:IllegalArgumentException){failed=true};assertTrue(failed) }
    @Test fun onlyExplicitReviewedPublicationCanBecomeExecutable()=runBlocking {
        val library=TemplateLibrary3(db,files,contracts)
        val invalid=library.stage(inputs(source.replace("durationMs: 6000","durationMs: -1")),at)
        assertEquals("invalidOrUnsupported",invalid.validationCode);assertTrue(library.published().isEmpty())
        rejects { library.publish(invalid.candidateId,"wrong","wrong",at) }
        val good=library.stage(inputs(),at);assertEquals("valid",good.validationCode);assertTrue(library.published().isEmpty())
        rejects { library.publish(good.candidateId,"wrong",checkNotNull(good.compiledPlanHash),at) }
        val published=publish(library,good)
        assertEquals(listOf(published),library.published());assertEquals(published,publish(library,good))
        assertEquals(80000,library.verify(published).expectedDurationMs)
        assertEquals("published",library.candidate(good.candidateId)?.reviewStatus)
    }
    @Test fun staleReviewAndFailedPublicationRetainTheLastHead()=runBlocking {
        val library=TemplateLibrary3(db,files,contracts)
        val original=publish(library,library.stage(inputs(),at))
        val nextText=source.replace("revision_runtime_synthetic_1","revision_runtime_v3")
        val next=library.stage(inputs(nextText),at)
        val stale=library.stage(inputs(source.replace("revision_runtime_synthetic_1","revision_runtime_v4")),at)
        val failing=TemplateLibrary3(db,files,contracts) {error("Injected publication failure")}
        rejects {publish(failing,next)}
        assertEquals(listOf(original),library.published());assertNull(db.library().revision(original.templateId,"revision_runtime_v3"))
        val updated=publish(library,next);rejects {publish(library,stale)}
        assertEquals(listOf(updated),library.published())
        rejects {publish(library,library.stage(inputs(),at))} // Older identity cannot silently restore defaults.
        val restored=publish(library,library.stage(inputs(source.replace("revision_runtime_synthetic_1","revision_runtime_v5")),at))
        assertEquals("revision_runtime_v5",restored.revisionId)
    }
    @Test fun tamperedStagingAndReusedRevisionCannotOverwritePublishedFiles()=runBlocking {
        val library=TemplateLibrary3(db,files,contracts)
        val original=publish(library,library.stage(inputs(),at))
        val reuse=library.stage(inputs(source.replace("durationMs: 6000","durationMs: 7000")),at)
        rejects {publish(library,reuse)}
        val candidate=library.stage(inputs(source.replace("revision_runtime_synthetic_1","revision_runtime_v3")),at)
        val descriptors=sessionJson.decodeFromString<List<LibraryFile3>>(candidate.sourceFilesJson)
        File(root,descriptors.first().path).appendText("\n# changed after review\n")
        rejects {publish(library,candidate)}
        assertEquals(listOf(original),library.published())
    }
    private suspend fun start2():SessionStart2 {
        val library=TemplateLibrary3(db,files,contracts);val row=publish(library,library.stage(inputs(),at))
        return SessionStart2(UUID.randomUUID().toString(),row.sourceSnapshotJson,row.sourceHash,row.compiledPlanJson,row.compiledPlanHash,at,"Europe/Berlin","2026-11-03")
    }
    @Test fun extraRepetitionIsAtomicIdempotentAndSurvivesRestartWithoutChangingBase()=runBlocking {
        val start=start2();var store=RoomSessionStore2(db,contracts)
        var saved=store.start(start,"start")
        saved=store.commit(start.sessionId,saved.revision,"tick",SessionAction2("tick",1000,at))
        val prior=saved
        val add=SessionAction2("add",recordedAt=at,adjustment=Adjustment2("add_left_one","set_left",at))
        val failing=RoomSessionStore2(db,contracts) {error("Injected action failure")}
        rejects {failing.commit(start.sessionId,prior.revision,"add",add)}
        assertEquals(prior,store.load(start.sessionId));assertEquals(1,db.library().plans(start.sessionId).size)
        saved=store.commit(start.sessionId,prior.revision,"add",add)
        assertEquals(saved,store.commit(start.sessionId,prior.revision,"add",add))
        assertEquals(90000,saved.plan.expectedDurationMs);assertEquals(80000,planJson2.decodeFromString<Plan2>(start.basePlanJson).expectedDurationMs)
        assertEquals(2,db.library().plans(start.sessionId).size)
        saved=store.commit(start.sessionId,saved.revision,"pause",SessionAction2("pause",250,at))
        val old=saved;db.close()
        db=Room.databaseBuilder(context,WorkoutDatabase::class.java,name).build();store=RoomSessionStore2(db,contracts)
        assertEquals(old,store.unfinished())
        val recovered=store.commit(start.sessionId,old.revision,"recover",SessionAction2("recover",recordedAt=at))
        assertEquals(old.state.observedRunMs,recovered.state.observedRunMs);assertEquals(1,recovered.checkpoint.unknownGapCount)
        assertNull(additionAvailable2(recovered.plan,recovered.state))
        rejects {store.commit(start.sessionId,recovered.revision,"unsupported-known-gap",SessionAction2("recover",recordedAt=at,gapMs=1000))}
        assertEquals(recovered,store.load(start.sessionId))
        assertEquals(2,db.library().plans(start.sessionId).size)
        assertEquals(start,recovered.start)
    }
    @Test fun completedIdentityRangesAreCompactAndLegacyWorkoutPreventsSecondStart()=runBlocking {
        val start=start2();val store=RoomSessionStore2(db,contracts)
        var saved=store.start(start,"start")
        saved=store.commit(start.sessionId,saved.revision,"advance",SessionAction2("tick",20000,at))
        assertEquals(listOf(OrdinalRange2(0,3)),saved.checkpoint.completedOrdinalRanges)
        rejects {store.start(start.copy(sessionId=UUID.randomUUID().toString()),"second")}
        saved=store.commit(start.sessionId,saved.revision,"end",SessionAction2("end",recordedAt=at))
        assertNull(store.unfinished());assertEquals("completed",saved.checkpoint.outcome)
        val legacy=RoomSessionStore(db)
        val template=asset("composite-pyramid.yaml");val exercises=listOf(asset("bridge-band-family.yaml"),asset("plank-family.yaml"))
        val plan=dev.gymassistant.engine.compileTemplate(template,exercises)
        val old=legacy.start(SessionStart(UUID.randomUUID().toString(),template,exercises,plan,hashPlan(plan),at,"Europe/Berlin","2026-11-03"),"legacy")
        rejects {store.start(start.copy(sessionId=UUID.randomUUID().toString()),"second-new")}
        assertEquals(old,legacy.unfinished());assertEquals(saved,store.load(start.sessionId))
    }
    @Test fun driverRecoveryRequiresResumeAndLateAddTapCannotMoveIntoTheNextSet()=runBlocking {
        val start=start2();val store=RoomSessionStore2(db,contracts);var now=0L
        val driver=SessionDriver2(store,{now},{at})
        driver.start(start)
        now=1000;driver.send("tick")
        val restarted=SessionDriver2(store,{now},{at})
        now=50000
        val recovered=checkNotNull(restarted.restore())
        assertEquals(1000,recovered.state.observedRunMs);assertTrue(restarted.needsRecovery)
        rejects {restarted.send("add")}
        restarted.send("resume")
        now+=5000
        val gap=restarted.send("tick")
        assertEquals(1000,gap.state.observedRunMs);assertEquals(5000,gap.state.unobservedGapMs)
        assertTrue(restarted.needsRecovery)
        restarted.send("resume")
        // A button sampled just before a boundary can be tapped just after it.
        var saved=store.load(start.sessionId)!!
        saved=store.commit(start.sessionId,saved.revision,"end",SessionAction2("end",recordedAt=at))
        val next=start.copy(sessionId=UUID.randomUUID().toString())
        val late=SessionDriver2(store,{now},{at});late.start(next)
        repeat(9) {now+=2000;late.send("tick")}
        now+=1500;late.send("tick")
        assertNotNull(additionAvailable2(checkNotNull(late.saved).plan,checkNotNull(late.saved).state))
        now+=1000
        val unchanged=late.send("add")
        assertTrue(unchanged.plan.adjustments.isEmpty());assertFalse(late.needsRecovery)
        assertEquals(20500,unchanged.state.cursorMs)
        assertNotNull(store.history(next.sessionId))
    }

    @Test fun naturalPlanEndAndAcknowledgementSurviveReopenWithoutAnActivePhase()=runBlocking {
        val start=start2();var store=RoomSessionStore2(db,contracts)
        var saved=store.start(start,"start")
        saved=store.commit(start.sessionId,saved.revision,"add",SessionAction2("add",recordedAt=at,adjustment=Adjustment2("complete_add","set_left",at)))
        saved=store.commit(start.sessionId,saved.revision,"natural-end",SessionAction2("tick",90000,at))
        assertEquals(dev.gymassistant.engine.SessionStatus.Completed,saved.state.status)
        assertNull(saved.checkpoint.activePhaseInstanceId);assertEquals(0L,saved.checkpoint.phaseOffsetMs)
        assertEquals(12,saved.state.completedInstanceIds.size);assertNotNull(store.unfinished())
        val atEnd=saved;db.close()
        db=Room.databaseBuilder(context,WorkoutDatabase::class.java,name).build();store=RoomSessionStore2(db,contracts)
        assertEquals(atEnd,store.unfinished())
        val driver=SessionDriver2(store,{0L},{at});val recovered=checkNotNull(driver.restore())
        assertEquals(dev.gymassistant.engine.SessionStatus.Completed,recovered.state.status)
        assertEquals(atEnd.state.observedRunMs,recovered.state.observedRunMs)
        assertEquals(0,recovered.checkpoint.unknownGapCount)
        val done=driver.send("finish")
        assertTrue(done.state.completionAcknowledged);assertNull(store.unfinished())
        assertEquals("plannedEnd",store.history(start.sessionId)?.completionKind)
        assertEquals(1,store.history(start.sessionId)?.events?.count {it.type=="planEndReached"})
    }

    @Test fun personalPayloadIsFileBackedFrozenAndAcknowledgesOnlyItsSession()=runBlocking {
        val start=start2();val sessions=RoomSessionStore2(db,contracts);var saved=sessions.start(start,"start")
        val payloads=DeliveryPayloadFiles4(File(root,"payloads"));val local=PersonalDeliveryStore4(db,payloads,contracts)
        rejects {local.preview(start.sessionId)}
        saved=sessions.commit(start.sessionId,saved.revision,"end",SessionAction2("end",1000,at))
        val (revision,history)=local.preview(start.sessionId);val bytes=history.toCanonicalJson();val hash=sha256Utf8(bytes)
        val connection=RoomDeliveryStore(db).bind(DriveConnectionRow("personal-binding","googleDrive","id","fixture@example.test","personal-root","personal-m1-test",at,at))
        rejects {local.enqueue(connection.connectionId,start.sessionId,"job",revision,"wrong")}
        val before=db.workouts().outbox(start.sessionId)
        val job=local.enqueue(connection.connectionId,start.sessionId,"personal-job",revision,hash)
        assertEquals(job,local.enqueue(connection.connectionId,start.sessionId,"unused-id",revision,hash))
        assertEquals(bytes,local.load(job.deliveryId).second.canonicalJson)
        assertEquals(before,db.workouts().outbox(start.sessionId))
        assertNull(db.deliveries().delivery(job.deliveryId))
        File(root,"payloads/"+job.payloadRelativePath).delete()
        assertEquals(bytes,local.load(job.deliveryId).second.canonicalJson) // Exact frozen reconstruction only.
        val loaded=local.load(job.deliveryId).second
        rejects {local.save(loaded,loaded.copy(canonicalJson="changed"))}
        local.save(loaded,loaded.copy(remoteFileId="reserved",state="delivered",attemptCount=1))
        assertTrue(db.workouts().outbox(start.sessionId).all {it.status=="delivered"})
        assertEquals(saved,sessions.load(start.sessionId))
        File(root,"payloads/"+job.payloadRelativePath).writeText("corrupt")
        rejects {local.load(job.deliveryId)}
    }
    @Test fun failedPersonalInsertLeavesNoExecutableJobAndDiscardedIsIneligible()=runBlocking {
        val start=start2();val sessions=RoomSessionStore2(db,contracts);var saved=sessions.start(start,"start")
        saved=sessions.commit(start.sessionId,saved.revision,"end",SessionAction2("end",1000,at))
        val payloads=DeliveryPayloadFiles4(File(root,"payloads"));val local=PersonalDeliveryStore4(db,payloads,contracts){error("Injected insert failure")}
        val (revision,history)=local.preview(start.sessionId)
        val connection=RoomDeliveryStore(db).bind(DriveConnectionRow("personal-binding","googleDrive","id","fixture@example.test","personal-root","personal-m1-test",at,at))
        rejects {local.enqueue(connection.connectionId,start.sessionId,"orphan-job",revision,sha256Utf8(history.toCanonicalJson()))}
        assertNull(db.personalDeliveries().get("orphan-job"));assertTrue(File(root,"payloads/orphan-job.json").isFile)
        val next=start.copy(sessionId="discarded2");val fresh=sessions.start(next,"start")
        sessions.commit(next.sessionId,fresh.revision,"discard",SessionAction2("discard",recordedAt=at))
        rejects {local.preview(next.sessionId)}
        assertEquals(listOf(start.sessionId),local.eligible().map {it.sessionId})
    }

}
