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
}
