package com.appia.ai.tool

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ToolSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabledMap = MutableStateFlow(loadAll())
    val enabledMap: StateFlow<Map<String, Boolean>> = _enabledMap.asStateFlow()

    fun isEnabled(toolId: String): Boolean = _enabledMap.value[toolId] ?: true

    fun setEnabled(toolId: String, enabled: Boolean) {
        prefs.edit().putBoolean(key(toolId), enabled).apply()
        _enabledMap.value = _enabledMap.value + (toolId to enabled)
    }

    private fun loadAll(): Map<String, Boolean> =
        prefs.all.mapNotNull { (k, v) ->
            if (k.startsWith(KEY_PREFIX) && v is Boolean) k.removePrefix(KEY_PREFIX) to v else null
        }.toMap()

    private fun key(toolId: String) = KEY_PREFIX + toolId

    companion object {
        private const val PREFS_NAME = "tool_settings"
        private const val KEY_PREFIX = "tool_enabled_"
    }
}
