package com.appia.ai.tool

import android.content.Context
import com.appia.ai.appia.AppiaClient
import com.appia.ai.appia.AppiaConfigStore
import com.appia.ai.appia.AppiaRoom
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadAppiaMessagesTool : Tool {

    override val id = "read_appia_messages"
    override val displayName = "读取 Appia 消息"
    override val description = "读取 Appia（Rocket.Chat）消息。不传 target 时读取有未读消息的房间；传 @用户名 或 #群组名 时读取该房间最近消息。"
    override val permission: ToolPermission? = null

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("target") {
                put("type", "string")
                put("description", "可选。房间：@用户名（私聊）或 #群组名（群聊）；不填则读取有未读的房间")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "每个房间读取的最近消息条数，默认 10，最大 50")
            }
        }
    }

    override suspend fun execute(args: JsonObject, context: Context): ToolResult {
        val target = args["target"]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null }
        val limit = (args["limit"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, 50)
        if (target != null && !target.startsWith("@") && !target.startsWith("#")) {
            return ToolResult.Failure("target 必须以 @（私聊）或 #（群聊）开头，例如 @zhangsan 或 #项目群；或者不传 target 读取未读")
        }

        val config = AppiaConfigStore(context).load()
        if (!config.isConfigured) {
            return ToolResult.Failure("尚未配置 Appia 连接。请告知用户前往 设置 → Appia 连接 填写服务器地址和令牌")
        }

        return try {
            val result = AppiaClient(config).readMessages(target, limit)
            if (result.isEmpty()) {
                val hint = if (target != null) "找不到房间 $target（可用 #群组名 或 @用户名 再试）" else "当前没有未读消息"
                ToolResult.Success(hint)
            } else {
                ToolResult.Success(format(result))
            }
        } catch (e: AppiaClient.ApiException) {
            ToolResult.Failure("Appia 读取失败：${AppiaClient.friendlyApiError(e.message ?: "")}")
        } catch (e: Exception) {
            ToolResult.Failure("Appia 网络请求失败：${e.message}")
        }
    }

    internal fun format(result: Map<AppiaRoom, List<com.appia.ai.appia.AppiaMessage>>): String {
        val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        for ((room, messages) in result) {
            sb.append("【${room.name}】（未读 ${room.unread} 条）\n")
            if (messages.isEmpty()) {
                sb.append("（无消息）\n")
            } else {
                for (msg in messages) {
                    val time = if (msg.timestamp > 0) timeFormat.format(Date(msg.timestamp)) else "--"
                    sb.append("[$time] ${msg.sender}: ${msg.text}\n")
                }
            }
        }
        return sb.toString().trimEnd()
    }
}
