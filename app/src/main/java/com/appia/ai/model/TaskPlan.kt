package com.appia.ai.model

data class ExecutionStep(
    val action: AgentAction,
    val description: String,
    val target: String? = null,
    val status: StepStatus = StepStatus.PENDING
)

data class TaskPlan(
    val goal: String,
    val steps: List<ExecutionStep>
)
