package com.appia.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class TraceStep(
    val title: String,
    val detail: String = "",
    val success: Boolean = true
)
