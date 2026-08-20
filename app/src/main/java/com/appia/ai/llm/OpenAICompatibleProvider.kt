package com.appia.ai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenAICompatibleProvider : LLMProvider {
    override val id = "openai_compatible"
    override val displayName = "OpenAI Compatible"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun Exception.toUserMessage(): String = when (this) {
        is java.net.UnknownHostException -> "无法解析服务器地址，请检查 Base URL 是否正确、设备是否联网"
        is java.net.ConnectException -> "无法连接到服务器，请检查 Base URL 和网络"
        is java.net.SocketTimeoutException -> "连接服务器超时，请稍后重试"
        is javax.net.ssl.SSLException -> "HTTPS 证书校验失败：${message ?: "请检查服务器证书"}"
        else -> message ?: "${javaClass.simpleName}（请检查网络和 Base URL 配置）"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(messages: List<ChatMessage>, config: ModelConfig): Flow<String> =
        callbackFlow {
            val body = buildRequestBody(messages, config)
            val request = Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/chat/completions")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = client.newCall(request).execute()
                val statusCode = response.code

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    val parsedMsg = parseErrorMessage(errorBody)
                    val hint = when (statusCode) {
                        401, 403 -> "API Key 无效或无权限"
                        404 -> "API 地址错误（404）。请检查 Base URL 是否正确"
                        429 -> "请求频率超限（429）"
                        500, 502, 503 -> "服务器错误（$statusCode）"
                        else -> "请求失败（$statusCode）"
                    }
                    val balanceHint = if (parsedMsg.contains("余额") || parsedMsg.contains("balance", ignoreCase = true)) {
                        "\n💡 提示: 可能是模型名称不匹配你的资源包。请尝试 glm-4、glm-4-air 或 glm-4v 等模型名。"
                    } else ""
                    trySend("[ERROR] $hint。${if (parsedMsg.isNotEmpty()) "API返回: $parsedMsg" else "详情: $errorBody"}$balanceHint")
                    channel.close()
                    return@callbackFlow
                }

                val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: return@callbackFlow))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    if (channel.isClosedForSend) break

                    val data = line ?: ""
                    if (!data.startsWith("data:")) continue

                    val payload = data.removePrefix("data:").trim()
                    if (payload == "[DONE]") {
                        break
                    }
                    if (payload.isEmpty()) continue

                    try {
                        val obj = json.parseToJsonElement(payload).jsonObject
                        // Check for error in SSE stream
                        val sseError = obj["error"] as? JsonObject
                        if (sseError != null) {
                            val errMsg = sseError["message"]?.jsonPrimitive?.content ?: sseError.toString()
                            trySend("[ERROR] API流式错误: $errMsg")
                            break
                        }
                        val choices = obj["choices"] as? JsonArray
                        val firstChoice = choices?.firstOrNull() as? JsonObject
                        val delta = firstChoice?.get("delta") as? JsonObject
                        val content = delta?.get("content")?.jsonPrimitive?.content
                        if (!content.isNullOrEmpty()) {
                            trySend(content)
                        }
                    } catch (_: Exception) {
                        // skip malformed chunks
                    }
                }

                response.close()
                channel.close()
            } catch (e: Exception) {
                trySend("[ERROR] 网络请求失败: ${e.toUserMessage()}")
                channel.close()
            }

            awaitClose { }
        }.flowOn(Dispatchers.IO)

    override suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<JsonObject>,
        config: ModelConfig
    ): Flow<ChatEvent> = callbackFlow {
        val body = buildToolRequestBody(messages, tools, config)
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                val parsedMsg = parseErrorMessage(errorBody)
                trySend(ChatEvent.Error("请求失败（${response.code}）。${parsedMsg.ifEmpty { errorBody }}"))
                channel.close()
                return@callbackFlow
            }

            val accumulators = sortedMapOf<Int, ToolCallAccumulator>()
            val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: return@callbackFlow))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                if (channel.isClosedForSend) break

                val data = line ?: ""
                if (!data.startsWith("data:")) continue
                val payload = data.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                if (payload.isEmpty()) continue

                try {
                    val obj = json.parseToJsonElement(payload).jsonObject
                    val sseError = obj["error"] as? JsonObject
                    if (sseError != null) {
                        val errMsg = sseError["message"]?.jsonPrimitive?.content ?: sseError.toString()
                        trySend(ChatEvent.Error("API流式错误: $errMsg"))
                        break
                    }
                    val choices = obj["choices"] as? JsonArray
                    val firstChoice = choices?.firstOrNull() as? JsonObject
                    val delta = firstChoice?.get("delta") as? JsonObject ?: continue

                    delta["content"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { trySend(ChatEvent.TextDelta(it)) }

                    val toolCallDeltas = delta["tool_calls"] as? JsonArray
                    toolCallDeltas?.forEach { element ->
                        val tc = element.jsonObject
                        val index = tc["index"]?.jsonPrimitive?.intOrNull ?: 0
                        val acc = accumulators.getOrPut(index) { ToolCallAccumulator() }
                        tc["id"]?.jsonPrimitive?.contentOrNull?.let { acc.id = it }
                        val fn = tc["function"] as? JsonObject
                        fn?.get("name")?.jsonPrimitive?.contentOrNull?.let { acc.name = it }
                        fn?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { acc.arguments.append(it) }
                    }
                } catch (_: Exception) {
                    // skip malformed chunks
                }
            }

            if (accumulators.isNotEmpty()) {
                trySend(ChatEvent.ToolCallsReady(accumulators.values.map { it.toToolCall() }))
            }

            response.close()
            channel.close()
        } catch (e: Exception) {
            trySend(ChatEvent.Error("网络请求失败: ${e.toUserMessage()}"))
            channel.close()
        }

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    private class ToolCallAccumulator {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
        fun toToolCall() = ToolCall(id, name, arguments.toString())
    }

    private fun buildToolRequestBody(
        messages: List<ChatMessage>,
        tools: List<JsonObject>,
        config: ModelConfig
    ): String {
        val messagesArray = buildJsonArray {
            messages.forEach { msg ->
                add(buildJsonObject {
                    put("role", JsonPrimitive(msg.role))
                    if (msg.toolCalls != null) {
                        put("content", JsonPrimitive(msg.content))
                        putJsonArray("tool_calls") {
                            msg.toolCalls.forEach { tc ->
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(tc.id))
                                    put("type", JsonPrimitive("function"))
                                    putJsonObject("function") {
                                        put("name", JsonPrimitive(tc.name))
                                        put("arguments", JsonPrimitive(tc.argumentsJson))
                                    }
                                })
                            }
                        }
                    } else {
                        put("content", JsonPrimitive(msg.content))
                    }
                    msg.toolCallId?.let { put("tool_call_id", JsonPrimitive(it)) }
                })
            }
        }

        val obj = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("messages", messagesArray)
            put("temperature", JsonPrimitive(config.temperature))
            put("max_tokens", JsonPrimitive(config.maxTokens))
            put("stream", JsonPrimitive(true))
            if (tools.isNotEmpty()) {
                putJsonArray("tools") { tools.forEach { add(it) } }
            }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun parseErrorMessage(body: String): String {
        if (body.isBlank()) return ""
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            // OpenAI format: {"error":{"message":"..."}}
            val error = obj["error"] as? JsonObject
            if (error != null) {
                return error["message"]?.jsonPrimitive?.content ?: ""
            }
            // Zhipu format: {"err":{"msg":"..."}} or {"msg":"..."}
            val err = obj["err"] as? JsonObject
            if (err != null) {
                return err["msg"]?.jsonPrimitive?.content ?: err["message"]?.jsonPrimitive?.content ?: ""
            }
            // Simple format: {"msg":"..."} or {"message":"..."}
            obj["msg"]?.jsonPrimitive?.content ?: obj["message"]?.jsonPrimitive?.content ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        config: ModelConfig
    ): String {
        val messagesArray = buildJsonArray {
            messages.forEach { msg ->
                add(buildJsonObject {
                    put("role", JsonPrimitive(msg.role))
                    put("content", JsonPrimitive(msg.content))
                })
            }
        }

        val obj = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("messages", messagesArray)
            put("temperature", JsonPrimitive(config.temperature))
            put("max_tokens", JsonPrimitive(config.maxTokens))
            put("stream", JsonPrimitive(true))
        }

        return json.encodeToString(JsonObject.serializer(), obj)
    }
}
