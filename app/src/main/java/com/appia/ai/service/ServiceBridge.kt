package com.appia.ai.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ServiceBridge {
    private var _service: AgentAccessibilityService? = null

    val service: AgentAccessibilityService? get() = _service

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    fun bind(service: AgentAccessibilityService) {
        _service = service
        _isReady.value = true
    }

    fun unbind() {
        _service = null
        _isReady.value = false
    }
}
