package dev.gymassistant.sync

import kotlin.test.*

class TemplateMergeTest {
    private val hold = FieldKey(listOf("template_demo", "set_left"), listOf("phases", "hold", "durationMs"))
    private val rest = FieldKey(listOf("template_demo", "set_right"), listOf("restMs"))
    private fun num(value: String) = SourceValue.Number(value)
    private fun source(rev: String, values: Map<FieldKey, SourceValue>, structure: String = "a".repeat(64)) = SourceFields("template_demo", rev, structure, values)
    @Test fun disjointEditsKeepBothAndRequireWholeValidation() {
        val base = source("rev_base", mapOf(hold to num("6"), rest to num("20")))
        val phone = source("rev_phone", mapOf(hold to num("8"), rest to num("20")))
        val drive = source("rev_drive", mapOf(hold to num("6"), rest to num("30")))
        val review = TemplateMergeReview(base, phone, drive)
        assertTrue(review.conflicts.isEmpty())
        var checked = false
        val candidate = review.candidate("rev_resolved", emptyMap()) { checked = true; it.values[hold] == num("8") && it.values[rest] == num("30") }
        assertTrue(checked); assertEquals(num("6"), base.values[hold]); assertEquals("rev_resolved", candidate.revisionId)
        assertFailsWith<IllegalStateException> { review.candidate("rev_other", emptyMap()) { false } }
    }
    @Test fun conflictsNeedIndividualChoicesAndCannotSelectAWholeVersionImplicitly() {
        val base = source("rev_base", mapOf(hold to num("6"), rest to num("20")))
        val phone = source("rev_phone", mapOf(hold to num("8"), rest to num("30")))
        val drive = source("rev_drive", mapOf(hold to num("10"), rest to num("40")))
        val review = TemplateMergeReview(base, phone, drive)
        assertEquals(2, review.conflicts.size)
        assertFailsWith<IllegalArgumentException> { review.candidate("rev_new", mapOf(hold to FieldChoice.PHONE)) { true } }
        val result = review.candidate("rev_new", mapOf(hold to FieldChoice.PHONE, rest to FieldChoice.DRIVE)) { true }
        assertEquals(num("8"), result.values[hold]); assertEquals(num("40"), result.values[rest])
        assertEquals(num("10"), drive.values[hold])
    }
    @Test fun deletedFieldAndExplicitNullRemainDistinct() {
        val base = source("rev_base", mapOf(hold to num("6")))
        val phone = source("rev_phone", emptyMap())
        val drive = source("rev_drive", mapOf(hold to SourceValue.Null))
        val review = TemplateMergeReview(base, phone, drive)
        assertEquals(SourceValue.Absent, review.conflicts.single().phone)
        assertEquals(SourceValue.Null, review.conflicts.single().drive)
        assertTrue(review.candidate("rev_new", mapOf(hold to FieldChoice.PHONE)) { true }.values.isEmpty())
        assertEquals(SourceValue.Null, review.candidate("rev_next", mapOf(hold to FieldChoice.DRIVE)) { true }.values[hold])
    }
    @Test fun structuralChangesBlockScalarMergeAndTemplateIdentityCannotChange() {
        val base = source("rev_base", mapOf(hold to num("6")))
        val reordered = source("rev_phone", base.values, "b".repeat(64))
        val review = TemplateMergeReview(base, reordered, base)
        assertTrue(review.structuralConflict)
        assertFailsWith<IllegalStateException> { review.candidate("rev_new", emptyMap()) { true } }
        assertFailsWith<IllegalArgumentException> { TemplateMergeReview(base, SourceFields("template_other", "rev_phone", base.structureHash, base.values), base) }
    }
    @Test fun unknownChoicesAndReusedRevisionIdsFailClosed() {
        val base = source("rev_base", mapOf(hold to num("6")))
        val review = TemplateMergeReview(base, base, base)
        assertFailsWith<IllegalArgumentException> { review.candidate("rev_new", mapOf(rest to FieldChoice.PHONE)) { true } }
        assertFailsWith<IllegalArgumentException> { review.candidate("rev_base", emptyMap()) { true } }
    }
    @Test fun reviewSnapshotsAreIndependentOfInputCollections() {
        val values = mutableMapOf<FieldKey, SourceValue>(hold to num("6"))
        val base = source("rev_base", values); val review = TemplateMergeReview(base, base, base)
        values[hold] = num("100")
        assertEquals(num("6"), review.candidate("rev_new", emptyMap()) { true }.values[hold])
    }
    @Test fun fieldIdentityIncludesBoundariesAndAllowsNumericSourceIds() {
        val a = FieldKey(listOf("aaa", "properties", "bbb"), listOf("ccc"))
        val b = FieldKey(listOf("aaa"), listOf("bbb", "properties", "ccc"))
        assertNotEquals(a.conflictId, b.conflictId)
        assertEquals(64, FieldKey(listOf("123"), listOf("durationMs")).conflictId.length)
        assertFailsWith<IllegalArgumentException> { FieldKey(listOf("../source"), listOf("name")) }
    }
    @Test fun decimalEquivalenceAndBoundsAreDeterministic() {
        assertEquals(num("1.0"), num("1e0")); assertEquals(num("-0"), num("0"))
        for (value in listOf("NaN", "Infinity", "1e999999999", "01", "1.")) assertFailsWith<IllegalArgumentException> { num(value) }
    }
}
