package com.appia.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentActionExtendedTest {
    @Test
    fun long_press_has_correct_name() {
        val action = AgentAction.LongPress(100, 200)
        assertEquals("long_press", action.actionName)
        assertEquals(1000, action.durationMs)
    }

    @Test
    fun long_press_custom_duration() {
        val action = AgentAction.LongPress(50, 50, 2000)
        assertEquals(2000, action.durationMs)
    }

    @Test
    fun swipe_has_correct_name() {
        val action = AgentAction.Swipe(0, 100, 200, 300)
        assertEquals("swipe", action.actionName)
    }

    @Test
    fun launch_app_has_correct_name() {
        val action = AgentAction.LaunchApp("com.tencent.mm")
        assertEquals("launch_app", action.actionName)
        assertEquals("com.tencent.mm", action.packageName)
    }
}
