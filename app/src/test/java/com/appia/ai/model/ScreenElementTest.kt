package com.appia.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenElementTest {
    @Test
    fun center_calculated_from_bounds() {
        val el = ScreenElement(bounds = Rect(0, 0, 100, 200))
        assertEquals(50, el.centerX)
        assertEquals(100, el.centerY)
    }

    @Test
    fun display_text_prefers_text_over_content_desc() {
        val el = ScreenElement(text = "Hello", contentDesc = "World")
        assertEquals("Hello", el.displayText)
    }

    @Test
    fun display_text_falls_back_to_content_desc() {
        val el = ScreenElement(contentDesc = "World")
        assertEquals("World", el.displayText)
    }

    @Test
    fun has_identifier_true_when_any_field_present() {
        assertTrue(ScreenElement(text = "x").hasIdentifier)
        assertTrue(ScreenElement(resourceId = "id").hasIdentifier)
    }
}
