package com.appia.ai.tool

import android.content.Context
import com.appia.ai.appia.AppiaClient
import com.appia.ai.appia.AppiaConfigStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class SendAppiaMessageTool : Tool {

    override val id = "send_appia_message"
    override val displayName = "发送 Appia 消息"
    override val description = "通过 Appia（Rocket.Chat）给联系人或群组发送一条消息。target 用 @用户名 表示私聊，用 #群组名 表示群聊。"
    override val permission: ToolPermission? = null

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("target") {
                put("type", "string")
                put("description", "接收者：@用户名（私聊）或 #群组名（群聊）")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "要发送的消息内容")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("target"))
            add(JsonPrimitive("text"))
        }
    }

    override suspend fun execute(args: JsonObject, context: Context): ToolResult {
        val target = args["target"]?.jsonPrimitive?.contentOrNull?.trim()
        val text = args["text"]?.jsonPrimitive?.contentOrNull?.trim()
        if (target.isNullOrEmpty() || text.isNullOrEmpty()) {
            return ToolResult.Failure("参数无效：target 和 text 都不能为空")
        }
        if (!target.startsWith("@") && !target.startsWith("#")) {
            return ToolResult.Failure("target 必须以 @（私聊）或 #（群聊）开头，例如 @zhangsan 或 #项目群")
        }

        val config = AppiaConfigStore(context).load()
        if (!config.isConfigured) {
            return ToolResult.Failure("尚未配置 Appia 连接。请告知用户前往 设置 → Appia 连接 填写服务器地址和令牌")
        }

        return try {
            AppiaClient(config).sendMessage(target, text)
            ToolResult.Success("已发送 Appia 消息给 $target")
        } catch (e: AppiaClient.ApiException) {
            ToolResult.Failure("Appia 发送失败：${AppiaClient.friendlyApiError(e.message ?: "")}")
        } catch (e: Exception) {
            ToolResult.Failure("Appia 网络请求失败：${e.message}")
        }
    }
}
