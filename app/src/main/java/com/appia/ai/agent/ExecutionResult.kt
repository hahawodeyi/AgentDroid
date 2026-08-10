package com.appia.ai.agent

import com.appia.ai.model.ExecutionStep

enum class ExecutionStatus { SUCCESS, PARTIAL, FAILED, CANCELLED }

data class ExecutionResult(
    val status: ExecutionStatus,
    val completedSteps: Int,
    val totalSteps: Int,
    val steps: List<ExecutionStep>,
    val message: String
) {
    val isSuccess: Boolean get() = status == ExecutionStatus.SUCCESS
    val summary: String
        get() = "$message ($completedSteps/$totalSteps 步完成)"
}

interface ExecutionCallbacks {
    fun onStepStart(step: ExecutionStep, index: Int)
    fun onStepDone(step: ExecutionStep, index: Int, success: Boolean)
    fun onProgress(message: String)
    suspend fun onTargetNotFound(target: String): Boolean
    suspend fun onSafetyCheck(step: ExecutionStep, decision: SafetyDecision): Boolean
    suspend fun onPopupDetected(popup: PopupInfo): Boolean
}
