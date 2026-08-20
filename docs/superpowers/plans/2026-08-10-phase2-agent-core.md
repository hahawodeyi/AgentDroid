# AgentDroid Phase 2 — Agent 核心层 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 实现 Agent 核心层 — 意图分类、任务规划、执行循环，让用户输入指令后 LLM 生成操作计划并逐步执行

**Architecture:** IntentClassifier（关键词预筛 → AGENT/CHAT）→ TaskPlanner（LLM 生成 JSON 计划）→ ExecutionLoop（解析 target → 坐标 → 执行 → 验证）。target 字段作为 LLM 文字描述与坐标之间的桥梁：TaskPlanner 生成 Tap(0,0) 占位 + target 文字，ExecutionLoop 通过 findElement 解析为真实坐标。

**Tech Stack:** Kotlin + kotlinx.serialization + Coroutines + JUnit4 + MockK

---

## File Structure

### Create:
- `agent/Intent.kt` — Intent 枚举 + 关键词列表
- `agent/IntentClassifier.kt` — 意图分类（纯函数，可测试）
- `agent/TaskPlanner.kt` — LLM 生成 JSON 操作计划
- `agent/PlanJsonParser.kt` — JSON 解析为 TaskPlan（纯函数，可测试）
- `agent/ExecutionLoop.kt` — 逐步执行 + target 解析 + 状态管理
- `ui/PlanConfirmCard.kt` — 计划确认卡片 UI

### Modify:
- `ui/ChatViewModel.kt` — 接入意图路由 + agent 执行流程
- `ui/ChatScreen.kt` — 集成计划确认卡片

---

## Task 1: Intent 枚举 + IntentClassifier

**Files:**
- Create: `app/src/main/java/com/appia/ai/agent/Intent.kt`
- Create: `app/src/main/java/com/appia/ai/agent/IntentClassifier.kt`
- Test: `app/src/test/java/com/appia/ai/agent/IntentClassifierTest.kt`

- [ ] **Step 1: 写 Intent 枚举**

```kotlin
package com.appia.ai.agent

enum class Intent { CHAT, AGENT, AMBIGUOUS }
```

- [ ] **Step 2: 写 IntentClassifier**

```kotlin
package com.appia.ai.agent

object IntentClassifier {

    private val agentKeywords = listOf(
        "打开", "启动", "发", "发送", "搜索", "查找", "拨", "打电话",
        "设置", "关闭", "添加", "删除", "点击", "输入", "滑动", "滚动",
        "返回", "回到桌面", "截图", "复制", "粘贴", "分享", "转发",
        "回复", "新建", "创建", "编辑", "修改", "清空", "退出"
    )

    fun classify(text: String): Intent {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Intent.CHAT

        val matched = agentKeywords.count { keyword ->
            trimmed.contains(keyword, ignoreCase = true)
        }

        return when {
            matched >= 1 -> Intent.AGENT
            else -> Intent.CHAT
        }
    }
}
```

- [ ] **Step 3: 写测试**

```kotlin
package com.appia.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentClassifierTest {
    @Test
    fun empty_text_returns_chat() {
        assertEquals(Intent.CHAT, IntentClassifier.classify(""))
        assertEquals(Intent.CHAT, IntentClassifier.classify("   "))
    }

    @Test
    fun keyword_send_triggers_agent() {
        assertEquals(Intent.AGENT, IntentClassifier.classify("给妈妈发微信说今晚不回家"))
    }

    @Test
    fun keyword_open_triggers_agent() {
        assertEquals(Intent.AGENT, IntentClassifier.classify("打开设置"))
    }

    @Test
    fun keyword_search_triggers_agent() {
        assertEquals(Intent.AGENT, IntentClassifier.classify("搜索附近的餐厅"))
    }

    @Test
    fun no_keyword_returns_chat() {
        assertEquals(Intent.CHAT, IntentClassifier.classify("今天天气怎么样"))
        assertEquals(Intent.CHAT, IntentClassifier.classify("你好"))
    }

    @Test
    fun keyword_input_triggers_agent() {
        assertEquals(Intent.AGENT, IntentClassifier.classify("输入 hello world"))
    }

    @Test
    fun keyword_back_triggers_agent() {
        assertEquals(Intent.AGENT, IntentClassifier.classify("返回上一页"))
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `cd AgentDroid && ./gradlew test --tests "com.appia.ai.agent.IntentClassifierTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, 7 tests passed

- [ ] **Step 5: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/agent/ app/src/test/java/com/appia/ai/agent/IntentClassifierTest.kt
git commit -m "feat: add IntentClassifier with keyword pre-filter"
```

---

## Task 2: PlanJsonParser — JSON 解析为 TaskPlan

**Files:**
- Create: `app/src/main/java/com/appia/ai/agent/PlanJsonParser.kt`
- Test: `app/src/test/java/com/appia/ai/agent/PlanJsonParserTest.kt`

- [ ] **Step 1: 写 PlanJsonParser**

```kotlin
package com.appia.ai.agent

import com.appia.ai.model.AgentAction
import com.appia.ai.model.Direction
import com.appia.ai.model.ExecutionStep
import com.appia.ai.model.TaskPlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class JsonStep(
    val action: String = "",
    val target: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val seconds: Float? = null,
    val description: String = ""
)

@Serializable
private data class JsonPlan(
    val goal: String = "",
    val steps: List<JsonStep> = emptyList()
)

object PlanJsonParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawJson: String): TaskPlan? {
        return try {
            val cleaned = cleanJson(rawJson)
            val plan = json.decodeFromString<JsonPlan>(cleaned)
            val steps = plan.steps.mapIndexedNotNull { _, s ->
                parseStep(s)
            }
            if (steps.isEmpty()) return null
            TaskPlan(goal = plan.goal, steps = steps)
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanJson(raw: String): String {
        var result = raw.trim()
        val start = result.indexOf("{")
        val end = result.lastIndexOf("}")
        if (start > 0 || end < result.length - 1) {
            if (start >= 0 && end > start) {
                result = result.substring(start, end + 1)
            }
        }
        return result
    }

    private fun parseStep(s: JsonStep): ExecutionStep? {
        val action = when (s.action.lowercase().trim()) {
            "tap", "click" -> AgentAction.Tap(0, 0)
            "input", "type" -> {
                val text = s.text ?: return null
                AgentAction.Input(text)
            }
            "back" -> AgentAction.Back
            "home" -> AgentAction.Home
            "scroll" -> {
                val dir = parseDirection(s.direction) ?: Direction.DOWN
                AgentAction.Scroll(dir)
            }
            "wait" -> AgentAction.Wait(s.seconds ?: 1.0f)
            else -> return null
        }
        return ExecutionStep(
            action = action,
            description = s.description,
            target = s.target,
        )
    }

    private fun parseDirection(dir: String?): Direction? {
        return when (dir?.lowercase()?.trim()) {
            "up" -> Direction.UP
            "down" -> Direction.DOWN
            "left" -> Direction.LEFT
            "right" -> Direction.RIGHT
            else -> null
        }
    }
}
```

- [ ] **Step 2: 写测试**

```kotlin
package com.appia.ai.agent

import com.appia.ai.model.AgentAction
import com.appia.ai.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlanJsonParserTest {
    @Test
    fun parse_valid_plan_with_tap_and_input() {
        val json = """
            {"goal":"给妈妈发微信","steps":[
                {"action":"tap","target":"搜索","description":"点击搜索"},
                {"action":"input","text":"妈妈","description":"输入联系人名"},
                {"action":"tap","target":"妈妈","description":"点击联系人"}
            ]}
        """.trimIndent()
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        assertEquals("给妈妈发微信", plan!!.goal)
        assertEquals(3, plan.steps.size)
        assertEquals("搜索", plan.steps[0].target)
        assert(plan.steps[1].action is AgentAction.Input)
        assertEquals("妈妈", (plan.steps[1].action as AgentAction.Input).text)
    }

    @Test
    fun parse_click_alias_works() {
        val json = """{"goal":"test","steps":[{"action":"click","target":"OK"}]}"""
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        assert(plan!!.steps[0].action is AgentAction.Tap)
    }

    @Test
    fun parse_scroll_with_direction() {
        val json = """{"goal":"test","steps":[{"action":"scroll","direction":"up","description":"向上滚动"}]}"""
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        val action = plan!!.steps[0].action as AgentAction.Scroll
        assertEquals(Direction.UP, action.direction)
    }

    @Test
    fun parse_back_and_home() {
        val json = """{"goal":"test","steps":[{"action":"back","description":"返回"},{"action":"home","description":"桌面"}]}"""
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        assert(plan!!.steps[0].action is AgentAction.Back)
        assert(plan.steps[1].action is AgentAction.Home)
    }

    @Test
    fun parse_wait_with_seconds() {
        val json = """{"goal":"test","steps":[{"action":"wait","seconds":2.5}]}"""
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        val action = plan!!.steps[0].action as AgentAction.Wait
        assertEquals(2.5f, action.seconds, 0.01f)
    }

    @Test
    fun parse_invalid_json_returns_null() {
        assertNull(PlanJsonParser.parse("not json"))
        assertNull(PlanJsonParser.parse(""))
    }

    @Test
    fun parse_empty_steps_returns_null() {
        val json = """{"goal":"test","steps":[]}"""
        assertNull(PlanJsonParser.parse(json))
    }

    @Test
    fun parse_json_with_markdown_fence() {
        val json = """
            ```json
            {"goal":"test","steps":[{"action":"back","description":"返回"}]}
            ```
        """.trimIndent()
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `cd AgentDroid && ./gradlew test --tests "com.appia.ai.agent.PlanJsonParserTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, 8 tests passed

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/agent/PlanJsonParser.kt app/src/test/java/com/appia/ai/agent/PlanJsonParserTest.kt
git commit -m "feat: add PlanJsonParser to parse LLM JSON into TaskPlan"
```

---

## Task 3: TaskPlanner — LLM 生成操作计划

**Files:**
- Create: `app/src/main/java/com/appia/ai/agent/TaskPlanner.kt`

- [ ] **Step 1: 写 TaskPlanner**

```kotlin
package com.appia.ai.agent

import com.appia.ai.llm.ChatMessage
import com.appia.ai.llm.LLMProvider
import com.appia.ai.llm.ModelConfig
import com.appia.ai.model.TaskPlan

class TaskPlanner {

    private val systemPrompt = """你是一个 Android 手机操作规划助手。用户会用自然语言描述想做的事情，你需要把它拆解成具体的操作步骤。

可用的操作类型：
- tap: 点击屏幕元素（需提供 target：元素的文字描述）
- input: 在输入框输入文字（需提供 text）
- back: 返回键
- home: 回到桌面
- scroll: 滑动屏幕（需提供 direction: up/down/left/right）
- wait: 等待页面加载（需提供 seconds: 等待秒数）

请返回 JSON 格式的操作计划，格式如下：
{"goal":"用户目标简述","steps":[{"action":"tap","target":"元素文字","description":"这一步在做什么"},{"action":"input","text":"要输入的内容","description":"说明"}]}

规则：
1. 只返回 JSON，不要包含其他文字
2. target 用屏幕上可见的文字描述（如"搜索"、"发送"、"妈妈"）
3. 步骤要具体可执行，不要包含用户无法理解的操作
4. 如果是发消息类操作，最后一步通常是 tap 发送按钮
5. 如果需要打开某个 App，第一步 tap App 名称或图标"""

    suspend fun plan(
        instruction: String,
        provider: LLMProvider,
        config: ModelConfig
    ): TaskPlan? {
        val messages = listOf(
            ChatMessage.system(systemPrompt),
            ChatMessage.user(instruction)
        )

        val fullResponse = StringBuilder()
        provider.chat(messages, config).collect { chunk ->
            if (chunk.isEmpty()) return@collect
            fullResponse.append(chunk)
        }

        return PlanJsonParser.parse(fullResponse.toString())
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/agent/TaskPlanner.kt
git commit -m "feat: add TaskPlanner for LLM-generated operation plans"
```

---

## Task 4: ExecutionLoop — 逐步执行 + target 解析

**Files:**
- Create: `app/src/main/java/com/appia/ai/agent/ExecutionLoop.kt`
- Create: `app/src/main/java/com/appia/ai/agent/ExecutionResult.kt`

- [ ] **Step 1: 写 ExecutionResult**

```kotlin
package com.appia.ai.agent

import com.appia.ai.model.ExecutionStep

enum class ExecutionStatus { SUCCESS, PARTIAL, FAILED, CANCELLED }

data class ExecutionResult(
    val status: ExecutionStatus,
    val completedSteps: Int,
    val totalSteps: Int,
    val steps: List<ExecutionStep>,
    val message: String
) {
    val isSuccess: Boolean get() = status == ExecutionStatus.SUCCESS
    val summary: String
        get() = "$message ($completedSteps/$totalSteps 步完成)"
}

interface ExecutionCallbacks {
    fun onStepStart(step: ExecutionStep, index: Int)
    fun onStepDone(step: ExecutionStep, index: Int, success: Boolean)
    fun onProgress(message: String)
    suspend fun onTargetNotFound(target: String): Boolean
}
```

- [ ] **Step 2: 写 ExecutionLoop**

```kotlin
package com.appia.ai.agent

import com.appia.ai.model.AgentAction
import com.appia.ai.model.ExecutionStep
import com.appia.ai.model.StepStatus
import com.appia.ai.model.TaskPlan
import com.appia.ai.service.AgentAccessibilityService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExecutionLoop(
    private val service: AgentAccessibilityService
) {
    private val mutex = Mutex()
    private var isPaused = false
    private var isCancelled = false

    suspend fun execute(
        plan: TaskPlan,
        callbacks: ExecutionCallbacks
    ): ExecutionResult {
        val steps = plan.steps.map { it.copy(status = StepStatus.PENDING) }.toMutableList()
        var completed = 0

        for ((index, step) in steps.withIndex()) {
            if (isCancelled) {
                steps[index] = step.copy(status = StepStatus.SKIPPED)
                return buildResult(ExecutionStatus.CANCELLED, completed, steps, plan, "任务已取消")
            }

            mutex.withLock { while (isPaused && !isCancelled) { } }
            if (isCancelled) continue

            steps[index] = step.copy(status = StepStatus.RUNNING)
            callbacks.onStepStart(steps[index], index)

            val resolvedAction = resolveTarget(step)
            if (resolvedAction == null) {
                callbacks.onProgress("找不到元素: ${step.target}")
                val shouldSkip = callbacks.onTargetNotFound(step.target ?: "")
                steps[index] = steps[index].copy(status = StepStatus.FAILED)
                callbacks.onStepDone(steps[index], index, false)
                if (!shouldSkip) {
                    return buildResult(ExecutionStatus.FAILED, completed, steps, plan, "步骤 ${index + 1} 失败: 找不到 ${step.target}")
                }
                continue
            }

            val success = try {
                service.executeAction(resolvedAction)
            } catch (_: Exception) {
                false
            }

            steps[index] = steps[index].copy(
                action = resolvedAction,
                status = if (success) StepStatus.DONE else StepStatus.FAILED
            )
            callbacks.onStepDone(steps[index], index, success)

            if (success) {
                completed++
                kotlinx.coroutines.delay(1000)
            } else {
                callbacks.onProgress("步骤 ${index + 1} 执行失败")
            }
        }

        val status = if (completed == plan.steps.size) ExecutionStatus.SUCCESS
                     else ExecutionStatus.PARTIAL
        return buildResult(status, completed, steps, plan, "执行完成")
    }

    private suspend fun resolveTarget(step: ExecutionStep): AgentAction? {
        val target = step.target ?: return step.action
        val element = service.findElement(target) ?: return null
        return when (val action = step.action) {
            is AgentAction.Tap -> AgentAction.Tap(element.centerX, element.centerY)
            else -> action
        }
    }

    fun pause() { isPaused = true }
    fun resume() { isPaused = false }
    fun stop() { isCancelled = true; isPaused = false }

    private fun buildResult(
        status: ExecutionStatus,
        completed: Int,
        steps: List<ExecutionStep>,
        plan: TaskPlan,
        message: String
    ): ExecutionResult {
        return ExecutionResult(
            status = status,
            completedSteps = completed,
            totalSteps = plan.steps.size,
            steps = steps,
            message = message
        )
    }
}
```

- [ ] **Step 3: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/agent/ExecutionResult.kt app/src/main/java/com/appia/ai/agent/ExecutionLoop.kt
git commit -m "feat: add ExecutionLoop with target resolution and step-by-step execution"
```

---

## Task 5: PlanConfirmCard — 计划确认 UI

**Files:**
- Create: `app/src/main/java/com/appia/ai/ui/PlanConfirmCard.kt`

- [ ] **Step 1: 写 PlanConfirmCard**

```kotlin
package com.appia.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appia.ai.model.ExecutionStep
import com.appia.ai.model.TaskPlan

@Composable
fun PlanConfirmCard(
    plan: TaskPlan,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "操作计划",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "目标: ${plan.goal}",
                style = MaterialTheme.typography.bodyMedium
            )
            plan.steps.forEachIndexed { index, step ->
                StepRow(index + 1, step)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("确认执行")
                }
            }
        }
    }
}

@Composable
private fun StepRow(index: Int, step: ExecutionStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$index.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            step.description.ifEmpty { step.action.actionName },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/ui/PlanConfirmCard.kt
git commit -m "feat: add PlanConfirmCard UI for plan confirmation"
```

---

## Task 6: ChatViewModel 接入意图路由 + Agent 执行

**Files:**
- Modify: `app/src/main/java/com/appia/ai/ui/ChatViewModel.kt`

- [ ] **Step 1: 添加 agent 相关状态和方法到 ChatViewModel**

在 `ChatUiState` data class 添加字段:
```kotlin
val pendingPlan: com.appia.ai.model.TaskPlan? = null,
val executionResult: com.appia.ai.agent.ExecutionResult? = null,
val isExecuting: Boolean = false
```

在 `ChatViewModel` 类中添加:
```kotlin
import com.appia.ai.agent.ExecutionLoop
import com.appia.ai.agent.ExecutionResult
import com.appia.ai.agent.ExecutionStatus
import com.appia.ai.agent.IntentClassifier
import com.appia.ai.agent.TaskPlanner
import com.appia.ai.model.TaskPlan
import com.appia.ai.service.ServiceBridge
```

添加属性:
```kotlin
private val taskPlanner = TaskPlanner()
private var executionLoop: ExecutionLoop? = null
```

修改 `send()` 方法 — 在发送消息前先分类意图:
```kotlin
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
        com.appia.ai.agent.Intent.CHAT -> handleChat(text, config)
        com.appia.ai.agent.Intent.AGENT -> handleAgent(text, config)
        com.appia.ai.agent.Intent.AMBIGUOUS -> {
            _uiState.value = _uiState.value.copy(
                error = "你是想让我操作手机吗？请更明确地描述操作意图。"
            )
        }
    }
}
```

添加 `handleChat` 方法（从原 send 的逻辑提取）:
```kotlin
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
```

添加 `handleAgent` 方法:
```kotlin
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
```

添加 `confirmPlan` 方法:
```kotlin
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

    viewModelScope.launch {
        val result = loop.execute(plan, object : com.appia.ai.agent.ExecutionCallbacks {
            override fun onStepStart(step: com.appia.ai.model.ExecutionStep, index: Int) {
                _uiState.value = _uiState.value.copy(
                    streamingContent = "执行步骤 ${index + 1}/${plan.steps.size}: ${step.description}"
                )
            }

            override fun onStepDone(step: com.appia.ai.model.ExecutionStep, index: Int, success: Boolean) {
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
        })

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
```

添加 `cancelPlan` 方法:
```kotlin
fun cancelPlan() {
    _uiState.value = _uiState.value.copy(pendingPlan = null)
}
```

添加 `stopExecution` 方法:
```kotlin
fun stopExecution() {
    executionLoop?.stop()
    _uiState.value = _uiState.value.copy(isExecuting = false)
}
```

- [ ] **Step 2: 编译验证**

Run: `cd AgentDroid && ./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 运行测试**

Run: `cd AgentDroid && ./gradlew test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/ui/ChatViewModel.kt
git commit -m "feat: integrate intent routing and agent execution into ChatViewModel"
```

---

## Task 7: ChatScreen 集成计划确认卡片

**Files:**
- Modify: `app/src/main/java/com/appia/ai/ui/ChatScreen.kt`

- [ ] **Step 1: 在 ChatScreen 中集成 PlanConfirmCard**

在 LazyColumn 的 items 之后、streamingContent 之前，添加 pendingPlan 展示:

```kotlin
state.pendingPlan?.let { plan ->
    item {
        PlanConfirmCard(
            plan = plan,
            onConfirm = viewModel::confirmPlan,
            onCancel = viewModel::cancelPlan
        )
    }
}
```

在输入区域，当 isExecuting 时禁用输入并显示停止按钮:

在输入框下方添加执行状态提示:
```kotlin
if (state.isExecuting) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            state.streamingContent,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = viewModel::stopExecution) {
            Text("停止")
        }
    }
}
```

在发送按钮的 enabled 条件中添加 `!state.isExecuting`:
```kotlin
IconButton(
    onClick = viewModel::send,
    enabled = state.inputText.isNotBlank() && !state.isStreaming && !state.isExecuting
)
```

- [ ] **Step 2: 编译验证**

Run: `cd AgentDroid && ./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 运行测试**

Run: `cd AgentDroid && ./gradlew test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/ui/ChatScreen.kt
git commit -m "feat: integrate PlanConfirmCard and execution status into ChatScreen"
```

---

## Task 8: 全量编译 + 全部测试

- [ ] **Step 1: 运行所有单元测试**

Run: `cd AgentDroid && ./gradlew test 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL, all tests passed

- [ ] **Step 2: 编译 debug APK**

Run: `cd AgentDroid && ./gradlew assembleDebug 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 验证 APK**

Run: `ls -lh app/build/outputs/apk/debug/app-debug.apk`
Expected: 文件存在

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add -A
git commit -m "build: verify Phase 2 compilation and tests pass"
```
