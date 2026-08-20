package com.appia.ai.tool

import android.content.Context
import com.appia.ai.appia.AppiaMessage
import com.appia.ai.appia.AppiaRoom
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAppiaMessagesToolTest {

    private val context = mockk<Context>()

    @Test
    fun `fails when target lacks @ or # prefix`() = runTest {
        val result = ReadAppiaMessagesTool().execute(
            buildJsonObject { put("target", "bob") },
            context
        )
        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).reason.contains("@"))
    }

    @Test
    fun `target is optional`() {
        val schema = ReadAppiaMessagesTool().parametersSchema.toString()
        assertTrue(!schema.contains("\"required\""))
        assertTrue(schema.contains("target"))
        assertTrue(schema.contains("limit"))
    }

    @Test
    fun `format renders rooms with unread and messages`() {
        val result = linkedMapOf(
            AppiaRoom("r1", "general", "c", 3) to listOf(
                AppiaMessage("alice", "hello", 1700000000000),
                AppiaMessage("bob", "world", 0)
            ),
            AppiaRoom("r2", "empty-room", "c", 0) to emptyList()
        )
        val text = ReadAppiaMessagesTool().format(result)
        assertTrue(text.contains("【general】（未读 3 条）"))
        assertTrue(text.contains("alice: hello"))
        assertTrue(text.contains("[--] bob: world"))
        assertTrue(text.contains("【empty-room】（未读 0 条）\n（无消息）"))
    }

    @Test
    fun `format preserves room order`() {
        val result = linkedMapOf(
            AppiaRoom("r1", "first", "c", 1) to emptyList<AppiaMessage>(),
            AppiaRoom("r2", "second", "c", 1) to emptyList()
        )
        val text = ReadAppiaMessagesTool().format(result)
        assertTrue(text.indexOf("first") < text.indexOf("second"))
        assertEquals(text, text.trim())
    }
}
