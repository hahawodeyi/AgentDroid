package com.appia.ai.llm

import com.appia.ai.model.TraceStep
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val trace: List<TraceStep>? = null
) {
    companion object {
        fun system(text: String) = ChatMessage("system", text)
        fun user(text: String) = ChatMessage("user", text)
        fun assistant(text: String) = ChatMessage("assistant", text)
        fun assistantWithToolCalls(calls: List<ToolCall>, text: String = "") =
            ChatMessage("assistant", text, toolCalls = calls)
        fun tool(toolCallId: String, content: String) =
            ChatMessage("tool", content, toolCallId = toolCallId)
    }
}
