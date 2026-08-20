package com.appia.ai.trigger

import android.content.Context

data class TriggerConfig(
    val enabled: Boolean = false,
    val hour: Int = 8,
    val minute: Int = 0,
    val instruction: String = DEFAULT_INSTRUCTION
) {
    companion object {
        const val DEFAULT_INSTRUCTION = "现在到了每天的提醒时间。请用一句话给我一个温暖的今日提醒，然后调用 post_notification 工具把它发到通知栏。"
    }
}

class TriggerStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): TriggerConfig = TriggerConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        hour = prefs.getInt(KEY_HOUR, 8),
        minute = prefs.getInt(KEY_MINUTE, 0),
        instruction = prefs.getString(KEY_INSTRUCTION, null) ?: TriggerConfig.DEFAULT_INSTRUCTION
    )

    fun save(config: TriggerConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putInt(KEY_HOUR, config.hour)
            .putInt(KEY_MINUTE, config.minute)
            .putString(KEY_INSTRUCTION, config.instruction)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "trigger_settings"
        private const val KEY_ENABLED = "trigger_enabled"
        private const val KEY_HOUR = "trigger_hour"
        private const val KEY_MINUTE = "trigger_minute"
        private const val KEY_INSTRUCTION = "trigger_instruction"
    }
}
