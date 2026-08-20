package com.appia.ai.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

interface LLMProvider {
    val id: String
    val displayName: String

    suspend fun chat(messages: List<ChatMessage>, config: ModelConfig): Flow<String>

    suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<JsonObject>,
        config: ModelConfig
    ): Flow<ChatEvent> = flow {
        emit(ChatEvent.Error("当前模型 Provider 暂不支持工具调用"))
    }

    suspend fun chatStream(
        messages: List<ChatMessage>,
        config: ModelConfig,
        onChunk: (String) -> Unit
    ) {
        chat(messages, config).collect { onChunk(it) }
    }
}
