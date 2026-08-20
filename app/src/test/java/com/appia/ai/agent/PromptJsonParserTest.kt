package com.appia.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptJsonParserTest {

    @Test
    fun `parses final reply`() {
        val result = PromptJsonParser.parse("""{"final":"你好呀"}""")
        assertEquals(PromptJsonParser.Result.Final("你好呀"), result)
    }

    @Test
    fun `parses single tool call`() {
        val result = PromptJsonParser.parse(
            """{"actions":[{"tool":"set_alarm","args":{"hour":8,"minute":0}}]}"""
        )
        assertTrue(result is PromptJsonParser.Result.Actions)
        val calls = (result as PromptJsonParser.Result.Actions).calls
        assertEquals(1, calls.size)
        assertEquals("set_alarm", calls[0].name)
        assertEquals("""{"hour":8,"minute":0}""", calls[0].argumentsJson)
    }

    @Test
    fun `parses multiple tool calls`() {
        val result = PromptJsonParser.parse(
            """{"actions":[{"tool":"a","args":{}},{"tool":"b","args":{"x":"y"}}]}"""
        )
        val calls = (result as PromptJsonParser.Result.Actions).calls
        assertEquals(listOf("a", "b"), calls.map { it.name })
    }

    @Test
    fun `strips markdown code fence`() {
        val result = PromptJsonParser.parse("```json\n{\"final\":\"ok\"}\n```")
        assertEquals(PromptJsonParser.Result.Final("ok"), result)
    }

    @Test
    fun `plain text is unparseable`() {
        assertEquals(PromptJsonParser.Result.Unparseable, PromptJsonParser.parse("你好"))
        assertEquals(PromptJsonParser.Result.Unparseable, PromptJsonParser.parse(""))
    }

    @Test
    fun `json without actions or final is unparseable`() {
        assertEquals(PromptJsonParser.Result.Unparseable, PromptJsonParser.parse("""{"foo":1}"""))
    }

    @Test
    fun `actions with missing tool name are dropped`() {
        val result = PromptJsonParser.parse(
            """{"actions":[{"args":{}},{"tool":"a","args":{}}]}"""
        )
        val calls = (result as PromptJsonParser.Result.Actions).calls
        assertEquals(1, calls.size)
        assertEquals("a", calls[0].name)
    }
}
