# AgentDroid Phase 4 — 鲁棒性 + 扩展 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 提升 Agent 在真实环境中的可靠性 — 超时重试、意外弹窗检测、重规划能力，并扩展操作集

**Architecture:** ExecutionLoop 增加超时包装 + 重试 + 连续失败追踪。PopupDetector 在每步执行前检查屏幕是否有意外弹窗。Replan 在步骤失败时让 LLM 重新生成剩余计划。新增 LongPress/Swipe/LaunchApp 三种动作。

**Tech Stack:** Kotlin + Coroutines + JUnit4 + MockK

---

## File Structure

### Create:
- `agent/PopupDetector.kt` — 意外弹窗检测（纯函数，可测试）
- `agent/Replanner.kt` — 失败时重新规划

### Modify:
- `model/AgentAction.kt` — 新增 LongPress, Swipe, LaunchApp
- `agent/PlanJsonParser.kt` — 解析新动作类型
- `service/AgentAccessibilityService.kt` — 实现新动作
- `agent/ExecutionLoop.kt` — 超时 + 重试 + 连续失败 + 弹窗检测 + 重规划
- `agent/ExecutionResult.kt` — 新增回调方法
- `agent/TaskPlanner.kt` — 添加 replan 方法

---

## Task 1: 新增 AgentAction 类型 + Service 实现

**Files:**
- Modify: `app/src/main/java/com/appia/ai/model/AgentAction.kt`
- Modify: `app/src/main/java/com/appia/ai/service/AgentAccessibilityService.kt`
- Test: `app/src/test/java/com/appia/ai/model/AgentActionExtendedTest.kt`

- [ ] **Step 1: 扩展 AgentAction sealed class**

在 `AgentAction.kt` 的 sealed class body 中新增三种动作，并更新 `actionName`:
```kotlin
    data class LongPress(val x: Int, val y: Int, val durationMs: Long = 1000) : AgentAction()
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val durationMs: Long = 300) : AgentAction()
    data class LaunchApp(val packageName: String) : AgentAction()
```

在 `actionName` 的 when 分支添加:
```kotlin
            is LongPress -> "long_press"
            is Swipe -> "swipe"
            is LaunchApp -> "launch_app"
```

- [ ] **Step 2: 在 AgentAccessibilityService 实现新动作**

在 `executeAction` 的 when 分支添加:
```kotlin
            is AgentAction.LongPress -> performLongPress(action.x, action.y, action.durationMs)
            is AgentAction.Swipe -> performSwipe(action.startX, action.startY, action.endX, action.endY, action.durationMs)
            is AgentAction.LaunchApp -> performLaunchApp(action.packageName)
```

添加实现方法:
```kotlin
    private fun performLongPress(x: Int, y: Int, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun performLaunchApp(packageName: String): Boolean {
        val launcher = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launcher)
        return true
    }
```

添加 import: `import android.content.Intent`

- [ ] **Step 3: 写测试**

```kotlin
package com.appia.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentActionExtendedTest {
    @Test
    fun long_press_has_correct_name() {
        val action = AgentAction.LongPress(100, 200)
        assertEquals("long_press", action.actionName)
        assertEquals(1000, action.durationMs)
    }

    @Test
    fun long_press_custom_duration() {
        val action = AgentAction.LongPress(50, 50, 2000)
        assertEquals(2000, action.durationMs)
    }

    @Test
    fun swipe_has_correct_name() {
        val action = AgentAction.Swipe(0, 100, 200, 300)
        assertEquals("swipe", action.actionName)
    }

    @Test
    fun launch_app_has_correct_name() {
        val action = AgentAction.LaunchApp("com.tencent.mm")
        assertEquals("launch_app", action.actionName)
        assertEquals("com.tencent.mm", action.packageName)
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `./gradlew :app:testDebugUnitTest --tests "com.appia.ai.model.AgentActionExtendedTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/model/AgentAction.kt app/src/main/java/com/appia/ai/service/AgentAccessibilityService.kt app/src/test/java/com/appia/ai/model/AgentActionExtendedTest.kt
git commit -m "feat: add LongPress, Swipe, LaunchApp actions"
```

---

## Task 2: PlanJsonParser 支持新动作

**Files:**
- Modify: `app/src/main/java/com/appia/ai/agent/PlanJsonParser.kt`
- Modify: `app/src/test/java/com/appia/ai/agent/PlanJsonParserTest.kt`

- [ ] **Step 1: 在 PlanJsonParser 添加新动作解析**

在 JsonStep data class 添加字段:
```kotlin
    val packageName: String? = null,
    val startX: Int? = null,
    val startY: Int? = null,
    val endX: Int? = null,
    val endY: Int? = null,
    val duration: Long? = null,
```

在 `parseStep` 的 when 分支添加:
```kotlin
            "long_press", "longpress" -> {
                val x = s.target // long_press 也可以用 target 定位
                AgentAction.LongPress(0, 0, s.duration ?: 1000)
            }
            "swipe" -> {
                AgentAction.Swipe(
                    s.startX ?: 500, s.startY ?: 1000,
                    s.endX ?: 500, s.endY ?: 300,
                    s.duration ?: 300
                )
            }
            "launch", "launch_app", "open_app" -> {
                val pkg = s.packageName ?: s.target ?: return null
                AgentAction.LaunchApp(pkg)
            }
```

- [ ] **Step 2: 添加测试**

```kotlin
    @Test
    fun parse_long_press() {
        val json = """{"goal":"test","steps":[{"action":"long_press","target":"图片","description":"长按图片","duration":2000}]}"""
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        assert(plan!!.steps[0].action is AgentAction.LongPress)
        assertEquals(2000, (plan.steps[0].action as AgentAction.LongPress).durationMs)
    }

    @Test
    fun parse_swipe_with_coordinates() {
        val json = """{"goal":"test","steps":[{"action":"swipe","startX":100,"startY":500,"endX":100,"endY":100,"description":"上滑"}]}"""
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        val action = plan!!.steps[0].action as AgentAction.Swipe
        assertEquals(100, action.startX)
        assertEquals(500, action.startY)
    }

    @Test
    fun parse_launch_app() {
        val json = """{"goal":"test","steps":[{"action":"launch_app","packageName":"com.tencent.mm","description":"打开微信"}]}"""
        val plan = PlanJsonParser.parse(json)
        assertNotNull(plan)
        val action = plan!!.steps[0].action as AgentAction.LaunchApp
        assertEquals("com.tencent.mm", action.packageName)
    }
```

- [ ] **Step 3: 运行测试**

Run: `./gradlew :app:testDebugUnitTest --tests "com.appia.ai.agent.PlanJsonParserTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/agent/PlanJsonParser.kt app/src/test/java/com/appia/ai/agent/PlanJsonParserTest.kt
git commit -m "feat: add LongPress, Swipe, LaunchApp parsing to PlanJsonParser"
```

---

## Task 3: PopupDetector — 意外弹窗检测

**Files:**
- Create: `app/src/main/java/com/appia/ai/agent/PopupDetector.kt`
- Test: `app/src/test/java/com/appia/ai/agent/PopupDetectorTest.kt`

- [ ] **Step 1: 写 PopupDetector**

```kotlin
package com.appia.ai.agent

import com.appia.ai.model.ScreenElement

data class PopupInfo(
    val isPopup: Boolean,
    val dismissText: String?,
    val description: String
)

object PopupDetector {

    private val popupKeywords = listOf(
        "关闭", "取消", "我知道了", "知道了", "确定",
        "暂不", "以后再说", "跳过", "不再提醒", "忽略",
        "拒绝", "不允许", "不再显示"
    )

    private val popupClassNames = listOf(
        "android.app.Dialog",
        "android.widget.PopupWindow",
        "AlertDialog"
    )

    fun detect(elements: List<ScreenElement>): PopupInfo {
        if (elements.isEmpty()) return PopupInfo(false, null, "屏幕为空")

        // 检查是否有弹窗特征的元素
        val hasPopupClass = elements.any { el ->
            popupClassNames.any { cls -> el.className.contains(cls, ignoreCase = true) }
        }

        // 查找可关闭的按钮
        val dismissButton = elements.firstOrNull { el ->
            el.clickable && popupKeywords.any { kw ->
                el.text.contains(kw, ignoreCase = true) ||
                el.contentDesc.contains(kw, ignoreCase = true)
            }
        }

        val dismissText = dismissButton?.text ?: dismissButton?.contentDesc

        // 如果有弹窗类元素 或 有关闭关键词的可点击元素
        if (hasPopupClass || dismissButton != null) {
            return PopupInfo(
                isPopup = true,
                dismissText = dismissText,
                description = if (dismissText != null)
                    "检测到弹窗，可关闭按钮: $dismissText"
                else
                    "检测到弹窗，但未找到关闭按钮"
            )
        }

        return PopupInfo(false, null, "无弹窗")
    }
}
```

- [ ] **Step 2: 写测试**

```kotlin
package com.appia.ai.agent

import com.appia.ai.model.ScreenElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupDetectorTest {
    @Test
    fun empty_screen_no_popup() {
        val result = PopupDetector.detect(emptyList())
        assertFalse(result.isPopup)
    }

    @Test
    fun close_button_detected_as_popup() {
        val elements = listOf(
            ScreenElement(text = "广告内容", clickable = false, index = 0),
            ScreenElement(text = "关闭", clickable = true, index = 1)
        )
        val result = PopupDetector.detect(elements)
        assertTrue(result.isPopup)
        assertEquals("关闭", result.dismissText)
    }

    @Test
    fun cancel_button_detected_as_popup() {
        val elements = listOf(
            ScreenElement(text = "允许通知？", clickable = false, index = 0),
            ScreenElement(text = "取消", clickable = true, index = 1),
            ScreenElement(text = "确定", clickable = true, index = 2)
        )
        val result = PopupDetector.detect(elements)
        assertTrue(result.isPopup)
        assertNotNull(result.dismissText)
    }

    @Test
    fun normal_screen_no_popup() {
        val elements = listOf(
            ScreenElement(text = "微信", clickable = true, className = "android.widget.TextView", index = 0),
            ScreenElement(text = "通讯录", clickable = true, className = "android.widget.TextView", index = 1)
        )
        val result = PopupDetector.detect(elements)
        assertFalse(result.isPopup)
    }

    @Test
    fun dialog_class_detected_as_popup() {
        val elements = listOf(
            ScreenElement(text = "更新提示", className = "android.app.Dialog", clickable = false, index = 0),
            ScreenElement(text = "暂不更新", clickable = true, index = 1)
        )
        val result = PopupDetector.detect(elements)
        assertTrue(result.isPopup)
        assertEquals("暂不更新", result.dismissText)
    }

    @Test
    fun popup_without_dismiss_button() {
        val elements = listOf(
            ScreenElement(text = "加载中...", className = "android.app.Dialog", clickable = false, index = 0)
        )
        val result = PopupDetector.detect(elements)
        assertTrue(result.isPopup)
        assertEquals(null, result.dismissText)
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `./gradlew :app:testDebugUnitTest --tests "com.appia.ai.agent.PopupDetectorTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, 6 tests passed

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/agent/PopupDetector.kt app/src/test/java/com/appia/ai/agent/PopupDetectorTest.kt
git commit -m "feat: add PopupDetector for unexpected dialog detection"
```

---

## Task 4: ExecutionLoop 超时 + 重试 + 弹窗检测 + 重规划

**Files:**
- Modify: `app/src/main/java/com/appia/ai/agent/ExecutionResult.kt`
- Modify: `app/src/main/java/com/appia/ai/agent/ExecutionLoop.kt`
- Create: `app/src/main/java/com/appia/ai/agent/Replanner.kt`

- [ ] **Step 1: 在 ExecutionCallbacks 添加弹窗检测回调**

在 `ExecutionResult.kt` 的 interface 添加:
```kotlin
    suspend fun onPopupDetected(popup: PopupInfo): Boolean
```

- [ ] **Step 2: 写 Replanner**

```kotlin
package com.appia.ai.agent

import com.appia.ai.llm.ChatMessage
import com.appia.ai.llm.LLMProvider
import com.appia.ai.llm.ModelConfig
import com.appia.ai.model.TaskPlan

class Replanner {

    private val systemPrompt = """你是一个 Android 手机操作规划助手。之前的操作计划部分失败了，你需要根据剩余目标和当前状态重新规划。

可用的操作类型：
- tap: 点击（需 target）
- input: 输入文字（需 text）
- back: 返回
- home: 桌面
- scroll: 滑动（需 direction: up/down/left/right）
- wait: 等待（需 seconds）
- long_press: 长按（需 target, 可选 duration）
- swipe: 滑动手势（需 startX/startY/endX/endY）
- launch_app: 打开应用（需 packageName）

返回 JSON 格式：
{"goal":"目标","steps":[{"action":"tap","target":"文字","description":"说明"}]}

只返回 JSON，不要其他文字。"""

    suspend fun replan(
        originalGoal: String,
        failedStep: String,
        remainingSteps: String,
        provider: LLMProvider,
        config: ModelConfig
    ): TaskPlan? {
        val userMsg = "原始目标: $originalGoal\n失败的步骤: $failedStep\n剩余计划: $remainingSteps\n\n请重新规划剩余步骤。"

        val messages = listOf(
            ChatMessage.system(systemPrompt),
            ChatMessage.user(userMsg)
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

- [ ] **Step 3: 修改 ExecutionLoop 添加超时 + 重试 + 弹窗检测**

在 ExecutionLoop 中添加常量和状态:
```kotlin
    companion object {
        private const val STEP_TIMEOUT_MS = 15000L
        private const val MAX_RETRIES = 1
        private const val MAX_CONSECUTIVE_FAILURES = 2
    }

    private var consecutiveFailures = 0
```

在 `executeAction` 调用处包装超时:
```kotlin
import kotlinx.coroutines.withTimeoutOrNull

// 替换原来的 val success = try { ... }
val success = try {
    withTimeoutOrNull(STEP_TIMEOUT_MS) {
        service.executeAction(resolvedAction)
    } ?: false
} catch (_: Exception) {
    false
}
```

在每步执行前添加弹窗检测（在 `callbacks.onStepStart` 之后）:
```kotlin
// 弹窗检测
val screenElements = service.getScreenElements()
val popup = PopupDetector.detect(screenElements)
if (popup.isPopup) {
    callbacks.onProgress("检测到弹窗: ${popup.description}")
    val shouldDismiss = callbacks.onPopupDetected(popup)
    if (shouldDismiss && popup.dismissText != null) {
        val dismissElement = service.findElement(popup.dismissText)
        if (dismissElement != null) {
            service.executeAction(AgentAction.Tap(dismissElement.centerX, dismissElement.centerY))
            delay(500)
        }
    }
}
```

在步骤失败后添加重试和重规划逻辑:
```kotlin
if (!success) {
    // 重试一次
    if (retries < MAX_RETRIES) {
        callbacks.onProgress("步骤 ${index + 1} 重试中...")
        delay(1000)
        // 重新解析 target 并重试
        val retryAction = resolveTarget(steps[index])
        if (retryAction != null) {
            val retrySuccess = try {
                withTimeoutOrNull(STEP_TIMEOUT_MS) {
                    service.executeAction(retryAction)
                } ?: false
            } catch (_: Exception) { false }

            if (retrySuccess) {
                steps[index] = steps[index].copy(
                    action = retryAction,
                    status = StepStatus.DONE
                )
                callbacks.onStepDone(steps[index], index, true)
                completed++
                consecutiveFailures = 0
                delay(1000)
                continue
            }
        }
    }

    consecutiveFailures++
    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
        callbacks.onProgress("连续失败 $consecutiveFailures 次，暂停任务")
        return buildResult(ExecutionStatus.FAILED, completed, steps, plan, "连续失败，已暂停")
    }
} else {
    consecutiveFailures = 0
}
```

注意：需要在 ChatViewModel 的 ExecutionCallbacks 实现中添加 `onPopupDetected` 方法。

- [ ] **Step 4: 编译验证**

Run: `./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (可能需要在 ChatViewModel 中补充 onPopupDetected 实现)

- [ ] **Step 5: 运行测试**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 6: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/agent/ app/src/main/java/com/appia/ai/ui/ChatViewModel.kt
git commit -m "feat: add timeout, retry, popup detection, and replan to ExecutionLoop"
```

---

## Task 5: 全量编译 + 全部测试

- [ ] **Step 1: 运行所有单元测试**

Run: `./gradlew :app:testDebugUnitTest 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL, all tests passed

- [ ] **Step 2: 编译 debug APK**

Run: `./gradlew assembleDebug 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 验证 APK**

Run: `ls -lh app/build/outputs/apk/debug/app-debug.apk`
Expected: 文件存在

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add -A
git commit -m "build: verify Phase 4 compilation and tests pass"
```
