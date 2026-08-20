package com.appia.ai.memory

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class MemoryEntry(
    val key: String,
    val content: String,
    val updatedAt: Long
)

class MemoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<MemoryEntry> = decode(prefs.getString(KEY_MEMORIES, null))

    fun get(key: String): MemoryEntry? = all().firstOrNull { it.key == key }

    fun put(key: String, content: String) {
        val updated = all().filterNot { it.key == key } +
            MemoryEntry(key = key, content = content, updatedAt = System.currentTimeMillis())
        prefs.edit().putString(KEY_MEMORIES, encode(updated.sortedBy { it.key })).apply()
    }

    fun remove(key: String): Boolean {
        val current = all()
        if (current.none { it.key == key }) return false
        prefs.edit().putString(KEY_MEMORIES, encode(current.filterNot { it.key == key })).apply()
        return true
    }

    fun clear() = prefs.edit().remove(KEY_MEMORIES).apply()

    fun formatForPrompt(): String {
        val entries = all()
        if (entries.isEmpty()) return ""
        return entries.joinToString("\n") { "- ${it.key}: ${it.content}" }
    }

    companion object {
        private const val PREFS_NAME = "agent_memory"
        private const val KEY_MEMORIES = "memories"

        internal val json = Json { ignoreUnknownKeys = true }

        internal fun decode(raw: String?): List<MemoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            (json.parseToJsonElement(raw) as? JsonArray)?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val key = obj["key"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val content = obj["content"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
                MemoryEntry(key, content, updatedAt)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        }

        internal fun encode(entries: List<MemoryEntry>): String = buildJsonArray {
        entries.forEach { entry ->
            add(buildJsonObject {
                put("key", entry.key)
                put("content", entry.content)
                put("updatedAt", JsonPrimitive(entry.updatedAt))
            })
        }
        }.toString()
    }
}
