package dev.gymassistant.session

import dev.gymassistant.engine.v2.*
import dev.gymassistant.sync.*
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class TemplateYamlReview5Test {
    private val repo=File(checkNotNull(System.getProperty("gym.root")))
    private fun schema(n:String)=Json.parseToJsonElement(repo.resolve("schemas/$n").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val base=TemplateSources3("# Synthetic retained comment 😀\n"+repo.resolve("examples/templates/timed-runtime-v2.yaml").readText(),
        listOf(repo.resolve("examples/exercises/bridge-band-family.yaml").readText()))
    private fun changed(duration:Int=6000,rest:Int=30000)=base.copy(templateYaml=base.templateYaml.replaceFirst("durationMs: 6000","durationMs: $duration").replace("durationMs: 30000","durationMs: $rest"))
    @Test fun disjointStableFieldChangesRetainEveryOtherSourceByteAndAllBranches() {
        val phone=changed(duration=7000);val drive=changed(rest=20000)
        val review=TemplateYamlReview5(base,phone,drive,contracts)
        assertFalse(review.structuralConflict);assertTrue(review.conflicts.isEmpty())
        val resolved=review.resolve("revision_resolved",emptyMap())
        val expected=phone.templateYaml.replace("durationMs: 30000","durationMs: 20000")
            .replace("revisionId: revision_runtime_synthetic_1","revisionId: \"revision_resolved\"")
        assertEquals(expected,resolved.templateYaml);assertEquals(base.exerciseYamls,resolved.exerciseYamls)
        assertEquals(base,review.base.sources);assertEquals(phone,review.phone.sources);assertEquals(drive,review.drive.sources)
        assertEquals("revision_resolved",compileTemplate2(resolved.templateYaml,resolved.exerciseYamls,contracts).revisionId)
    }
    @Test fun twoConflictsRequireIndividualDecisionsAndCanRetainDifferentBranches() {
        val review=TemplateYamlReview5(base,changed(7000,20000),changed(8000,40000),contracts)
        assertEquals(2,review.conflicts.size)
        assertFails {review.resolve("revision_missing",emptyMap())}
        val choices=review.conflicts.associate {it.field to if("step_final_rest" in it.field.owners)FieldChoice.DRIVE else FieldChoice.PHONE}
        val resolved=review.resolve("revision_resolved",choices)
        assertTrue(resolved.templateYaml.contains("durationMs: 7000"));assertTrue(resolved.templateYaml.contains("durationMs: 40000"))
        assertFails {review.resolve("revision_runtime_synthetic_1",choices)}
    }
    @Test fun commentsLayoutAndUnsupportedArrayEditsBlockRatherThanSilentlyDisappear() {
        val alternatives=listOf(base.copy(templateYaml=base.templateYaml.replace("retained comment","externally edited comment")),
            base.copy(templateYaml=base.templateYaml.replace("names: {en:","names: { en:")),
            base.copy(exerciseYamls=base.exerciseYamls.map {it.replace("tags: [strength, mobility]","tags: [strength, recovery]")}))
        for(drive in alternatives){
            val review=TemplateYamlReview5(base,base,drive,contracts)
            assertTrue(review.structuralConflict);assertFails {review.resolve("revision_resolved",emptyMap())}
            assertEquals(drive,review.drive.sources)
        }
    }
    @Test fun changedSourceIdentityIsNeverMatchedByItsPositionOrName() {
        val drive=base.copy(templateYaml=base.templateYaml.replace("set_left","set_replaced"))
        val review=TemplateYamlReview5(base,base,drive,contracts)
        assertTrue(review.structuralConflict);assertFails {review.resolve("revision_resolved",emptyMap())}
    }
    @Test fun unicodeOffsetsAndQuotedSourceValuesStayValid() {
        val drive=base.copy(templateYaml=base.templateYaml.replace("notes: Synthetic fixed target","notes: 'Quoted \"target\": more 😀'"))
        val review=TemplateYamlReview5(base,base,drive,contracts)
        assertFalse(review.structuralConflict)
        val resolved=review.resolve("revision_unicode",emptyMap())
        val notes=readYaml2(resolved.templateYaml).getValue("sections").jsonArray[0].jsonObject.getValue("blocks").jsonArray[0].jsonObject
            .getValue("occurrences").jsonArray[0].jsonObject.getValue("sets").jsonArray[0].jsonObject.getValue("resistance").jsonObject.getValue("notes").jsonPrimitive.content
        assertEquals("Quoted \"target\": more 😀",notes);assertTrue(resolved.templateYaml.startsWith("# Synthetic retained comment 😀\n"))
    }
    @Test fun blockScalarReplacementKeepsItsFollowingKeyAndRetainedComments() {
        val original=base.exerciseYamls.single()
        val block=original.replace("  en: Synthetic training example used only for automated testing.","  en: | # Synthetic retained block header\n    Synthetic first line.\n    Synthetic second line.")
        val source=base.copy(exerciseYamls=listOf(block))
        val drive=source.copy(exerciseYamls=listOf(block.replace("Synthetic first line.","Synthetic changed line 😀.")))
        val review=TemplateYamlReview5(source,source,drive,contracts)
        assertFalse(review.structuralConflict)
        val result=review.resolve("revision_block",emptyMap())
        val parsed=readYaml2(result.exerciseYamls.single())
        assertEquals(readYaml2(drive.exerciseYamls.single()).getValue("description"),parsed.getValue("description"))
        assertEquals(readYaml2(block).getValue("tags"),parsed.getValue("tags"))
        assertTrue(result.exerciseYamls.single().contains("# Synthetic retained block header"))
        val changedComment=source.copy(exerciseYamls=listOf(block.replace("retained block header","externally edited header")))
        assertTrue(TemplateYamlReview5(source,source,changedComment,contracts).structuralConflict)
    }
    @Test fun candidateInjectionOrInvalidCombinedValuesCannotPassCompleteValidation() {
        val captured=TemplateYamlSource5(base,contracts)
        val fields=captured.fields
        val key=fields.values.keys.first {"set_left" in it.owners && "phase_work" in it.owners && it.properties==listOf("durationMs")}
        val invalid=SourceFields(fields.templateId,"revision_invalid",fields.structureHash,fields.values+(key to SourceValue.Number("0")))
        assertFails {captured.renderCandidate(invalid)}
        val injected=SourceFields(fields.templateId,"revision_injected",fields.structureHash,fields.values+(FieldKey(listOf("template_source",fields.templateId),listOf("unknown")) to SourceValue.Text("synthetic")))
        assertFails {captured.renderCandidate(injected)};assertEquals(base,captured.sources)
    }
    @Test fun crlfSourcesKeepLineEndingsOutsideExplicitlyChangedScalarTokens() {
        val source=base.copy(templateYaml=base.templateYaml.replace("\n","\r\n"))
        val drive=source.copy(templateYaml=source.templateYaml.replace("durationMs: 30000","durationMs: 20000"))
        val result=TemplateYamlReview5(source,source,drive,contracts).resolve("revision_crlf",emptyMap())
        assertEquals(drive.templateYaml.replace("revisionId: revision_runtime_synthetic_1","revisionId: \"revision_crlf\""),result.templateYaml)
        // Existing import contract rejects BOM-prefixed YAML; do not silently normalize it during merging.
        assertFails {TemplateYamlSource5(source.copy(templateYaml="\uFEFF"+source.templateYaml),contracts)}
    }
}
