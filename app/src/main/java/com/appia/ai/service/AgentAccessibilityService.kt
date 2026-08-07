package com.appia.ai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.appia.ai.model.AgentAction
import com.appia.ai.model.Direction
import com.appia.ai.model.ScreenElement
import kotlinx.coroutines.delay

class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceBridge.bind(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Phase 1: passive query mode, no event handling needed
    }

    override fun onInterrupt() {
        ServiceBridge.unbind()
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceBridge.unbind()
    }

    fun getScreenElements(): List<ScreenElement> {
        val root = rootInActiveWindow ?: return emptyList()
        return AccessibilityTreeParser.parse(root)
    }

    fun getClickableElements(): List<ScreenElement> {
        return AccessibilityTreeParser.filterClickable(getScreenElements())
    }

    fun findElement(target: String): ScreenElement? {
        return ElementMatcher.find(target, getScreenElements())
    }

    suspend fun executeAction(action: AgentAction): Boolean {
        return when (action) {
            is AgentAction.Tap -> performTap(action.x, action.y)
            is AgentAction.Input -> performInput(action.text)
            is AgentAction.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
            is AgentAction.Home -> performGlobalAction(GLOBAL_ACTION_HOME)
            is AgentAction.Scroll -> performScroll(action.direction)
            is AgentAction.Wait -> { delay((action.seconds * 1000).toLong()); true }
        }
    }

    private fun performTap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun performInput(text: String): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun performScroll(direction: Direction): Boolean {
        val (startY, endY) = when (direction) {
            Direction.UP -> 300 to 1000
            Direction.DOWN -> 1000 to 300
            else -> 500 to 500
        }
        val (startX, endX) = when (direction) {
            Direction.LEFT -> 1000 to 300
            Direction.RIGHT -> 300 to 1000
            else -> 500 to 500
        }
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
