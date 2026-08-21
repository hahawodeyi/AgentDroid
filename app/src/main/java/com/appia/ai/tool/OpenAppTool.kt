package com.appia.ai.tool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class OpenAppTool : Tool {

    override val id = "open_app"
    override val displayName = "打开应用"
    override val description = "根据应用名称打开手机上已安装的应用，例如\"微信\"、\"相机\"。"
    override val permission: ToolPermission? = null

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("app_name") {
                put("type", "string")
                put("description", "要打开的应用名称")
            }
        }
        putJsonArray("required") {
            add(kotlinx.serialization.json.JsonPrimitive("app_name"))
        }
    }

    override suspend fun execute(args: JsonObject, context: Context): ToolResult {
        val appName = args["app_name"]?.jsonPrimitive?.content?.trim()
        if (appName.isNullOrEmpty()) {
            return ToolResult.Failure("参数无效：app_name 不能为空")
        }

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        val match = activities.firstOrNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString()
            label.equals(appName, ignoreCase = true) || label.contains(appName, ignoreCase = true)
        }

        if (match == null) {
            val available = activities
                .map { it.loadLabel(pm).toString() }
                .distinct()
                .sorted()
                .take(40)
            return ToolResult.Failure(
                "未找到名为「$appName」的应用。当前可见的应用有：${available.joinToString("、")}" +
                    "。请从这些应用中选择用户想要的（名称可能不完全一致），然后用准确名称重试。"
            )
        }

        val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchIntent == null) {
            return ToolResult.Failure("「$appName」没有可启动的入口")
        }

        return try {
            context.startActivity(launchIntent)
            val label = match.loadLabel(pm).toString()
            ToolResult.Success("已打开应用：$label")
        } catch (e: Exception) {
            ToolResult.Failure("打开应用失败: ${e.message}")
        }
    }
}
