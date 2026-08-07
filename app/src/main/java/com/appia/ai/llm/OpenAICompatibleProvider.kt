package com.appia.ai.llm

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class OpenAICompatibleProvider : LLMProvider {
    override val id = "openai_compatible"
    override val displayName = "OpenAI Compatible"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val sseFactory = EventSources.createFactory(client)

    override suspend fun chat(messages: List<ChatMessage>, config: ModelConfig): Flow<String> =
        callbackFlow {
            val body = buildRequestBody(messages, config)
            val request = Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/chat/completions")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val eventSource = sseFactory.newEventSource(request, object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data == "[DONE]") {
                        trySend("")
                        channel.close()
                        return
                    }
                    try {
                        val obj = json.parseToJsonElement(data) as JsonObject
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

                override fun onClosed(eventSource: EventSource) {
                    channel.close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val errorMsg = response?.body?.string() ?: t?.message ?: "Unknown error"
                    trySend("[ERROR] $errorMsg")
                    channel.close()
                }
            })

            awaitClose { eventSource.cancel() }
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
