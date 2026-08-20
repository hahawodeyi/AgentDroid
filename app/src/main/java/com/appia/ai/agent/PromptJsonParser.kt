package com.appia.ai.agent

import com.appia.ai.llm.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object PromptJsonParser {

    sealed class Result {
        data class Final(val text: String) : Result()
        data class Actions(val calls: List<ToolCall>) : Result()
        data object Unparseable : Result()
    }

    fun parse(text: String): Result {
        val cleaned = stripCodeFence(text.trim())
        if (cleaned.isEmpty()) return Result.Unparseable

        val obj = try {
            Json.parseToJsonElement(cleaned).jsonObject
        } catch (_: Exception) {
            return Result.Unparseable
        }

        obj["final"]?.jsonPrimitive?.let { return Result.Final(it.content) }

        val actions = obj["actions"] ?: return Result.Unparseable
        val calls = try {
            actions.jsonArray.mapIndexedNotNull { index, element ->
                val action = element.jsonObject
                val toolName = action["tool"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
                val args = action["args"]?.toString() ?: "{}"
                ToolCall(id = "prompt_call_$index", name = toolName, argumentsJson = args)
            }
        } catch (_: Exception) {
            return Result.Unparseable
        }

        return if (calls.isEmpty()) Result.Unparseable else Result.Actions(calls)
    }

    private fun stripCodeFence(text: String): String {
        val fence = Regex("^```(?:json)?\\s*\\n([\\s\\S]*?)\\n?```$")
        return fence.find(text)?.groupValues?.get(1)?.trim() ?: text
    }
}
