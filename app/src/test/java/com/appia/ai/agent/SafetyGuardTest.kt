package com.appia.ai.agent

import com.appia.ai.model.AgentAction
import com.appia.ai.model.ExecutionStep
import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyGuardTest {
    private fun step(target: String? = null, description: String = ""): ExecutionStep {
        return ExecutionStep(
            action = AgentAction.Tap(0, 0),
            description = description,
            target = target
        )
    }

    @Test
    fun send_button_requires_confirm() {
        assertEquals(SafetyDecision.CONFIRM, SafetyGuard.check(step(target = "发送按钮")))
    }

    @Test
    fun delete_requires_confirm() {
        assertEquals(SafetyDecision.CONFIRM, SafetyGuard.check(step(description = "删除消息")))
    }

    @Test
    fun payment_requires_confirm() {
        assertEquals(SafetyDecision.CONFIRM, SafetyGuard.check(step(target = "确认支付")))
    }

    @Test
    fun format_factory_reset_blocked() {
        assertEquals(SafetyDecision.BLOCK, SafetyGuard.check(step(description = "恢复出厂设置")))
    }

    @Test
    fun normal_navigation_allowed() {
        assertEquals(SafetyDecision.ALLOW, SafetyGuard.check(step(target = "搜索")))
        assertEquals(SafetyDecision.ALLOW, SafetyGuard.check(step(target = "返回")))
        assertEquals(SafetyDecision.ALLOW, SafetyGuard.check(step(description = "打开App")))
    }

    @Test
    fun empty_target_and_desc_allowed() {
        assertEquals(SafetyDecision.ALLOW, SafetyGuard.check(step()))
    }

    @Test
    fun submit_requires_confirm() {
        assertEquals(SafetyDecision.CONFIRM, SafetyGuard.check(step(description = "提交表单")))
    }
}
