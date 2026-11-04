package dev.gymassistant.session

import dev.gymassistant.engine.v2.*
import dev.gymassistant.sync.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private val syncJson5=Json { encodeDefaults=true; ignoreUnknownKeys=false; isLenient=false; coerceInputValues=false }

@Serializable private data class SyncFileDto5(val role:String,val relativeName:String,val objectId:String,val sha256:String,val byteCount:Int)
@Serializable private data class SyncManifestDto5(val schemaVersion:String,val templateId:String,val revisionId:String,
    val previousRevisionId:String?,val sourceHash:String,val compiledPlanHash:String,val engineVersion:String,val files:List<SyncFileDto5>)
@Serializable private data class SyncHeadDto5(val schemaVersion:String,val templateId:String,val generationId:String,
    val revisionManifestId:String,val workingFolderId:String,val sourceHash:String,val previousGenerationId:String?)

/** Strict bounded JSON, including duplicate-key rejection. Errors deliberately exclude source text. */
object TemplateSyncContracts5 {
    private fun checked(raw:String) {
        require(raw.toByteArray(Charsets.UTF_8).size in 1..65536)
        syncJson5.parseToJsonElement(raw).jsonObject
        readYaml2(raw) // The bounded YAML parser rejects duplicate keys, anchors and excessive nesting.
    }
    fun manifest(raw:String):TemplateRevisionManifest = try {
        checked(raw)
        val dto=syncJson5.decodeFromString<SyncManifestDto5>(raw)
        TemplateRevisionManifest(dto.templateId,dto.revisionId,dto.previousRevisionId,dto.sourceHash,dto.compiledPlanHash,
            dto.files.map { f->RevisionFile(when(f.role){"template"->SourceRole.TEMPLATE;"exercise"->SourceRole.EXERCISE;else->error("role")},f.relativeName,f.objectId,f.sha256,f.byteCount) },dto.engineVersion,dto.schemaVersion)
    }catch(_:Exception){throw IllegalArgumentException("Invalid or unsupported Template revision manifest")}
    fun head(raw:String):TemplatePublicationHead = try {
        checked(raw)
        val dto=syncJson5.decodeFromString<SyncHeadDto5>(raw)
        TemplatePublicationHead(dto.templateId,dto.generationId,dto.revisionManifestId,dto.workingFolderId,dto.sourceHash,dto.previousGenerationId,dto.schemaVersion)
    }catch(_:Exception){throw IllegalArgumentException("Invalid or unsupported Template publication head")}
    fun encode(manifest:TemplateRevisionManifest):String = syncJson5.encodeToString(SyncManifestDto5(manifest.schemaVersion,manifest.templateId,manifest.revisionId,
        manifest.previousRevisionId,manifest.sourceHash,manifest.compiledPlanHash,manifest.engineVersion,manifest.files.map { f->SyncFileDto5(if(f.role==SourceRole.TEMPLATE)"template" else "exercise",f.relativeName,f.objectId,f.sha256,f.byteCount) }))
    fun encode(head:TemplatePublicationHead):String = syncJson5.encodeToString(SyncHeadDto5(head.schemaVersion,head.templateId,head.generationId,head.revisionManifestId,head.workingFolderId,head.sourceHash,head.previousGenerationId))

    /** Verifies every retained byte and recompiles the entire source. This does not publish/adopt anything. */
    fun bundle(manifest:TemplateRevisionManifest,payloads:Map<String,ByteArray>,contracts:ImportContracts2):TemplateSources3 {
        val checked=manifest.checkedPayloads(payloads)
        val template=checked.single { it.first.role==SourceRole.TEMPLATE }.second
        val exercises=checked.filter { it.first.role==SourceRole.EXERCISE }.map { it.second }.sortedBy { readYaml2(it).text2("familyId") }
        val sources=TemplateSources3(template,exercises)
        val plan=compileTemplate2(template,exercises,contracts)
        check(plan.templateId==manifest.templateId && plan.revisionId==manifest.revisionId && manifest.engineVersion=="kotlin-timed-2")
        check(hashUtf8_2(sessionJson.encodeToString(sources))==manifest.sourceHash) { "Source snapshot differs from manifest" }
        check(hashUtf8_2(planJson2.encodeToString(plan))==manifest.compiledPlanHash) { "Compiled plan differs from manifest" }
        return sources
    }
}
