package com.appia.ai.agent

enum class AgentMode(val prefKey: String, val displayName: String, val description: String) {
    NATIVE_TOOLS(
        "native_tools",
        "Function Calling",
        "使用 API 原生的工具调用能力，稳定可靠（推荐）"
    ),
    PROMPT_JSON(
        "prompt_json",
        "Prompt JSON",
        "通过提示词约定 JSON 指令并手写解析，可观察底层原理，适合对比实验"
    );

    companion object {
        fun fromKey(key: String?): AgentMode = entries.firstOrNull { it.prefKey == key } ?: NATIVE_TOOLS
    }
}
