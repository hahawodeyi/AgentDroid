package com.appia.ai.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appia.ai.agent.AgentEngine
import com.appia.ai.data.SettingsRepository
import com.appia.ai.llm.ChatMessage
import com.appia.ai.llm.ModelRegistry
import com.appia.ai.tool.ToolRegistry
import com.appia.ai.tool.ToolSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AgentTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = TriggerStore(context)
                val config = store.load()
                if (!config.enabled) return@launch

                runTrigger(context.applicationContext, config)

                TriggerScheduler.scheduleNext(context, config)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {

        fun runNow(context: Context, config: TriggerConfig) {
            CoroutineScope(Dispatchers.IO).launch {
                runTrigger(context.applicationContext, config)
            }
        }

        private suspend fun runTrigger(context: Context, config: TriggerConfig) {
            val modelConfig = SettingsRepository(context).getActiveConfig() ?: return
            val provider = ModelRegistry.get(modelConfig)

            val engine = AgentEngine(
                provider = provider,
                config = modelConfig,
                toolRegistry = ToolRegistry.createDefault(),
                toolStore = ToolSettingsStore(context),
                context = context,
                maxIterations = 4
            )

            try {
                engine.run(
                    history = listOf(ChatMessage.user(config.instruction)),
                    onTextDelta = {},
                    onTrace = {}
                )
            } catch (_: Exception) {
                // 静默失败：后台触发不打断用户，下次触发再试
            }
        }
    }
}
