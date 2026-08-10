package com.appia.ai.agent

import com.appia.ai.model.AgentAction
import com.appia.ai.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlanJsonParserTest {
    @Test
    fun parse_valid_plan_with_tap_and_input() {
        val json = """
            {"goal":"给妈妈发微信","steps":[
                {"action":"tap","target":"搜索","description":"点击搜索"},
                {"action":"input","text":"妈妈","description":"输入联系人名"},
                {"action":"tap","target":"妈妈","description":"点击联系人"}
            ]}
        """.trimIndent()
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        assertEquals("给妈妈发微信", plan!!.goal)
        assertEquals(3, plan.steps.size)
        assertEquals("搜索", plan.steps[0].target)
        assert(plan.steps[1].action is AgentAction.Input)
        assertEquals("妈妈", (plan.steps[1].action as AgentAction.Input).text)
    }

    @Test
    fun parse_click_alias_works() {
        val json = """{"goal":"test","steps":[{"action":"click","target":"OK"}]}"""
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        assert(plan!!.steps[0].action is AgentAction.Tap)
    }

    @Test
    fun parse_scroll_with_direction() {
        val json = """{"goal":"test","steps":[{"action":"scroll","direction":"up","description":"向上滚动"}]}"""
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        val action = plan!!.steps[0].action as AgentAction.Scroll
        assertEquals(Direction.UP, action.direction)
    }

    @Test
    fun parse_back_and_home() {
        val json = """{"goal":"test","steps":[{"action":"back","description":"返回"},{"action":"home","description":"桌面"}]}"""
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        assert(plan!!.steps[0].action is AgentAction.Back)
        assert(plan.steps[1].action is AgentAction.Home)
    }

    @Test
    fun parse_wait_with_seconds() {
        val json = """{"goal":"test","steps":[{"action":"wait","seconds":2.5}]}"""
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        val action = plan!!.steps[0].action as AgentAction.Wait
        assertEquals(2.5f, action.seconds, 0.01f)
    }

    @Test
    fun parse_invalid_json_returns_null() {
        assertNull(PlanJsonParser.parse("not json"))
        assertNull(PlanJsonParser.parse(""))
    }

    @Test
    fun parse_empty_steps_returns_null() {
        val json = """{"goal":"test","steps":[]}"""
        assertNull(PlanJsonParser.parse(json))
    }

    @Test
    fun parse_json_with_markdown_fence() {
        val json = """
            ```json
            {"goal":"test","steps":[{"action":"back","description":"返回"}]}
            ```
        """.trimIndent()
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
    }
}
