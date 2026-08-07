package com.appia.ai.service

import android.view.accessibility.AccessibilityNodeInfo
import com.appia.ai.model.Rect
import com.appia.ai.model.ScreenElement

object AccessibilityTreeParser {

    fun parse(root: AccessibilityNodeInfo?): List<ScreenElement> {
        if (root == null) return emptyList()
        val result = mutableListOf<ScreenElement>()
        parseNode(root, result)
        return result
    }

    private fun parseNode(
        node: AccessibilityNodeInfo,
        result: MutableList<ScreenElement>
    ) {
        val androidRect = android.graphics.Rect()
        node.getBoundsInScreen(androidRect)
        val bounds = Rect(
            left = androidRect.left,
            top = androidRect.top,
            right = androidRect.right,
            bottom = androidRect.bottom
        )
        val element = ScreenElement(
            text = node.text?.toString() ?: "",
            resourceId = node.viewIdResourceName ?: "",
            contentDesc = node.contentDescription?.toString() ?: "",
            className = node.className?.toString() ?: "",
            bounds = bounds,
            clickable = node.isClickable,
            index = result.size
        )
        result.add(element)

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { parseNode(it, result) }
        }
    }

    fun filterClickable(elements: List<ScreenElement>): List<ScreenElement> {
        return elements.filter { it.clickable && it.hasIdentifier }
    }

    fun toDescription(elements: List<ScreenElement>): String {
        return elements.joinToString("\n") { el ->
            "[${el.index}] text=${el.displayText} class=${el.className} id=${el.resourceId} center=(${el.centerX},${el.centerY})"
        }
    }
}
