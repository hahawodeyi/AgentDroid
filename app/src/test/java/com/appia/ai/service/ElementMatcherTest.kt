package com.appia.ai.service

import com.appia.ai.model.ScreenElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ElementMatcherTest {
    private val elements = listOf(
        ScreenElement(text = "Login", contentDesc = "", resourceId = "com.app:id/btn_login", index = 0),
        ScreenElement(text = "", contentDesc = "Settings", resourceId = "com.app:id/settings", index = 1),
        ScreenElement(text = "Submit Form", contentDesc = "", resourceId = "com.app:id/submit", index = 2)
    )

    @Test
    fun find_exact_text_match() {
        val result = ElementMatcher.find("Login", elements)
        assertNotNull(result)
        assertEquals(0, result!!.index)
    }

    @Test
    fun find_exact_content_desc_match() {
        val result = ElementMatcher.find("Settings", elements)
        assertNotNull(result)
        assertEquals(1, result!!.index)
    }

    @Test
    fun find_partial_text_match() {
        val result = ElementMatcher.find("Submit", elements)
        assertNotNull(result)
        assertEquals(2, result!!.index)
    }

    @Test
    fun find_resource_id_suffix_match() {
        val result = ElementMatcher.find("btn_login", elements)
        assertNotNull(result)
        assertEquals(0, result!!.index)
    }

    @Test
    fun find_returns_null_when_no_match() {
        val result = ElementMatcher.find("Nonexistent", elements)
        assertNull(result)
    }

    @Test
    fun find_returns_null_for_blank_target() {
        val result = ElementMatcher.find("", elements)
        assertNull(result)
    }

    @Test
    fun find_all_returns_multiple_matches() {
        val results = ElementMatcher.findAll(listOf("Login", "Settings"), elements)
        assertEquals(2, results.size)
    }
}
