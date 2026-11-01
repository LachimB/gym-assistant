package dev.gymassistant.engine

import kotlinx.serialization.Serializable
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.yamlList
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar

private fun YamlNode.map(): YamlMap = yamlMap

private fun YamlNode.list(): YamlList = yamlList

private fun YamlNode.text(): String = yamlScalar.content

private fun YamlMap.child(key: String): YamlNode =
    get(key) ?: error("missing YAML key '$key'")

private fun YamlMap.optional(key: String): YamlNode? = get(key)

private fun YamlMap.long(key: String): Long = child(key).text().toLong()

private data class SetDef(
    val setId: String,
    val variationId: String,
    val repetitions: Int,
    val exerciseId: String,
    val resistanceLabel: String,
    val phases: List<PhaseDef>,
)

private data class PhaseDef(
    val phaseId: String,
    val name: String,
    val durationMs: Long,
)

fun compileTemplate(templateYaml: String, exerciseYamls: List<String>): CompiledPlan {
    val variationsByExercise = exerciseYamls.associate { yaml ->
        val root = Yaml.default.parseToYamlNode(yaml).map()
        val familyId = root.child("familyId").text()
        val variations = root.child("variations").list().items.map { item ->
            item.map().child("variationId").text()
        }.toSet()
        familyId to variations
    }
    val root = Yaml.default.parseToYamlNode(templateYaml).map()
    val phases = mutableListOf<CompiledPhase>()
    var cursor = 0L
    val seenIds = mutableSetOf<String>()
    fun claim(id: String) {
        check(seenIds.add(id)) { "duplicate structural ID $id" }
    }
    for (sectionNode in root.child("sections").list().items) {
        val section = sectionNode.map()
        claim(section.child("sectionId").text())
        for (blockNode in section.child("blocks").list().items) {
            val block = blockNode.map()
            claim(block.child("blockId").text())
            val sets = linkedMapOf<String, SetDef>()
            for (occurrenceNode in block.child("occurrences").list().items) {
                val occurrence = occurrenceNode.map()
                val occurrenceId = occurrence.child("occurrenceId").text()
                claim(occurrenceId)
                val exerciseId = occurrence.child("exerciseId").text()
                val knownVariations = variationsByExercise[exerciseId]
                    ?: error("unknown exercise $exerciseId")
                for (setNode in occurrence.child("sets").list().items) {
                    val set = setNode.map()
                    val setId = set.child("setId").text()
                    check(setId !in sets) { "duplicate set ID $setId" }
                    claim(setId)
                    val variationId = set.child("variationId").text()
                    check(variationId in knownVariations) { "unknown variation $variationId" }
                    val phaseDefs = set.child("phases").list().items.map { phaseNode ->
                        val phase = phaseNode.map()
                        check(phase.child("mode").text() == "timed") { "M0 compiler only accepts timed phases" }
                        PhaseDef(
                            phaseId = phase.child("phaseId").text(),
                            name = phase.child("name").text(),
                            durationMs = phase.long("durationMs"),
                        )
                    }
                    check(phaseDefs.map { it.phaseId }.toSet().size == phaseDefs.size) { "duplicate phase id" }
                    phaseDefs.forEach { check(it.durationMs > 0) { "nonpositive duration" } }
                    sets[setId] = SetDef(
                        setId = setId,
                        variationId = variationId,
                        repetitions = set.child("repetitions").text().toInt(),
                        exerciseId = exerciseId,
                        resistanceLabel = resistanceLabel(set.optional("resistance")),
                        phases = phaseDefs,
                    )
                }
            }
            val coverage = mutableListOf<Pair<String, Int>>()
            for (stepNode in block.child("order").list().items) {
                val step = stepNode.map()
                val stepId = step.child("stepId").text()
                claim(stepId)
                if (step.child("kind").text() == "rest") {
                    val duration = step.long("durationMs")
                    check(duration > 0) { "nonpositive rest" }
                    phases += CompiledPhase(
                        index = phases.size,
                        stepId = stepId,
                        name = "rest",
                        startMs = cursor,
                        endMs = cursor + duration,
                    )
                    cursor += duration
                } else {
                    val setId = step.child("setId").text()
                    val setDef = sets[setId] ?: error("unknown set: $setId")
                    val repetition = step.child("repetitionIndex").text().toInt()
                    check(repetition in 1..setDef.repetitions) { "repetition out of range" }
                    coverage += setId to repetition
                    for (phase in setDef.phases) {
                        phases += CompiledPhase(
                            index = phases.size,
                            stepId = stepId,
                            name = phase.name,
                            startMs = cursor,
                            endMs = cursor + phase.durationMs,
                            phaseId = phase.phaseId,
                            setId = setId,
                            repetitionIndex = repetition,
                            exerciseId = setDef.exerciseId,
                            variationId = setDef.variationId,
                            resistanceLabel = setDef.resistanceLabel,
                        )
                        cursor += phase.durationMs
                    }
                }
            }
            val expected = sets.flatMap { (setId, setDef) ->
                (1..setDef.repetitions).map { setId to it }
            }.toSet()
            check(coverage.toSet() == expected) { "missing or extra repetitions" }
            check(coverage.size == coverage.toSet().size) { "duplicate planned repetitions" }
        }
    }
    return CompiledPlan(
        schemaVersion = root.child("schemaVersion").text(),
        templateId = root.child("templateId").text(),
        revisionId = root.child("revisionId").text(),
        expectedDurationMs = cursor,
        phases = phases,
    )
}

private fun resistanceLabel(resistance: YamlNode?): String {
    if (resistance == null) return ""
    val bands = resistance.map().optional("bands")?.list()?.items.orEmpty()
    return bands.joinToString(", ") { bandNode ->
        val band = bandNode.map()
        val equipment = band.child("equipmentId").text()
        val count = band.child("count").text()
        val placement = band.child("placement").text()
        "$equipment x$count $placement"
    }
}

@Serializable
data class TimelinePhase(
    val stepId: String,
    val startMs: Long,
    val endMs: Long,
    val name: String,
    val phaseId: String? = null,
    val setId: String? = null,
    val repetitionIndex: Int? = null,
)

fun timelineProjection(plan: CompiledPlan): List<TimelinePhase> =
    plan.phases.map { phase ->
        TimelinePhase(
            stepId = phase.stepId,
            startMs = phase.startMs,
            endMs = phase.endMs,
            name = phase.name,
            phaseId = phase.phaseId,
            setId = phase.setId,
            repetitionIndex = phase.repetitionIndex,
        )
    }
