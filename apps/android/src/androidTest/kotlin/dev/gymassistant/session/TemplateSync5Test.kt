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
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
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
        val binding=TemplateSyncBinding5("binding-test","connection-test","project-test",template.templateId,"template-folder","working-folder","history-folder","metadata-folder","gym-template-test","head-object",enabled=true,createdAt=at,updatedAt=at)
        db.templateSync().insertBinding(binding);return binding
    }
    private fun rows()=oldTables.associateWith {table->db.openHelper.writableDatabase.query("SELECT * FROM `$table` ORDER BY rowid").use {c->buildList {while(c.moveToNext())add((0 until c.columnCount).map {i->if(c.isNull(i))null else c.getString(i)})}}}
    private fun legacy(rows:Map<String,List<List<String?>>>):SupportSQLiteOpenHelper {
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("dev.gymassistant.session.WorkoutDatabase/4.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject("database")}
        return FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(4){
            override fun onCreate(sql:SupportSQLiteDatabase){val entities=schema.getJSONArray("entities");for(i in 0 until entities.length()){val e=entities.getJSONObject(i);val table=e.getString("tableName");sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table));val indices=e.getJSONArray("indices");for(j in 0 until indices.length())sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}
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
}
