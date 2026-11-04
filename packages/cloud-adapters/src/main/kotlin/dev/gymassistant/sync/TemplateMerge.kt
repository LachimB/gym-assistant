package dev.gymassistant.sync

import java.math.BigDecimal
import java.security.MessageDigest

/** Scalar source values only. Arrays, ownership and ordering are structural, never index-matched. */
sealed interface SourceValue {
    data object Absent : SourceValue
    data object Null : SourceValue
    data class Text(val value: String) : SourceValue { init { require(value.length <= 65536) } }
    data class Flag(val value: Boolean) : SourceValue
    class Number(value: String) : SourceValue {
        val canonical: String
        init {
            require(value.length in 1..64 && value.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")))
            val number = BigDecimal(value).stripTrailingZeros()
            require(number.precision() <= 32 && number.scale() in -18..18)
            canonical = if (number.signum() == 0) "0" else number.toPlainString()
        }
        override fun equals(other: Any?) = other is Number && canonical == other.canonical
        override fun hashCode() = canonical.hashCode()
        override fun toString() = "Number" // Never leak source data through an exception/debug representation.
    }
}

/** Source adapter supplies stable ancestor IDs, never names or positional array indices. */
class FieldKey(owners: List<String>, properties: List<String>) : Comparable<FieldKey> {
    val owners: List<String> = owners.toList()
    val properties: List<String> = properties.toList()
    init {
        require(this.owners.size in 1..12 && this.properties.size in 1..12)
        require(this.owners.all { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{2,127}")) })
        require(this.properties.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,127}")) })
    }
    private fun encoded() = (listOf(owners.size.toString()) + owners + listOf(properties.size.toString()) + properties).joinToString("") { "${it.length}:$it" }
    val conflictId: String get() = MessageDigest.getInstance("SHA-256").digest(encoded().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    override fun equals(other: Any?) = other is FieldKey && owners == other.owners && properties == other.properties
    override fun hashCode() = 31 * owners.hashCode() + properties.hashCode()
    override fun compareTo(other: FieldKey) = encoded().compareTo(other.encoded())
    override fun toString() = "FieldKey" // Labels/values belong only in the explicitly selected review UI.
}

data class FieldConflict(val field: FieldKey, val base: SourceValue, val phone: SourceValue, val drive: SourceValue)
enum class FieldChoice { PHONE, DRIVE }

class SourceFields(val templateId: String, val revisionId: String, val structureHash: String, values: Map<FieldKey, SourceValue>) {
    val values = values.toMap()
    init {
        require(templateId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{2,127}")) && revisionId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{2,127}")))
        require(structureHash.matches(Regex("[a-f0-9]{64}")) && this.values.size <= 10000)
    }
}

/** This is a candidate builder, not publication. Callers must reconstruct, compile and explicitly review the whole result. */
class TemplateMergeReview(base: SourceFields, phone: SourceFields, drive: SourceFields) {
    private val templateId = base.templateId
    private val revisions = setOf(base.revisionId, phone.revisionId, drive.revisionId)
    private val structureHash = base.structureHash
    private val merged: Map<FieldKey, SourceValue>
    val conflicts: List<FieldConflict>
    val structuralConflict: Boolean
    init {
        require(base.templateId == phone.templateId && base.templateId == drive.templateId) { "Template identity mismatch" }
        structuralConflict = base.structureHash != phone.structureHash || base.structureHash != drive.structureHash
        val fields = (base.values.keys + phone.values.keys + drive.values.keys).toSortedSet()
        val combined = linkedMapOf<FieldKey, SourceValue>()
        val pending = mutableListOf<FieldConflict>()
        if (!structuralConflict) for (field in fields) {
            val b = base.values[field] ?: SourceValue.Absent
            val p = phone.values[field] ?: SourceValue.Absent
            val d = drive.values[field] ?: SourceValue.Absent
            when {
                p == d -> combined[field] = p
                p == b -> combined[field] = d
                d == b -> combined[field] = p
                else -> pending.add(FieldConflict(field, b, p, d))
            }
        }
        merged = combined.toMap()
        conflicts = pending.toList()
    }
    fun candidate(newRevisionId: String, choices: Map<FieldKey, FieldChoice>, validateWhole: (SourceFields) -> Boolean): SourceFields {
        check(!structuralConflict) { "Structural changes require source review" }
        require(newRevisionId !in revisions) { "A resolved candidate requires a new revision identity" }
        require(choices.keys == conflicts.map { it.field }.toSet()) { "Every conflict needs exactly one explicit field choice" }
        val result = merged.toMutableMap()
        for (conflict in conflicts) result[conflict.field] = when (choices.getValue(conflict.field)) {
            FieldChoice.PHONE -> conflict.phone
            FieldChoice.DRIVE -> conflict.drive
        }
        val candidate = SourceFields(templateId, newRevisionId, structureHash, result.filterValues { it != SourceValue.Absent })
        check(validateWhole(candidate)) { "Resolved source failed complete validation" }
        return candidate
    }
}
