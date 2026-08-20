package com.appia.ai.tool

import android.content.Context
import android.provider.Settings
import com.appia.ai.model.AgentAction
import com.appia.ai.model.Direction
import com.appia.ai.service.ServiceBridge
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val accessibilityPermission = ToolPermission(
    manifestPermission = ToolPermission.ACCESSIBILITY,
    title = "无障碍服务",
    rationale = "用于读取屏幕内容并执行点击、输入等操作",
    isRuntime = false,
    settingsIntentAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
)

private fun noService(): ToolResult.PermissionRequired =
    ToolResult.PermissionRequired(accessibilityPermission)

class ReadScreenTool : Tool {

    override val id = "read_screen"
    override val displayName = "读取屏幕"
    override val description = "读取当前屏幕上的 UI 元素列表，返回每个元素的文本、描述和位置。需要先开启无障碍服务。操作屏幕前先调用它了解当前界面。"
    override val permission = accessibilityPermission

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("clickable_only") {
                put("type", "boolean")
                put("description", "为 true 时只返回可点击的元素，默认 false")
            }
        }
    }

    override suspend fun execute(args: JsonObject, context: Context): ToolResult {
        val service = ServiceBridge.service ?: return noService()
        val clickableOnly = args["clickable_only"]?.jsonPrimitive?.contentOrNull == "true"
        val elements = if (clickableOnly) service.getClickableElements() else service.getScreenElements()
        val visible = elements.filter { it.hasIdentifier }
        if (visible.isEmpty()) {
            return ToolResult.Success("屏幕上没有可识别的 UI 元素（可能当前界面不支持无障碍读取）")
        }
        val lines = visible.take(50).map { element ->
            "[${element.bounds.left},${element.bounds.top} - ${element.bounds.right},${element.bounds.bottom}] " +
                "${element.displayText}${if (element.clickable) "（可点击）" else ""}"
        }
        return ToolResult.Success(
            "当前屏幕共 ${visible.size} 个元素${if (visible.size > 50) "（仅列出前 50 个）" else ""}：\n" +
                lines.joinToString("\n")
        )
    }
}

class ScreenActionTool : Tool {

    override val id = "screen_action"
    override val displayName = "屏幕操作"
    override val description = "对屏幕执行一个操作：点击坐标、点击包含某文本的元素、输入文字、滚动、返回、回主页、等待。执行前通常先用 read_screen 确认目标位置。涉及支付、发送消息、删除等不可逆操作时，必须先征得用户明确同意。"
    override val permission = accessibilityPermission

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "操作类型：tap（按坐标点击）、tap_text（点击含指定文本的元素）、input（在当前输入框输入）、scroll、back、home、wait")
            }
            putJsonObject("x") {
                put("type", "integer")
                put("description", "tap 动作的横坐标")
            }
            putJsonObject("y") {
                put("type", "integer")
                put("description", "tap 动作的纵坐标")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "tap_text 的目标文本，或 input 要输入的内容")
            }
            putJsonObject("direction") {
                put("type", "string")
                put("description", "scroll 的方向：up / down / left / right，默认 down")
            }
            putJsonObject("seconds") {
                put("type", "number")
                put("description", "wait 的等待秒数，默认 1")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("action"))
        }
    }

    override suspend fun execute(args: JsonObject, context: Context): ToolResult {
        val service = ServiceBridge.service ?: return noService()
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
            ?: return ToolResult.Failure("缺少参数 action")

        val agentAction = when (action) {
            "tap" -> {
                val x = args["x"]?.jsonPrimitive?.intOrNull
                val y = args["y"]?.jsonPrimitive?.intOrNull
                if (x == null || y == null) return ToolResult.Failure("tap 需要整数参数 x 和 y")
                AgentAction.Tap(x, y)
            }
            "tap_text" -> {
                val target = args["text"]?.jsonPrimitive?.contentOrNull?.trim()
                if (target.isNullOrEmpty()) return ToolResult.Failure("tap_text 需要参数 text")
                val element = service.findElement(target)
                    ?: return ToolResult.Failure("屏幕上找不到包含「$target」的元素，可先用 read_screen 查看当前界面")
                AgentAction.Tap(element.centerX, element.centerY)
            }
            "input" -> {
                val text = args["text"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult.Failure("input 需要参数 text")
                AgentAction.Input(text)
            }
            "scroll" -> {
                val direction = when (args["direction"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                    "up" -> Direction.UP
                    "left" -> Direction.LEFT
                    "right" -> Direction.RIGHT
                    else -> Direction.DOWN
                }
                AgentAction.Scroll(direction)
            }
            "back" -> AgentAction.Back
            "home" -> AgentAction.Home
            "wait" -> AgentAction.Wait(args["seconds"]?.jsonPrimitive?.floatOrNull ?: 1f)
            else -> return ToolResult.Failure("未知的 action：$action，支持 tap / tap_text / input / scroll / back / home / wait")
        }

        val success = service.executeAction(agentAction)
        return if (success) {
            ToolResult.Success("已执行 $action。界面可能已变化，可用 read_screen 查看最新状态。")
        } else {
            ToolResult.Failure("$action 执行失败（系统拒绝了该操作）")
        }
    }
}
