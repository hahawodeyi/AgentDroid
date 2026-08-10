package com.appia.ai.agent

import com.appia.ai.llm.ChatMessage
import com.appia.ai.llm.LLMProvider
import com.appia.ai.llm.ModelConfig
import com.appia.ai.model.TaskPlan

class Replanner {

    private val systemPrompt = """你是一个 Android 手机操作规划助手。之前的操作计划部分失败了，你需要根据剩余目标和当前状态重新规划。

可用的操作类型：
- tap: 点击（需 target）
- input: 输入文字（需 text）
- back: 返回
- home: 桌面
- scroll: 滑动（需 direction: up/down/left/right）
- wait: 等待（需 seconds）
- long_press: 长按（需 target, 可选 duration）
- swipe: 滑动手势（需 startX/startY/endX/endY）
- launch_app: 打开应用（需 packageName）

返回 JSON 格式：
{"goal":"目标","steps":[{"action":"tap","target":"文字","description":"说明"}]}

只返回 JSON，不要其他文字。"""

    suspend fun replan(
        originalGoal: String,
        failedStep: String,
        remainingSteps: String,
        provider: LLMProvider,
        config: ModelConfig
    ): TaskPlan? {
        val userMsg = "原始目标: $originalGoal\n失败的步骤: $failedStep\n剩余计划: $remainingSteps\n\n请重新规划剩余步骤。"

        val messages = listOf(
            ChatMessage.system(systemPrompt),
            ChatMessage.user(userMsg)
        )

        val fullResponse = StringBuilder()
        provider.chat(messages, config).collect { chunk ->
            if (chunk.isEmpty()) return@collect
            fullResponse.append(chunk)
        }

        return PlanJsonParser.parse(fullResponse.toString())
    }
}
