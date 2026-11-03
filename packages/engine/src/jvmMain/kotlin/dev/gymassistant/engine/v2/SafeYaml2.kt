package dev.gymassistant.engine.v2

import it.krzeminski.snakeyaml.engine.kmp.api.Load
import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.api.lowlevel.Parse
import it.krzeminski.snakeyaml.engine.kmp.events.*
import it.krzeminski.snakeyaml.engine.kmp.schema.JsonSchema
import kotlinx.serialization.json.*

/** Bounded JSON-compatible YAML. Exceptions never expose source text. */
fun readYaml2(source: String): JsonObject {
    require(source.toByteArray(Charsets.UTF_8).size <= 1048576) { "YAML exceeds limit" }
    try {
        val settings=LoadSettings.builder().setSchema(JsonSchema()).setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false).setMaxAliasesForCollections(0).setCodePointLimit(1048576).build()
        var depth=0;var documents=0;var nodes=0
        for(event in Parse(settings).parse(source)) {
            check(++nodes<=100000)
            check(event !is NodeEvent || event.anchor==null)
            check(event !is ScalarEvent || event.tag==null)
            if(event is ScalarEvent && event.plain) {
                val ambiguous=event.value.lowercase() in setOf(".nan",".inf","+.inf","-.inf","yes","no","on","off") ||
                    Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}([Tt ].*)?").matches(event.value) ||
                    Regex("[+-]?0[xobXOB].*").matches(event.value) || Regex("[+-]?[0-9][0-9_]*_[0-9_]+").matches(event.value)
                check(!ambiguous)
            }
            check(event !is CollectionStartEvent || event.tag==null)
            when(event.eventId) {
                Event.ID.Alias -> error("Aliases unsupported")
                Event.ID.DocumentStart -> check(++documents==1)
                Event.ID.MappingStart,Event.ID.SequenceStart -> check(++depth<=64)
                Event.ID.MappingEnd,Event.ID.SequenceEnd -> depth--
                else -> Unit
            }
        }
        fun json(value: Any?,level: Int=0): JsonElement {
            check(level<=64)
            return when(value) {
                null -> JsonNull
                is String -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is Number -> {check(value.toDouble().isFinite() && kotlin.math.abs(value.toDouble())<=MAX_SAFE_2);Json.parseToJsonElement(value.toString())}
                is Map<*,*> -> JsonObject(value.entries.associate { (key,v) -> check(key is String);key to json(v,level+1) })
                is List<*> -> JsonArray(value.map { json(it,level+1) })
                else -> error("Non-JSON YAML value")
            }
        }
        return json(Load(settings).loadOne(source)) as? JsonObject ?: error("Object required")
    } catch (_: Exception) { throw IllegalArgumentException("YAML is invalid, unsafe or unsupported") }
}

/** Implements the assertion keywords used by our checked-in import schemas. */
fun matchesSchema2(value: JsonElement,schema: JsonObject): Boolean {
    val annotations=setOf("\$schema","\$id","title","description","default","examples")
    val supported=annotations+setOf("type","const","enum","properties","required","additionalProperties","propertyNames",
        "items","minItems","maxItems","uniqueItems","minProperties","maxProperties","minLength","maxLength","pattern",
        "minimum","maximum","oneOf","anyOf","allOf","if","then","else")
    check(schema.keys.all { it in supported }) { "Unsupported trusted schema keyword" }
    fun num(el: JsonElement?)=(el as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    fun int(key: String)=schema[key]?.jsonPrimitive?.int
    fun fits(s: JsonElement)=matchesSchema2(value,s.jsonObject)
    val type=schema["type"]?.jsonPrimitive?.content
    if(type!=null && !when(type) {
        "object" -> value is JsonObject;"array" -> value is JsonArray
        "string" -> value is JsonPrimitive && value.isString
        "integer" -> num(value)?.let { it.isFinite() && it%1.0==0.0 }==true
        "number" -> num(value)?.isFinite()==true
        "boolean" -> value is JsonPrimitive && !value.isString && value.booleanOrNull!=null
        "null" -> value==JsonNull;else -> error("Unsupported schema type")
    }) return false
    if("const" in schema && value!=schema["const"])return false
    if(schema["enum"]?.jsonArray?.let { value !in it }==true)return false
    if(schema["allOf"]?.jsonArray?.any { !fits(it) }==true)return false
    if(schema["anyOf"]?.jsonArray?.none { fits(it) }==true)return false
    if(schema["oneOf"]?.jsonArray?.count { fits(it) }?.let { it!=1 }==true)return false
    schema["if"]?.let { condition ->
        val branch=if(fits(condition))schema["then"] else schema["else"]
        if(branch!=null && !fits(branch))return false
    }
    if(value is JsonObject) {
        if(int("minProperties")?.let { value.size<it }==true || int("maxProperties")?.let { value.size>it }==true)return false
        if(schema["required"]?.jsonArray?.any { it.jsonPrimitive.content !in value }==true)return false
        val props=schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
        for((key,v) in value) {
            if(schema["propertyNames"]?.let { !matchesSchema2(JsonPrimitive(key),it.jsonObject) }==true)return false
            val spec=props[key] ?: schema["additionalProperties"]
            if(spec==JsonPrimitive(false))return false
            if(spec is JsonObject && !matchesSchema2(v,spec))return false
        }
    }
    if(value is JsonArray) {
        if(int("minItems")?.let { value.size<it }==true || int("maxItems")?.let { value.size>it }==true)return false
        if(schema["uniqueItems"]==JsonPrimitive(true) && value.distinct().size!=value.size)return false
        schema["items"]?.let { spec -> if(value.any { !matchesSchema2(it,spec.jsonObject) })return false }
    }
    if(value is JsonPrimitive && value.isString) {
        val n=value.content.codePointCount(0,value.content.length)
        if(int("minLength")?.let { n<it }==true || int("maxLength")?.let { n>it }==true)return false
        if(schema["pattern"]?.let { !Regex(it.jsonPrimitive.content).containsMatchIn(value.content) }==true)return false
    }
    val n=num(value)
    if(n!=null && (num(schema["minimum"])?.let { n<it }==true || num(schema["maximum"])?.let { n>it }==true))return false
    return true
}
