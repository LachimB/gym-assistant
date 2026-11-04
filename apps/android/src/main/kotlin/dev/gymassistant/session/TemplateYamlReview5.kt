package dev.gymassistant.session

import dev.gymassistant.engine.v2.*
import dev.gymassistant.sync.*
import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.api.lowlevel.Compose
import it.krzeminski.snakeyaml.engine.kmp.nodes.*
import it.krzeminski.snakeyaml.engine.kmp.schema.JsonSchema
import kotlinx.serialization.json.*

private data class ScalarSpan5(val start:Int,val end:Int,val key:FieldKey?,val kind:String,val headerComment:String)

/** Validated source coordinates, not an arbitrary text merge. Unknown arrays/comments/layout are structural. */
private class YamlDocumentMap5(val raw:String,val sourceKind:String,val identity:String) {
    private val document=readYaml2(raw)
    private val spans=mutableListOf<ScalarSpan5>()
    val values=linkedMapOf<FieldKey,SourceValue>()
    private val ownIds=mapOf("sections" to "sectionId","blocks" to "blockId","occurrences" to "occurrenceId",
        "sets" to "setId","phases" to "phaseId","order" to "stepId","variations" to "variationId")
    init {
        val settings=LoadSettings.builder().setSchema(JsonSchema()).setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false).setMaxAliasesForCollections(0).setCodePointLimit(1048576).build()
        fun scalar(value:JsonElement):SourceValue = when {
            value==JsonNull -> SourceValue.Null
            value.jsonPrimitive.isString -> SourceValue.Text(value.jsonPrimitive.content)
            value.jsonPrimitive.booleanOrNull!=null -> SourceValue.Flag(value.jsonPrimitive.boolean)
            else -> SourceValue.Number(value.jsonPrimitive.content)
        }
        fun capture(node:ScalarNode,value:JsonElement,key:FieldKey?) {
            val start=raw.offsetByCodePoints(0,checkNotNull(node.startMark).index)
            val end=raw.offsetByCodePoints(0,checkNotNull(node.endMark).index)
            require(start>=0 && end>start && end<=raw.length)
            val typed=scalar(value)
            val kind=when(typed){is SourceValue.Text->"text";is SourceValue.Flag->"boolean";is SourceValue.Number->"number";else->"null"}
            if(key!=null){check(values.put(key,typed)==null);require(values.size<=10000)}
            val comment=Regex("^[|>][0-9+-]*[ \\t]*(#[^\\r\\n]*)?(?:\\r?\\n|\\r)")
                .find(raw.substring(start,end))?.groups?.get(1)?.value.orEmpty()
            spans+=ScalarSpan5(start,end,key,kind,comment)
        }
        fun walk(node:Node,value:JsonElement,owners:List<String>,path:List<String>,ownKey:String?=null,root:Boolean=false) {
            when(node) {
                is MappingNode -> for(tuple in node.value) {
                    val key=(tuple.keyNode as ScalarNode).value
                    val child=value.jsonObject.getValue(key)
                    if(root && key=="revisionId" && sourceKind=="template_source")capture(tuple.valueNode as ScalarNode,child,null)
                    else if(key!=ownKey && !(root && key in setOf("schemaVersion","templateId","familyId")))
                        walk(tuple.valueNode,child,owners,path+key)
                }
                is SequenceNode -> {
                    val collection=path.lastOrNull();val idKey=ownIds[collection]
                    // Arrays lacking declared stable identities need full source review; never infer by position/labels.
                    if(idKey!=null)for((index,item) in node.value.withIndex()) {
                        val child=value.jsonArray[index]
                        val id=child.jsonObject.getValue(idKey).jsonPrimitive.content
                        walk(item,child,owners+listOf(checkNotNull(collection),id),emptyList(),idKey)
                    }
                }
                is ScalarNode -> capture(node,value,FieldKey(owners,path))
                else -> error("Unsupported YAML node")
            }
        }
        walk(checkNotNull(Compose(settings).compose(raw)),document,listOf(sourceKind,identity),emptyList(),root=true)
        val ordered=spans.sortedBy {it.start}
        require(ordered.zipWithNext().all {(a,b)->a.end<=b.start})
    }
    fun structure():JsonArray=buildJsonArray {
        var offset=0
        for(span in spans.sortedBy {it.start}) {
            add(buildJsonArray {add("raw");add(raw.substring(offset,span.start))})
            add(buildJsonArray {add(if(span.key==null)"revision" else "field");add(span.key?.conflictId ?: "revision");add(span.kind);add(span.headerComment)})
            offset=span.end
        }
        add(buildJsonArray {add("raw");add(raw.substring(offset))})
    }
    fun render(candidate:SourceFields):String {
        val result=StringBuilder(raw)
        for(span in spans.sortedByDescending {it.start}) {
            val value=span.key?.let {candidate.values.getValue(it)} ?: SourceValue.Text(candidate.revisionId)
            if(span.key!=null && value==values[span.key])continue
            val encoded=when(value) {
                is SourceValue.Text -> JsonPrimitive(value.value).toString()
                is SourceValue.Flag -> value.value.toString()
                is SourceValue.Number -> value.canonical
                SourceValue.Null -> "null"
                SourceValue.Absent -> error("Field removal needs source review")
            }
            // Block scalars can own their trailing line break. Retain it so the next key cannot join this value.
            val suffix=Regex("(?:\\r\\n|\\n|\\r)+$").find(raw.substring(span.start,span.end))?.value.orEmpty()
            result.replace(span.start,span.end,encoded+(if(span.headerComment.isEmpty())"" else " "+span.headerComment)+suffix)
        }
        return result.toString()
    }
}

/** Captures all raw branches. Conservative layout/comment changes remain visible structural conflicts. */
class TemplateYamlSource5(sources:TemplateSources3,private val contracts:ImportContracts2) {
    val sources=TemplateSources3(sources.templateYaml,sources.exerciseYamls.toList())
    private val plan=compileTemplate2(this.sources.templateYaml,this.sources.exerciseYamls,contracts)
    private val template=YamlDocumentMap5(this.sources.templateYaml,"template_source",plan.templateId)
    private val exercises=this.sources.exerciseYamls.map {YamlDocumentMap5(it,"exercise_source",readYaml2(it).text2("familyId"))}.sortedBy {it.identity}
    private val documents=listOf(template)+exercises
    val fields:SourceFields
    init {
        require(documents.size in 2..16 && documents.sumOf {it.raw.toByteArray(Charsets.UTF_8).size.toLong()}<=8388608)
        require(exercises.map {it.identity}.distinct().size==exercises.size)
        val structure=buildJsonArray {for(doc in documents)add(buildJsonObject {
            put("kind",doc.sourceKind);put("identity",doc.identity);put("source",doc.structure())
        })}
        val entries=documents.flatMap {it.values.entries};require(entries.map {it.key}.distinct().size==entries.size)
        fields=SourceFields(plan.templateId,plan.revisionId,hashUtf8_2(structure.toString()),entries.associate {it.key to it.value})
    }
    fun renderCandidate(candidate:SourceFields):TemplateSources3 {
        require(candidate.templateId==fields.templateId && candidate.structureHash==fields.structureHash && candidate.values.keys==fields.values.keys)
        require(candidate.revisionId!=fields.revisionId)
        val result=TemplateSources3(template.render(candidate),exercises.map {it.render(candidate)})
        val checked=compileTemplate2(result.templateYaml,result.exerciseYamls,contracts)
        check(checked.templateId==candidate.templateId && checked.revisionId==candidate.revisionId)
        return result
    }
}

/** Produces a fully validated candidate only. Existing library review/CAS must still authorize adoption. */
class TemplateYamlReview5(base:TemplateSources3,phone:TemplateSources3,drive:TemplateSources3,contracts:ImportContracts2) {
    val base=TemplateYamlSource5(base,contracts)
    val phone=TemplateYamlSource5(phone,contracts)
    val drive=TemplateYamlSource5(drive,contracts)
    private val review=TemplateMergeReview(this.base.fields,this.phone.fields,this.drive.fields)
    val conflicts get()=review.conflicts
    val structuralConflict get()=review.structuralConflict
    fun resolve(newRevisionId:String,choices:Map<FieldKey,FieldChoice>):TemplateSources3 {
        val result=review.candidate(newRevisionId,choices) {phone.renderCandidate(it);true}
        return phone.renderCandidate(result)
    }
}
