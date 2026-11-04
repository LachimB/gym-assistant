package dev.gymassistant.sync

import kotlin.test.*

class TemplateSyncContractsTest {
    private val template = "schemaVersion: 1.1.0\n".encodeToByteArray()
    private val exercise = "familyId: exercise_demo\n".encodeToByteArray()
    private val files = listOf(
        RevisionFile(SourceRole.TEMPLATE, "template.yaml", "object-template", hashSourceBytes(template), template.size),
        RevisionFile(SourceRole.EXERCISE, "exercise-demo.yaml", "object-exercise", hashSourceBytes(exercise), exercise.size),
    )
    private fun manifest(input: List<RevisionFile> = files) = TemplateRevisionManifest("template_demo", "rev_new", "rev_base", "a".repeat(64), "b".repeat(64), input)
    @Test fun completeBundleRequiresExactBytesAndIds() {
        val source = mapOf("object-template" to template, "object-exercise" to exercise)
        assertEquals(2, manifest().checkedPayloads(source).size)
        assertFailsWith<IllegalArgumentException> { manifest().checkedPayloads(source - "object-template") }
        assertFailsWith<IllegalArgumentException> { manifest().checkedPayloads(source + ("other" to template)) }
        assertFailsWith<IllegalStateException> { manifest().checkedPayloads(source + ("object-template" to ByteArray(template.size))) }
    }
    @Test fun unsafeDuplicateOversizedOrRoleAmbiguousInputsFail() {
        assertFailsWith<IllegalArgumentException> { manifest(listOf(files[0], files[0])) }
        assertFailsWith<IllegalArgumentException> { manifest(listOf(files[1], files[1].copy(relativeName="second.yaml",objectId="other"))) }
        assertFailsWith<IllegalArgumentException> { manifest(files + (1..15).map { files[1].copy(relativeName="exercise-$it.yaml",objectId="object-$it") }) }
        for (name in listOf("../template.yaml", "nested/template.yaml", "C:template.yaml", "template.json", ".hidden.yaml"))
            assertFailsWith<IllegalArgumentException> { files[0].copy(relativeName=name) }
        assertFailsWith<IllegalArgumentException> { files[0].copy(byteCount=1048577) }
        assertFailsWith<IllegalArgumentException> { manifest(files.map { it.copy(byteCount=1048576) } + (1..7).map { files[1].copy(relativeName="extra-$it.yaml",objectId="extra-$it",byteCount=1048576) }) }
    }
    @Test fun invalidUtf8IsNotNormalizedOrRepaired() {
        val invalid=byteArrayOf(0xc3.toByte(),0x28)
        val bad=files[0].copy(sha256=hashSourceBytes(invalid),byteCount=invalid.size)
        assertFails { manifest(listOf(bad,files[1])).checkedPayloads(mapOf(bad.objectId to invalid,files[1].objectId to exercise)) }
    }
    @Test fun publicationAdvanceKeepsWorkingFolderAndLineageStable() {
        val head=TemplatePublicationHead("template_demo","generation_old","manifest-old","working-fixed","a".repeat(64),null)
        val next=head.copy(generationId="generation_new",revisionManifestId="manifest-new",previousGenerationId=head.generationId)
        assertEquals(next,head.advance(head,next))
        assertFailsWith<IllegalStateException> { head.advance(next,next) }
        assertFailsWith<IllegalArgumentException> { head.advance(head,next.copy(workingFolderId="other-folder")) }
        assertFailsWith<IllegalArgumentException> { head.advance(head,next.copy(previousGenerationId="generation_other")) }
        assertFailsWith<IllegalArgumentException> { head.copy(schemaVersion="2.0.0") }
    }
}
