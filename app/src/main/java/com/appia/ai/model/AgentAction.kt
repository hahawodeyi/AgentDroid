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
    data class LongPress(val x: Int, val y: Int, val durationMs: Long = 1000) : AgentAction()
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val durationMs: Long = 300) : AgentAction()
    data class LaunchApp(val packageName: String) : AgentAction()

    val actionName: String
        get() = when (this) {
            is Tap -> "tap"
            is Input -> "input"
            is Back -> "back"
            is Home -> "home"
            is Scroll -> "scroll"
            is Wait -> "wait"
            is LongPress -> "long_press"
            is Swipe -> "swipe"
            is LaunchApp -> "launch_app"
        }
}
