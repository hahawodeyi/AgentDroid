package com.appia.ai.agent

import com.appia.ai.model.AgentAction
import com.appia.ai.model.Direction
import com.appia.ai.model.ExecutionStep
import com.appia.ai.model.TaskPlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class JsonStep(
    val action: String = "",
    val target: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val seconds: Float? = null,
    val description: String = ""
)

@Serializable
private data class JsonPlan(
    val goal: String = "",
    val steps: List<JsonStep> = emptyList()
)

object PlanJsonParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawJson: String): TaskPlan? {
        return try {
            val cleaned = cleanJson(rawJson)
            val plan = json.decodeFromString<JsonPlan>(cleaned)
            val steps = plan.steps.mapIndexedNotNull { _, s ->
                parseStep(s)
            }
            if (steps.isEmpty()) return null
            TaskPlan(goal = plan.goal, steps = steps)
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanJson(raw: String): String {
        var result = raw.trim()
        // Strip markdown code fences
        if (result.startsWith("```")) {
            result = result.removePrefix("```json").removePrefix("```").trim()
            result = result.removeSuffix("```").trim()
        }
        // Extract JSON object if surrounded by other text
        val start = result.indexOf("{")
        val end = result.lastIndexOf("}")
        if (start >= 0 && end > start) {
            result = result.substring(start, end + 1)
        }
        return result
    }

    private fun parseStep(s: JsonStep): ExecutionStep? {
        val action = when (s.action.lowercase().trim()) {
            "tap", "click" -> AgentAction.Tap(0, 0)
            "input", "type" -> {
                val text = s.text ?: return null
                AgentAction.Input(text)
            }
            "back" -> AgentAction.Back
            "home" -> AgentAction.Home
            "scroll" -> {
                val dir = parseDirection(s.direction) ?: Direction.DOWN
                AgentAction.Scroll(dir)
            }
            "wait" -> AgentAction.Wait(s.seconds ?: 1.0f)
            else -> return null
        }
        return ExecutionStep(
            action = action,
            description = s.description,
            target = s.target,
        )
    }

    private fun parseDirection(dir: String?): Direction? {
        return when (dir?.lowercase()?.trim()) {
            "up" -> Direction.UP
            "down" -> Direction.DOWN
            "left" -> Direction.LEFT
            "right" -> Direction.RIGHT
            else -> null
        }
    }
}
