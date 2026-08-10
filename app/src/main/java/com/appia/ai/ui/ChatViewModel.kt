package com.appia.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appia.ai.agent.ExecutionCallbacks
import com.appia.ai.agent.PopupInfo
import com.appia.ai.agent.ExecutionLoop
import com.appia.ai.agent.ExecutionResult
import com.appia.ai.agent.ExecutionStatus
import com.appia.ai.agent.Intent
import com.appia.ai.agent.SafetyDecision
import com.appia.ai.agent.IntentClassifier
import com.appia.ai.agent.TaskPlanner
import com.appia.ai.data.SettingsRepository
import com.appia.ai.llm.ChatMessage
import com.appia.ai.llm.ModelConfig
import com.appia.ai.llm.ModelRegistry
import com.appia.ai.model.ExecutionStep
import com.appia.ai.model.TaskPlan
import com.appia.ai.service.FloatingOverlayService
import com.appia.ai.service.ServiceBridge
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
    val error: String? = null,
    val isAccessibilityReady: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val pendingPlan: TaskPlan? = null,
    val executionResult: ExecutionResult? = null,
    val isExecuting: Boolean = false
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)

    private val _uiState = MutableStateFlow(ChatUiState(activeConfig = settings.getActiveConfig()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val configs: StateFlow<List<ModelConfig>> = settings.configs
    val activeConfigId: StateFlow<String> = settings.activeConfigId

    private val taskPlanner = TaskPlanner()
    private var executionLoop: ExecutionLoop? = null

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun send() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isStreaming || _uiState.value.isExecuting) return

        val config = settings.getActiveConfig()
        if (config == null || !config.isValid) {
            _uiState.value = _uiState.value.copy(error = "请先在设置中配置模型和 API Key")
            return
        }

        val intent = IntentClassifier.classify(text)

        when (intent) {
            Intent.CHAT -> handleChat(text, config)
            Intent.AGENT -> handleAgent(text, config)
            Intent.AMBIGUOUS -> {
                _uiState.value = _uiState.value.copy(
                    error = "你是想让我操作手机吗？请更明确地描述操作意图。"
                )
            }
        }
    }

    private fun handleChat(text: String, config: ModelConfig) {
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

    private fun handleAgent(text: String, config: ModelConfig) {
        val userMsg = ChatMessage.user(text)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            inputText = "",
            isStreaming = true,
            streamingContent = "正在生成操作计划...",
            error = null
        )

        viewModelScope.launch {
            val provider = ModelRegistry.get(config)
            val plan = taskPlanner.plan(text, provider, config)

            if (plan == null) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    streamingContent = "",
                    error = "无法生成操作计划，请重试或换个描述方式"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isStreaming = false,
                streamingContent = "",
                pendingPlan = plan
            )
        }
    }

    fun confirmPlan() {
        val plan = _uiState.value.pendingPlan ?: return
        val service = ServiceBridge.service

        if (service == null) {
            _uiState.value = _uiState.value.copy(
                pendingPlan = null,
                error = "无障碍服务未开启，请先开启权限"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            pendingPlan = null,
            isExecuting = true
        )

        val loop = ExecutionLoop(service)
        executionLoop = loop

        if (FloatingOverlayService.canDrawOverlays(getApplication())) {
            FloatingOverlayService.start(getApplication(), plan.steps.size)
        }

        viewModelScope.launch {
            val result = loop.execute(plan, object : ExecutionCallbacks {
                override fun onStepStart(step: ExecutionStep, index: Int) {
                    _uiState.value = _uiState.value.copy(
                        streamingContent = "执行步骤 ${index + 1}/${plan.steps.size}: ${step.description}"
                    )
                }

                override fun onStepDone(step: ExecutionStep, index: Int, success: Boolean) {
                    _uiState.value = _uiState.value.copy(
                        streamingContent = if (success)
                            "步骤 ${index + 1} 完成"
                        else
                            "步骤 ${index + 1} 失败"
                    )
                }

                override fun onProgress(message: String) {
                    _uiState.value = _uiState.value.copy(streamingContent = message)
                }

                override suspend fun onTargetNotFound(target: String): Boolean {
                    return false
                }
                override suspend fun onSafetyCheck(step: ExecutionStep, decision: SafetyDecision): Boolean {
                    return true
                }
                override suspend fun onPopupDetected(popup: PopupInfo): Boolean {
                    return true
                }
            })

            FloatingOverlayService.stop(getApplication())

            val statusText = when (result.status) {
                ExecutionStatus.SUCCESS -> "✅ ${result.summary}"
                ExecutionStatus.PARTIAL -> "⚠️ ${result.summary}"
                ExecutionStatus.FAILED -> "❌ ${result.summary}"
                ExecutionStatus.CANCELLED -> "🚫 ${result.summary}"
            }

            _uiState.value = _uiState.value.copy(
                isExecuting = false,
                executionResult = result,
                streamingContent = "",
                messages = _uiState.value.messages + ChatMessage.assistant(statusText)
            )
            executionLoop = null
        }
    }

    fun cancelPlan() {
        _uiState.value = _uiState.value.copy(pendingPlan = null)
    }

    fun stopExecution() {
        executionLoop?.stop()
        FloatingOverlayService.stop(getApplication())
        _uiState.value = _uiState.value.copy(isExecuting = false)
    }

    fun pauseExecution() {
        executionLoop?.pause()
    }

    fun resumeExecution() {
        executionLoop?.resume()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun checkAccessibilityReady() {
        _uiState.value = _uiState.value.copy(isAccessibilityReady = ServiceBridge.isReady.value)
    }

    fun checkOverlayPermission() {
        _uiState.value = _uiState.value.copy(
            canDrawOverlays = FloatingOverlayService.canDrawOverlays(getApplication())
        )
    }

    fun refreshActiveConfig() {
        _uiState.value = _uiState.value.copy(activeConfig = settings.getActiveConfig())
    }

    fun saveConfigs(list: List<ModelConfig>) = settings.saveConfigs(list)
    fun saveActiveConfig(id: String) = settings.saveActiveConfig(id)
    fun presets() = ModelRegistry.presets()
}
