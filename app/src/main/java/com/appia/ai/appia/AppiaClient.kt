package com.appia.ai.appia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class AppiaRoom(
    val rid: String,
    val name: String,
    val type: String,
    val unread: Int
)

data class AppiaMessage(
    val sender: String,
    val text: String,
    val timestamp: Long
)

class AppiaClient(
    private val config: AppiaConfig,
    private val client: OkHttpClient = defaultClient()
) {

    class ApiException(message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sendMessage(target: String, text: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("channel", target)
            put("text", text)
        }.toString().toRequestBody(JSON_MEDIA)

        val request = Request.Builder()
            .url("${config.serverUrl}/api/v1/chat.postMessage")
            .addHeader("X-Auth-Token", config.authToken)
            .addHeader("X-User-Id", config.userId)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException("HTTP ${response.code}: ${extractError(responseBody)}")
            }
            val obj = try {
                json.parseToJsonElement(responseBody).jsonObject
            } catch (_: Exception) {
                throw ApiException("响应不是合法 JSON")
            }
            if (obj["success"]?.jsonPrimitive?.content != "true") {
                throw ApiException(extractError(responseBody).ifEmpty { "发送失败" })
            }
            obj["channel"]?.jsonObject?.get("_id")?.jsonPrimitive?.content
                ?: obj["message"]?.jsonObject?.get("rid")?.jsonPrimitive?.content
                ?: target
        }
    }

    suspend fun listRooms(): List<AppiaRoom> = withContext(Dispatchers.IO) {
        val body = get("${config.serverUrl}/api/v1/subscriptions.get")
        val update = body["update"]
        if (update == null || update !is kotlinx.serialization.json.JsonArray) {
            if (body["success"]?.jsonPrimitive?.contentOrNull == "true") return@withContext emptyList()
            throw ApiException(extractError(body.toString()).ifEmpty { "获取订阅列表失败" })
        }
        update.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val rid = obj["rid"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            AppiaRoom(
                rid = rid,
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: rid,
                type = obj["t"]?.jsonPrimitive?.contentOrNull ?: "c",
                unread = obj["unread"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }
    }

    suspend fun readHistory(room: AppiaRoom, count: Int): List<AppiaMessage> =
        withContext(Dispatchers.IO) {
            val endpoint = when (room.type) {
                "d" -> "im.history"
                "p" -> "groups.history"
                else -> "channels.history"
            }
            val body = get("${config.serverUrl}/api/v1/$endpoint?roomId=${room.rid}&count=$count")
            val messages = body["messages"]
            if (messages == null || messages !is kotlinx.serialization.json.JsonArray) {
                if (body["success"]?.jsonPrimitive?.contentOrNull == "true") return@withContext emptyList()
                throw ApiException(extractError(body.toString()).ifEmpty { "获取消息失败" })
            }
            messages.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val text = obj["msg"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val sender = obj["u"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull
                    ?: "unknown"
                val ts = obj["ts"]?.jsonObject?.get("\$date")?.jsonPrimitive?.longOrNull ?: 0L
                AppiaMessage(sender = sender, text = text, timestamp = ts)
            }.sortedBy { it.timestamp }
        }

    suspend fun readMessages(target: String?, limit: Int): Map<AppiaRoom, List<AppiaMessage>> =
        withContext(Dispatchers.IO) {
            val rooms = listRooms()
            val selected = if (target != null) {
                val name = target.removePrefix("@").removePrefix("#").lowercase()
                rooms.filter { it.name.lowercase() == name }
            } else {
                rooms.filter { it.unread > 0 }.sortedByDescending { it.unread }.take(5)
            }
            if (selected.isEmpty()) return@withContext emptyMap()
            val result = LinkedHashMap<AppiaRoom, List<AppiaMessage>>()
            for (room in selected) {
                result[room] = readHistory(room, limit)
            }
            result
        }

    private fun get(url: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .addHeader("X-Auth-Token", config.authToken)
            .addHeader("X-User-Id", config.userId)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException("HTTP ${response.code}: ${extractError(responseBody)}")
            }
            return try {
                json.parseToJsonElement(responseBody).jsonObject
            } catch (_: Exception) {
                throw ApiException("响应不是合法 JSON")
            }
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        internal fun extractError(body: String): String {
            if (body.isBlank()) return ""
            return try {
                val obj = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                obj["error"]?.jsonPrimitive?.content
                    ?: obj["message"]?.jsonPrimitive?.content
                    ?: ""
            } catch (_: Exception) {
                body.take(200)
            }
        }

        internal fun friendlyApiError(message: String): String {
            val unauthorized = message.contains("HTTP 401") ||
                message.contains("You must be logged in", ignoreCase = true)
            return if (unauthorized) {
                "鉴权失败：X-User-Id 必须填个人访问令牌页面显示的 User ID（不是登录用户名），并确认令牌完整且未删除"
            } else {
                message
            }
        }
    }
}
