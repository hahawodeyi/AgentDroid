package com.appia.ai.agent

import android.content.Context
import com.appia.ai.llm.ToolCall
import com.appia.ai.model.TraceStep
import com.appia.ai.tool.ToolRegistry
import com.appia.ai.tool.ToolResult
import com.appia.ai.tool.ToolSettingsStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class ToolCallExecutor(
    private val toolRegistry: ToolRegistry,
    private val toolStore: ToolSettingsStore,
    private val context: Context
) {
    suspend fun execute(call: ToolCall): Pair<TraceStep, String> {
        val tool = toolRegistry.find(call.name)
            ?: return TraceStep("调用 ${call.name}", "工具不存在", success = false) to
                "错误：不存在名为 ${call.name} 的工具"

        if (!toolStore.isEnabled(tool.id)) {
            return TraceStep("调用 ${tool.displayName}", "已被用户关闭", success = false) to
                "工具「${tool.displayName}」已被用户关闭。请告知用户前往 设置 → 工具权限管理 开启后再试。"
        }

        val args = try {
            Json.parseToJsonElement(call.argumentsJson.ifEmpty { "{}" }).jsonObject
        } catch (_: Exception) {
            return TraceStep("调用 ${tool.displayName}", "参数格式错误: ${call.argumentsJson}", success = false) to
                "错误：工具参数不是合法 JSON：${call.argumentsJson}"
        }

        return when (val result = tool.execute(args, context)) {
            is ToolResult.Success ->
                TraceStep("调用 ${tool.displayName}", result.message, success = true) to result.message
            is ToolResult.Failure ->
                TraceStep("调用 ${tool.displayName}", result.reason, success = false) to
                    "工具执行失败：${result.reason}"
            is ToolResult.PermissionRequired ->
                TraceStep("调用 ${tool.displayName}", "缺少权限：${result.permission.title}", success = false) to
                    "工具「${tool.displayName}」需要权限「${result.permission.title}」（${result.permission.rationale}），但用户尚未授权。请告知用户前往 设置 → 工具权限管理 开启。"
        }
    }
}
