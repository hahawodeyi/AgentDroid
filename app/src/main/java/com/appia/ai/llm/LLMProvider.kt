package com.appia.ai.llm

import kotlinx.coroutines.flow.Flow

interface LLMProvider {
    val id: String
    val displayName: String

    suspend fun chat(messages: List<ChatMessage>, config: ModelConfig): Flow<String>

    suspend fun chatStream(
        messages: List<ChatMessage>,
        config: ModelConfig,
        onChunk: (String) -> Unit
    ) {
        chat(messages, config).collect { onChunk(it) }
    }
}
