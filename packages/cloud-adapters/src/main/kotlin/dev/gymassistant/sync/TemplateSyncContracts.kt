package dev.gymassistant.sync

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal fun domainId(value: String) = require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{2,127}"))) { "Invalid domain identity" }
internal fun objectId(value: String) = require(value.matches(Regex("[A-Za-z0-9_-]{1,160}"))) { "Invalid object handle" }
internal fun sourceHash(value: String) = require(value.matches(Regex("[a-f0-9]{64}"))) { "Invalid content hash" }
fun hashSourceBytes(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

enum class SourceRole { TEMPLATE, EXERCISE }
data class RevisionFile(
    val role: SourceRole, val relativeName: String, val objectId: String, val sha256: String, val byteCount: Int,
) {
    init {
        require(relativeName.length in 1..128 && !relativeName.startsWith('.') && !relativeName.contains("..") &&
            relativeName.none { it == '/' || it == '\\' || it == ':' || it.isISOControl() } &&
            (relativeName.endsWith(".yaml") || relativeName.endsWith(".yml"))) { "Invalid source filename" }
        objectId(objectId); sourceHash(sha256)
        require(byteCount in 1..1048576) { "Source file size outside import limits" }
    }
}

/** Immutable publication descriptor. Parsing and compilation must independently validate all retained source. */
class TemplateRevisionManifest(
    val templateId: String, val revisionId: String, val previousRevisionId: String?,
    val sourceHash: String, val compiledPlanHash: String, files: List<RevisionFile>,
    val engineVersion: String = "kotlin-timed-2", val schemaVersion: String = "1.0.0",
) {
    val files = files.toList()
    init {
        domainId(templateId); domainId(revisionId); previousRevisionId?.let { domainId(it); require(it != revisionId) }
        sourceHash(sourceHash); sourceHash(compiledPlanHash)
        require(schemaVersion == "1.0.0" && engineVersion == "kotlin-timed-2") { "Unsupported sync contract" }
        require(this.files.size in 2..16 && this.files.count { it.role == SourceRole.TEMPLATE } == 1)
        require(this.files.map { it.relativeName }.distinct().size == this.files.size && this.files.map { it.objectId }.distinct().size == this.files.size)
        require(this.files.sumOf { it.byteCount.toLong() } <= 8388608) { "Source bundle exceeds import limit" }
    }
    /** Returns fresh decoded input only; caller still checks full source/compiled hashes with the existing compiler. */
    fun checkedPayloads(payloads: Map<String, ByteArray>): List<Pair<RevisionFile, String>> {
        require(payloads.keys == files.map { it.objectId }.toSet()) { "Source bundle incomplete or unexpected" }
        return files.map { file ->
            val bytes = payloads.getValue(file.objectId)
            check(bytes.size == file.byteCount && hashSourceBytes(bytes) == file.sha256) { "Source bytes differ from manifest" }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            file to text
        }
    }
}

data class TemplatePublicationHead(
    val templateId: String, val generationId: String, val revisionManifestId: String, val workingFolderId: String,
    val sourceHash: String, val previousGenerationId: String?, val schemaVersion: String = "1.0.0",
) {
    init {
        domainId(templateId); domainId(generationId); objectId(revisionManifestId); objectId(workingFolderId); sourceHash(sourceHash)
        require(schemaVersion == "1.0.0" && revisionManifestId != workingFolderId)
        previousGenerationId?.let { domainId(it); require(it != generationId) }
    }
    fun advance(expected: TemplatePublicationHead, next: TemplatePublicationHead): TemplatePublicationHead {
        check(this == expected) { "Publication head changed" }
        require(next.templateId == templateId && next.workingFolderId == workingFolderId) { "Stable binding changed" }
        require(next.generationId != generationId && next.previousGenerationId == generationId) { "Publication lineage changed" }
        return next
    }
}
