package dev.gymassistant.session

import dev.gymassistant.engine.v2.*
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Test
import kotlin.test.*

class TemplateSyncQueueFiles5Test {
    private val repo=File(checkNotNull(System.getProperty("gym.root")))
    private val root=Files.createTempDirectory("synthetic-queue-").toFile()
    private fun schema(n:String)=Json.parseToJsonElement(repo.resolve("schemas/$n").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val fixture=Json.parseToJsonElement(repo.resolve("examples/sessions/synthetic-runtime-v2-session.json").readText()).jsonObject
    private val snapshot=fixture.getValue("sourcePlanSnapshot").jsonObject
    private val row=TemplateRevisionRow(fixture.getValue("templateId").jsonPrimitive.content,fixture.getValue("revisionId").jsonPrimitive.content,
        snapshot.getValue("sourceSnapshotJson").jsonPrimitive.content,snapshot.getValue("sourceHash").jsonPrimitive.content,
        snapshot.getValue("compiledPlanJson").jsonPrimitive.content,snapshot.getValue("compiledPlanHash").jsonPrimitive.content,"1.1.0",
        publishedAt="2026-11-04T12:00:00Z",candidateId="candidate-test")
    private val binding=TemplateSyncBinding5("binding-test","connection-test","root-binding-test",row.templateId,"template-folder","working-folder",
        "history-folder","metadata-folder","gym-template-test","head-file",enabled=true,createdAt=row.publishedAt,updatedAt=row.publishedAt)
    private val raw=TemplateSyncFiles5(root,contracts)
    private val store=TemplateSyncQueueFiles5(raw,contracts)
    private val scope="f".repeat(64)
    private fun prepare()=store.prepare("job-test","operation-test",binding,scope,row)
    @After fun cleanup(){root.deleteRecursively()}
    @Test fun reopenRetainsExactPublishedSourceWithoutInventingRemoteObjectsOrAbsentHead() {
        val pointer=prepare();val loaded=TemplateSyncQueueFiles5(TemplateSyncFiles5(root,contracts),contracts).load(pointer)
        assertEquals(row.sourceSnapshotJson,sessionJson.encodeToString(loaded.sources));assertEquals(row.sourceHash,loaded.sourceHash)
        assertNull(loaded.expectedRemoteGeneration);assertNull(loaded.expectedRemoteHeadTag)
        val dto=Json.parseToJsonElement(root.resolve(pointer.path).readText()).jsonObject
        assertEquals("localPublication",dto.getValue("kind").jsonPrimitive.content)
        assertTrue(dto.getValue("sources").jsonArray.all {"objectId" !in it.jsonObject && "expectedTag" !in it.jsonObject})
        assertFails {TemplateSyncProgressFiles5(raw).load(pointer)}
    }
    @Test fun baselineIsRetainedAsAnExpectationAndJobScopeCannotChange() {
        val based=binding.copy(baseRevisionId="base-revision",baseSourceHash="a".repeat(64),lastRemoteGeneration="base-generation",lastRemoteHeadTag="base-tag")
        val pointer=store.prepare("job-test","operation-test",based,scope,row);val loaded=store.load(pointer)
        assertEquals(based.baseRevisionId,loaded.baseRevisionId);assertEquals(based.baseSourceHash,loaded.baseSourceHash)
        val job=TemplateSyncJob5("job-test",binding.bindingId,"operation-test","push",row.revisionId,"base-generation","base-tag",row.sourceHash,row.compiledPlanHash,pointer.path,pointer.sha256)
        loaded.requireJob(job)
        for(changed in listOf(job.copy(direction="pull"),job.copy(operationId="another-operation"),job.copy(expectedRemoteGeneration="another-generation"),job.copy(sourceHash="b".repeat(64))))
            assertFails {loaded.requireJob(changed)}
    }
    @Test fun changedPublishedBytesHashPlanOrIdentityCannotQueue() {
        for(changed in listOf(row.copy(sourceHash="a".repeat(64)),row.copy(compiledPlanHash="b".repeat(64)),row.copy(revisionId="another-revision"),
            row.copy(sourceSnapshotJson=row.sourceSnapshotJson+" "),row.copy(compiledPlanJson=row.compiledPlanJson+" "),row.copy(templateFormatVersion="9.0.0")))
            assertFails {store.prepare("job-test","operation-test",binding,scope,changed)}
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }
    @Test fun malformedVersionDuplicateKeyMissingSourceOrAlteredSourceLeavesOriginalEvidence() {
        val pointer=prepare();val original=root.resolve(pointer.path).readBytes()
        val dto=Json.parseToJsonElement(original.toString(Charsets.UTF_8)).jsonObject
        val invalid=listOf(JsonObject(dto+("schemaVersion" to JsonPrimitive("9.0.0"))).toString(),JsonObject(dto-"schemaVersion").toString(),
            JsonObject(dto+("unknown" to JsonPrimitive(true))).toString(),JsonObject(dto+("bindingHash" to JsonPrimitive("invalid"))).toString())
        for(text in invalid)assertFails {store.load(raw.writeRecord(text))}
        assertFails {raw.writeRecord(original.toString(Charsets.UTF_8).replaceFirst("{","{\"kind\":\"duplicate\","))}
        val first=dto.getValue("sources").jsonArray[0].jsonObject.getValue("sha256").jsonPrimitive.content
        root.resolve("$first.yaml").writeText("invalid synthetic source")
        assertFails {store.load(pointer)};root.resolve("$first.yaml").delete();assertFails {store.load(pointer)}
        assertContentEquals(original,root.resolve(pointer.path).readBytes())
    }
    @Test fun reorderDuplicateNamesAndInvalidSizesCannotBeReinterpreted() {
        val pointer=prepare();val dto=Json.parseToJsonElement(root.resolve(pointer.path).readText()).jsonObject
        val descriptors=dto.getValue("sources").jsonArray
        for(changed in listOf(JsonArray(descriptors.reversed()),JsonArray(listOf(descriptors[0],descriptors[0])),
            JsonArray(listOf(JsonObject(descriptors[0].jsonObject+("byteCount" to JsonPrimitive(-1))),descriptors[1]))))
            assertFails {store.load(raw.writeRecord(JsonObject(dto+("sources" to changed)).toString()))}
        assertEquals(row.sourceHash,store.load(pointer).sourceHash)
    }
}
