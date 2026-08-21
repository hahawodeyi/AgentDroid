package com.appia.ai.agent

import android.content.Context
import com.appia.ai.llm.ChatEvent
import com.appia.ai.llm.ChatMessage
import com.appia.ai.llm.LLMProvider
import com.appia.ai.llm.ModelConfig
import com.appia.ai.llm.ToolCall
import com.appia.ai.model.TraceStep
import com.appia.ai.tool.Tool
import com.appia.ai.tool.ToolPermission
import com.appia.ai.tool.ToolRegistry
import com.appia.ai.tool.ToolResult
import com.appia.ai.tool.ToolSettingsStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeProvider(private val script: List<List<ChatEvent>>) : LLMProvider {
    override val id = "fake"
    override val displayName = "Fake"
    var callCount = 0
        private set
    val receivedMessages = mutableListOf<List<ChatMessage>>()

    override suspend fun chat(messages: List<ChatMessage>, config: ModelConfig): Flow<String> = flow {}

    override suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<JsonObject>,
        config: ModelConfig
    ): Flow<ChatEvent> = flow {
        receivedMessages.add(messages.toList())
        val events = script.getOrElse(callCount) { listOf(ChatEvent.TextDelta("done")) }
        callCount++
        events.forEach { emit(it) }
    }
}

private class RecordingTool(
    override val id: String = "fake_tool",
    override val displayName: String = "假工具",
    override val permission: ToolPermission? = null,
    private val result: ToolResult = ToolResult.Success("ok")
) : Tool {
    override val description = "a fake tool"
    override val parametersSchema: JsonObject = buildJsonObject { put("type", "object") }
    var executedWith: JsonObject? = null
        private set

    override suspend fun execute(args: JsonObject, context: Context): ToolResult {
        executedWith = args
        return result
    }
}

class AgentEngineTest {

    private val config = ModelConfig()
    private val context: Context = mockk(relaxed = true)

    private fun storeWith(allEnabled: Boolean): ToolSettingsStore =
        mockk { every { isEnabled(any()) } returns allEnabled }

    private fun engine(
        provider: FakeProvider,
        tools: List<Tool>,
        store: ToolSettingsStore = storeWith(true),
        maxIterations: Int = 8
    ) = AgentEngine(provider, config, ToolRegistry(tools), store, context, maxIterations)

    @Test
    fun `returns text directly when model calls no tools`() = runTest {
        val provider = FakeProvider(listOf(listOf(ChatEvent.TextDelta("你好"))))
        val result = engine(provider, emptyList()).run(emptyList(), {}, {})
        assertEquals("你好", result)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `executes tool call and feeds result back to the model`() = runTest {
        val tool = RecordingTool()
        val provider = FakeProvider(listOf(
            listOf(ChatEvent.ToolCallsReady(listOf(ToolCall("c1", "fake_tool", "{\"x\":1}")))),
            listOf(ChatEvent.TextDelta("完成了"))
        ))
        val traces = mutableListOf<TraceStep>()

        val result = engine(provider, listOf(tool)).run(emptyList(), {}, traces::add)

        assertEquals("完成了", result)
        assertTrue(tool.executedWith != null)
        assertTrue(traces.any { it.title.contains("假工具") && it.success })

        val secondCallMessages = provider.receivedMessages[1]
        val assistantMsg = secondCallMessages.first { it.toolCalls != null }
        assertEquals("c1", assistantMsg.toolCalls!!.first().id)
        val toolMsg = secondCallMessages.first { it.role == "tool" }
        assertEquals("c1", toolMsg.toolCallId)
        assertEquals("ok", toolMsg.content)
    }

    @Test
    fun `text emitted before tool call is not kept as final answer`() = runTest {
        val tool = RecordingTool()
        val provider = FakeProvider(listOf(
            listOf(
                ChatEvent.TextDelta("发送消息失败"),
                ChatEvent.ToolCallsReady(listOf(ToolCall("c1", "fake_tool", "{}")))
            ),
            listOf(ChatEvent.TextDelta("已发送"))
        ))

        val result = engine(provider, listOf(tool)).run(emptyList(), {}, {})

        assertEquals("已发送", result)
    }

    @Test
    fun `permission required produces trace and asks user to grant`() = runTest {
        val permission = ToolPermission("android.permission.POST_NOTIFICATIONS", "通知权限", "发通知", true)
        val tool = RecordingTool(result = ToolResult.PermissionRequired(permission))
        val provider = FakeProvider(listOf(
            listOf(ChatEvent.ToolCallsReady(listOf(ToolCall("c1", "fake_tool", "{}")))),
            listOf(ChatEvent.TextDelta("请去开启权限"))
        ))
        val traces = mutableListOf<TraceStep>()

        val result = engine(provider, listOf(tool)).run(emptyList(), {}, traces::add)

        assertEquals("请去开启权限", result)
        assertTrue(traces.any { !it.success && it.detail.contains("缺少权限") })
        val toolMsg = provider.receivedMessages[1].first { it.role == "tool" }
        assertTrue(toolMsg.content.contains("尚未授权"))
    }

    @Test
    fun `disabled tool is not executed and model is told`() = runTest {
        val tool = RecordingTool()
        val provider = FakeProvider(listOf(
            listOf(ChatEvent.ToolCallsReady(listOf(ToolCall("c1", "fake_tool", "{}")))),
            listOf(ChatEvent.TextDelta("工具被关闭了"))
        ))

        val result = engine(provider, listOf(tool), store = storeWith(false)).run(emptyList(), {}, {})

        assertEquals("工具被关闭了", result)
        assertTrue(tool.executedWith == null)
        val toolMsg = provider.receivedMessages[1].first { it.role == "tool" }
        assertTrue(toolMsg.content.contains("已被用户关闭"))
    }

    @Test
    fun `stops at max iterations to avoid infinite loop`() = runTest {
        val tool = RecordingTool()
        val provider = FakeProvider(emptyList()) // default event yields text; override below
        val loopProvider = FakeProvider(List(10) {
            listOf(ChatEvent.ToolCallsReady(listOf(ToolCall("c$it", "fake_tool", "{}"))))
        })
        val traces = mutableListOf<TraceStep>()

        engine(loopProvider, listOf(tool), maxIterations = 2).run(emptyList(), {}, traces::add)

        assertEquals(2, loopProvider.callCount)
        assertTrue(traces.any { it.title.contains("最大执行轮数") })
    }
}
