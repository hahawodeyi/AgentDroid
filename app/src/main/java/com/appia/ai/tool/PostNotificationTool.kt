package com.appia.ai.tool

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.appia.ai.R
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class PostNotificationTool : Tool {

    override val id = "post_notification"
    override val displayName = "发送通知"
    override val description = "在系统通知栏发送一条通知。需要提供标题和内容。"
    override val permission = ToolPermission(
        manifestPermission = Manifest.permission.POST_NOTIFICATIONS,
        title = "通知权限",
        rationale = "用于在通知栏发送提醒消息",
        isRuntime = true
    )

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "通知标题")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "通知正文内容")
            }
        }
        putJsonArray("required") {
            add(kotlinx.serialization.json.JsonPrimitive("title"))
            add(kotlinx.serialization.json.JsonPrimitive("content"))
        }
    }

    override suspend fun execute(args: JsonObject, context: Context): ToolResult {
        if (!PermissionChecker.isGranted(context, permission)) {
            return ToolResult.PermissionRequired(permission!!)
        }
        val title = args["title"]?.jsonPrimitive?.content
        val content = args["content"]?.jsonPrimitive?.content
        if (title.isNullOrBlank() || content.isNullOrBlank()) {
            return ToolResult.Failure("参数无效：title 和 content 不能为空")
        }

        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 10000).toInt(), notification)
            ToolResult.Success("通知已发送：$title")
        } catch (e: SecurityException) {
            ToolResult.PermissionRequired(permission!!)
        } catch (e: Exception) {
            ToolResult.Failure("发送通知失败: ${e.message}")
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Agent 提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Agent 主动发出的提醒通知" }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "agent_alerts"
        private const val NOTIFICATION_ID_BASE = 40000
    }
}
