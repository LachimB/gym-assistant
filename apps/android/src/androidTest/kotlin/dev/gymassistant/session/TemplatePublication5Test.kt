package dev.gymassistant.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gymassistant.engine.v2.*
import dev.gymassistant.sync.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class TemplatePublication5Test {
    private val context:Context=ApplicationProvider.getApplicationContext()
    private val name="publication-test-${UUID.randomUUID()}.db"
    private val root=File(context.cacheDir,"publication-${UUID.randomUUID()}")
    private var db=open();private var now=1000L
    private fun open()=Room.databaseBuilder(context,WorkoutDatabase::class.java,name).addMigrations(DELIVERY_MIGRATION_1_2,LIBRARY_MIGRATION_2_3,PERSONAL_DELIVERY_MIGRATION_3_4,TEMPLATE_SYNC_MIGRATION_4_5).build()
    private fun asset(n:String)=context.assets.open(n).bufferedReader().use {it.readText()}
    private val contracts=ImportContracts2(mapOf("1.0.0" to Json.parseToJsonElement(asset("template.v1.schema.json")).jsonObject,
        "1.1.0" to Json.parseToJsonElement(asset("template.v1.1.schema.json")).jsonObject),Json.parseToJsonElement(asset("exercise.v1.schema.json")).jsonObject)
    private fun syncFiles()=TemplateSyncFiles5(File(root,"sync"),contracts)
    private fun workingStore()=TemplateSyncProgressStore5(db,TemplateSyncProgressFiles5(syncFiles()),{now})
    private fun store(before:suspend ()->Unit={},commit:()->Unit={})=TemplatePublicationStore5(db,TemplatePublicationFiles5(syncFiles()),workingStore(),{now},before,commit)
    private data class Fixture(val lease:SyncLease5,val intent:PublicationHeadIntent,val revision:TemplateRevisionRow)
    private suspend fun fixture(workingComplete:Boolean=true):Fixture {
        val at="2026-11-05T12:00:00Z";val library=TemplateLibrary3(db,LibraryFiles3(File(root,"library")),contracts)
        val candidate=library.stage(listOf(ImportFile3("template.yaml",asset("timed-runtime-v2.yaml").toByteArray()),ImportFile3("exercise.yaml",asset("bridge-band-family.yaml").toByteArray())),at)
        val revision=library.publish(candidate.candidateId,checkNotNull(candidate.sourceHash),checkNotNull(candidate.compiledPlanHash),at)
        RoomDeliveryStore(db).bind(DriveConnectionRow("connection-test","googleDrive","synthetic-owner","fixture@example.test","personal-root","personal-m1-test",at,at))
        db.templateSync().insertRoot(ProjectStorageRoot5("project-test",permissionId="synthetic-owner",commonRootFolderId="project-root",marker="gym-project-test",state="active"))
        db.templateSync().insertBinding(TemplateSyncBinding5("binding-test","connection-test","project-test",revision.templateId,"template-folder","working-folder","history-folder","metadata-folder","gym-template-test","head-object",enabled=true,createdAt=at,updatedAt=at))
        val sources=sessionJson.decodeFromString<TemplateSources3>(revision.sourceSnapshotJson);val texts=listOf(sources.templateYaml)+sources.exerciseYamls
        val descriptors=texts.mapIndexed {i,t->RevisionFile(if(i==0)SourceRole.TEMPLATE else SourceRole.EXERCISE,if(i==0)"template.yaml" else "exercise-${i-1}.yaml","immutable-file-$i",hashSourceBytes(t.toByteArray()),t.toByteArray().size)}
        val manifest=TemplateRevisionManifest(revision.templateId,revision.revisionId,null,revision.sourceHash,revision.compiledPlanHash,descriptors)
        val payloads=descriptors.mapIndexed {i,f->f.objectId to texts[i].toByteArray()}.toMap()
        val steps=descriptors.mapIndexed {i,f->WorkingFileStep("working-file-$i","working-folder","gym-template-test",f.relativeName,"text/plain",null,null,payloads.getValue(f.objectId))}
        val pointer=syncFiles().prepare("job-test","operation-test","binding-test",revision.revisionId,manifest,payloads,steps)
        db.templateSync().insertJob(TemplateSyncJob5("job-test","binding-test","operation-test","push",revision.revisionId,sourceHash=revision.sourceHash,compiledPlanHash=revision.compiledPlanHash,intentPath=pointer.path,intentHash=pointer.sha256))
        val lease=checkNotNull(TemplateSyncLeases5(db).claim("job-test",now,"boot-test"))
        if(workingComplete)for(step in steps)workingStore().acknowledge(lease,WorkingReceipt5(step.objectId,"working-tag",step.intendedHash))
        val intended=TemplatePublicationHead(revision.templateId,"generation-test","manifest-object","working-folder",revision.sourceHash,null)
        return Fixture(lease,PublicationHeadIntent("operation-test","head-object","metadata-folder","gym-template-test",null,null,intended),revision)
    }
    private fun rows()=listOf("session_starts","checkpoints","actions","unfinished_slot","outbox","drive_connections","session_deliveries","template_candidates","template_revisions","template_heads","execution_plan_revisions","personal_session_deliveries").associateWith {table->
        db.openHelper.writableDatabase.query("SELECT * FROM `$table` ORDER BY rowid").use {c->buildList {while(c.moveToNext())add((0 until c.columnCount).map {i->if(c.isNull(i))null else c.getString(i)})}}}
    private fun snapshot(head:TemplatePublicationHead,tag:String="\"head-tag\""):PublicationHeadSnapshot {
        val metadata=buildJsonObject {
            put("id","head-object");put("title","head.json");put("mimeType","application/json");put("etag",tag);put("ownedByMe",true);put("shared",false)
            put("labels",buildJsonObject {put("trashed",false)});put("parents",buildJsonArray {add(buildJsonObject {put("id","metadata-folder")})})
            put("properties",buildJsonArray {add(buildJsonObject {put("key","gymTemplateM1");put("value","gym-template-test");put("visibility","PRIVATE")})})
            put("permissions",buildJsonArray {add(buildJsonObject {put("id","synthetic-owner");put("type","user");put("role","owner")})})
        }
        return PublicationHeadSnapshot("head-object",setOf("metadata-folder"),"gym-template-test","head.json","application/json",tag,false,head,metadata.toString().toByteArray(),("\n"+TemplateSyncContracts5.encode(head)+"\n").toByteArray())
    }
    @After fun cleanup(){db.close();context.deleteDatabase(name);root.deleteRecursively()}
    @Test fun publicationReopensWithRawObservationsWithoutAdoptionJobCompletionOrLegacyChanges()=runBlocking {
        val f=fixture();val before=rows();val s=store();val begun=s.begin(f.lease,"revision-folder",f.intent,null,true)
        val count=File(root,"sync").listFiles()!!.size
        assertEquals(begun,s.begin(f.lease,"revision-folder",f.intent,null,true));assertEquals(count,File(root,"sync").listFiles()!!.size)
        for((i,step) in s.load(f.lease).immutableSteps.withIndex())s.acknowledgeImmutable(f.lease,ImmutableReceipt5(step.objectId,"\"immutable-$i\"",step.sha256))
        val observation=snapshot(f.intent.intended);s.observe(f.lease,observation);s.acknowledgePublication(f.lease,observation.tag)
        assertEquals(before,rows());assertEquals("running",db.templateSync().job("job-test")!!.state);assertNull(db.templateSync().binding("binding-test")!!.lastRemoteGeneration)
        db.close();db=open();val reopened=store();val checked=reopened.load(f.lease)
        assertEquals(observation.tag,checked.publishedTag);assertArrayEquals(observation.contentBytes,checked.observations.last()!!.contentBytes)
        reopened.observe(f.lease,null);assertEquals(observation.tag,reopened.load(f.lease).publishedTag);assertEquals(before,rows())
        assertEquals(f.revision.revisionId,db.library().head(f.revision.templateId)!!.revisionId)
    }
    @Test fun injectedPointerFailureLeavesOldSqlPointerAndAllowsIdempotentRetry()=runBlocking {
        val f=fixture();val before=rows();val s=store();s.begin(f.lease,"revision-folder",f.intent,null,true)
        val prior=db.templateSync().job("job-test")!!;val step=s.load(f.lease).immutableSteps.first();val receipt=ImmutableReceipt5(step.objectId,"\"immutable-tag\"",step.sha256)
        var failed=false;try{store(commit={error("Injected pointer commit failure")}).acknowledgeImmutable(f.lease,receipt)}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(prior,db.templateSync().job("job-test"));assertTrue(s.load(f.lease).receipts.isEmpty())
        val next=s.acknowledgeImmutable(f.lease,receipt);assertEquals(next,s.acknowledgeImmutable(f.lease,receipt));assertEquals(before,rows())
    }
    @Test fun leaseExpiryBeforePointerTransactionCannotCommitPreparedProgress()=runBlocking {
        val f=fixture();val prior=db.templateSync().job("job-test")!!;val before=rows();var failed=false
        try{store(before={now=f.lease.until}).begin(f.lease,"revision-folder",f.intent,null,true)}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(prior,db.templateSync().job("job-test"));assertEquals(before,rows())
        val nextLease=checkNotNull(TemplateSyncLeases5(db).claim("job-test",now,"boot-test"));assertTrue(nextLease.generation>f.lease.generation)
        store().begin(nextLease,"revision-folder",f.intent,null,true);assertNull(store().load(nextLease).publishedTag)
    }
    @Test fun namespaceRetargetDuringFilePreparationPreventsPointerAcknowledgement()=runBlocking {
        val f=fixture();val prior=db.templateSync().job("job-test")!!;val before=rows();var failed=false
        try{store(before={db.openHelper.writableDatabase.execSQL("UPDATE template_sync_bindings SET metadataFolderId='other-metadata' WHERE bindingId='binding-test'")}).begin(f.lease,"revision-folder",f.intent,null,true)}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(prior,db.templateSync().job("job-test"));assertEquals(before,rows())
        failed=false;try{store().begin(f.lease,"revision-folder",f.intent,null,true)}catch(_:IllegalStateException){failed=true};assertTrue(failed)
    }
    @Test fun rawQuarantineReopensWithoutChangingLegacyRowsAndFailedCommitRetainsPriorPointer()=runBlocking {
        val f=fixture();val before=rows();val s=store();s.begin(f.lease,"revision-folder",f.intent,null,true)
        val raw=PublicationRawObservation5("HEAD","head-object",200,"{bad-json".toByteArray(),byteArrayOf())
        val prior=db.templateSync().job("job-test")!!;var failed=false
        try{store(commit={error("Injected raw pointer failure")}).retainRaw(f.lease,raw)}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(prior,db.templateSync().job("job-test"));assertTrue(s.load(f.lease).rawObservations.isEmpty())
        s.retainRaw(f.lease,raw);db.close();db=open();val reopened=store().load(f.lease)
        assertEquals(1,reopened.rawObservations.size);assertArrayEquals(raw.metadataBytes,reopened.rawObservations.single().metadataBytes)
        assertArrayEquals(byteArrayOf(),reopened.rawObservations.single().contentBytes);assertNull(reopened.publishedTag)
        assertEquals(before,rows());assertEquals("running",db.templateSync().job("job-test")!!.state)
    }
    @Test fun expiredLeaseCannotAcknowledgeRawObservations()=runBlocking {
        val f=fixture();val s=store();s.begin(f.lease,"revision-folder",f.intent,null,true)
        val prior=db.templateSync().job("job-test")!!;val before=rows();var failed=false
        try{store(before={now=f.lease.until}).retainRaw(f.lease,PublicationRawObservation5("HEAD","head-object",404,null,null))}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(prior,db.templateSync().job("job-test"));assertEquals(before,rows())
        val next=checkNotNull(TemplateSyncLeases5(db).claim("job-test",now,"boot-test"))
        assertTrue(store().load(next).rawObservations.isEmpty())
    }
    private inner class Provider(val captured:CheckedPublication5) {
        val objects=mutableMapOf<String,ByteArray>();var head:TemplatePublicationHead?=null;var creates=0;var headWrites=0;var closes=0
        var loseFileAck=true;var loseHeadAck=false;var matches=true;var competing=false;var onHeadRead:()->Unit={}
        val workingBytes=mutableMapOf<String,ByteArray>();var workingCreates=0;var loseWorkingAck=false
        fun open(retain:(PublicationRawObservation5)->Unit):PublicationRemoteSet5 {
            val immutable=object:ImmutableFileRemote {
                override fun verifyNamespace(){}
                override fun read(objectId:String):ImmutableFileSnapshot? {
                    val step=captured.immutableSteps.single {it.objectId==objectId};val bytes=objects[objectId]
                    retain(PublicationRawObservation5("IMMUTABLE",objectId,if(bytes==null)404 else 200,bytes?.let {"{}".toByteArray()},bytes))
                    return bytes?.let {ImmutableFileSnapshot(objectId,setOf(step.folderId),step.marker,step.name,step.mimeType,"\"immutable-tag\"",false,it)}
                }
                override fun createReserved(step:ImmutableFileStep){check(step.objectId !in objects);objects[step.objectId]=step.bytes;creates++
                    if(loseFileAck){loseFileAck=false;error("Synthetic lost file acknowledgement")}}
            }
            val remote=object:PublicationHeadRemote {
                override fun verifyNamespace(){}
                override fun read(objectId:String):PublicationHeadSnapshot? {
                    onHeadRead();val s=head?.let {snapshot(it)}
                    retain(PublicationRawObservation5("HEAD",objectId,if(s==null)404 else 200,s?.metadataBytes,s?.contentBytes));return s
                }
                override fun replace(objectId:String,expectedTag:String,head:TemplatePublicationHead)=error("Unexpected replacement")
                override fun createReserved(intent:PublicationHeadIntent){check(head==null);head=intent.intended;headWrites++
                    if(loseHeadAck){loseHeadAck=false;error("Synthetic lost head acknowledgement")}}
                override fun verifyImmutablePublication(head:TemplatePublicationHead){check(head==captured.intent.intended);for(step in captured.immutableSteps)check(objects[step.objectId]?.contentEquals(step.bytes)==true)}
                override fun workingVectorMatches():Boolean {
                    if(competing)head=captured.intent.intended.copy(generationId="later-generation",previousGenerationId=captured.intent.intended.generationId)
                    return matches
                }
            }
            val working=object:WorkingFileRemote {
                override fun verifyNamespace(){}
                override fun read(objectId:String):WorkingFileSnapshot? {
                    val step=captured.working.captured.steps.single {it.objectId==objectId};val bytes=workingBytes[objectId]
                    retain(PublicationRawObservation5("WORKING",objectId,if(bytes==null)404 else 200,bytes?.let {"{}".toByteArray()},bytes))
                    return bytes?.let {WorkingFileSnapshot(objectId,setOf(step.folderId),step.marker,step.name,step.mimeType,"\"working-tag\"",false,it)}
                }
                override fun replace(objectId:String,expectedTag:String,bytes:ByteArray)=error("Unexpected replacement")
                override fun createReserved(step:WorkingFileStep){
                    // Verify remote immutable content exists before any editable file can be changed.
                    for(pin in captured.immutableSteps)check(objects[pin.objectId]?.contentEquals(pin.bytes)==true)
                    check(step.objectId !in workingBytes);workingBytes[step.objectId]=step.intendedBytes;workingCreates++
                    if(loseWorkingAck){loseWorkingAck=false;error("Synthetic lost working acknowledgement")}
                }
            }
            return PublicationRemoteSet5(immutable,remote,if(captured.historyFirst)working else null){closes++}
        }
    }
    @Test fun executorReopensLostFileAndHeadAcknowledgementsUsingTheOriginalIdentities()=runBlocking {
        val f=fixture();val before=rows();store().begin(f.lease,"revision-folder",f.intent,null,true)
        val provider=Provider(store().load(f.lease));var failed=false
        try{TemplatePublicationExecutor5(store()).execute(f.lease){_,retain->provider.open(retain)}}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(1,provider.creates);assertEquals(1,provider.closes);assertTrue(store().load(f.lease).receipts.isEmpty())
        db.close();db=open();provider.loseHeadAck=true;failed=false
        try{TemplatePublicationExecutor5(store()).execute(f.lease){_,retain->provider.open(retain)}}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertTrue(store().load(f.lease).allImmutableAcknowledged);assertNull(store().load(f.lease).publishedTag)
        db.close();db=open()
        val result=TemplatePublicationExecutor5(store()).execute(f.lease){_,retain->provider.open(retain)}
        assertEquals(HeadPublicationResult.PUBLISHED_WORKING_COPY_VERIFIED,result.result);assertEquals(provider.captured.immutableSteps.size,provider.creates)
        assertEquals(1,provider.headWrites);assertEquals(3,provider.closes);assertTrue(store().load(f.lease).rawObservations.isNotEmpty())
        assertEquals(before,rows());assertEquals("running",db.templateSync().job("job-test")!!.state);assertNull(db.templateSync().binding("binding-test")!!.lastRemoteGeneration)
    }
    @Test fun executorRetainsPublicationWhenLaterWorkingCopyOrHeadDiffersAndNeverRecreatesMissingReceipts()=runBlocking {
        val f=fixture();val before=rows();store().begin(f.lease,"revision-folder",f.intent,null,true)
        val provider=Provider(store().load(f.lease));provider.loseFileAck=false;provider.matches=false
        // Initial publication needs a matching working vector; begin this recovery with an already committed head.
        provider.head=f.intent.intended
        val result=TemplatePublicationExecutor5(store()).execute(f.lease){_,retain->provider.open(retain)}
        assertEquals(HeadPublicationResult.PUBLISHED_WITH_PENDING_WORKING_CHANGE,result.result);assertEquals("\"head-tag\"",store().load(f.lease).publishedTag)
        provider.competing=true
        val later=TemplatePublicationExecutor5(store()).execute(f.lease){_,retain->provider.open(retain)}
        assertEquals(HeadPublicationResult.PUBLISHED_WITH_COMPETING_HEAD,later.result);assertEquals("\"head-tag\"",store().load(f.lease).publishedTag)
        val count=provider.creates;provider.objects.remove(provider.captured.immutableSteps.first().objectId);var failed=false
        try{TemplatePublicationExecutor5(store()).execute(f.lease){_,retain->provider.open(retain)}}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(count,provider.creates);assertEquals(before,rows())
    }
    @Test fun executorCannotWriteAfterRawRetentionLosesTheLease()=runBlocking {
        val f=fixture();val before=rows();store().begin(f.lease,"revision-folder",f.intent,null,true)
        val provider=Provider(store().load(f.lease));provider.loseFileAck=false;provider.onHeadRead={now=f.lease.until};var failed=false
        try{TemplatePublicationExecutor5(store()).execute(f.lease){_,retain->provider.open(retain)}}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(0,provider.headWrites);assertEquals(1,provider.closes);assertEquals(before,rows())
    }
    @Test fun nativeScopeMustMatchTheCurrentLeasedBindingBeforeAnyTokenBatchOpens()=runBlocking {
        val f=fixture();val s=store();s.begin(f.lease,"revision-folder",f.intent,null,true);val before=rows()
        val ids=setOf("project-root","personal-root","template-folder","working-folder","history-folder","metadata-folder","revision-folder")
        s.requireNativeScope(f.lease,"synthetic-owner","project-root",ids,"template-folder","working-folder","history-folder","metadata-folder","revision-folder")
        for(mode in 0..3){var failed=false;try {
            s.requireNativeScope(f.lease,if(mode==0)"another-owner" else "synthetic-owner",if(mode==1)"another-root" else "project-root",
                if(mode==2)ids-"personal-root" else ids,"template-folder","working-folder","history-folder",if(mode==3)"another-metadata" else "metadata-folder","revision-folder")
        }catch(_:IllegalStateException){failed=true};assertTrue(failed)}
        assertEquals(before,rows())
    }
    @Test fun historyFirstTransferReopensAfterALostWorkingAcknowledgementWithoutRepeatingImmutableCreates()=runBlocking {
        val f=fixture(false);val before=rows();val s=store();s.begin(f.lease,"revision-folder",f.intent,null,true,true)
        val provider=Provider(s.load(f.lease));provider.loseFileAck=false;provider.loseWorkingAck=true;var failed=false
        try{TemplatePublicationExecutor5(store()).execute(f.lease){_,retain->provider.open(retain)}}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);val pending=s.load(f.lease);assertTrue(pending.allImmutableAcknowledged);assertTrue(pending.working.receipts.isEmpty())
        assertEquals(1,provider.workingCreates);assertEquals(0,provider.headWrites);assertNull(pending.publishedTag)
        val original=pending.originPointer;db.close();db=open()
        val result=TemplatePublicationExecutor5(store()).execute(f.lease){_,retain->provider.open(retain)}
        val completed=store().load(f.lease)
        assertEquals(HeadPublicationResult.PUBLISHED_WORKING_COPY_VERIFIED,result.result)
        assertEquals(provider.captured.immutableSteps.size,provider.creates);assertEquals(provider.captured.working.captured.steps.size,provider.workingCreates)
        assertEquals(1,provider.headWrites);assertEquals(original,completed.originPointer);assertTrue(completed.working.allFilesAcknowledged)
        assertTrue(completed.rawObservations.any {it.kind=="WORKING"});assertEquals(before,rows())
        assertEquals("running",db.templateSync().job("job-test")!!.state);assertNull(db.templateSync().binding("binding-test")!!.lastRemoteGeneration)
    }
    @Test fun historyFirstWorkingReceiptRollbackCannotAdvanceTheTransferPointer()=runBlocking {
        val f=fixture(false);val before=rows();val s=store();s.begin(f.lease,"revision-folder",f.intent,null,true,true)
        val step=s.load(f.lease).working.captured.steps.first();val receipt=WorkingReceipt5(step.objectId,"\"working-tag\"",step.intendedHash)
        var failed=false;try{s.acknowledgeWorking(f.lease,receipt)}catch(_:IllegalStateException){failed=true};assertTrue(failed)
        for(pin in s.load(f.lease).immutableSteps)s.acknowledgeImmutable(f.lease,ImmutableReceipt5(pin.objectId,"\"immutable-tag\"",pin.sha256))
        val prior=db.templateSync().job("job-test");failed=false
        try{store(commit={error("Synthetic working pointer failure")}).acknowledgeWorking(f.lease,receipt)}catch(_:IllegalStateException){failed=true}
        assertTrue(failed);assertEquals(prior,db.templateSync().job("job-test"));assertTrue(s.load(f.lease).working.receipts.isEmpty())
        s.acknowledgeWorking(f.lease,receipt);assertEquals(receipt,s.load(f.lease).working.receipts[step.objectId]);assertEquals(before,rows())
    }
}
