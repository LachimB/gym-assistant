package dev.gymassistant.session

import dev.gymassistant.engine.v2.*
import dev.gymassistant.sync.*
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class TemplateSyncContracts5Test {
    private val root=File(checkNotNull(System.getProperty("gym.root")))
    private fun schema(n:String)=Json.parseToJsonElement(root.resolve("schemas/$n").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val raw=root.resolve("examples/template-sync/synthetic-revision.json").readText()
    @Test fun strictManifestRecompilesTheExactSyntheticSourceWithoutAdoption() {
        val manifest=TemplateSyncContracts5.manifest(raw)
        val fixture=Json.parseToJsonElement(root.resolve("examples/sessions/synthetic-runtime-v2-session.json").readText()).jsonObject
        val sources=sessionJson.decodeFromString<TemplateSources3>(fixture.getValue("sourcePlanSnapshot").jsonObject.getValue("sourceSnapshotJson").jsonPrimitive.content)
        val payloads=manifest.files.associate { f->f.objectId to (if(f.role==SourceRole.TEMPLATE)sources.templateYaml else sources.exerciseYamls.single()).toByteArray() }
        assertEquals(sources,TemplateSyncContracts5.bundle(manifest,payloads,contracts))
        val again=TemplateSyncContracts5.manifest(TemplateSyncContracts5.encode(manifest))
        assertEquals(manifest.files,again.files);assertEquals(manifest.sourceHash,again.sourceHash)
        assertFails {TemplateSyncContracts5.bundle(manifest,payloads.mapValues { (_,v)->v+byteArrayOf(32) },contracts)}
        val wrongPlan=TemplateRevisionManifest(manifest.templateId,manifest.revisionId,null,manifest.sourceHash,"a".repeat(64),manifest.files)
        assertFails {TemplateSyncContracts5.bundle(wrongPlan,payloads,contracts)}
    }
    @Test fun manifestRejectsUnknownFieldsDuplicateKeysAndWrongScalarTypes() {
        val obj=Json.parseToJsonElement(raw).jsonObject
        for(invalid in listOf(
            raw.replaceFirst("{","{\"schemaVersion\":\"1.0.0\","),
            JsonObject(obj+("unknown" to JsonPrimitive(true))).toString(),
            JsonObject(obj+("schemaVersion" to JsonPrimitive("2.0.0"))).toString(),
            raw.replace("\"byteCount\":", "\"byteCount\":\"invalid\",\"ignored\":"),
            " ".repeat(65537),"[{}]",
        ))assertFailsWith<IllegalArgumentException> {TemplateSyncContracts5.manifest(invalid)}
    }
    @Test fun publicationHeadRoundTripsAndRejectsMalformedOrChangedIdentity() {
        val rawHead=root.resolve("examples/template-sync/synthetic-head.json").readText()
        val head=TemplateSyncContracts5.head(rawHead)
        assertEquals(head,TemplateSyncContracts5.head(TemplateSyncContracts5.encode(head)))
        assertFails {TemplateSyncContracts5.head(rawHead.replace("1.0.0","9.0.0"))}
        assertFails {TemplateSyncContracts5.head(rawHead.replaceFirst("{","{\"generationId\":\"duplicate\","))}
        val next=head.copy(generationId="generation_next",previousGenerationId=head.generationId)
        assertEquals(next,head.advance(head,next))
        assertFails {head.advance(head,next.copy(workingFolderId="another-folder"))}
    }
}
