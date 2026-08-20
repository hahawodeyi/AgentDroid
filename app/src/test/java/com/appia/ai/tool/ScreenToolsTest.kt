package com.appia.ai.tool

import io.mockk.mockk
import android.content.Context
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenToolsTest {

    private val context = mockk<Context>()

    @Test
    fun `read_screen requires accessibility when service not running`() = runTest {
        val result = ReadScreenTool().execute(buildJsonObject {}, context)
        assertTrue(result is ToolResult.PermissionRequired)
        assertEquals(
            ToolPermission.ACCESSIBILITY,
            (result as ToolResult.PermissionRequired).permission.manifestPermission
        )
    }

    @Test
    fun `screen_action requires accessibility when service not running`() = runTest {
        val result = ScreenActionTool().execute(buildJsonObject { put("action", "back") }, context)
        assertTrue(result is ToolResult.PermissionRequired)
    }

    @Test
    fun `screen tools declare accessibility permission`() {
        assertEquals(ToolPermission.ACCESSIBILITY, ReadScreenTool().permission?.manifestPermission)
        assertEquals(ToolPermission.ACCESSIBILITY, ScreenActionTool().permission?.manifestPermission)
    }
}
