package com.appia.ai.model

enum class Direction { UP, DOWN, LEFT, RIGHT }

enum class StepStatus { PENDING, RUNNING, DONE, FAILED, SKIPPED }

sealed class AgentAction {
    data class Tap(val x: Int, val y: Int) : AgentAction()
    data class Input(val text: String) : AgentAction()
    object Back : AgentAction()
    object Home : AgentAction()
    data class Scroll(val direction: Direction) : AgentAction()
    data class Wait(val seconds: Float) : AgentAction()

    val actionName: String
        get() = when (this) {
            is Tap -> "tap"
            is Input -> "input"
            is Back -> "back"
            is Home -> "home"
            is Scroll -> "scroll"
            is Wait -> "wait"
        }
}
