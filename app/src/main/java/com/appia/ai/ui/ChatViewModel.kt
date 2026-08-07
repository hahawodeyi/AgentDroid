package com.appia.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appia.ai.data.SettingsRepository
import com.appia.ai.llm.ChatMessage
import com.appia.ai.llm.ModelConfig
import com.appia.ai.llm.ModelRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val activeConfig: ModelConfig? = null,
    val error: String? = null
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)

    private val _uiState = MutableStateFlow(ChatUiState(activeConfig = settings.getActiveConfig()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val configs: StateFlow<List<ModelConfig>> = settings.configs
    val activeConfigId: StateFlow<String> = settings.activeConfigId

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun send() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isStreaming) return

        val config = settings.getActiveConfig()
        if (config == null || !config.isValid) {
            _uiState.value = _uiState.value.copy(error = "请先在设置中配置模型和 API Key")
            return
        }

        val userMsg = ChatMessage.user(text)
        val currentMessages = _uiState.value.messages + userMsg

        _uiState.value = _uiState.value.copy(
            messages = currentMessages,
            inputText = "",
            isStreaming = true,
            streamingContent = "",
            error = null
        )

        viewModelScope.launch {
            val provider = ModelRegistry.get(config)
            val systemPrompt = ChatMessage.system(
                "你是 AgentDroid，一个运行在 Android 设备上的智能助手。请简洁、准确地回答用户问题。"
            )
            val messages = listOf(systemPrompt) + currentMessages

            try {
                val fullResponse = StringBuilder()
                provider.chat(messages, config).collect { chunk ->
                    if (chunk.startsWith("[ERROR]")) {
                        _uiState.value = _uiState.value.copy(
                            isStreaming = false,
                            error = chunk.removePrefix("[ERROR] ").trim(),
                            streamingContent = ""
                        )
                        return@collect
                    }
                    if (chunk.isEmpty()) return@collect
                    fullResponse.append(chunk)
                    _uiState.value = _uiState.value.copy(streamingContent = fullResponse.toString())
                }

                val assistantMsg = ChatMessage.assistant(fullResponse.toString())
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + assistantMsg,
                    isStreaming = false,
                    streamingContent = ""
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    error = e.message ?: "请求失败",
                    streamingContent = ""
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun refreshActiveConfig() {
        _uiState.value = _uiState.value.copy(activeConfig = settings.getActiveConfig())
    }

    fun saveConfigs(list: List<ModelConfig>) = settings.saveConfigs(list)
    fun saveActiveConfig(id: String) = settings.saveActiveConfig(id)
    fun presets() = ModelRegistry.presets()
}
