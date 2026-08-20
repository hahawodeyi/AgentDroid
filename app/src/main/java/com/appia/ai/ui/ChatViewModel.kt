package com.appia.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appia.ai.agent.ExecutionCallbacks
import com.appia.ai.agent.AgentEngine
import com.appia.ai.agent.AgentMode
import com.appia.ai.agent.PopupInfo
import com.appia.ai.agent.PromptJsonEngine
import com.appia.ai.agent.SafetyDecision
import com.appia.ai.service.OverlayBridge
import kotlinx.coroutines.CompletableDeferred
import com.appia.ai.agent.ExecutionLoop
import com.appia.ai.agent.ExecutionResult
import com.appia.ai.agent.ExecutionStatus
import com.appia.ai.appia.AppiaConfig
import com.appia.ai.appia.AppiaConfigStore
import com.appia.ai.data.SettingsRepository
import com.appia.ai.llm.ChatMessage
import com.appia.ai.llm.ModelConfig
import com.appia.ai.llm.ModelRegistry
import com.appia.ai.model.ExecutionStep
import com.appia.ai.model.TaskPlan
import com.appia.ai.model.TraceStep
import com.appia.ai.service.FloatingOverlayService
import com.appia.ai.service.ServiceBridge
import com.appia.ai.tool.PermissionChecker
import com.appia.ai.tool.Tool
import com.appia.ai.tool.ToolRegistry
import com.appia.ai.tool.ToolSettingsStore
import com.appia.ai.trigger.TriggerConfig
import com.appia.ai.trigger.TriggerScheduler
import com.appia.ai.trigger.TriggerStore
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
    val isExecuting: Boolean = false,
    val pendingSafetyCheck: Pair<ExecutionStep, SafetyDecision>? = null,
    val currentTrace: List<TraceStep> = emptyList()
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)

    private val _uiState = MutableStateFlow(ChatUiState(activeConfig = settings.getActiveConfig()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val configs: StateFlow<List<ModelConfig>> = settings.configs
    val activeConfigId: StateFlow<String> = settings.activeConfigId

    private var executionLoop: ExecutionLoop? = null
    private var safetyDeferred: CompletableDeferred<Boolean>? = null

    private val toolRegistry = ToolRegistry.createDefault()
    private val toolStore = ToolSettingsStore(app)
    private val appPrefs = app.getSharedPreferences("agentdroid_prefs", android.content.Context.MODE_PRIVATE)

    private val _agentMode = MutableStateFlow(
        AgentMode.fromKey(appPrefs.getString(KEY_AGENT_MODE, null))
    )
    val agentMode: StateFlow<AgentMode> = _agentMode.asStateFlow()

    fun setAgentMode(mode: AgentMode) {
        appPrefs.edit().putString(KEY_AGENT_MODE, mode.prefKey).apply()
        _agentMode.value = mode
    }

    private val triggerStore = TriggerStore(app)

    private val _triggerConfig = MutableStateFlow(triggerStore.load())
    val triggerConfig: StateFlow<TriggerConfig> = _triggerConfig.asStateFlow()

    fun saveTriggerConfig(config: TriggerConfig) {
        triggerStore.save(config)
        _triggerConfig.value = config
        if (config.enabled) {
            TriggerScheduler.scheduleNext(getApplication(), config)
        } else {
            TriggerScheduler.cancel(getApplication())
        }
    }

    private val appiaStore = AppiaConfigStore(app)

    private val _appiaConfig = MutableStateFlow(appiaStore.load())
    val appiaConfig: StateFlow<AppiaConfig> = _appiaConfig.asStateFlow()

    fun saveAppiaConfig(config: AppiaConfig) {
        appiaStore.save(config)
        _appiaConfig.value = appiaStore.load()
    }

    private val _tools = MutableStateFlow(toolRegistry.all())
    val tools: StateFlow<List<Tool>> = _tools.asStateFlow()

    val toolEnabledMap: StateFlow<Map<String, Boolean>> = toolStore.enabledMap

    private val _toolPermissionGranted = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val toolPermissionGranted: StateFlow<Map<String, Boolean>> = _toolPermissionGranted.asStateFlow()

    init {
        refreshToolPermissions()
    }

    fun refreshToolPermissions() {
        val context = getApplication<Application>()
        _toolPermissionGranted.value = toolRegistry.all().associate { tool ->
            tool.id to PermissionChecker.isGranted(context, tool.permission)
        }
    }

    fun setToolEnabled(toolId: String, enabled: Boolean) {
        toolStore.setEnabled(toolId, enabled)
    }

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

        handleAgentConversation(text, config)
    }

    private fun handleAgentConversation(text: String, config: ModelConfig) {
        val userMsg = ChatMessage.user(text)
        val history = _uiState.value.messages + userMsg

        _uiState.value = _uiState.value.copy(
            messages = history,
            inputText = "",
            isStreaming = true,
            streamingContent = "",
            currentTrace = emptyList(),
            error = null
        )

        viewModelScope.launch {
            val provider = ModelRegistry.get(config)
            val onTextDelta: (String) -> Unit = { delta ->
                _uiState.value = _uiState.value.copy(
                    streamingContent = _uiState.value.streamingContent + delta
                )
            }
            val onTrace: (TraceStep) -> Unit = { step ->
                _uiState.value = _uiState.value.copy(
                    currentTrace = _uiState.value.currentTrace + step
                )
            }

            try {
                val finalText = when (_agentMode.value) {
                    AgentMode.NATIVE_TOOLS -> AgentEngine(
                        provider = provider,
                        config = config,
                        toolRegistry = toolRegistry,
                        toolStore = toolStore,
                        context = getApplication()
                    ).run(history, onTextDelta, onTrace)
                    AgentMode.PROMPT_JSON -> PromptJsonEngine(
                        provider = provider,
                        config = config,
                        toolRegistry = toolRegistry,
                        toolStore = toolStore,
                        context = getApplication()
                    ).run(history, onTextDelta, onTrace)
                }

                val trace = _uiState.value.currentTrace.ifEmpty { null }
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + ChatMessage.assistant(finalText).copy(trace = trace),
                    isStreaming = false,
                    streamingContent = "",
                    currentTrace = emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    error = e.message ?: "请求失败",
                    streamingContent = "",
                    currentTrace = emptyList()
                )
            }
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

        val config = settings.getActiveConfig()
        val loop = ExecutionLoop(service, config)
        executionLoop = loop

        if (FloatingOverlayService.canDrawOverlays(getApplication())) {
            FloatingOverlayService.start(getApplication(), plan.steps.size)
        }
        OverlayBridge.bind(
            onPause = { executionLoop?.pause() },
            onResume = { executionLoop?.resume() },
            onStop = { stopExecution() }
        )

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
                    val deferred = CompletableDeferred<Boolean>()
                    safetyDeferred = deferred
                    _uiState.value = _uiState.value.copy(pendingSafetyCheck = Pair(step, decision))
                    val result = deferred.await()
                    _uiState.value = _uiState.value.copy(pendingSafetyCheck = null)
                    safetyDeferred = null
                    return result
                }
                override suspend fun onPopupDetected(popup: PopupInfo): Boolean {
                    return true
                }
            })

            OverlayBridge.unbind()
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

    fun confirmSafetyCheck() {
        safetyDeferred?.complete(true)
    }

    fun denySafetyCheck() {
        safetyDeferred?.complete(false)
    }

    fun cancelPlan() {
        _uiState.value = _uiState.value.copy(pendingPlan = null)
    }

    fun stopExecution() {
        executionLoop?.stop()
        OverlayBridge.unbind()
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

    companion object {
        private const val KEY_AGENT_MODE = "agent_mode"
    }
}
