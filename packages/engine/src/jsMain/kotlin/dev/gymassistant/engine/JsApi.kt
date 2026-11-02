package dev.gymassistant.engine

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { encodeDefaults = true; explicitNulls = false }

@Serializable
private data class CommandDto(val op: String, val ms: Long = 0)

@OptIn(ExperimentalJsExport::class)
@JsExport
fun compileTimelineJson(templateYaml: String, exerciseYamls: Array<String>): String {
    val plan = compileTemplate(templateYaml, exerciseYamls.toList())
    return json.encodeToString(timelineProjection(plan))
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun runCommandsJson(templateYaml: String, exerciseYamls: Array<String>, commandsJson: String): String {
    val plan = compileTemplate(templateYaml, exerciseYamls.toList())
    val commands = json.decodeFromString<List<CommandDto>>(commandsJson).map { dto ->
        when (dto.op) {
            "start" -> EngineCommand.Start
            "pause" -> EngineCommand.Pause
            "resume" -> EngineCommand.Resume
            "advance" -> EngineCommand.Advance(dto.ms)
            "stepForward" -> EngineCommand.StepForward
            "stepBackward" -> EngineCommand.StepBackward
            "noteUnobserved" -> EngineCommand.NoteUnobserved(dto.ms)
            "acknowledge" -> EngineCommand.Acknowledge
            "endEarly" -> EngineCommand.EndEarly
            else -> error("unknown command ${dto.op}")
        }
    }
    return json.encodeToString(snapshot(plan, reduceAll(plan, commands)))
}
