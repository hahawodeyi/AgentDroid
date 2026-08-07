package com.appia.ai.service

import com.appia.ai.model.ScreenElement

object ElementMatcher {

    fun find(target: String, elements: List<ScreenElement>): ScreenElement? {
        if (target.isBlank() || elements.isEmpty()) return null

        elements.firstOrNull { it.text.equals(target, ignoreCase = true) }
            ?.let { return it }

        elements.firstOrNull { it.contentDesc.equals(target, ignoreCase = true) }
            ?.let { return it }

        elements.firstOrNull { it.text.contains(target, ignoreCase = true) }
            ?.let { return it }

        elements.firstOrNull { it.contentDesc.contains(target, ignoreCase = true) }
            ?.let { return it }

        elements.firstOrNull {
            it.resourceId.substringAfter("/").equals(target, ignoreCase = true)
        }?.let { return it }

        return null
    }

    fun findAll(targets: List<String>, elements: List<ScreenElement>): List<ScreenElement> {
        return targets.mapNotNull { find(it, elements) }
    }
}
