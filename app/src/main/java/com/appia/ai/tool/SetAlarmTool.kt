package com.appia.ai.tool

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class SetAlarmTool : Tool {

    override val id = "set_alarm"
    override val displayName = "设置闹钟"
    override val description = "设置系统闹钟。需要提供 24 小时制的小时和分钟，可选闹钟标签。"
    override val permission: ToolPermission? = null

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("hour") {
                put("type", "integer")
                put("description", "小时，0-23")
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("description", "分钟，0-59")
            }
            putJsonObject("label") {
                put("type", "string")
                put("description", "闹钟标签，可为空")
            }
        }
        putJsonArray("required") {
            add(kotlinx.serialization.json.JsonPrimitive("hour"))
            add(kotlinx.serialization.json.JsonPrimitive("minute"))
        }
    }

    override suspend fun execute(args: JsonObject, context: Context): ToolResult {
        val hour = args["hour"]?.jsonPrimitive?.intOrNull
        val minute = args["minute"]?.jsonPrimitive?.intOrNull
        if (hour == null || hour !in 0..23 || minute == null || minute !in 0..59) {
            return ToolResult.Failure("参数无效：hour 需为 0-23 的整数，minute 需为 0-59 的整数")
        }
        val label = args["label"]?.jsonPrimitive?.content ?: ""

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult.Success("已打开闹钟设置：${"%02d:%02d".format(hour, minute)}${if (label.isNotEmpty()) "（$label）" else ""}")
        } catch (e: Exception) {
            ToolResult.Failure("无法打开闹钟应用: ${e.message}")
        }
    }
}
