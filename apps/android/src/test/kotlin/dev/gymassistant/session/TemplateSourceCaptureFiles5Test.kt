package dev.gymassistant.session

import dev.gymassistant.engine.v2.*
import dev.gymassistant.sync.hashSourceBytes
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Test
import kotlin.test.*

class TemplateSourceCaptureFiles5Test {
    private val repo=File(checkNotNull(System.getProperty("gym.root")))
    private val root=Files.createTempDirectory("synthetic-source-capture-").toFile()
    private fun schema(n:String)=Json.parseToJsonElement(repo.resolve("schemas/$n").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val fixture=Json.parseToJsonElement(repo.resolve("examples/sessions/synthetic-runtime-v2-session.json").readText()).jsonObject
    private val snapshot=fixture.getValue("sourcePlanSnapshot").jsonObject
    private val row=TemplateRevisionRow(fixture.getValue("templateId").jsonPrimitive.content,fixture.getValue("revisionId").jsonPrimitive.content,
        snapshot.getValue("sourceSnapshotJson").jsonPrimitive.content,snapshot.getValue("sourceHash").jsonPrimitive.content,
        snapshot.getValue("compiledPlanJson").jsonPrimitive.content,snapshot.getValue("compiledPlanHash").jsonPrimitive.content,"1.1.0",
        publishedAt="2026-11-05T12:00:00Z",candidateId="candidate-test")
    private val binding=TemplateSyncBinding5("binding-test","connection-test","root-binding-test",row.templateId,"template-folder","working-folder",
        "history-folder","metadata-folder","gym-template-test","head-file",enabled=true,createdAt=row.publishedAt,updatedAt=row.publishedAt)
    private val files=TemplateSyncFiles5(root,contracts);private val origins=TemplateSyncQueueFiles5(files,contracts)
    private val store=TemplateSourceCaptureFiles5(files,origins)
    private val namespace=SourceCaptureNamespace5("f".repeat(64),"synthetic-owner","project-root","personal-area","template-folder","working-folder","history-folder","metadata-folder","head-file","gym-template-test")
    private val pins=listOf(SourceCapturePin5("template","template.yaml","source-template","text/plain"),SourceCapturePin5("exercise","exercise.yaml","source-exercise","text/plain"))
    private fun origin(b:TemplateSyncBinding5=binding)=origins.prepare("job-test","operation-test",b,namespace.bindingHash,row)
    @After fun cleanup(){root.deleteRecursively()}
    @Test fun reopenedIncompleteCapturePreservesFrozenSourceAndMalformedRawResponses() {
        val origin=origin();val original=root.resolve(origin.path).readBytes();var pointer=store.prepare(origin,namespace,pins,"observation-test")
        val metadata="{bad-json".toByteArray();val content=byteArrayOf(0xC3.toByte(),0x28)
        pointer=store.retainRaw(pointer,PublicationRawObservation5("WORKING","source-template",200,metadata,content))
        metadata.fill(32);content.fill(32)
        pointer=store.retainRaw(pointer,PublicationRawObservation5("HEAD","head-file",404,null,null))
        val raw=TemplateSyncFiles5(root,contracts);val reopened=TemplateSourceCaptureFiles5(raw,TemplateSyncQueueFiles5(raw,contracts)).load(pointer)
        assertEquals(origin,reopened.originPointer);assertEquals(row.sourceHash,reopened.local.sourceHash);assertEquals(pins,reopened.pins)
        assertEquals(2,reopened.rawObservations.size);assertContentEquals(byteArrayOf(0xC3.toByte(),0x28),reopened.rawObservations.first().contentBytes)
        assertContentEquals(original,root.resolve(origin.path).readBytes())
        assertEquals("capturing",Json.parseToJsonElement(files.readRecord(pointer)).jsonObject.getValue("stage").jsonPrimitive.content)
    }
    @Test fun emptyPinsAndMissingHeadDoNotEraseTheExpectedRemoteBaseOrAuthorizeAnotherJob() {
        val b=binding.copy(baseRevisionId="base-revision",baseSourceHash="a".repeat(64),lastRemoteGeneration="base-generation",lastRemoteHeadTag="\"base-tag\"")
        val original=origin(b);val pointer=store.retainRaw(store.prepare(original,namespace,emptyList(),"observation-test"),PublicationRawObservation5("HEAD","head-file",404,null,null))
        val checked=store.load(pointer);assertTrue(checked.pins.isEmpty());assertEquals("base-generation",checked.local.expectedRemoteGeneration)
        val job=TemplateSyncJob5("job-test",binding.bindingId,"operation-test","push",row.revisionId,"base-generation","\"base-tag\"",row.sourceHash,row.compiledPlanHash,original.path,original.sha256,state="running")
        checked.requireJob(job);assertFails {checked.requireJob(job.copy(state="complete"))}
        assertFails {checked.requireJob(job.copy(intentPath=pointer.path,intentHash=pointer.sha256))}
        assertFails {checked.requireJob(job.copy(operationId="another-operation"))}
    }
    @Test fun aliasesUnsafeNamesDuplicatePinsAndWrongBindingFingerprintAreRejected() {
        val original=origin();assertFails {store.prepare(original,namespace.copy(bindingHash="a".repeat(64)),pins,"observation-test")}
        assertFails {namespace.copy(headFileId=namespace.workingFolderId)}
        for(name in listOf("../template.yaml","nested/template.yaml","C:template.yaml",".hidden.yaml","template.json"))assertFails {pins.first().copy(relativeName=name)}
        for(changed in listOf(pins+pins.first(),pins.map {it.copy(role="template")},pins.map {it.copy(objectId=namespace.headFileId)},listOf(pins.first())))
            assertFails {store.prepare(original,namespace,changed,"observation-test")}
        assertFails {pins.first().copy(mimeType="application/json")}
    }
    @Test fun unknownCompletionClaimTamperedAuditOrSourceCannotBecomeAnAcceptedCapture() {
        val original=origin();val pointer=store.retainRaw(store.prepare(original,namespace,pins,"observation-test"),PublicationRawObservation5("HEAD","head-file",200,"{bad".toByteArray(),null))
        val d=Json.parseToJsonElement(files.readRecord(pointer)).jsonObject
        for(changed in listOf(d+("stage" to JsonPrimitive("complete")),d+("schemaVersion" to JsonPrimitive("9.0.0")),d+("candidateId" to JsonPrimitive("invented-candidate"))))
            assertFails {store.load(files.writeRecord(JsonObject(changed).toString()))}
        root.resolve(hashSourceBytes("{bad".toByteArray())+".yaml").writeText("changed")
        assertFails {store.load(pointer)};assertEquals(row.sourceHash,origins.load(original).sourceHash)
        val source=sessionJson.decodeFromString<TemplateSources3>(row.sourceSnapshotJson).templateYaml.toByteArray()
        root.resolve(hashSourceBytes(source)+".yaml").writeText("changed source")
        assertFails {store.load(store.prepare(original,namespace,pins,"observation-test"))}
    }
    @Test fun separateCapturesOfTheSameJobCannotBorrowEachOthersAuditChain() {
        val original=origin();val first=store.prepare(original,namespace,pins,"observation-first")
        val second=store.prepare(original,namespace,pins,"observation-second")
        val raw=PublicationRawObservation5("HEAD","head-file",404,null,null)
        val a=store.retainRaw(first,raw);val b=store.retainRaw(second,raw)
        val firstDoc=Json.parseToJsonElement(files.readRecord(a)).jsonObject;val secondDoc=Json.parseToJsonElement(files.readRecord(b)).jsonObject
        for(changed in listOf(firstDoc+("rawAudit" to secondDoc.getValue("rawAudit")),
            firstDoc+("rawAudit" to secondDoc.getValue("rawAudit"))+("captureOriginPath" to secondDoc.getValue("captureOriginPath"))+("captureOriginHash" to secondDoc.getValue("captureOriginHash"))))
            assertFails {store.load(files.writeRecord(JsonObject(changed).toString()))}
        assertEquals("observation-first",store.load(a).observationId);assertEquals(first,store.load(a).captureOrigin)
        assertEquals("observation-second",store.load(b).observationId);assertEquals(second,store.load(b).captureOrigin)
    }
}
