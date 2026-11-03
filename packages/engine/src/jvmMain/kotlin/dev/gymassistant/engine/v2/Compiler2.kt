package dev.gymassistant.engine.v2

import kotlinx.serialization.json.*

data class ImportContracts2(val templates: Map<String,JsonObject>,val exercise: JsonObject)
internal fun JsonObject.text2(key:String)=(this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("Missing text field")
internal fun JsonObject.opt2(key:String)=(this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.long2(key:String)=this[key]?.jsonPrimitive?.content?.toBigDecimalOrNull()?.longValueExact() ?: error("Missing integer field")
internal fun JsonObject.list2(key:String)=this[key]?.jsonArray ?: error("Missing array field")
private fun label2(value:JsonElement?):String? {
    val names=value as? JsonObject ?: return null
    return (names["en"] ?: names.values.firstOrNull())?.jsonPrimitive?.content
}

/** Compile a validated, fixed timed source. Addition eligibility is explicit, never inferred. */
fun compileTemplate2(templateYaml:String,exerciseYamls:List<String>,contracts:ImportContracts2):Plan2 {
    try { return compileChecked2(templateYaml,exerciseYamls,contracts) }
    catch(_:Exception) { throw IllegalArgumentException("Template or exercise contract is invalid or unsupported") }
}
private fun compileChecked2(templateYaml:String,exerciseYamls:List<String>,contracts:ImportContracts2):Plan2 {
    require(exerciseYamls.size in 1..32 && (listOf(templateYaml)+exerciseYamls).sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }<=8388608)
    val root=readYaml2(templateYaml);val version=root.text2("schemaVersion")
    check(matchesSchema2(root,checkNotNull(contracts.templates[version])))
    val families=linkedMapOf<String,JsonObject>()
    for(yaml in exerciseYamls) {
        val family=readYaml2(yaml);check(matchesSchema2(family,contracts.exercise))
        check(families.put(family.text2("familyId"),family)==null)
        val variations=family.list2("variations").map { it.jsonObject.text2("variationId") }
        check(variations.size==variations.toSet().size)
    }
    val templateId=root.text2("templateId");val revisionId=root.text2("revisionId")
    val phases=mutableListOf<Phase2>();val policies=mutableListOf<AdditionPolicy2>();val identities=mutableSetOf<String>();var cursor=0L;var budget=65536L
    fun claim(id:String) { check(identities.add(id)) }
    fun append(phase:Phase2) {
        check(phases.size<MAX_PHASES_2);check(phase.endMs>phase.startMs)
        budget+=planJson2.encodeToString(Phase2.serializer(),phase).toByteArray(Charsets.UTF_8).size*3L+1024L;check(budget<=8388608)
        phases+=phase;cursor=phase.endMs
    }
    data class Definition(val set:JsonObject,val occurrence:JsonObject,val family:JsonObject,val variation:JsonObject,val resistance:Resistance2?)
    for(sectionNode in root.list2("sections")) {
        val section=sectionNode.jsonObject;claim(section.text2("sectionId"))
        for(blockNode in section.list2("blocks")) {
            val block=blockNode.jsonObject;val blockId=block.text2("blockId");claim(blockId)
            val sets=linkedMapOf<String,Definition>();val stepSets=mutableMapOf<String,String>();val repPhases=mutableMapOf<String,List<Phase2>>()
            for(occNode in block.list2("occurrences")) {
                val occ=occNode.jsonObject;claim(occ.text2("occurrenceId"));val family=checkNotNull(families[occ.text2("exerciseId")])
                for(setNode in occ.list2("sets")) {
                    val set=setNode.jsonObject;val sid=set.text2("setId");claim(sid)
                    val variation=family.list2("variations").map { it.jsonObject }.single { it.text2("variationId")==set.text2("variationId") }
                    check(set.opt2("side")!="alternating")
                    val defs=set.list2("phases").map { it.jsonObject }
                    check(defs.map { it.text2("phaseId") }.distinct().size==defs.size)
                    check(defs.all { it.text2("mode")=="timed" && (it.opt2("transition")==null || it.opt2("transition")=="immediate") })
                    check(set.long2("repetitions") in 1..MAX_PHASES_2.toLong())
                    val resistance=(set["resistance"] as? JsonObject)?.let { r ->
                        val weight=(r["weight"] as? JsonObject)?.let { Weight2(it["value"]!!.jsonPrimitive.double,it.text2("unit")) }
                        val bands=(r["bands"] as? JsonArray).orEmpty().map { b -> b.jsonObject.let { Band2(it.text2("equipmentId"),it.long2("count").also { n->check(n in 1..Int.MAX_VALUE) }.toInt(),it.text2("placement")) } }
                        Resistance2(weight,bands,r.opt2("notes"))
                    }
                    sets[sid]=Definition(set,occ,family,variation,resistance)
                }
            }
            val order=block.list2("order").map { it.jsonObject };val coverage=mutableSetOf<Pair<String,Long>>()
            for((index,step) in order.withIndex()) {
                val stepId=step.text2("stepId");claim(stepId)
                if(step.text2("kind")=="rest") {
                    val duration=safe2(step.long2("durationMs"));check(duration>0)
                    val role=step.opt2("role") ?: "unclassified";val resetFor=step.opt2("resetForStepId")
                    val prior=order.getOrNull(index-1)
                    val owner=if(role=="reset") {
                        check(resetFor!=null && prior?.opt2("kind")=="repetition" && prior.opt2("stepId")==resetFor)
                        checkNotNull(sets[prior.text2("setId")])
                    } else {check(resetFor==null);null}
                    val unit=if(owner==null) identity2("unit",templateId,revisionId,stepId) else checkNotNull(repPhases[resetFor]).first().unitId
                    append(Phase2(instanceId=identity2("phase",templateId,revisionId,stepId),ordinal=phases.size,unitId=unit,sourceStepId=stepId,blockId=blockId,
                        occurrenceId=owner?.occurrence?.opt2("occurrenceId"),setId=owner?.set?.opt2("setId"),side=owner?.set?.opt2("side"),
                        exerciseId=owner?.occurrence?.opt2("exerciseId"),variationId=owner?.set?.opt2("variationId"),repetitionIndex=if(owner!=null)prior!!.long2("repetitionIndex").toInt() else null,
                        exerciseName=owner?.let { label2(it.occurrence["namesOverride"]) ?: label2(it.family["names"]) },variationName=owner?.let { label2(it.variation["names"]) },resistance=owner?.resistance,
                        name=label2(step["label"]) ?: if(role=="reset")"Reset" else "Rest",role=role,startMs=cursor,endMs=plus2(cursor,duration),baseStartMs=cursor))
                } else {
                    val sid=step.text2("setId");val d=checkNotNull(sets[sid]);val rep=step.long2("repetitionIndex")
                    check(rep in 1..d.set.long2("repetitions") && coverage.add(sid to rep));stepSets[stepId]=sid
                    val unit=identity2("unit",templateId,revisionId,stepId)
                    val compiled=mutableListOf<Phase2>()
                    for(node in d.set.list2("phases")) {
                        val phase=node.jsonObject;val duration=safe2(phase.long2("durationMs"));check(duration>0)
                        val p=Phase2(instanceId=identity2("phase",templateId,revisionId,stepId,phase.text2("phaseId")),ordinal=phases.size,unitId=unit,
                            sourceStepId=stepId,blockId=blockId,occurrenceId=d.occurrence.text2("occurrenceId"),setId=sid,phaseId=phase.text2("phaseId"),
                            repetitionIndex=rep.toInt(),exerciseId=d.occurrence.text2("exerciseId"),variationId=d.set.text2("variationId"),side=d.set.opt2("side"),
                            exerciseName=label2(d.occurrence["namesOverride"]) ?: label2(d.family["names"]),variationName=label2(d.variation["names"]),
                            name=phase.text2("name"),role=phase.opt2("role") ?: "unclassified",startMs=cursor,endMs=plus2(cursor,duration),baseStartMs=cursor,resistance=d.resistance)
                        append(p);compiled+=p
                    }
                    repPhases[stepId]=compiled
                }
            }
            val expected=sets.flatMap { (id,d)->(1..d.set.long2("repetitions")).map { id to it } }.toSet();check(coverage==expected)
            for((sid,d) in sets) {
                val policy=d.set["inSessionRepetition"] as? JsonObject ?: continue
                check(version=="1.1.0" && policy.text2("mode")=="appendToCurrentSet")
                val reset=safe2(policy.long2("resetMs"))
                check(d.set.list2("phases").all { it.jsonObject.opt2("role")=="work" })
                val indices=order.indices.filter { order[it].opt2("kind")=="repetition" && order[it].opt2("setId")==sid }
                check(indices.map { order[it].long2("repetitionIndex") }==(1..d.set.long2("repetitions")).toList())
                for(i in 0 until indices.lastIndex) {
                    val middle=order.subList(indices[i]+1,indices[i+1])
                    if(reset==0L) check(middle.isEmpty()) else check(middle.size==1 && middle[0].opt2("role")=="reset" && middle[0].opt2("resetForStepId")==order[indices[i]].text2("stepId") && middle[0].long2("durationMs")==reset)
                }
                val last=order[indices.last()];val next=order.getOrNull(indices.last()+1)
                val existing=next?.opt2("role")=="reset" && next.opt2("resetForStepId")==last.text2("stepId")
                if(existing)check(next!!.long2("durationMs")==reset)
                val core=checkNotNull(repPhases[last.text2("stepId")]);val anchor=if(existing)phases.single { it.sourceStepId==next!!.text2("stepId") } else core.last()
                policies+=AdditionPolicy2(sid,blockId,d.occurrence.text2("occurrenceId"),anchor.instanceId,reset,existing,core)
            }
        }
    }
    check(phases.isNotEmpty())
    return Plan2(templateId,revisionId,label2(root["names"]) ?: "Training Template",cursor,cursor,phases.toList(),policies.toList()).also { requirePlanBudget2(it) }
}
