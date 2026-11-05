package dev.gymassistant.drive

import dev.gymassistant.engine.v2.ImportContracts2
import dev.gymassistant.session.*
import dev.gymassistant.sync.*
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class TemplateKnownSourceCompile5Test {
    private val repo=File(checkNotNull(System.getProperty("gym.root")))
    private fun schema(name:String)=Json.parseToJsonElement(repo.resolve("schemas/$name").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val fixture=Json.parseToJsonElement(repo.resolve("examples/sessions/synthetic-runtime-v2-session.json").readText()).jsonObject
    private val snapshot=fixture.getValue("sourcePlanSnapshot").jsonObject
    private val sources=sessionJson.decodeFromString<TemplateSources3>(snapshot.getValue("sourceSnapshotJson").jsonPrimitive.content)
    private val templateId=fixture.getValue("templateId").jsonPrimitive.content
    private val revisionId=fixture.getValue("revisionId").jsonPrimitive.content
    private val head=TemplatePublicationHead(templateId,"generation-test","manifest-test","working-test",snapshot.getValue("sourceHash").jsonPrimitive.content,null)
    private fun source(role:String,id:String,raw:String)=SourceReadObject5(SourceCapturePin5(role,"$id.yaml",id,"text/plain"),"\"tag\"",byteArrayOf(65),raw.toByteArray())
    private fun vector(template:String=sources.templateYaml,exercise:String=sources.exerciseYamls.single(),headValue:TemplatePublicationHead?=head)=
        KnownSourceVector5(headValue?.let {PublicationHeadSnapshot("head-test",setOf("metadata-test"),"gym-template-test","head.json","application/json","\"head-tag\"",false,it,
            byteArrayOf(65),byteArrayOf(65))},listOf(source("exercise","known-exercise",exercise),source("template","known-template",template)))
    @Test fun completeSourceCompilesByDeclaredRolesToExistingCanonicalHashes() {
        val compiled=vector().compileWorkingSource(templateId,contracts)
        assertEquals(sources,compiled.sources);assertEquals(templateId,compiled.templateId);assertEquals(revisionId,compiled.revisionId)
        assertEquals(snapshot.getValue("sourceHash").jsonPrimitive.content,compiled.sourceHash)
        assertEquals(snapshot.getValue("compiledPlanHash").jsonPrimitive.content,compiled.compiledPlanHash)
    }
    @Test fun externalBytesAndUnchangedRevisionAreRetainedWithoutClaimingPublishedHeadEquality() {
        val changed=sources.templateYaml+"\n# synthetic external edit\n";val vector=vector(template=changed)
        val compiled=vector.compileWorkingSource(templateId,contracts)
        assertEquals(changed,compiled.sources.templateYaml);assertEquals(revisionId,compiled.revisionId)
        assertNotEquals(vector.head!!.head.sourceHash,compiled.sourceHash)
        assertEquals(snapshot.getValue("compiledPlanHash").jsonPrimitive.content,compiled.compiledPlanHash)
        assertEquals(head,vector.head.head)
        assertEquals(compiled.sourceHash,vector(template=changed,headValue=null).compileWorkingSource(templateId,contracts).sourceHash)
    }
    @Test fun wrongTemplateOrHeadMissingDependencyMalformedAndDuplicateFamiliesAreRejected() {
        assertFails {vector().compileWorkingSource("another-template",contracts)}
        assertFails {vector(headValue=head.copy(templateId="another-template")).compileWorkingSource(templateId,contracts)}
        assertFails {vector(exercise="{bad").compileWorkingSource(templateId,contracts)}
        assertFails {vector(exercise=sources.templateYaml).compileWorkingSource(templateId,contracts)}
        val duplicate=KnownSourceVector5(null,vector().files+source("exercise","duplicate-family",sources.exerciseYamls.single()))
        assertFails {duplicate.compileWorkingSource(templateId,contracts)}
        assertFails {KnownSourceVector5(null,listOf(vector().files.last())).compileWorkingSource(templateId,contracts)}
    }
}
