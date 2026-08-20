package com.appia.ai.tool

import android.content.Context
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

interface Tool {
    val id: String
    val displayName: String
    val description: String
    val permission: ToolPermission?
    val parametersSchema: JsonObject

    suspend fun execute(args: JsonObject, context: Context): ToolResult
}

fun Tool.toFunctionSpec(): JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", id)
        put("description", description)
        put("parameters", parametersSchema)
    }
}
