package com.appia.ai.agent

import com.appia.ai.llm.ChatMessage
import com.appia.ai.llm.LLMProvider
import com.appia.ai.llm.ModelConfig
import com.appia.ai.model.TaskPlan

class TaskPlanner {

    private val systemPrompt = """你是一个 Android 手机操作规划助手。用户会用自然语言描述想做的事情，你需要把它拆解成具体的操作步骤。

可用的操作类型：
- tap: 点击屏幕元素（需提供 target：元素的文字描述）
- input: 在输入框输入文字（需提供 text）
- back: 返回键
- home: 回到桌面
- scroll: 滑动屏幕（需提供 direction: up/down/left/right）
- wait: 等待页面加载（需提供 seconds: 等待秒数）

请返回 JSON 格式的操作计划，格式如下：
{"goal":"用户目标简述","steps":[{"action":"tap","target":"元素文字","description":"这一步在做什么"},{"action":"input","text":"要输入的内容","description":"说明"}]}

规则：
1. 只返回 JSON，不要包含其他文字
2. target 用屏幕上可见的文字描述（如"搜索"、"发送"、"妈妈"）
3. 步骤要具体可执行，不要包含用户无法理解的操作
4. 如果是发消息类操作，最后一步通常是 tap 发送按钮
5. 如果需要打开某个 App，第一步 tap App 名称或图标"""

    suspend fun plan(
        instruction: String,
        provider: LLMProvider,
        config: ModelConfig
    ): TaskPlan? {
        val messages = listOf(
            ChatMessage.system(systemPrompt),
            ChatMessage.user(instruction)
        )

        val fullResponse = StringBuilder()
        provider.chat(messages, config).collect { chunk ->
            if (chunk.isEmpty()) return@collect
            fullResponse.append(chunk)
        }

        return PlanJsonParser.parse(fullResponse.toString())
    }
}
