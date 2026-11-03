package dev.gymassistant.engine.v2

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest

const val MAX_SAFE_2: Long = 9007199254740991L
const val MAX_PHASES_2 = 10000
const val MAX_ADDITIONS_2 = 1000
internal fun safe2(value: Long): Long { require(value in 0..MAX_SAFE_2) { "Unsafe time/count" }; return value }
internal fun plus2(a: Long,b: Long): Long = safe2(Math.addExact(a,b))
fun hashUtf8_2(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
fun identity2(prefix: String,vararg parts: String) = prefix+"_"+hashUtf8_2(parts.joinToString("|") { "${it.length}:$it" })
val planJson2 = Json { encodeDefaults = true; explicitNulls = false }

@Serializable data class Resistance2(val weight: Weight2?=null,val bands: List<Band2> = emptyList(),val notes: String?=null)
@Serializable data class Weight2(val value: Double,val unit: String)
@Serializable data class Band2(val equipmentId: String,val count: Int,val placement: String)
@Serializable data class Phase2(
    val instanceId: String,val ordinal: Int,val unitId: String,val sourceStepId: String,val blockId: String,val occurrenceId: String?=null,
    val setId: String?=null,val phaseId: String?=null,val repetitionIndex: Int?=null,
    val exerciseId: String?=null,val variationId: String?=null,val side: String?=null,
    val exerciseName: String?=null,val variationName: String?=null,val name: String,val role: String,
    val startMs: Long,val endMs: Long,val baseStartMs: Long?=null,val resistance: Resistance2?=null,
    val adjustmentId: String?=null,
)
@Serializable data class AdditionPolicy2(
    val setId: String,val blockId: String,val occurrenceId: String,val anchorInstanceId: String,
    val resetMs: Long,val existingReset: Boolean,val core: List<Phase2>,
)
@Serializable data class Adjustment2(val adjustmentId: String,val setId: String,val recordedAt: String)
@Serializable data class Plan2(
    val templateId: String,val revisionId: String,val templateName: String,
    val originalDurationMs: Long,val expectedDurationMs: Long,val phases: List<Phase2>,
    val policies: List<AdditionPolicy2>,val adjustments: List<Adjustment2> = emptyList(),
    val planFormatVersion: String="2.0.0",
)

fun requirePlanBudget2(plan:Plan2) {
    require(plan.templateName.toByteArray(Charsets.UTF_8).size<=4096)
    val estimated=65536L+plan.adjustments.size*512L+plan.phases.sumOf {
        planJson2.encodeToString(Phase2.serializer(),it).toByteArray(Charsets.UTF_8).size*3L+1024L
    }
    require(estimated<=8388608) { "Compiled plan exceeds resource limit" }
}
