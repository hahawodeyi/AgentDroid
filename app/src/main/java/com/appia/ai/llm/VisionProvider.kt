package com.appia.ai.llm

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class VisionProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun findElementByScreenshot(
        bitmap: Bitmap,
        target: String,
        config: ModelConfig
    ): Pair<Int, Int>? {
        val base64 = bitmapToBase64(bitmap)
        val responseText = sendVisionRequest(base64, target, config) ?: return null
        return parseCoordinates(responseText)
    }

    private suspend fun sendVisionRequest(
        base64Image: String,
        target: String,
        config: ModelConfig
    ): String? {
        val prompt = "你是一个 Android 屏幕分析助手。用户想点击屏幕上描述为「$target」的元素。" +
            "请仔细查看截图，找到最匹配的元素，返回其中心点坐标。" +
            "只返回 JSON 格式：{\"x\": 数字, \"y\": 数字}，不要包含其他文字。" +
            "如果找不到匹配的元素，返回：{\"x\": -1, \"y\": -1}"

        val contentArray = buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive(prompt))
            })
            add(buildJsonObject {
                put("type", JsonPrimitive("image_url"))
                put("image_url", buildJsonObject {
                    put("url", JsonPrimitive("data:image/png;base64,$base64Image"))
                })
            })
        }

        val body = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", contentArray)
                })
            })
            put("max_tokens", JsonPrimitive(200))
            put("temperature", JsonPrimitive(0.1))
        }

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            if (!response.isSuccessful) return null
            val obj = json.parseToJsonElement(responseBody) as JsonObject
            val choices = obj["choices"] as? JsonArray ?: return null
            val firstChoice = choices.firstOrNull() as? JsonObject ?: return null
            val message = firstChoice["message"] as? JsonObject ?: return null
            message["content"]?.toString()?.trim('"') ?: null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCoordinates(response: String): Pair<Int, Int>? {
        return try {
            val cleaned = response.trim()
            val start = cleaned.indexOf("{")
            val end = cleaned.lastIndexOf("}")
            if (start < 0 || end <= start) return null
            val jsonStr = cleaned.substring(start, end + 1)
            val obj = json.parseToJsonElement(jsonStr) as JsonObject
            val x = obj["x"]?.toString()?.trim('"')?.toIntOrNull() ?: return null
            val y = obj["y"]?.toString()?.trim('"')?.toIntOrNull() ?: return null
            if (x < 0 || y < 0) return null
            Pair(x, y)
        } catch (_: Exception) {
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
