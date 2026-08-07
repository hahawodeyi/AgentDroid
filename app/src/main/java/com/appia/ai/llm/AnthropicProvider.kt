package com.appia.ai.llm

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
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

class AnthropicProvider : LLMProvider {
    override val id = "anthropic"
    override val displayName = "Anthropic (Claude)"

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val sseFactory = EventSources.createFactory(client)

    override suspend fun chat(messages: List<ChatMessage>, config: ModelConfig): Flow<String> =
        callbackFlow {
            val systemMessage = messages.firstOrNull { it.role == "system" }?.content ?: ""
            val chatMessages = messages.filter { it.role != "system" }

            val body = buildRequestBody(systemMessage, chatMessages, config)
            val request = Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/messages")
                .addHeader("x-api-key", config.apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val eventSource = sseFactory.newEventSource(request, object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    try {
                        val obj = json.parseToJsonElement(data).jsonObject
                        val eventType = obj["type"]?.jsonPrimitive?.content

                        when (eventType) {
                            "content_block_delta" -> {
                                val delta = obj["delta"]?.jsonObject
                                val deltaType = delta?.get("type")?.jsonPrimitive?.content
                                if (deltaType == "text_delta") {
                                    val text = delta["text"]?.jsonPrimitive?.content
                                    if (!text.isNullOrEmpty()) {
                                        trySend(text)
                                    }
                                }
                            }
                            "message_stop" -> {
                                trySend("")
                                channel.close()
                            }
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
        systemMessage: String,
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
            put("max_tokens", JsonPrimitive(config.maxTokens))
            put("stream", JsonPrimitive(true))
            if (systemMessage.isNotEmpty()) {
                put("system", JsonPrimitive(systemMessage))
            }
        }

        return json.encodeToString(JsonObject.serializer(), obj)
    }
}
