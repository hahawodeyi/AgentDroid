package com.appia.ai.tool

data class ToolPermission(
    val manifestPermission: String,
    val title: String,
    val rationale: String,
    val isRuntime: Boolean,
    val settingsIntentAction: String? = null
) {
    companion object {
        const val ACCESSIBILITY = "accessibility_service"
    }
}
