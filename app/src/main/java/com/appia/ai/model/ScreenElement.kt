package com.appia.ai.model

data class ScreenElement(
    val text: String = "",
    val resourceId: String = "",
    val contentDesc: String = "",
    val className: String = "",
    val bounds: Rect = Rect(),
    val clickable: Boolean = false,
    val index: Int = 0
) {
    val centerX: Int get() = (bounds.left + bounds.right) / 2
    val centerY: Int get() = (bounds.top + bounds.bottom) / 2

    val displayText: String
        get() = when {
            text.isNotEmpty() -> text
            contentDesc.isNotEmpty() -> contentDesc
            resourceId.isNotEmpty() -> resourceId
            else -> "(empty)"
        }

    val hasIdentifier: Boolean
        get() = text.isNotEmpty() || contentDesc.isNotEmpty() || resourceId.isNotEmpty()
}
