package com.appia.ai.llm

object ModelRegistry {
    private val providers = mapOf(
        "openai_compatible" to OpenAICompatibleProvider(),
        "anthropic" to AnthropicProvider()
    )

    private val presets = listOf(
        ModelConfig("openai", "OpenAI", "https://api.openai.com/v1", "", "gpt-4o-mini", isAnthropic = false),
        ModelConfig("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "", "deepseek-chat", isAnthropic = false),
        ModelConfig("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "", "qwen-plus", isAnthropic = false),
        ModelConfig("moonshot", "Moonshot (Kimi)", "https://api.moonshot.cn/v1", "", "moonshot-v1-8k", isAnthropic = false),
        ModelConfig("ollama", "Ollama (Local)", "http://10.0.2.2:11434/v1", "", "qwen2.5:7b", isAnthropic = false),
        ModelConfig("anthropic", "Anthropic (Claude)", "https://api.anthropic.com/v1", "", "claude-sonnet-4-20250514", isAnthropic = true)
    )

    fun get(config: ModelConfig): LLMProvider {
        return if (config.isAnthropic) providers["anthropic"]!! else providers["openai_compatible"]!!
    }

    fun presets(): List<ModelConfig> = presets.toList()
}
