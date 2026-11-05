package dev.gymassistant.session

import dev.gymassistant.engine.v2.*
import dev.gymassistant.sync.*
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Test
import kotlin.test.*

class TemplateSyncFiles5Test {
    private val repo=File(checkNotNull(System.getProperty("gym.root")))
    private val root=Files.createTempDirectory("synthetic-sync-test-").toFile()
    private fun schema(n:String)=Json.parseToJsonElement(repo.resolve("schemas/$n").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val store=TemplateSyncFiles5(root,contracts)
    private val manifest=TemplateSyncContracts5.manifest(repo.resolve("examples/template-sync/synthetic-revision.json").readText())
    private val fixture=Json.parseToJsonElement(repo.resolve("examples/sessions/synthetic-runtime-v2-session.json").readText()).jsonObject
    private val sources=sessionJson.decodeFromString<TemplateSources3>(fixture.getValue("sourcePlanSnapshot").jsonObject.getValue("sourceSnapshotJson").jsonPrimitive.content)
    private val payloads=manifest.files.associate { f->f.objectId to (if(f.role==SourceRole.TEMPLATE)sources.templateYaml else sources.exerciseYamls.single()).toByteArray() }
    private val steps=manifest.files.mapIndexed {i,f->WorkingFileStep("working-file-$i","working-folder","gym-template-test",f.relativeName,"text/plain",null,null,payloads.getValue(f.objectId))}
    @After fun cleanup(){root.deleteRecursively()}
    private fun prepare()=store.prepare("job-test","operation-test","binding-test",manifest.revisionId,manifest,payloads,steps)
    @Test fun freshStoreReopensExactIntentSourcesAndBeforeImages() {
        val before="names: {en: old synthetic source}\n".toByteArray()
        val changed=steps.map {WorkingFileStep(it.objectId,it.folderId,it.marker,it.name,it.mimeType,"prior-tag",before,it.intendedBytes)}
        val pointer=store.prepare("job-test","operation-test","binding-test",manifest.revisionId,manifest,payloads,changed)
        val loaded=TemplateSyncFiles5(root,contracts).load(pointer)
        assertEquals("operation-test",loaded.operationId);assertEquals(manifest.sourceHash,loaded.manifest.sourceHash)
        for(step in loaded.steps){assertContentEquals(before,step.beforeBytes);assertEquals("prior-tag",step.expectedTag)}
        assertEquals(pointer,store.prepare("job-test","operation-test","binding-test",manifest.revisionId,manifest,payloads,changed))
    }
    @Test fun tamperedOrMissingPayloadAndWrongPointerFailClosed() {
        val pointer=prepare();val payload=root.resolve("${steps.first().intendedHash}.yaml")
        payload.writeText("names: {en: externally changed}\n")
        assertFails {store.load(pointer)}
        payload.delete();assertFails {store.load(pointer)}
        assertFails {store.load(pointer.copy(sha256="a".repeat(64)))}
        assertFails {store.load(pointer.copy(path="../job-test.json"))}
    }
    @Test fun reusedJobIdentityCannotReplaceRetainedIntent() {
        val pointer=prepare();val original=root.resolve(pointer.path).readBytes()
        assertFails {store.prepare("job-test","different-operation","binding-test",manifest.revisionId,manifest,payloads,steps)}
        assertContentEquals(original,root.resolve(pointer.path).readBytes())
    }
    @Test fun wrongWorkingBytesAreRejectedBeforeIntentBecomesUsable() {
        val wrong=steps.map {WorkingFileStep(it.objectId,it.folderId,it.marker,it.name,it.mimeType,null,null,"names: {en: wrong}\n".toByteArray())}
        assertFails {store.prepare("job-test","operation-test","binding-test",manifest.revisionId,manifest,payloads,wrong)}
        assertFalse(root.resolve("job-test.json").exists())
    }
    @Test fun additiveProgressReopensWithoutReplacingCapturedSourceAndDuplicateAckIsIdempotent() {
        val original=prepare();val capturedBytes=root.resolve(original.path).readBytes();val progress=TemplateSyncProgressFiles5(store)
        val first=WorkingReceipt5(steps[0].objectId,"tag-after-one",steps[0].intendedHash)
        val next=progress.acknowledge(original,first)
        assertNotEquals(original.path,next.path);assertContentEquals(capturedBytes,root.resolve(original.path).readBytes())
        val reopened=TemplateSyncProgressFiles5(TemplateSyncFiles5(root,contracts));val loaded=reopened.load(next)
        assertEquals(original,loaded.capturedPointer);assertEquals(first,loaded.receipts[first.objectId]);assertFalse(loaded.allFilesAcknowledged)
        val count=root.listFiles()!!.size;assertEquals(next,reopened.acknowledge(next,first));assertEquals(count,root.listFiles()!!.size)
        val second=WorkingReceipt5(steps[1].objectId,"tag-after-two",steps[1].intendedHash)
        val all=reopened.load(reopened.acknowledge(next,second));assertTrue(all.allFilesAcknowledged);assertEquals(2,all.receipts.size)
    }
    @Test fun unknownFileWrongHashOrChangedAcknowledgementCannotReplaceARecordedFact() {
        val original=prepare();val progress=TemplateSyncProgressFiles5(store)
        val first=WorkingReceipt5(steps[0].objectId,"tag-after",steps[0].intendedHash)
        val next=progress.acknowledge(original,first);val priorBytes=root.resolve(next.path).readBytes()
        for(invalid in listOf(first.copy(objectId="unknown-file"),first.copy(sha256="a".repeat(64)),first.copy(tag="different-observed-tag")))
            assertFails {progress.acknowledge(next,invalid)}
        assertContentEquals(priorBytes,root.resolve(next.path).readBytes());assertEquals(first,progress.load(next).receipts[first.objectId])
    }
    @Test fun malformedOrRetargetedProgressDoesNotAuthorizeAnotherJobOrCollapseDuplicateReceipts() {
        val original=prepare();val progress=TemplateSyncProgressFiles5(store)
        val next=progress.acknowledge(original,WorkingReceipt5(steps[0].objectId,"tag-after",steps[0].intendedHash))
        val doc=Json.parseToJsonElement(root.resolve(next.path).readText()).jsonObject
        val receipts=doc.getValue("receipts").jsonArray
        val duplicate=store.writeRecord(JsonObject(doc+("receipts" to JsonArray(listOf(receipts[0],receipts[0])))).toString())
        assertFails {progress.load(duplicate)}
        val other=store.prepare("another-job","another-operation","binding-test",manifest.revisionId,manifest,payloads,steps)
        val retarget=store.writeRecord(JsonObject(doc+("capturedPath" to JsonPrimitive(other.path))+("capturedHash" to JsonPrimitive(other.sha256))).toString())
        assertFails {progress.load(retarget)}
        val newer=store.writeRecord(JsonObject(doc+("schemaVersion" to JsonPrimitive("9.0.0"))).toString());assertFails {progress.load(newer)}
        assertEquals(1,progress.load(next).receipts.size)
    }
    @Test fun legacyCapturedIntentRemainsReadableAndCorruptProgressLeavesItUntouched() {
        val original=prepare();val progress=TemplateSyncProgressFiles5(store)
        assertTrue(progress.load(original).receipts.isEmpty())
        val next=progress.acknowledge(original,WorkingReceipt5(steps[0].objectId,"tag-after",steps[0].intendedHash))
        root.resolve(next.path).writeText("{}")
        assertFails {progress.load(next)};assertEquals(manifest.sourceHash,store.load(original).manifest.sourceHash)
        val unknown=store.writeRecord("{\"kind\":\"unknownFutureFormat\"}");assertFails {progress.load(unknown)}
    }
    @Test fun receiptScopeMustMatchTheFrozenJobAndNeverAcceptControlCharacters() {
        val original=prepare();val checked=TemplateSyncProgressFiles5(store).load(original)
        val job=TemplateSyncJob5("job-test","binding-test","operation-test","push",manifest.revisionId,
            sourceHash=manifest.sourceHash,compiledPlanHash=manifest.compiledPlanHash,intentPath=original.path,intentHash=original.sha256)
        checked.requireJob(job)
        assertFails {checked.requireJob(job.copy(operationId="another-operation"))}
        assertFails {checked.requireJob(job.copy(sourceHash="a".repeat(64)))}
        assertFails {WorkingReceipt5(steps[0].objectId,"tag\r\ninjection",steps[0].intendedHash)}
    }
    @Test fun immutableRevisionObjectsCannotDoubleAsEditableWorkingFiles() {
        val overlapping=steps.mapIndexed {i,step->WorkingFileStep(if(i==0)manifest.files[0].objectId else step.objectId,
            step.folderId,step.marker,step.name,step.mimeType,step.expectedTag,step.beforeBytes,step.intendedBytes)}
        assertFails {store.prepare("job-test","operation-test","binding-test",manifest.revisionId,manifest,payloads,overlapping)}
        assertFalse(root.resolve("job-test.json").exists())
    }
}
