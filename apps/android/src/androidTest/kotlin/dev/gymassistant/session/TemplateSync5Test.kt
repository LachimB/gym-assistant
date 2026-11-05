package dev.gymassistant.session

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gymassistant.engine.v2.*
import dev.gymassistant.sync.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateSync5Test {
    private val context:Context=ApplicationProvider.getApplicationContext()
    private val name="sync-test-${UUID.randomUUID()}.db"
    private val root=File(context.cacheDir,"sync-${UUID.randomUUID()}")
    private var db=open()
    private fun open()=Room.databaseBuilder(context,WorkoutDatabase::class.java,name).addMigrations(DELIVERY_MIGRATION_1_2,LIBRARY_MIGRATION_2_3,PERSONAL_DELIVERY_MIGRATION_3_4,TEMPLATE_SYNC_MIGRATION_4_5).build()
    private fun asset(n:String)=context.assets.open(n).bufferedReader().use {it.readText()}
    private val contracts=ImportContracts2(mapOf("1.0.0" to Json.parseToJsonElement(asset("template.v1.schema.json")).jsonObject,"1.1.0" to Json.parseToJsonElement(asset("template.v1.1.schema.json")).jsonObject),Json.parseToJsonElement(asset("exercise.v1.schema.json")).jsonObject)
    private val at="2026-11-04T12:00:00Z"
    private val oldTables=listOf("session_starts","checkpoints","actions","unfinished_slot","outbox","drive_connections","session_deliveries","template_candidates","template_revisions","template_heads","execution_plan_revisions","personal_session_deliveries")
    @After fun cleanup(){db.close();context.deleteDatabase(name);root.deleteRecursively()}
    private suspend fun publish():TemplateRevisionRow {
        val library=TemplateLibrary3(db,LibraryFiles3(root),contracts)
        val candidate=library.stage(listOf(ImportFile3("template.yaml",asset("timed-runtime-v2.yaml").toByteArray()),ImportFile3("exercise.yaml",asset("bridge-band-family.yaml").toByteArray())),at)
        return library.publish(candidate.candidateId,checkNotNull(candidate.sourceHash),checkNotNull(candidate.compiledPlanHash),at)
    }
    private suspend fun binding():TemplateSyncBinding5 {
        val template=publish()
        RoomDeliveryStore(db).bind(DriveConnectionRow("connection-test","googleDrive","synthetic-account","owner@example.test","personal-root","personal-m1-test",at,at))
        db.templateSync().insertRoot(ProjectStorageRoot5("project-test",permissionId="synthetic-account",commonRootFolderId="project-root",marker="gym-project-test",state="active"))
        assertEquals("project-test",db.templateSync().rootForAccount("googleDrive","synthetic-account")?.rootBindingId)
        assertNull(db.templateSync().rootForAccount("googleDrive","another-account"))
        assertNull(db.templateSync().rootForAccount("another-provider","synthetic-account"))
        val binding=TemplateSyncBinding5("binding-test","connection-test","project-test",template.templateId,"template-folder","working-folder","history-folder","metadata-folder","gym-template-test","head-object",enabled=true,createdAt=at,updatedAt=at)
        db.templateSync().insertBinding(binding);return binding
    }
    private fun rows()=oldTables.associateWith {table->db.openHelper.writableDatabase.query("SELECT * FROM `$table` ORDER BY rowid").use {c->buildList {while(c.moveToNext())add((0 until c.columnCount).map {i->if(c.isNull(i))null else c.getString(i)})}}}
    private fun legacy(rows:Map<String,List<List<String?>>>):SupportSQLiteOpenHelper {
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("dev.gymassistant.session.WorkoutDatabase/4.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject("database")}
        return FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(4){
            override fun onCreate(sql:SupportSQLiteDatabase){val entities=schema.getJSONArray("entities");for(i in 0 until entities.length()){val e=entities.getJSONObject(i);val table=e.getString("tableName");sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table));val indices=e.optJSONArray("indices");if(indices!=null)for(j in 0 until indices.length())sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}
                val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())sql.execSQL(setup.getString(i))
                for((table,records) in rows)for(record in records)sql.execSQL("INSERT INTO `$table` VALUES (${record.joinToString(","){"?"}})",record.toTypedArray())}
            override fun onUpgrade(sql:SupportSQLiteDatabase,old:Int,new:Int){error("Unexpected fixture upgrade")}
        }).build())
    }
    @Test fun migrationPreservesAllTwelveTablesAndRollbackLeavesVersionFour()=runBlocking {
        val template=publish();val sessions=RoomSessionStore2(db,contracts)
        val start=SessionStart2("session-test",template.sourceSnapshotJson,template.sourceHash,template.compiledPlanJson,template.compiledPlanHash,at,"UTC","2026-11-04")
        val saved=sessions.start(start,"start-test");sessions.commit(start.sessionId,saved.revision,"end-test",SessionAction2("end",1000,at))
        val connection=RoomDeliveryStore(db).bind(DriveConnectionRow("connection-test","googleDrive","synthetic-account","owner@example.test","personal-root","personal-m1-test",at,at))
        val store=PersonalDeliveryStore4(db,DeliveryPayloadFiles4(File(root,"payloads")),contracts);val(revision,history)=store.preview(start.sessionId)
        val job=store.enqueue(connection.connectionId,start.sessionId,"delivery-test",revision,sha256Utf8(history.toCanonicalJson()))
        val legacyDelivery=RoomDeliveryStore(db)
        val sample=SyntheticDeliverySample.create(asset("composite-pyramid.yaml"),listOf(asset("bridge-band-family.yaml"),asset("plank-family.yaml")))
        legacyDelivery.prepareSynthetic(sample)
        val legacyJob=legacyDelivery.enqueue(connection.connectionId,sample.saved.start.sessionId,"legacy-delivery-test")
        val pausedStart=start.copy(sessionId="paused-session-test")
        val paused=sessions.start(pausedStart,"paused-start-test")
        sessions.commit(pausedStart.sessionId,paused.revision,"pause-test",SessionAction2("pause",100,at))
        val before=rows();assertTrue(before.values.all {it.isNotEmpty()});db.close();context.deleteDatabase(name);legacy(before).use {it.writableDatabase}
        var entered=false
        db=Room.databaseBuilder(context,WorkoutDatabase::class.java,name).addMigrations(object:Migration(4,5){override fun migrate(sql:SupportSQLiteDatabase){entered=true;TEMPLATE_SYNC_MIGRATION_4_5.migrate(sql);error("Injected migration failure")}}).build()
        var failed=false;try{db.openHelper.writableDatabase}catch(_:Exception){failed=true};assertTrue(entered && failed);db.close()
        legacy(before).use {helper->assertEquals(4,helper.writableDatabase.version);assertEquals(0,helper.writableDatabase.query("SELECT count(*) FROM sqlite_master WHERE name='project_storage_roots'").use {it.moveToFirst();it.getInt(0)})}
        db=open();assertEquals(5,db.openHelper.writableDatabase.version);assertEquals(before,rows())
        assertEquals(history.toCanonicalJson(),PersonalDeliveryStore4(db,DeliveryPayloadFiles4(File(root,"payloads")),contracts).load(job.deliveryId).second.canonicalJson)
        assertEquals(legacyJob,RoomDeliveryStore(db).load(legacyJob.deliveryId).second)
        assertEquals(pausedStart.sessionId,sessionsForReopenedDatabase().unfinished()?.start?.sessionId)
        for(table in listOf("project_storage_roots","template_sync_bindings","template_sync_jobs","template_sync_observations"))assertEquals(0,db.openHelper.writableDatabase.query("SELECT count(*) FROM `$table`").use {it.moveToFirst();it.getInt(0)})
    }
    private fun sessionsForReopenedDatabase()=RoomSessionStore2(db,contracts)
    @Test fun durableLeaseSerializesWorkersAndRejectsStaleAcknowledgements()=runBlocking {
        val binding=binding();val template=checkNotNull(db.library().head(binding.templateId))
        fun job(id:String)=TemplateSyncJob5(id,binding.bindingId,"operation-$id","push",template.revisionId,sourceHash="a".repeat(64),compiledPlanHash="b".repeat(64),intentPath="$id.json",intentHash="c".repeat(64))
        db.templateSync().insertJob(job("job-one"));db.templateSync().insertJob(job("job-two"));val store=TemplateSyncLeases5(db)
        val first=checkNotNull(store.claim("job-one",1000,"boot-one"));assertNull(store.claim("job-two",1001,"boot-one"))
        val next=checkNotNull(store.claim("job-one",first.until,"boot-one"));assertTrue(next.generation>first.generation)
        var rejected=false;try{store.release(first,first.until,SyncJobState5.COMPLETE)}catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
        store.release(next,first.until+1,SyncJobState5.RETRY,nextAttemptAt=first.until+100)
        assertNull(store.claim("job-one",first.until+2,"boot-one"))
        val other=checkNotNull(store.claim("job-two",first.until+2,"boot-one"))
        val rebooted=checkNotNull(store.claim("job-one",first.until+101,"boot-two"))
        assertEquals("boot-two",store.owned(rebooted,first.until+102).leaseBootId)
        assertNotEquals(other.owner,rebooted.owner)
    }
    @Test fun inactiveRootOrAccountMismatchCannotClaimWork()=runBlocking {
        val binding=binding();val template=checkNotNull(db.library().head(binding.templateId))
        db.templateSync().insertJob(TemplateSyncJob5("job-test",binding.bindingId,"operation-test","push",template.revisionId,
            sourceHash="a".repeat(64),compiledPlanHash="b".repeat(64),intentPath="job-test.json",intentHash="c".repeat(64)))
        val store=TemplateSyncLeases5(db)
        db.openHelper.writableDatabase.execSQL("UPDATE project_storage_roots SET state='reserved'")
        assertNull(store.claim("job-test",1000,"boot-test"))
        db.openHelper.writableDatabase.execSQL("UPDATE project_storage_roots SET state='active',permissionId='another-synthetic-account'")
        assertNull(store.claim("job-test",1000,"boot-test"))
        db.openHelper.writableDatabase.execSQL("UPDATE project_storage_roots SET permissionId='synthetic-account'")
        assertNotNull(store.claim("job-test",1000,"boot-test"))
    }
    private suspend fun progressFixture():Pair<TemplateSyncJob5,TemplateSyncFiles5> {
        val binding=binding();val head=checkNotNull(db.library().head(binding.templateId));val revision=checkNotNull(db.library().revision(binding.templateId,head.revisionId))
        val raw=listOf(asset("timed-runtime-v2.yaml").toByteArray(),asset("bridge-band-family.yaml").toByteArray())
        val descriptors=raw.mapIndexed {i,bytes->RevisionFile(if(i==0)SourceRole.TEMPLATE else SourceRole.EXERCISE,if(i==0)"template.yaml" else "exercise.yaml","immutable-source-$i",hashSourceBytes(bytes),bytes.size)}
        val manifest=TemplateRevisionManifest(binding.templateId,revision.revisionId,null,revision.sourceHash,revision.compiledPlanHash,descriptors)
        val steps=descriptors.mapIndexed {i,file->WorkingFileStep("working-source-$i",binding.workingFolderId,binding.marker,file.relativeName,"text/plain","prior-tag","source: earlier synthetic bytes\n".toByteArray(),raw[i])}
        val files=TemplateSyncFiles5(File(root,"template-sync"),contracts)
        val pointer=files.prepare("job-progress","operation-progress",binding.bindingId,revision.revisionId,manifest,descriptors.mapIndexed {i,file->file.objectId to raw[i]}.toMap(),steps)
        val job=TemplateSyncJob5("job-progress",binding.bindingId,"operation-progress","push",revision.revisionId,
            sourceHash=revision.sourceHash,compiledPlanHash=revision.compiledPlanHash,intentPath=pointer.path,intentHash=pointer.sha256)
        db.templateSync().insertJob(job);return job to files
    }
    @Test fun progressSurvivesReopenAndLeavesAllExecutionTablesUnchanged()=runBlocking {
        val(job,files)=progressFixture();val prior=rows();val leases=TemplateSyncLeases5(db)
        val lease=checkNotNull(leases.claim(job.jobId,1000,"boot-progress"))
        val store=TemplateSyncProgressStore5(db,TemplateSyncProgressFiles5(files),{1001})
        val source=store.load(lease);val first=source.captured.steps[0]
        val pointer=store.acknowledge(lease,WorkingReceipt5(first.objectId,"verified-one",first.intendedHash))
        assertNotEquals(job.intentPath,pointer.path);assertEquals(prior,rows());assertEquals("running",db.templateSync().job(job.jobId)?.state)
        leases.release(lease,1002,SyncJobState5.RETRY);db.close();db=open()
        val resumed=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,1003,"boot-progress"))
        val restarted=TemplateSyncProgressStore5(db,TemplateSyncProgressFiles5(TemplateSyncFiles5(File(root,"template-sync"),contracts)),{1004})
        val checked=restarted.load(resumed);assertEquals(1,checked.receipts.size);assertFalse(checked.allFilesAcknowledged)
        val second=checked.captured.steps[1];restarted.acknowledge(resumed,WorkingReceipt5(second.objectId,"verified-two",second.intendedHash))
        assertTrue(restarted.load(resumed).allFilesAcknowledged);assertEquals("running",db.templateSync().job(job.jobId)?.state);assertEquals(prior,rows())
        assertEquals(job.sourceHash,files.load(SyncIntentFile5(job.intentPath,job.intentHash)).manifest.sourceHash)
    }
    @Test fun failedProgressCommitRollsBackPointerAndCanRetryTheSameVerifiedResult()=runBlocking {
        val(job,files)=progressFixture();val prior=rows();val lease=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,1000,"boot-progress"))
        val originalRow=checkNotNull(db.templateSync().job(job.jobId));var entered=false
        val failing=TemplateSyncProgressStore5(db,TemplateSyncProgressFiles5(files),{1001},{entered=true;error("Injected pointer commit failure")})
        val step=files.load(SyncIntentFile5(job.intentPath,job.intentHash)).steps[0];val receipt=WorkingReceipt5(step.objectId,"verified-one",step.intendedHash)
        var rejected=false;try{failing.acknowledge(lease,receipt)}catch(_:IllegalStateException){rejected=true}
        assertTrue(entered && rejected);assertEquals(originalRow,db.templateSync().job(job.jobId));assertEquals(prior,rows())
        val retry=TemplateSyncProgressStore5(db,TemplateSyncProgressFiles5(files),{1002});assertTrue(retry.load(lease).receipts.isEmpty())
        val accepted=retry.acknowledge(lease,receipt);assertEquals(accepted,retry.acknowledge(lease,receipt));assertEquals(1,retry.load(lease).receipts.size)
        assertEquals(prior,rows())
    }
    @Test fun expiryDuringVerificationStaleOwnersAndDisabledBindingsCannotAcknowledge()=runBlocking {
        val(job,files)=progressFixture();val leases=TemplateSyncLeases5(db);val lease=checkNotNull(leases.claim(job.jobId,1000,"boot-progress"))
        val step=files.load(SyncIntentFile5(job.intentPath,job.intentHash)).steps[0];val receipt=WorkingReceipt5(step.objectId,"verified-one",step.intendedHash)
        var reads=0;val expiring=TemplateSyncProgressStore5(db,TemplateSyncProgressFiles5(files),{if(++reads==1)1001 else lease.until})
        var rejected=false;try{expiring.acknowledge(lease,receipt)}catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
        assertEquals(job.intentPath,db.templateSync().job(job.jobId)?.intentPath)
        val current=checkNotNull(leases.claim(job.jobId,lease.until,"boot-progress"))
        val retry=TemplateSyncProgressStore5(db,TemplateSyncProgressFiles5(files),{lease.until+1})
        rejected=false;try{retry.acknowledge(lease,receipt)}catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
        db.openHelper.writableDatabase.execSQL("UPDATE template_sync_bindings SET enabled=0")
        rejected=false;try{retry.acknowledge(current,receipt)}catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
        assertEquals(job.intentPath,db.templateSync().job(job.jobId)?.intentPath)
    }
    @Test fun capturedWorkingFolderCannotBeRetargetedUnderAnExistingLease()=runBlocking {
        val(job,files)=progressFixture();val lease=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,1000,"boot-progress"))
        db.openHelper.writableDatabase.execSQL("UPDATE template_sync_bindings SET workingFolderId='another-working-folder'")
        var rejected=false;try{TemplateSyncProgressStore5(db,TemplateSyncProgressFiles5(files),{1001}).load(lease)}catch(_:IllegalStateException){rejected=true}
        assertTrue(rejected);assertEquals(job.intentPath,db.templateSync().job(job.jobId)?.intentPath);assertEquals(5,db.openHelper.writableDatabase.version)
    }
    private fun queue(hook:()->Unit={})=TemplateSyncQueue5(db,TemplateSyncQueueFiles5(TemplateSyncFiles5(File(root,"template-sync"),contracts),contracts),hook)
    private suspend fun selected(binding:TemplateSyncBinding5)=checkNotNull(db.library().head(binding.templateId)).let {checkNotNull(db.library().revision(it.templateId,it.revisionId))}
    @Test fun offlineQueueReopensExactSourceAndConcurrentDuplicateEnqueueKeepsOneLogicalJob()=runBlocking {
        val binding=binding();val revision=selected(binding);val before=rows()
        val jobs=coroutineScope {(1..2).map {n->async {queue().enqueue(binding.bindingId,revision.revisionId,revision.sourceHash,revision.compiledPlanHash,"offline-operation","offline-job-$n")}}.awaitAll()}
        assertEquals(jobs[0],jobs[1]);val job=jobs[0]
        assertEquals(1,db.openHelper.writableDatabase.query("SELECT count(*) FROM template_sync_jobs").use {it.moveToFirst();it.getInt(0)})
        assertEquals("{}",job.reservedObjectIdsJson);assertEquals("queued",job.state);assertEquals(before,rows())
        db.close();db=open();val lease=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,1000,"boot-offline"))
        val checked=queue().load(lease,{1001})
        assertEquals(revision.sourceSnapshotJson,sessionJson.encodeToString(checked.sources));assertNull(checked.expectedRemoteGeneration)
        assertNull(checked.expectedRemoteHeadTag);assertEquals(before,rows())
    }
    @Test fun failedOfflineEnqueueRollsBackAndRetriesWithoutChangingPublishedOrExecutionRecords()=runBlocking {
        val binding=binding();val revision=selected(binding);val before=rows();var entered=false
        var rejected=false;try {queue {entered=true;error("Injected queue commit failure")}.enqueue(binding.bindingId,revision.revisionId,
            revision.sourceHash,revision.compiledPlanHash,"offline-operation","offline-job")}catch(_:IllegalStateException){rejected=true}
        assertTrue(entered && rejected);assertNull(db.templateSync().operation("offline-operation"));assertEquals(before,rows())
        val job=queue().enqueue(binding.bindingId,revision.revisionId,revision.sourceHash,revision.compiledPlanHash,"offline-operation","offline-job")
        assertEquals(job,db.templateSync().operation("offline-operation"));assertEquals(before,rows())
    }
    @Test fun disabledBindingStaleSelectionAndReusedOperationCannotEnqueueAnotherSource()=runBlocking {
        val binding=binding();val revision=selected(binding);val before=rows()
        db.openHelper.writableDatabase.execSQL("UPDATE template_sync_bindings SET enabled=0")
        var rejected=false;try{queue().enqueue(binding.bindingId,revision.revisionId,revision.sourceHash,revision.compiledPlanHash,"offline-operation")}
        catch(_:IllegalStateException){rejected=true};assertTrue(rejected);assertNull(db.templateSync().operation("offline-operation"))
        db.openHelper.writableDatabase.execSQL("UPDATE template_sync_bindings SET enabled=1")
        rejected=false;try{queue().enqueue(binding.bindingId,revision.revisionId,"a".repeat(64),revision.compiledPlanHash,"offline-operation")}
        catch(_:IllegalStateException){rejected=true};assertTrue(rejected);assertEquals(before,rows())
        val first=queue().enqueue(binding.bindingId,revision.revisionId,revision.sourceHash,revision.compiledPlanHash,"offline-operation")
        val library=TemplateLibrary3(db,LibraryFiles3(root),contracts)
        val changed=asset("timed-runtime-v2.yaml").replace(revision.revisionId,"next-offline-revision")
        val candidate=library.stage(listOf(ImportFile3("template.yaml",changed.toByteArray()),ImportFile3("exercise.yaml",asset("bridge-band-family.yaml").toByteArray())),at)
        val next=library.publish(candidate.candidateId,checkNotNull(candidate.sourceHash),checkNotNull(candidate.compiledPlanHash),at)
        val after=rows()
        rejected=false;try{queue().enqueue(binding.bindingId,revision.revisionId,revision.sourceHash,revision.compiledPlanHash,"stale-operation")}
        catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
        rejected=false;try{queue().enqueue(binding.bindingId,next.revisionId,next.sourceHash,next.compiledPlanHash,"offline-operation")}
        catch(_:IllegalStateException){rejected=true};assertTrue(rejected);assertEquals(first,db.templateSync().operation("offline-operation"));assertEquals(after,rows())
    }
    @Test fun offlineIntentCannotBeClaimedUnderARetargetedWorkingFolder()=runBlocking {
        val binding=binding();val revision=selected(binding);val before=rows()
        val job=queue().enqueue(binding.bindingId,revision.revisionId,revision.sourceHash,revision.compiledPlanHash,"offline-operation")
        val lease=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,1000,"boot-offline"))
        db.openHelper.writableDatabase.execSQL("UPDATE template_sync_bindings SET workingFolderId='retargeted-working-folder'")
        var rejected=false;try{queue().load(lease,{1001})}catch(_:IllegalStateException){rejected=true}
        assertTrue(rejected);assertEquals(job.intentPath,db.templateSync().job(job.jobId)?.intentPath);assertEquals(before,rows())
    }
    private class MemoryWorkingRemote(steps:List<WorkingFileStep>):WorkingFileRemote {
        val objects=steps.associate {step->step.objectId to WorkingFileSnapshot(step.objectId,setOf(step.folderId),step.marker,step.name,step.mimeType,
            checkNotNull(step.expectedTag),false,checkNotNull(step.beforeBytes))}.toMutableMap()
        var writes=0;var verifications=0;var afterWrite:(String)->Unit={};var afterRead:(String)->Unit={}
        override fun verifyNamespace(){verifications++}
        override fun read(objectId:String)=objects[objectId].also {afterRead(objectId)}
        override fun replace(objectId:String,expectedTag:String,bytes:ByteArray) {
            val prior=checkNotNull(objects[objectId]);check(prior.tag==expectedTag)
            writes++;objects[objectId]=WorkingFileSnapshot(prior.objectId,prior.parents,prior.marker,prior.name,prior.mimeType,"result-$writes",false,bytes)
            afterWrite(objectId)
        }
        override fun createReserved(step:WorkingFileStep){error("Unexpected create in existing-file fixture")}
        fun edit(id:String) {val prior=checkNotNull(objects[id]);objects[id]=WorkingFileSnapshot(prior.objectId,prior.parents,prior.marker,prior.name,prior.mimeType,"external-tag",false,"source: external synthetic edit\n".toByteArray())}
    }
    private fun executor(files:TemplateSyncFiles5,clock:()->Long)=TemplateWorkingExecutor5(TemplateSyncProgressStore5(db,TemplateSyncProgressFiles5(files),clock))
    @Test fun interruptedWorkingWriteReopensAndAcknowledgesSameFileWithoutAnotherWrite()=runBlocking {
        val(job,files)=progressFixture();val before=rows();val steps=files.load(SyncIntentFile5(job.intentPath,job.intentHash)).steps
        val remote=MemoryWorkingRemote(steps);var interrupted=false
        remote.afterWrite={if(!interrupted){interrupted=true;throw java.io.IOException("Synthetic lost acknowledgement")}}
        val first=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,1000,"boot-execute"))
        var failed=false;try{executor(files,{1001}).execute(first,remote)}catch(_:java.io.IOException){failed=true};assertTrue(failed)
        assertEquals(1,remote.writes);assertEquals(job.intentPath,db.templateSync().job(job.jobId)?.intentPath)
        TemplateSyncLeases5(db).release(first,1002,SyncJobState5.RETRY);db.close();db=open()
        val resumed=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,1003,"boot-execute"))
        val verified=executor(TemplateSyncFiles5(File(root,"template-sync"),contracts),{1004}).execute(resumed,remote)
        assertEquals(job.operationId,verified.operationId);assertEquals(2,verified.receipts.size);assertEquals(2,remote.writes)
        assertEquals("running",db.templateSync().job(job.jobId)?.state);assertEquals(before,rows())
    }
    @Test fun acknowledgedExternalEditIsNeverOverwrittenOrForgottenOnRetry()=runBlocking {
        val(job,files)=progressFixture();val before=rows();val steps=files.load(SyncIntentFile5(job.intentPath,job.intentHash)).steps
        val remote=MemoryWorkingRemote(steps);val lease=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,1000,"boot-execute"))
        val checked=executor(files,{1001}).execute(lease,remote);remote.edit(steps[0].objectId)
        val altered=remote.objects.getValue(steps[0].objectId).bytes
        var failed=false;try{executor(files,{1002}).execute(lease,remote)}catch(_:IllegalStateException){failed=true};assertTrue(failed)
        assertEquals(2,remote.writes);assertArrayEquals(altered,remote.objects.getValue(steps[0].objectId).bytes)
        val retained=TemplateSyncProgressStore5(db,TemplateSyncProgressFiles5(files),{1003}).load(lease)
        assertEquals(checked.receipts.associateBy {it.objectId},retained.receipts);assertEquals(before,rows())
    }
    @Test fun finalVectorRecheckDetectsAnEarlierEditDuringALaterFileWrite()=runBlocking {
        val(job,files)=progressFixture();val steps=files.load(SyncIntentFile5(job.intentPath,job.intentHash)).steps;val remote=MemoryWorkingRemote(steps)
        remote.afterWrite={id->if(id==steps[1].objectId)remote.edit(steps[0].objectId)}
        val lease=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,1000,"boot-execute"))
        var failed=false;try{executor(files,{1001}).execute(lease,remote)}catch(_:IllegalStateException){failed=true};assertTrue(failed)
        assertEquals(2,remote.writes);assertEquals("external-tag",remote.objects.getValue(steps[0].objectId).tag)
        assertTrue(TemplateSyncProgressStore5(db,TemplateSyncProgressFiles5(files),{1002}).load(lease).allFilesAcknowledged)
        assertEquals("running",db.templateSync().job(job.jobId)?.state)
    }
    @Test fun leaseExpiryDuringAWriteCannotAcknowledgeButReclaimRecoversTheSameObject()=runBlocking {
        val(job,files)=progressFixture();val before=rows();val steps=files.load(SyncIntentFile5(job.intentPath,job.intentHash)).steps;val remote=MemoryWorkingRemote(steps)
        val first=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,1000,"boot-execute"));var now=1001L
        remote.afterWrite={now=first.until}
        var failed=false;try{executor(files,{now}).execute(first,remote)}catch(_:IllegalStateException){failed=true};assertTrue(failed)
        assertEquals(job.intentPath,db.templateSync().job(job.jobId)?.intentPath);assertEquals(1,remote.writes)
        val current=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,now,"boot-execute"));now++;remote.afterWrite={}
        val priorVerifications=remote.verifications
        failed=false;try{executor(files,{now}).execute(first,remote)}catch(_:IllegalStateException){failed=true};assertTrue(failed)
        assertEquals(priorVerifications,remote.verifications)
        assertEquals(2,executor(files,{now}).execute(current,remote).receipts.size);assertEquals(2,remote.writes);assertEquals(before,rows())
    }
    @Test fun cancellationAfterReadStopsBeforeMutationAndDoesNotAcknowledgeProgress()=runBlocking {
        val(job,files)=progressFixture();val before=rows();val steps=files.load(SyncIntentFile5(job.intentPath,job.intentHash)).steps
        val remote=MemoryWorkingRemote(steps);val lease=checkNotNull(TemplateSyncLeases5(db).claim(job.jobId,1000,"boot-execute"))
        lateinit var work:Deferred<VerifiedWorkingVector5>
        work=async(start=CoroutineStart.LAZY){executor(files,{1001}).execute(lease,remote)}
        remote.afterRead={work.cancel()};work.start()
        var cancelled=false;try{work.await()}catch(_:CancellationException){cancelled=true};assertTrue(cancelled)
        assertEquals(0,remote.writes);assertEquals(job.intentPath,db.templateSync().job(job.jobId)?.intentPath);assertEquals(before,rows())
    }
}
