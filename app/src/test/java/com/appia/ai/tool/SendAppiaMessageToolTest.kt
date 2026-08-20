package com.appia.ai.tool

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test

class SendAppiaMessageToolTest {

    private val context = mockk<Context>()

    @Test
    fun `fails when target or text missing`() = runTest {
        val tool = SendAppiaMessageTool()
        val r1 = tool.execute(buildJsonObject { put("text", "hi") }, context)
        assertTrue(r1 is ToolResult.Failure)
        val r2 = tool.execute(buildJsonObject { put("target", "@bob") }, context)
        assertTrue(r2 is ToolResult.Failure)
    }

    @Test
    fun `fails when target lacks @ or # prefix`() = runTest {
        val result = SendAppiaMessageTool().execute(
            buildJsonObject {
                put("target", "bob")
                put("text", "hi")
            },
            context
        )
        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).reason.contains("@"))
    }
}
