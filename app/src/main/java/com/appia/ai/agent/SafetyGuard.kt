package com.appia.ai.agent

import com.appia.ai.model.ExecutionStep

enum class SafetyDecision { ALLOW, CONFIRM, BLOCK }

object SafetyGuard {

    private val confirmKeywords = listOf(
        "发送", "确认", "支付", "转账", "删除", "清除",
        "退出", "注销", "提交", "购买", "预订", "下单",
        "同意", "授权", "允许", "确定"
    )

    private val blockKeywords = listOf(
        "格式化", "恢复出厂", "root", "刷机"
    )

    fun check(step: ExecutionStep): SafetyDecision {
        val target = step.target?.lowercase() ?: ""
        val desc = step.description.lowercase()
        val combined = "$target $desc"

        if (blockKeywords.any { combined.contains(it.lowercase()) }) {
            return SafetyDecision.BLOCK
        }

        if (confirmKeywords.any { combined.contains(it.lowercase()) }) {
            return SafetyDecision.CONFIRM
        }

        return SafetyDecision.ALLOW
    }
}
