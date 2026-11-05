package dev.gymassistant.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gymassistant.engine.v2.*
import dev.gymassistant.drive.*
import dev.gymassistant.sync.TemplatePublicationHead
import java.io.File
import java.io.Closeable
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
    private fun readFolders():List<TemplateFolderPin5> {
        fun pin(id:String,parent:String,name:String,key:String="gymTemplateM1",marker:String="gym-template-test")=TemplateFolderPin5(id,parent,name,key,marker)
        return listOf(pin("project-root","my-drive-test","Gym Assistant","gymProjectM1","gym-project-test"),
            pin("dev-folder","project-root","dev","gymEnvironmentM1","env-dev-test"),pin("personal-area","dev-folder","PERSONAL M1","gymPersonalM1","personal-m1-test"),
            pin("library-folder","personal-area","Templates","gymTemplateLibraryM1","library-test"),pin("template-folder","library-folder","selected fixture"),
            pin("working-folder","template-folder","Current working copy"),pin("history-folder","template-folder","History"),pin("metadata-folder","template-folder","Sync metadata"))
    }
    private inner class ReadHttp(val f:Fixture):TemplateHttp5,Closeable {
        var calls=0;var closed=false;var onMedia:()->Unit={}
        var replacementTemplate:String?=null
        val sources=sessionJson.decodeFromString<TemplateSources3>(f.revision.sourceSnapshotJson)
        override fun close(){closed=true}
        override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse {
            calls++;check(request.method=="GET")
            if(request.path==ACCOUNT_PATH)return DeliveryHttpResponse(200,"{\"user\":{\"permissionId\":\"synthetic-owner\"}}".toByteArray())
            val id=request.path.substringAfter("/drive/v2/files/").substringBefore('?')
            if(request.path.endsWith("?alt=media")) {
                onMedia()
                val value=if(id=="head-file")TemplateSyncContracts5.encode(TemplatePublicationHead(f.revision.templateId,"generation-test","manifest-test","working-folder",f.revision.sourceHash,null))
                    else if(id==pins.first().objectId)replacementTemplate?:sources.templateYaml else sources.exerciseYamls.single()
                return DeliveryHttpResponse(200,value.toByteArray())
            }
            val folder=readFolders().singleOrNull {it.id==id};val source=pins.singleOrNull {it.objectId==id}
            val data=buildJsonObject {
                put("id",id);put("title",folder?.name?:source?.relativeName?:"head.json")
                put("mimeType",if(folder!=null)"application/vnd.google-apps.folder" else source?.mimeType?:"application/json")
                put("etag","\"tag-$id\"");put("ownedByMe",true);put("shared",false)
                put("labels",buildJsonObject {put("trashed",false)})
                put("parents",buildJsonArray {add(buildJsonObject {put("id",folder?.parent?:if(source!=null)"working-folder" else "metadata-folder")})})
                put("properties",buildJsonArray {add(buildJsonObject {put("key",folder?.markerKey?:"gymTemplateM1");put("value",folder?.marker?:"gym-template-test");put("visibility","PRIVATE")})})
                put("permissions",buildJsonArray {add(buildJsonObject {put("id","synthetic-owner");put("type","user");put("role","owner")})})
            }
            return DeliveryHttpResponse(200,data.toString().toByteArray())
        }
    }
    @Test fun readonlyNativeBatchChecksLeaseMarkersAndBudgetThenDurablyRetainsOriginalsWithoutAdoption()=runBlocking {
        val f=fixture();val before=rows();val s=store();s.begin(f.lease,"observation-test",f.namespace,pins,at)
        val http=ReadHttp(f);var charged=0
        val result=TemplateSourceReadExecutor5(s,http).read(f.lease,"observation-test","synthetic",{charged++},readFolders()){reader->
            reader.verifyNamespace();assertEquals(f.revision.templateId,reader.readHead()!!.head.templateId);reader.readSource(pins.first().objectId)!!
        }
        assertArrayEquals(http.sources.templateYaml.toByteArray(),result.contentBytes);assertEquals(http.calls,charged);assertTrue(http.closed)
        val checked=s.load(f.lease,"observation-test");assertTrue(checked.rawObservations.map {it.kind}.containsAll(listOf("NAMESPACE","HEAD","WORKING")))
        assertNull(db.templateSync().observation("observation-test")!!.candidateId);assertNull(db.templateSync().observation("observation-test")!!.remoteGeneration)
        assertEquals(f.job,db.templateSync().job("job-test"));assertEquals(before,rows())
    }
    @Test fun wrongMarkersBudgetExhaustionOrLeaseExpiryCannotReturnAReadonlyCapture()=runBlocking {
        val f=fixture();val before=rows();val s=store();s.begin(f.lease,"observation-test",f.namespace,pins,at)
        val wrong=ReadHttp(f);var failed=false
        try{TemplateSourceReadExecutor5(s,wrong).read(f.lease,"observation-test","synthetic",{error("Budget must not be reached")},
            readFolders().map {if(it.id=="project-root")it.copy(marker="gym-project-other")else it}){it.verifyNamespace()}}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(0,wrong.calls)
        val budget=ReadHttp(f);failed=false
        try{TemplateSourceReadExecutor5(s,budget).read(f.lease,"observation-test","synthetic",{throw DeliveryFailure("budget")},readFolders()){it.verifyNamespace()}}catch(error:DeliveryFailure){assertEquals("budget",error.reason);failed=true}
        assertTrue(failed);assertEquals(0,budget.calls);assertTrue(budget.closed)
        val expired=ReadHttp(f);expired.onMedia={now=f.lease.until};failed=false
        try{TemplateSourceReadExecutor5(s,expired).read(f.lease,"observation-test","synthetic",{},readFolders()){it.verifyNamespace();it.readSource(pins.first().objectId)}}catch(_:DeliveryFailure){failed=true}
        assertTrue(failed);assertTrue(expired.closed);assertEquals(before,rows());assertEquals(f.job,db.templateSync().job("job-test"))
    }
    @Test fun completeKnownVectorRetainsBothPassesAcrossReopenWithoutClassifyingOrAdopting()=runBlocking {
        val f=fixture();val before=rows();val s=store();s.begin(f.lease,"observation-test",f.namespace,pins,at)
        val http=ReadHttp(f);var charged=0
        val vector=TemplateSourceReadExecutor5(s,http).read(f.lease,"observation-test","synthetic",{charged++},readFolders()){it.captureKnownVector(pins)}
        assertEquals(39,http.calls);assertEquals(http.calls,charged);assertTrue(http.closed)
        assertEquals(f.revision.templateId,vector.head!!.head.templateId)
        assertEquals(pins,vector.files.map {it.pin});assertArrayEquals(http.sources.templateYaml.toByteArray(),vector.files.first().contentBytes)
        val compiled=vector.compileWorkingSource(f.revision.templateId,contracts)
        assertEquals(f.revision.sourceHash,compiled.sourceHash);assertEquals(f.revision.compiledPlanHash,compiled.compiledPlanHash)
        db.close();db=open();val checked=store().load(f.lease,"observation-test")
        assertEquals(37,checked.rawObservations.size)
        assertEquals(4,checked.rawObservations.count {it.kind=="WORKING" && it.contentBytes!=null})
        val row=db.templateSync().observation("observation-test")!!
        assertEquals("changing",row.validationCode);assertEquals("pending",row.reviewState);assertNull(row.candidateId);assertNull(row.remoteHeadTag)
        assertEquals(f.job,db.templateSync().job("job-test"));assertEquals(before,rows())
    }
    @Test fun changedFileInSecondPassIsRetainedButCannotReturnACompleteKnownVector()=runBlocking {
        val f=fixture();val before=rows();val s=store();s.begin(f.lease,"observation-test",f.namespace,pins,at)
        val http=ReadHttp(f);var media=0
        http.onMedia={media++;if(media==5)http.replacementTemplate="synthetic changed source"}
        var failed=false
        try{TemplateSourceReadExecutor5(s,http).read(f.lease,"observation-test","synthetic",{},readFolders()){it.captureKnownVector(pins)}}
        catch(error:DeliveryFailure){assertEquals("conflict",error.reason);failed=true}
        assertTrue(failed);assertTrue(http.closed)
        val raw=s.load(f.lease,"observation-test").rawObservations.filter {it.kind=="WORKING" && it.objectId==pins.first().objectId && it.contentBytes!=null}
        assertTrue(raw.any {it.contentBytes!!.contentEquals(http.sources.templateYaml.toByteArray())})
        assertTrue(raw.any {it.contentBytes!!.contentEquals("synthetic changed source".toByteArray())})
        assertNull(db.templateSync().observation("observation-test")!!.candidateId)
        assertEquals(f.job,db.templateSync().job("job-test"));assertEquals(before,rows())
    }
}
