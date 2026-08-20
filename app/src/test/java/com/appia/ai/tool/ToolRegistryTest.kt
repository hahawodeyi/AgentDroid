package com.appia.ai.tool

import android.content.Context
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTool(
    override val id: String,
    override val displayName: String = id,
    override val permission: ToolPermission? = null
) : Tool {
    override val description = "fake tool $id"
    override val parametersSchema: JsonObject = buildJsonObject { put("type", "object") }

    override suspend fun execute(args: JsonObject, context: Context): ToolResult =
        ToolResult.Success("ok")
}

class ToolRegistryTest {

    @Test
    fun `find returns tool by id`() {
        val registry = ToolRegistry(listOf(FakeTool("a"), FakeTool("b")))
        assertEquals("a", registry.find("a")?.id)
        assertNull(registry.find("missing"))
    }

    @Test
    fun `all returns every registered tool`() {
        val registry = ToolRegistry(listOf(FakeTool("a"), FakeTool("b"), FakeTool("c")))
        assertEquals(3, registry.all().size)
    }

    @Test
    fun `default registry contains the built-in tools`() {
        val registry = ToolRegistry.createDefault()
        assertNotNull(registry.find("set_alarm"))
        assertNotNull(registry.find("post_notification"))
        assertNotNull(registry.find("open_app"))
        assertNotNull(registry.find("save_memory"))
        assertNotNull(registry.find("recall_memory"))
        assertNotNull(registry.find("forget_memory"))
        assertNotNull(registry.find("read_screen"))
        assertNotNull(registry.find("screen_action"))
        assertNotNull(registry.find("send_appia_message"))
    }

    @Test
    fun `toFunctionSpec produces OpenAI function calling shape`() {
        val spec = FakeTool("set_alarm").toFunctionSpec()
        assertEquals("function", spec["type"]?.jsonPrimitive?.content)
        val fn = spec["function"]!!.jsonObject
        assertEquals("set_alarm", fn["name"]?.jsonPrimitive?.content)
        assertTrue(fn.containsKey("description"))
        assertTrue(fn.containsKey("parameters"))
    }
}
