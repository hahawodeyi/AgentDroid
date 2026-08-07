package com.appia.ai.service

import com.appia.ai.model.ScreenElement
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityTreeParserTest {
    @Test
    fun parse_null_root_returns_empty() {
        val result = AccessibilityTreeParser.parse(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun parse_single_node_extracts_fields() {
        val node = mockk<android.view.accessibility.AccessibilityNodeInfo>()
        every { node.text } returns "Login"
        every { node.viewIdResourceName } returns "com.app:id/btn_login"
        every { node.contentDescription } returns null
        every { node.className } returns "android.widget.Button"
        every { node.isClickable } returns true
        every { node.childCount } returns 0
        every { node.getBoundsInScreen(any()) } answers {
            val rect = firstArg<android.graphics.Rect>()
            rect.left = 0
            rect.top = 0
            rect.right = 100
            rect.bottom = 50
        }

        val result = AccessibilityTreeParser.parse(node)
        assertEquals(1, result.size)
        assertEquals("Login", result[0].text)
        assertEquals("com.app:id/btn_login", result[0].resourceId)
        assertTrue(result[0].clickable)
        assertEquals(50, result[0].centerX)
        assertEquals(25, result[0].centerY)
    }

    @Test
    fun filter_clickable_returns_only_clickable_with_id() {
        val elements = listOf(
            ScreenElement(text = "Btn", clickable = true, index = 0),
            ScreenElement(text = "", clickable = true, index = 1),
            ScreenElement(text = "Text", clickable = false, index = 2)
        )
        val filtered = AccessibilityTreeParser.filterClickable(elements)
        assertEquals(1, filtered.size)
        assertEquals("Btn", filtered[0].text)
    }

    @Test
    fun to_description_includes_index_and_text() {
        val elements = listOf(
            ScreenElement(text = "Hello", className = "TextView", index = 0)
        )
        val desc = AccessibilityTreeParser.toDescription(elements)
        assertTrue(desc.contains("[0]"))
        assertTrue(desc.contains("Hello"))
    }
}
