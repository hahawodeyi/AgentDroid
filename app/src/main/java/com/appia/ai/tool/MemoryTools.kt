package com.appia.ai.tool

import android.content.Context
import com.appia.ai.memory.MemoryStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class SaveMemoryTool : Tool {

    override val id = "save_memory"
    override val displayName = "记住信息"
    override val description = "把关于用户的重要信息保存到长期记忆，例如偏好、习惯、常用地点。key 用简短英文标识（如 favorite_drink），content 是要记住的内容。相同 key 会覆盖旧值。"
    override val permission: ToolPermission? = null

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("key") {
                put("type", "string")
                put("description", "记忆的简短标识，英文小写下划线，如 favorite_drink")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "要记住的内容，用中文简述")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("key"))
            add(JsonPrimitive("content"))
        }
    }

    override suspend fun execute(args: JsonObject, context: Context): ToolResult {
        val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
        val content = args["content"]?.jsonPrimitive?.contentOrNull?.trim()
        if (key.isNullOrEmpty() || content.isNullOrEmpty()) {
            return ToolResult.Failure("参数无效：key 和 content 都不能为空")
        }
        if (!key.matches(Regex("[a-z0-9_]{1,40}"))) {
            return ToolResult.Failure("key 只能包含小写字母、数字和下划线，最长 40 字符")
        }
        MemoryStore(context).put(key, content)
        return ToolResult.Success("已记住：$key = $content")
    }
}

class RecallMemoryTool : Tool {

    override val id = "recall_memory"
    override val displayName = "回忆信息"
    override val description = "读取长期记忆中的信息。不传 key 时返回全部记忆；传 key 时只返回对应的那条。"
    override val permission: ToolPermission? = null

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("key") {
                put("type", "string")
                put("description", "可选，要查询的记忆标识")
            }
        }
    }

    override suspend fun execute(args: JsonObject, context: Context): ToolResult {
        val store = MemoryStore(context)
        val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
        if (!key.isNullOrEmpty()) {
            val entry = store.get(key)
            return if (entry != null) {
                ToolResult.Success("${entry.key}: ${entry.content}")
            } else {
                ToolResult.Success("没有找到 key 为 $key 的记忆")
            }
        }
        val entries = store.all()
        if (entries.isEmpty()) return ToolResult.Success("目前没有任何记忆")
        return ToolResult.Success(
            "共 ${entries.size} 条记忆：\n" + entries.joinToString("\n") { "- ${it.key}: ${it.content}" }
        )
    }
}

class ForgetMemoryTool : Tool {

    override val id = "forget_memory"
    override val displayName = "删除记忆"
    override val description = "删除一条长期记忆。需要用户明确要求删除某条记忆时才使用。"
    override val permission: ToolPermission? = null

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("key") {
                put("type", "string")
                put("description", "要删除的记忆标识")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("key"))
        }
    }

    override suspend fun execute(args: JsonObject, context: Context): ToolResult {
        val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
        if (key.isNullOrEmpty()) return ToolResult.Failure("参数无效：key 不能为空")
        return if (MemoryStore(context).remove(key)) {
            ToolResult.Success("已删除记忆：$key")
        } else {
            ToolResult.Success("没有找到 key 为 $key 的记忆")
        }
    }
}
