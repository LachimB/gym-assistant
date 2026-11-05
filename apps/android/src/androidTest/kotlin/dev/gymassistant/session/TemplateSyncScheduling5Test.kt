package dev.gymassistant.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.testing.*
import dev.gymassistant.engine.v2.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TemplateSyncScheduling5Test {
    private val context:Context=ApplicationProvider.getApplicationContext()
    private val root=File(context.cacheDir,"scheduler-${UUID.randomUUID()}")
    private val db=Room.inMemoryDatabaseBuilder(context,WorkoutDatabase::class.java).build()
    private val calls=CopyOnWriteArrayList<String>()
    private var outcome=TemplateWakeResult5.IDLE
    private val factory=object:WorkerFactory() {
        override fun createWorker(context:Context,name:String,parameters:WorkerParameters):ListenableWorker?=
            if(name==TemplateSyncWorker5::class.java.name)TemplateSyncWorker5(context,parameters,TemplateWakeHandler5 {id->calls+=id;outcome})else null
    }
    private lateinit var manager:WorkManager
    @Before fun setup() {
        val config=Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context,config);manager=WorkManager.getInstance(context)
    }
    @After fun cleanup()=runBlocking {
        manager.cancelAllWork().await();WorkManagerTestInitHelper.closeWorkDatabase();db.close();root.deleteRecursively();Unit
    }
    private fun asset(name:String)=context.assets.open(name).bufferedReader().use {it.readText()}
    private suspend fun binding():TemplateSyncBinding5 {
        val contracts=ImportContracts2(mapOf("1.0.0" to Json.parseToJsonElement(asset("template.v1.schema.json")).jsonObject,
            "1.1.0" to Json.parseToJsonElement(asset("template.v1.1.schema.json")).jsonObject),Json.parseToJsonElement(asset("exercise.v1.schema.json")).jsonObject)
        val library=TemplateLibrary3(db,LibraryFiles3(root),contracts)
        val candidate=library.stage(listOf(ImportFile3("template.yaml",asset("timed-runtime-v2.yaml").toByteArray()),ImportFile3("exercise.yaml",asset("bridge-band-family.yaml").toByteArray())))
        val row=library.publish(candidate.candidateId,checkNotNull(candidate.sourceHash),checkNotNull(candidate.compiledPlanHash))
        val at="2026-11-05T12:00:00Z"
        RoomDeliveryStore(db).bind(DriveConnectionRow("connection-test","googleDrive","synthetic-account","owner@example.test","personal-root","personal-m1-test",at,at))
        db.templateSync().insertRoot(ProjectStorageRoot5("root-test",permissionId="synthetic-account",commonRootFolderId="project-root",marker="gym-project-test",state="active"))
        val binding=TemplateSyncBinding5("binding-test","connection-test","root-test",row.templateId,"template-folder","working-folder","history-folder","metadata-folder",
            "gym-template-test","head-file",enabled=true,createdAt=at,updatedAt=at)
        db.templateSync().insertBinding(binding);return binding
    }
    private suspend fun info(name:String)=manager.getWorkInfosForUniqueWorkFlow(name).first().single()
    private suspend fun waitFor(id:UUID,predicate:(WorkInfo)->Boolean):WorkInfo=withTimeout(10000) {
        manager.getWorkInfoByIdFlow(id).first {it!=null && predicate(it)}!!
    }
    @Test fun reconcileIsUniqueConnectedAndRetainsThePeriodicScheduleAcrossRepeatedStartup()=runBlocking {
        val binding=binding();val scheduler=TemplateSyncScheduling5(db,manager)
        scheduler.reconcile();val once=info(TemplateSyncScheduling5.onceName(binding.bindingId));val periodic=info(TemplateSyncScheduling5.periodicName(binding.bindingId))
        scheduler.reconcile();assertEquals(once.id,info(TemplateSyncScheduling5.onceName(binding.bindingId)).id)
        assertEquals(periodic.id,info(TemplateSyncScheduling5.periodicName(binding.bindingId)).id)
        assertEquals(NetworkType.CONNECTED,once.constraints.requiredNetworkType);assertEquals(NetworkType.CONNECTED,periodic.constraints.requiredNetworkType)
        assertEquals(900000L,periodic.initialDelayMillis);assertEquals(900000L,periodic.periodicityInfo!!.repeatIntervalMillis)
        assertTrue(calls.isEmpty())
        val request=TemplateSyncScheduling5.once(binding.bindingId)
        assertEquals(setOf(TemplateSyncWorker5.BINDING),request.workSpec.input.keyValueMap.keys)
        assertEquals(binding.bindingId,request.workSpec.input.getString(TemplateSyncWorker5.BINDING))
        assertEquals(BackoffPolicy.EXPONENTIAL,request.workSpec.backoffPolicy);assertEquals(30000L,request.workSpec.backoffDelayDuration)
        checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setAllConstraintsMet(once.id)
        assertEquals(WorkInfo.State.SUCCEEDED,waitFor(once.id){it.state.isFinished}.state);assertEquals(listOf(binding.bindingId),calls.toList())
        assertEquals(WorkInfo.State.ENQUEUED,info(TemplateSyncScheduling5.periodicName(binding.bindingId)).state)
    }
    @Test fun optOutOrUnverifiedScopeCancelsBothKindsWithoutWakingOrClaimingAJob()=runBlocking {
        val binding=binding();val scheduler=TemplateSyncScheduling5(db,manager);scheduler.reconcile()
        val once=info(TemplateSyncScheduling5.onceName(binding.bindingId));val periodic=info(TemplateSyncScheduling5.periodicName(binding.bindingId))
        db.openHelper.writableDatabase.execSQL("UPDATE template_sync_bindings SET enabled=0");scheduler.reconcile()
        assertEquals(WorkInfo.State.CANCELLED,waitFor(once.id){it.state.isFinished}.state)
        assertEquals(WorkInfo.State.CANCELLED,waitFor(periodic.id){it.state.isFinished}.state)
        var rejected=false;try{scheduler.wake(binding.bindingId)}catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
        db.openHelper.writableDatabase.execSQL("UPDATE template_sync_bindings SET enabled=1")
        db.openHelper.writableDatabase.execSQL("UPDATE project_storage_roots SET permissionId='another-synthetic-account'")
        scheduler.reconcile();assertTrue(calls.isEmpty())
        assertTrue(manager.getWorkInfosByTagFlow(TemplateSyncScheduling5.TAG).first().all {it.state.isFinished})
        assertEquals(0,db.openHelper.writableDatabase.query("SELECT count(*) FROM template_sync_jobs").use {it.moveToFirst();it.getInt(0)})
    }
    @Test fun retryDoesNotBecomeSuccessAndAnOwnerGateHasNoAutomaticRetry()=runBlocking {
        val binding=binding();val scheduler=TemplateSyncScheduling5(db,manager);outcome=TemplateWakeResult5.RETRY;scheduler.reconcile()
        val once=info(TemplateSyncScheduling5.onceName(binding.bindingId));checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setAllConstraintsMet(once.id)
        val retried=waitFor(once.id){it.runAttemptCount>=1 && it.state==WorkInfo.State.ENQUEUED}
        assertFalse(retried.state.isFinished);assertEquals(1,calls.size)
        val worker=TestListenableWorkerBuilder.from(context,TemplateSyncWorker5::class.java).setInputData(workDataOf(TemplateSyncWorker5.BINDING to binding.bindingId)).setWorkerFactory(factory).build()
        outcome=TemplateWakeResult5.WAITING_FOR_OWNER
        assertEquals(ListenableWorker.Result.success(workDataOf("outcome" to "waitingForOwner")),worker.doWork())
    }
    @Test fun malformedInputsOrMissingRuntimeCannotReachTheHandler()=runBlocking {
        val invalid=listOf(workDataOf(TemplateSyncWorker5.BINDING to "../invalid"),workDataOf(TemplateSyncWorker5.BINDING to "binding-test","unexpected" to "synthetic-value"),Data.EMPTY)
        for(input in invalid) {
            val worker=TestListenableWorkerBuilder.from(context,TemplateSyncWorker5::class.java).setInputData(input).setWorkerFactory(factory).build()
            assertEquals(ListenableWorker.Result.failure(workDataOf("reason" to "contract")),worker.doWork())
        }
        val missing=TestListenableWorkerBuilder.from(context,TemplateSyncWorker5::class.java).setInputData(workDataOf(TemplateSyncWorker5.BINDING to "binding-test")).build()
        assertEquals(ListenableWorker.Result.failure(workDataOf("reason" to "notConfigured")),missing.doWork());assertTrue(calls.isEmpty())
    }
    @Test fun packagedManifestKeepsOrdinarySchedulingWithoutForegroundOrDiagnosticComponents() {
        @Suppress("DEPRECATION")
        val info=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS or
            android.content.pm.PackageManager.GET_SERVICES or android.content.pm.PackageManager.GET_RECEIVERS)
        val permissions=info.requestedPermissions.orEmpty().toSet()
        assertTrue(permissions.containsAll(setOf("android.permission.WAKE_LOCK","android.permission.ACCESS_NETWORK_STATE","android.permission.RECEIVE_BOOT_COMPLETED")))
        assertFalse("android.permission.FOREGROUND_SERVICE" in permissions)
        assertFalse(info.services.orEmpty().any {it.name=="androidx.work.impl.foreground.SystemForegroundService"})
        assertFalse(info.receivers.orEmpty().any {it.name=="androidx.work.impl.diagnostics.DiagnosticsReceiver"})
        val service=info.services.orEmpty().single {it.name=="androidx.work.impl.background.systemjob.SystemJobService"}
        assertEquals("android.permission.BIND_JOB_SERVICE",service.permission)
    }
}
