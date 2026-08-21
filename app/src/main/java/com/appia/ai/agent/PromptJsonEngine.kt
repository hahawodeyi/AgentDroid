package com.appia.ai.agent

import android.content.Context
import com.appia.ai.llm.ChatMessage
import com.appia.ai.llm.LLMProvider
import com.appia.ai.llm.ModelConfig
import com.appia.ai.memory.MemoryStore
import com.appia.ai.model.TraceStep
import com.appia.ai.tool.ToolRegistry
import com.appia.ai.tool.ToolSettingsStore

class PromptJsonEngine(
    private val provider: LLMProvider,
    private val config: ModelConfig,
    private val toolRegistry: ToolRegistry,
    private val toolStore: ToolSettingsStore,
    private val context: Context,
    private val maxIterations: Int = 8
) {
    class AgentException(message: String) : Exception(message)

    private val executor = ToolCallExecutor(toolRegistry, toolStore, context)

    suspend fun run(
        history: List<ChatMessage>,
        onTextDelta: (String) -> Unit,
        onTrace: (TraceStep) -> Unit
    ): String {
        val messages = mutableListOf(ChatMessage.system(buildSystemPrompt()))
        if (history.size > MAX_HISTORY_MESSAGES) {
            messages.addAll(history.takeLast(MAX_HISTORY_MESSAGES))
        } else {
            messages.addAll(history)
        }

        repeat(maxIterations) { iteration ->
            val fullText = StringBuilder()

            provider.chat(messages, config).collect { chunk ->
                if (chunk.startsWith("[ERROR]")) {
                    throw AgentException(chunk.removePrefix("[ERROR] ").trim())
                }
                fullText.append(chunk)
            }

            val parseResult = PromptJsonParser.parse(fullText.toString())

            when (parseResult) {
                is PromptJsonParser.Result.Final -> {
                    if (parseResult.text.isNotEmpty()) onTextDelta(parseResult.text)
                    return parseResult.text
                }
                is PromptJsonParser.Result.Actions -> {
                    messages.add(ChatMessage.assistant(fullText.toString()))
                    parseResult.calls.forEachIndexed { index, call ->
                        val (step, resultText) = executor.execute(call)
                        onTrace(step)
                        messages.add(
                            ChatMessage.user("[工具结果 ${index + 1}/${parseResult.calls.size}] ${call.name}: $resultText")
                        )
                    }
                }
                is PromptJsonParser.Result.Unparseable -> {
                    if (iteration == maxIterations - 1 || fullText.isBlank()) {
                        onTrace(TraceStep("解析失败", "模型未按约定格式输出", success = false))
                        if (fullText.isNotBlank()) onTextDelta(fullText.toString())
                        return fullText.toString()
                    }
                    onTrace(TraceStep("格式纠错", "模型输出无法解析，要求重新输出", success = false))
                    messages.add(ChatMessage.assistant(fullText.toString()))
                    messages.add(ChatMessage.user(FORMAT_CORRECTION_PROMPT))
                }
            }
        }

        onTrace(TraceStep("已达最大执行轮数", "为避免无限循环已停止", success = false))
        return "任务步骤过多，已停止。"
    }

    private fun buildSystemPrompt(): String {
        val enabledTools = toolRegistry.enabled(toolStore)
        val toolDescriptions = enabledTools.joinToString("\n") { tool ->
            "- ${tool.id}: ${tool.description} 参数: ${tool.parametersSchema}"
        }

        return """你是 AgentDroid，运行在用户 Android 手机上的智能助手。你可以通过输出 JSON 指令来调用工具。

可用工具：
$toolDescriptions

输出格式（二选一，且只能输出其中一种，不要输出任何其他内容）：

1. 调用工具（可一次调用多个）：
{"actions":[{"tool":"工具id","args":{"参数名":"参数值"}}]}

2. 最终回复（当任务完成或只是普通聊天时）：
{"final":"要展示给用户的回复文本"}

规则：
1. 只输出 JSON，不要输出 markdown 代码块标记或任何解释文字
2. 调用工具后，用户消息会带着 [工具结果] 返回给你；工具结果是唯一事实来源，必须根据最新结果决定继续调用工具还是输出 final
3. 如果工具返回"需要权限"或"已被关闭"，用 final 转告用户如何开启（设置 → 工具权限管理）
4. 普通聊天问题直接输出 final，不要强行调用工具
5. 当用户告诉你值得记住的个人信息时，主动调用 save_memory 保存；涉及用户偏好的问题时，先用 recall_memory 查看""" + memorySection()
    }

    private fun memorySection(): String {
        val memories = MemoryStore(context).formatForPrompt()
        if (memories.isEmpty()) return ""
        return "\n\n以下是你记住的关于用户的信息：\n" + memories
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 20

        private const val FORMAT_CORRECTION_PROMPT =
            "你的上一条回复不是合法 JSON 或未包含 actions/final 字段。请严格按格式重新输出，只输出 JSON。"
    }
}
