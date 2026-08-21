package com.appia.ai.agent

import android.content.Context
import com.appia.ai.llm.ChatEvent
import com.appia.ai.llm.ChatMessage
import com.appia.ai.llm.LLMProvider
import com.appia.ai.llm.ModelConfig
import com.appia.ai.llm.ToolCall
import com.appia.ai.memory.MemoryStore
import com.appia.ai.model.TraceStep
import com.appia.ai.tool.ToolRegistry
import com.appia.ai.tool.ToolSettingsStore
import com.appia.ai.tool.toFunctionSpec

class AgentEngine(
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
        messages.addAll(trimHistory(history))

        val enabledTools = toolRegistry.enabled(toolStore)
        val toolSpecs = enabledTools.map { it.toFunctionSpec() }
        val finalText = StringBuilder()

        repeat(maxIterations) {
            val turnText = StringBuilder()
            var pendingCalls: List<ToolCall> = emptyList()

            provider.chatWithTools(messages, toolSpecs, config).collect { event ->
                when (event) {
                    is ChatEvent.TextDelta -> {
                        turnText.append(event.text)
                        onTextDelta(event.text)
                    }
                    is ChatEvent.ToolCallsReady -> pendingCalls = event.calls
                    is ChatEvent.Error -> throw AgentException(event.message)
                }
            }

            if (pendingCalls.isEmpty()) {
                finalText.append(turnText)
                return finalText.toString()
            }

            messages.add(ChatMessage.assistantWithToolCalls(pendingCalls, turnText.toString()))

            pendingCalls.forEach { call ->
                val (step, resultText) = executor.execute(call)
                onTrace(step)
                messages.add(ChatMessage.tool(call.id, resultText))
            }
        }

        onTrace(TraceStep("已达最大执行轮数", "为避免无限循环已停止", success = false))
        return finalText.toString().ifEmpty { "任务步骤过多，已停止。" }
    }

    private fun buildSystemPrompt(): String {
        val memories = MemoryStore(context).formatForPrompt()
        if (memories.isEmpty()) return SYSTEM_PROMPT
        return SYSTEM_PROMPT + "\n\n以下是你记住的关于用户的信息：\n" + memories
    }

    internal fun trimHistory(history: List<ChatMessage>): List<ChatMessage> {
        if (history.size <= MAX_HISTORY_MESSAGES) return history
        return history.takeLast(MAX_HISTORY_MESSAGES)
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 20

        private val SYSTEM_PROMPT = """你是 AgentDroid，运行在用户 Android 手机上的智能助手。你可以使用提供的工具来完成用户的请求。

规则：
1. 当用户的请求可以通过工具完成时，调用相应工具，不要凭空编造执行结果
2. 工具执行后，工具返回结果是唯一事实来源；必须严格根据最新工具结果用简洁的中文告知用户，不要沿用之前轮次的错误结论
3. 如果工具返回"需要权限"或"已被关闭"的信息，直接转告用户如何开启（设置 → 工具权限管理）
4. 普通聊天问题直接回答，不要强行使用工具
5. 一次回复中可以调用多个工具；如果任务需要多步，先调用当前能确定的工具，等结果返回后再继续
6. 当用户告诉你值得记住的个人信息（偏好、习惯、常用设置等），主动调用 save_memory 保存；涉及用户偏好的问题时，先用 recall_memory 查看是否已有相关记忆
7. 操作用户屏幕时（read_screen / screen_action），每一步都基于最近一次 read_screen 的结果行动；涉及支付、发送消息、删除内容等不可逆操作前，必须先用文字向用户确认，得到同意后才执行"""
    }
}
