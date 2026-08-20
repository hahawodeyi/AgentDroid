package com.appia.ai.appia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

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
    }
}
