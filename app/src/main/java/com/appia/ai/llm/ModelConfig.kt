package com.appia.ai.llm

import kotlinx.serialization.Serializable

@Serializable
data class ModelConfig(
    val providerId: String = "openai",
    val displayName: String = "OpenAI",
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val isAnthropic: Boolean = false,
    val supportsVision: Boolean = false
) {
    val isValid: Boolean
        get() = apiKey.isNotBlank() && model.isNotBlank()
}

@Serializable
data class ModelConfigList(
    val configs: MutableList<ModelConfig> = mutableListOf()
)
