package com.appia.ai.tool

sealed class ToolResult {
    data class Success(val message: String) : ToolResult()
    data class Failure(val reason: String) : ToolResult()
    data class PermissionRequired(val permission: ToolPermission) : ToolResult()
}
