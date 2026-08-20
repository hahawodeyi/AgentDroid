package com.appia.ai.tool

class ToolRegistry(tools: List<Tool>) {

    private val byId = tools.associateBy { it.id }

    fun all(): List<Tool> = byId.values.toList()

    fun find(id: String): Tool? = byId[id]

    fun enabled(store: ToolSettingsStore): List<Tool> = all().filter { store.isEnabled(it.id) }

    companion object {
        fun createDefault(): ToolRegistry = ToolRegistry(
            listOf(
                SetAlarmTool(),
                PostNotificationTool(),
                OpenAppTool(),
                SaveMemoryTool(),
                RecallMemoryTool(),
                ForgetMemoryTool(),
                ReadScreenTool(),
                ScreenActionTool(),
                SendAppiaMessageTool(),
                ReadAppiaMessagesTool()
            )
        )
    }
}
