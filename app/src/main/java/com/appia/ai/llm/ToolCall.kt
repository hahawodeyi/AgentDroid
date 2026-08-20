package com.appia.ai.llm

import kotlinx.serialization.Serializable

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

sealed class ChatEvent {
    data class TextDelta(val text: String) : ChatEvent()
    data class ToolCallsReady(val calls: List<ToolCall>) : ChatEvent()
    data class Error(val message: String) : ChatEvent()
}
