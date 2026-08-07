package com.appia.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentActionTest {
    @Test
    fun tap_has_correct_action_name() {
        val action = AgentAction.Tap(100, 200)
        assertEquals("tap", action.actionName)
    }

    @Test
    fun scroll_stores_direction() {
        val action = AgentAction.Scroll(Direction.DOWN)
        assertEquals(Direction.DOWN, action.direction)
        assertEquals("scroll", action.actionName)
    }

    @Test
    fun back_is_singleton() {
        assertEquals(AgentAction.Back, AgentAction.Back)
        assertEquals("back", AgentAction.Back.actionName)
    }

    @Test
    fun wait_stores_seconds() {
        val action = AgentAction.Wait(1.5f)
        assertEquals(1.5f, action.seconds, 0.01f)
    }
}
