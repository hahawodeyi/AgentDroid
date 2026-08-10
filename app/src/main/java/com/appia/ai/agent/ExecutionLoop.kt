package com.appia.ai.agent

import com.appia.ai.model.AgentAction
import com.appia.ai.model.ExecutionStep
import com.appia.ai.model.StepStatus
import com.appia.ai.model.TaskPlan
import com.appia.ai.agent.SafetyDecision
import com.appia.ai.service.AgentAccessibilityService
import kotlinx.coroutines.delay

class ExecutionLoop(
    private val service: AgentAccessibilityService
) {
    @Volatile
    private var isPaused = false

    @Volatile
    private var isCancelled = false

    suspend fun execute(
        plan: TaskPlan,
        callbacks: ExecutionCallbacks
    ): ExecutionResult {
        val steps = plan.steps.map { it.copy(status = StepStatus.PENDING) }.toMutableList()
        var completed = 0

        for ((index, step) in steps.withIndex()) {
            if (isCancelled) {
                steps[index] = step.copy(status = StepStatus.SKIPPED)
                return buildResult(ExecutionStatus.CANCELLED, completed, steps, plan, "任务已取消")
            }

            while (isPaused && !isCancelled) {
                delay(200)
            }
            if (isCancelled) continue

            steps[index] = step.copy(status = StepStatus.RUNNING)
            callbacks.onStepStart(steps[index], index)

            val resolvedAction = resolveTarget(step)
            if (resolvedAction == null) {
                callbacks.onProgress("找不到元素: ${step.target}")
                val shouldSkip = callbacks.onTargetNotFound(step.target ?: "")
                steps[index] = steps[index].copy(status = StepStatus.FAILED)
                callbacks.onStepDone(steps[index], index, false)
                if (!shouldSkip) {
                    return buildResult(ExecutionStatus.FAILED, completed, steps, plan, "步骤 ${index + 1} 失败: 找不到 ${step.target}")
                }
                continue
            }

            // Safety check
            val safetyDecision = SafetyGuard.check(steps[index])
            when (safetyDecision) {
                SafetyDecision.BLOCK -> {
                    callbacks.onProgress("操作被安全拦截: ${step.description}")
                    steps[index] = steps[index].copy(status = StepStatus.SKIPPED)
                    callbacks.onStepDone(steps[index], index, false)
                    return buildResult(ExecutionStatus.FAILED, completed, steps, plan, "步骤 ${index + 1} 被安全拦截")
                }
                SafetyDecision.CONFIRM -> {
                    callbacks.onProgress("需要确认: ${step.description}")
                    val approved = callbacks.onSafetyCheck(steps[index], safetyDecision)
                    if (!approved) {
                        steps[index] = steps[index].copy(status = StepStatus.SKIPPED)
                        callbacks.onStepDone(steps[index], index, false)
                        continue
                    }
                }
                SafetyDecision.ALLOW -> { /* proceed normally */ }
            }

            val success = try {
                service.executeAction(resolvedAction)
            } catch (_: Exception) {
                false
            }

            steps[index] = steps[index].copy(
                action = resolvedAction,
                status = if (success) StepStatus.DONE else StepStatus.FAILED
            )
            callbacks.onStepDone(steps[index], index, success)

            if (success) {
                completed++
                delay(1000)
            } else {
                callbacks.onProgress("步骤 ${index + 1} 执行失败")
            }
        }

        val status = if (completed == plan.steps.size) ExecutionStatus.SUCCESS
                     else ExecutionStatus.PARTIAL
        return buildResult(status, completed, steps, plan, "执行完成")
    }

    private suspend fun resolveTarget(step: ExecutionStep): AgentAction? {
        val target = step.target ?: return step.action
        val element = service.findElement(target) ?: return null
        return when (val action = step.action) {
            is AgentAction.Tap -> AgentAction.Tap(element.centerX, element.centerY)
            else -> action
        }
    }

    fun pause() { isPaused = true }
    fun resume() { isPaused = false }
    fun stop() { isCancelled = true; isPaused = false }

    private fun buildResult(
        status: ExecutionStatus,
        completed: Int,
        steps: List<ExecutionStep>,
        plan: TaskPlan,
        message: String
    ): ExecutionResult {
        return ExecutionResult(
            status = status,
            completedSteps = completed,
            totalSteps = plan.steps.size,
            steps = steps,
            message = message
        )
    }
}
