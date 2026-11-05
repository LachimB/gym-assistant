package dev.gymassistant.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gymassistant.engine.v2.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class TemplateSourceCapture5Test {
    private val context:Context=ApplicationProvider.getApplicationContext();private val name="capture-test-${UUID.randomUUID()}.db"
    private val root=File(context.cacheDir,"source-capture-${UUID.randomUUID()}");private var db=open();private var now=1000L
    private fun open()=Room.databaseBuilder(context,WorkoutDatabase::class.java,name).addMigrations(DELIVERY_MIGRATION_1_2,LIBRARY_MIGRATION_2_3,PERSONAL_DELIVERY_MIGRATION_3_4,TEMPLATE_SYNC_MIGRATION_4_5).build()
    private fun asset(n:String)=context.assets.open(n).bufferedReader().use {it.readText()}
    private val contracts=ImportContracts2(mapOf("1.0.0" to Json.parseToJsonElement(asset("template.v1.schema.json")).jsonObject,
        "1.1.0" to Json.parseToJsonElement(asset("template.v1.1.schema.json")).jsonObject),Json.parseToJsonElement(asset("exercise.v1.schema.json")).jsonObject)
    private fun syncFiles()=TemplateSyncFiles5(File(root,"sync"),contracts)
    private fun queueFiles()=TemplateSyncQueueFiles5(syncFiles(),contracts)
    private fun store(before:suspend ()->Unit={},commit:()->Unit={}):TemplateSourceCaptureStore5 {
        val files=syncFiles();return TemplateSourceCaptureStore5(db,TemplateSourceCaptureFiles5(files,TemplateSyncQueueFiles5(files,contracts)),{now},before,commit)
    }
    private data class Fixture(val lease:SyncLease5,val namespace:SourceCaptureNamespace5,val revision:TemplateRevisionRow,val job:TemplateSyncJob5)
    private val pins=listOf(SourceCapturePin5("template","template.yaml","working-template","text/plain"),SourceCapturePin5("exercise","exercise.yaml","working-exercise","text/plain"))
    private val at="2026-11-05T12:00:00Z"
    private suspend fun fixture():Fixture {
        val library=TemplateLibrary3(db,LibraryFiles3(File(root,"library")),contracts)
        val candidate=library.stage(listOf(ImportFile3("template.yaml",asset("timed-runtime-v2.yaml").toByteArray()),ImportFile3("exercise.yaml",asset("bridge-band-family.yaml").toByteArray())),at)
        val revision=library.publish(candidate.candidateId,checkNotNull(candidate.sourceHash),checkNotNull(candidate.compiledPlanHash),at)
        RoomDeliveryStore(db).bind(DriveConnectionRow("connection-test","googleDrive","synthetic-owner","fixture@example.test","personal-area","personal-m1-test",at,at))
        db.templateSync().insertRoot(ProjectStorageRoot5("project-test",permissionId="synthetic-owner",commonRootFolderId="project-root",marker="gym-project-test",state="active"))
        db.templateSync().insertBinding(TemplateSyncBinding5("binding-test","connection-test","project-test",revision.templateId,"template-folder","working-folder","history-folder","metadata-folder","gym-template-test","head-file",enabled=true,createdAt=at,updatedAt=at))
        val queue=TemplateSyncQueue5(db,queueFiles());val job=queue.enqueue("binding-test",revision.revisionId,revision.sourceHash,revision.compiledPlanHash,"operation-test","job-test")
        val lease=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,now,"boot-test"));val local=queue.load(lease){now}
        val namespace=SourceCaptureNamespace5(local.bindingHash,"synthetic-owner","project-root","personal-area","template-folder","working-folder","history-folder","metadata-folder","head-file","gym-template-test")
        return Fixture(lease,namespace,revision,db.templateSync().job(job.jobId)!!)
    }
    private fun rows()=listOf("session_starts","checkpoints","actions","unfinished_slot","outbox","drive_connections","session_deliveries","template_candidates","template_revisions","template_heads","execution_plan_revisions","personal_session_deliveries").associateWith {table->
        db.openHelper.writableDatabase.query("SELECT * FROM `$table` ORDER BY rowid").use {c->buildList {while(c.moveToNext())add((0 until c.columnCount).map {i->if(c.isNull(i))null else c.getString(i)})}}}
    @After fun cleanup(){db.close();context.deleteDatabase(name);root.deleteRecursively()}
    @Test fun incompleteCaptureReopensWithoutCandidateAdoptionJobAdvanceOrLegacyChanges()=runBlocking {
        val f=fixture();val before=rows();val s=store();val first=s.begin(f.lease,"observation-test",f.namespace,pins,at)
        assertEquals(first,s.begin(f.lease,"observation-test",f.namespace,pins,at))
        val raw=PublicationRawObservation5("WORKING","working-template",200,"{bad-json".toByteArray(),byteArrayOf())
        s.retainRaw(f.lease,"observation-test",raw);db.close();db=open();val checked=store().load(f.lease,"observation-test")
        assertEquals(1,checked.rawObservations.size);assertArrayEquals(raw.metadataBytes,checked.rawObservations.single().metadataBytes)
        assertArrayEquals(byteArrayOf(),checked.rawObservations.single().contentBytes)
        val row=db.templateSync().observation("observation-test")!!;assertEquals("changing",row.validationCode);assertEquals("pending",row.reviewState)
        assertNull(row.candidateId);assertNull(row.remoteGeneration);assertNull(row.remoteHeadTag)
        assertEquals(f.job,db.templateSync().job("job-test"));assertEquals(before,rows());assertEquals(f.revision.revisionId,db.library().head(f.revision.templateId)!!.revisionId)
    }
    @Test fun failedPointerCommitRetainsThePriorObservationAndAllowsExplicitRetry()=runBlocking {
        val f=fixture();val before=rows();val s=store();s.begin(f.lease,"observation-test",f.namespace,pins,at)
        val prior=db.templateSync().observation("observation-test");val raw=PublicationRawObservation5("HEAD","head-file",404,null,null);var failed=false
        try{store(commit={error("Synthetic capture pointer failure")}).retainRaw(f.lease,"observation-test",raw)}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(prior,db.templateSync().observation("observation-test"));assertTrue(s.load(f.lease,"observation-test").rawObservations.isEmpty())
        val next=s.retainRaw(f.lease,"observation-test",raw);assertEquals(next,s.begin(f.lease,"observation-test",f.namespace,pins,at))
        assertEquals(1,s.load(f.lease,"observation-test").rawObservations.size);assertEquals(f.job,db.templateSync().job("job-test"));assertEquals(before,rows())
    }
    @Test fun expiredLeaseAndNamespaceRetargetCannotAcknowledgePreparedCapture()=runBlocking {
        val f=fixture();val before=rows();var failed=false
        try{store(before={now=f.lease.until}).begin(f.lease,"observation-test",f.namespace,pins,at)}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertNull(db.templateSync().observation("observation-test"));assertEquals(before,rows())
        val next=checkNotNull(TemplateSyncLeases5(db).claim("job-test",now,"boot-test"));failed=false
        try{store(before={db.openHelper.writableDatabase.execSQL("UPDATE template_sync_bindings SET workingFolderId='other-working' WHERE bindingId='binding-test'")}).begin(next,"observation-test",f.namespace,pins,at)}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertNull(db.templateSync().observation("observation-test"));assertEquals(before,rows())
    }
    @Test fun anObservationCannotAppendAfterItsFrozenJobOriginOrReviewStateChanges()=runBlocking {
        val f=fixture();val before=rows();val s=store();s.begin(f.lease,"observation-test",f.namespace,pins,at)
        val prior=db.templateSync().observation("observation-test");val raw=PublicationRawObservation5("HEAD","head-file",404,null,null)
        db.openHelper.writableDatabase.execSQL("UPDATE template_sync_observations SET reviewState='rejected' WHERE observationId='observation-test'")
        var failed=false;try{s.retainRaw(f.lease,"observation-test",raw)}catch(_:IllegalStateException){failed=true};assertTrue(failed)
        assertEquals(prior!!.observationHash,db.templateSync().observation("observation-test")!!.observationHash)
        db.openHelper.writableDatabase.execSQL("UPDATE template_sync_observations SET reviewState='pending' WHERE observationId='observation-test'")
        val binding=db.templateSync().binding("binding-test")!!;val other=queueFiles().prepare("job-test","operation-test",binding,f.namespace.bindingHash,f.revision)
        db.openHelper.writableDatabase.execSQL("UPDATE template_sync_jobs SET intentPath=?,intentHash=? WHERE jobId='job-test'",arrayOf(other.path,other.sha256))
        failed=false;try{s.retainRaw(f.lease,"observation-test",raw)}catch(_:IllegalStateException){failed=true};assertTrue(failed)
        assertEquals(prior,db.templateSync().observation("observation-test"));assertEquals(before,rows())
    }
    @Test fun leaseExpiryAfterObservationSqlWriteRollsBackAcknowledgement()=runBlocking {
        val f=fixture();val before=rows();val s=store();s.begin(f.lease,"observation-test",f.namespace,pins,at)
        val prior=db.templateSync().observation("observation-test");var failed=false
        try{store(commit={now=f.lease.until}).retainRaw(f.lease,"observation-test",PublicationRawObservation5("HEAD","head-file",404,null,null))}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(prior,db.templateSync().observation("observation-test"));assertEquals(before,rows())
        val next=checkNotNull(TemplateSyncLeases5(db).claim("job-test",now,"boot-test"))
        assertTrue(store().load(next,"observation-test").rawObservations.isEmpty())
    }
}
