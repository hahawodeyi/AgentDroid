# AgentDroid Phase 3 — 安全 + 悬浮窗 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 实现 SafetyGuard（敏感操作拦截）和 FloatingOverlayService（悬浮球 + 展开面板），让 Agent 执行过程安全可控、可视化

**Architecture:** SafetyGuard 作为纯函数检测敏感操作（target 包含发送/删除/转账等关键词），返回 ALLOW/CONFIRM/BLOCK。ExecutionLoop 每步执行前调用 SafetyGuard，CONFIRM 时暂停等待用户确认。FloatingOverlayService 用 WindowManager 实现悬浮窗，收起态小球 + 展开态面板。

**Tech Stack:** Kotlin + Compose + WindowManager + JUnit4 + MockK

---

## File Structure

### Create:
- `agent/SafetyGuard.kt` — 敏感操作检测（纯函数，可测试）
- `service/FloatingOverlayService.kt` — 悬浮窗服务
- `res/values/strings.xml` — 添加 overlay 相关字符串

### Modify:
- `agent/ExecutionLoop.kt` — 集成 SafetyGuard
- `agent/ExecutionResult.kt` — 添加 onSafetyCheck callback
- `ui/ChatViewModel.kt` — 启动/停止 overlay + 安全确认处理
- `AndroidManifest.xml` — 添加 overlay 权限 + 注册 service
- `ui/ChatScreen.kt` — overlay 权限检查入口

---

## Task 1: SafetyGuard — 敏感操作检测

**Files:**
- Create: `app/src/main/java/com/appia/ai/agent/SafetyGuard.kt`
- Test: `app/src/test/java/com/appia/ai/agent/SafetyGuardTest.kt`

- [ ] **Step 1: 写 SafetyGuard**

```kotlin
package com.appia.ai.agent

import com.appia.ai.model.ExecutionStep

enum class SafetyDecision { ALLOW, CONFIRM, BLOCK }

object SafetyGuard {

    private val confirmKeywords = listOf(
        "发送", "确认", "支付", "转账", "删除", "清除",
        "退出", "注销", "提交", "购买", "预订", "下单",
        "同意", "授权", "允许", "确定"
    )

    private val blockKeywords = listOf(
        "格式化", "恢复出厂", "root", "刷机"
    )

    fun check(step: ExecutionStep): SafetyDecision {
        val target = step.target?.lowercase() ?: ""
        val desc = step.description.lowercase()
        val combined = "$target $desc"

        // 1. BLOCK: 绝对禁止的操作
        if (blockKeywords.any { combined.contains(it.lowercase()) }) {
            return SafetyDecision.BLOCK
        }

        // 2. CONFIRM: 需要用户确认的敏感操作
        if (confirmKeywords.any { combined.contains(it.lowercase()) }) {
            return SafetyDecision.CONFIRM
        }

        // 3. ALLOW: 普通操作
        return SafetyDecision.ALLOW
    }
}
```

- [ ] **Step 2: 写测试**

```kotlin
package com.appia.ai.agent

import com.appia.ai.model.AgentAction
import com.appia.ai.model.ExecutionStep
import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyGuardTest {
    private fun step(target: String? = null, description: String = ""): ExecutionStep {
        return ExecutionStep(
            action = AgentAction.Tap(0, 0),
            description = description,
            target = target
        )
    }

    @Test
    fun send_button_requires_confirm() {
        assertEquals(SafetyDecision.CONFIRM, SafetyGuard.check(step(target = "发送按钮")))
    }

    @Test
    fun delete_requires_confirm() {
        assertEquals(SafetyDecision.CONFIRM, SafetyGuard.check(step(description = "删除消息")))
    }

    @Test
    fun payment_requires_confirm() {
        assertEquals(SafetyDecision.CONFIRM, SafetyGuard.check(step(target = "确认支付")))
    }

    @Test
    fun format_factory_reset_blocked() {
        assertEquals(SafetyDecision.BLOCK, SafetyGuard.check(step(description = "恢复出厂设置")))
    }

    @Test
    fun normal_navigation_allowed() {
        assertEquals(SafetyDecision.ALLOW, SafetyGuard.check(step(target = "搜索")))
        assertEquals(SafetyDecision.ALLOW, SafetyGuard.check(step(target = "返回")))
        assertEquals(SafetyDecision.ALLOW, SafetyGuard.check(step(description = "打开App")))
    }

    @Test
    fun empty_target_and_desc_allowed() {
        assertEquals(SafetyDecision.ALLOW, SafetyGuard.check(step()))
    }

    @Test
    fun submit_requires_confirm() {
        assertEquals(SafetyDecision.CONFIRM, SafetyGuard.check(step(description = "提交表单")))
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `cd AgentDroid && ./gradlew :app:testDebugUnitTest --tests "com.appia.ai.agent.SafetyGuardTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, 7 tests passed

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/agent/SafetyGuard.kt app/src/test/java/com/appia/ai/agent/SafetyGuardTest.kt
git commit -m "feat: add SafetyGuard for sensitive operation detection"
```

---

## Task 2: ExecutionLoop 集成 SafetyGuard

**Files:**
- Modify: `app/src/main/java/com/appia/ai/agent/ExecutionResult.kt`
- Modify: `app/src/main/java/com/appia/ai/agent/ExecutionLoop.kt`

- [ ] **Step 1: 在 ExecutionCallbacks 添加安全确认回调**

在 `ExecutionResult.kt` 的 `ExecutionCallbacks` interface 中添加:
```kotlin
suspend fun onSafetyCheck(step: ExecutionStep, decision: SafetyDecision): Boolean
```

完整 interface:
```kotlin
interface ExecutionCallbacks {
    fun onStepStart(step: ExecutionStep, index: Int)
    fun onStepDone(step: ExecutionStep, index: Int, success: Boolean)
    fun onProgress(message: String)
    suspend fun onTargetNotFound(target: String): Boolean
    suspend fun onSafetyCheck(step: ExecutionStep, decision: SafetyDecision): Boolean
}
```

- [ ] **Step 2: 在 ExecutionLoop 的 execute 方法中集成 SafetyGuard**

在 `resolveTarget` 之后、`executeAction` 之前添加安全检查:
```kotlin
// 安全检查
val safetyDecision = SafetyGuard.check(steps[index])
when (safetyDecision) {
    SafetyDecision.BLOCK -> {
        callbacks.onProgress("操作被安全拦截: ${step.description}")
        steps[index] = steps[index].copy(status = StepStatus.SKIPPED)
        callbacks.onStepDone(steps[index], index, false)
        return buildResult(ExecutionStatus.FAILED, completed, steps, plan, "步骤 ${index + 1} 被安全拦截")
    }
    SafetyDecision.CONFIRM -> {
        callbacks.onProgress("需要确认: ${step.description}")
        val approved = callbacks.onSafetyCheck(steps[index], safetyDecision)
        if (!approved) {
            steps[index] = steps[index].copy(status = StepStatus.SKIPPED)
            callbacks.onStepDone(steps[index], index, false)
            continue
        }
    }
    SafetyDecision.ALLOW -> { /* 正常执行 */ }
}
```

需要在文件顶部添加 import:
```kotlin
import com.appia.ai.agent.SafetyGuard
import com.appia.ai.agent.SafetyDecision
```

- [ ] **Step 3: 编译验证**

Run: `cd AgentDroid && ./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (ChatViewModel 中的 ExecutionCallbacks 实现需要补充 onSafetyCheck)

注意：ChatViewModel 中的匿名 ExecutionCallbacks 对象需要实现新方法，这会在 Task 4 中处理。如果编译失败，先在 ChatViewModel 中添加空实现:
```kotlin
override suspend fun onSafetyCheck(step: ExecutionStep, decision: SafetyDecision): Boolean {
    return true // Phase 3 Task 4 会完善此方法
}
```

- [ ] **Step 4: 运行测试**

Run: `cd AgentDroid && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/agent/ExecutionResult.kt app/src/main/java/com/appia/ai/agent/ExecutionLoop.kt app/src/main/java/com/appia/ai/ui/ChatViewModel.kt
git commit -m "feat: integrate SafetyGuard into ExecutionLoop"
```

---

## Task 3: FloatingOverlayService — 悬浮窗服务

**Files:**
- Create: `app/src/main/java/com/appia/ai/service/FloatingOverlayService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 在 AndroidManifest 添加权限和注册 Service**

在 `<uses-permission>` 区域添加:
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

在 `<application>` 块内、AccessibilityService 之后添加:
```xml
<service
    android:name=".service.FloatingOverlayService"
    android:exported="false" />
```

- [ ] **Step 2: 写 FloatingOverlayService**

```kotlin
package com.appia.ai.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.FrameLayout

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null

    private var totalSteps = 0
    private var currentStep = 0
    private var stepDescription = ""

    private var onPauseClick: (() -> Unit)? = null
    private var onStopClick: (() -> Unit)? = null
    private var isPaused = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            totalSteps = it.getIntExtra(EXTRA_TOTAL_STEPS, 0)
            currentStep = 0
            showBubble()
        }
        return START_NOT_STICKY
    }

    fun updateProgress(step: Int, total: Int, description: String) {
        currentStep = step
        totalSteps = total
        stepDescription = description
        updateBubbleText()
        updatePanelText()
    }

    fun setCallbacks(onPause: () -> Unit, onStop: () -> Unit) {
        onPauseClick = onPause
        onStopClick = onStop
    }

    private fun getLayoutType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun showBubble() {
        if (bubbleView != null) return

        val dp = resources.displayMetrics.density
        val size = (48 * dp).toInt()

        val container = FrameLayout(this).apply {
            setBackgroundColor(0xFF6750A4.toInt())
        }

        val textView = TextView(this).apply {
            text = "0/$totalSteps"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            gravity = android.view.Gravity.CENTER
        }

        container.addView(textView, FrameLayout.LayoutParams(size, size).apply {
            gravity = android.view.Gravity.CENTER
        })

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    if (panelView == null) showPanel() else hidePanel()
                    v.performClick()
                }
            }
            false
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 200
        }

        windowManager.addView(container, params)
        bubbleView = container
    }

    private fun showPanel() {
        if (panelView != null) return

        val dp = resources.displayMetrics.density
        val width = (280 * dp).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF0FFFFFF.toInt())
            setPadding(
                (16 * dp).toInt(), (12 * dp).toInt(),
                (16 * dp).toInt(), (12 * dp).toInt()
            )
        }

        val titleView = TextView(this).apply {
            text = "AgentDroid 执行中"
            textSize = 14f
            setTextColor(0xFF333333.toInt())
        }

        val progressView = TextView(this).apply {
            text = "步骤 $currentStep/$totalSteps"
            textSize = 13f
            setTextColor(0xFF666666.toInt())
        }

        val descView = TextView(this).apply {
            text = stepDescription
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            maxLines = 2
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val pauseButton = Button(this).apply {
            text = if (isPaused) "恢复" else "暂停"
            setOnClickListener {
                isPaused = !isPaused
                this.text = if (isPaused) "恢复" else "暂停"
                onPauseClick?.invoke()
            }
        }

        val stopButton = Button(this).apply {
            text = "停止"
            setOnClickListener {
                onStopClick?.invoke()
            }
        }

        val btnParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        buttonRow.addView(pauseButton, btnParams)
        buttonRow.addView(stopButton, btnParams)

        container.addView(titleView)
        container.addView(progressView)
        container.addView(descView)
        container.addView(buttonRow)

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    hidePanel()
                    v.performClick()
                }
            }
            false
        }

        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 260
        }

        windowManager.addView(container, params)
        panelView = container
    }

    private fun hidePanel() {
        panelView?.let {
            windowManager.removeView(it)
            panelView = null
        }
    }

    private fun updateBubbleText() {
        bubbleView?.let { view ->
            (view as? FrameLayout)?.getChildAt(0)?.let { child ->
                (child as? TextView)?.text = "$currentStep/$totalSteps"
            }
        }
    }

    private fun updatePanelText() {
        panelView?.let { view ->
            (view as? LinearLayout)?.let { layout ->
                if (layout.childCount >= 3) {
                    (layout.getChildAt(1) as? TextView)?.text = "步骤 $currentStep/$totalSteps"
                    (layout.getChildAt(2) as? TextView)?.text = stepDescription
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hidePanel()
        bubbleView?.let {
            windowManager.removeView(it)
            bubbleView = null
        }
    }

    companion object {
        const val EXTRA_TOTAL_STEPS = "total_steps"

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Settings.canDrawOverlays(context)
            else true
        }

        fun start(context: Context, totalSteps: Int) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                putExtra(EXTRA_TOTAL_STEPS, totalSteps)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd AgentDroid && ./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/service/FloatingOverlayService.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add FloatingOverlayService with bubble and expandable panel"
```

---

## Task 4: ChatViewModel 集成 SafetyGuard + Overlay

**Files:**
- Modify: `app/src/main/java/com/appia/ai/ui/ChatViewModel.kt`

- [ ] **Step 1: 添加 overlay 启动/停止 + 安全确认**

在 `confirmPlan()` 方法中，执行循环开始前启动 overlay:
```kotlin
// 启动悬浮窗
if (FloatingOverlayService.canDrawOverlays(getApplication())) {
    FloatingOverlayService.start(getApplication(), plan.steps.size)
}
```

在 `confirmPlan()` 的 `ExecutionCallbacks` 实现中，完善 `onSafetyCheck`:
```kotlin
override suspend fun onSafetyCheck(step: ExecutionStep, decision: SafetyDecision): Boolean {
    return true // Phase 3: 自动确认，Phase 4 会接入 UI 弹窗确认
}
```

在 `onStepStart` 中更新 overlay:
```kotlin
override fun onStepStart(step: ExecutionStep, index: Int) {
    _uiState.value = _uiState.value.copy(
        streamingContent = "执行步骤 ${index + 1}/${plan.steps.size}: ${step.description}"
    )
    // 更新悬浮窗
    FloatingOverlayService::class.java
    // 通过 ServiceBridge 或直接更新
}
```

需要在文件中添加 import:
```kotlin
import com.appia.ai.service.FloatingOverlayService
import com.appia.ai.agent.SafetyDecision
```

在 `confirmPlan` 的执行完成后停止 overlay:
```kotlin
// 停止悬浮窗
FloatingOverlayService.stop(getApplication())
```

在 `stopExecution()` 中也停止 overlay:
```kotlin
fun stopExecution() {
    executionLoop?.stop()
    FloatingOverlayService.stop(getApplication())
    _uiState.value = _uiState.value.copy(isExecuting = false)
}
```

- [ ] **Step 2: 添加 overlay 权限状态**

在 `ChatUiState` 添加:
```kotlin
val canDrawOverlays: Boolean = false
```

添加检查方法:
```kotlin
fun checkOverlayPermission() {
    _uiState.value = _uiState.value.copy(
        canDrawOverlays = FloatingOverlayService.canDrawOverlays(getApplication())
    )
}
```

- [ ] **Step 3: 编译验证**

Run: `cd AgentDroid && ./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 运行测试**

Run: `cd AgentDroid && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/ui/ChatViewModel.kt
git commit -m "feat: integrate SafetyGuard and FloatingOverlay into ChatViewModel"
```

---

## Task 5: ChatScreen 添加 overlay 权限引导

**Files:**
- Modify: `app/src/main/java/com/appia/ai/ui/ChatScreen.kt`
- Modify: `app/src/main/java/com/appia/ai/ui/PermissionGuideScreen.kt`

- [ ] **Step 1: ChatScreen 添加 overlay 权限检查**

在 `LaunchedEffect(Unit)` 中同时检查 overlay 权限:
```kotlin
LaunchedEffect(Unit) {
    viewModel.checkAccessibilityReady()
    viewModel.checkOverlayPermission()
}
```

在 topBar actions 中，当 `!state.canDrawOverlays` 时显示警告（和 accessibility 警告并列，或合并为通用的权限提示）。

在无障碍服务未开启提示下方添加:
```kotlin
if (!state.canDrawOverlays) {
    Text(
        "悬浮窗权限未开启",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    )
}
```

- [ ] **Step 2: PermissionGuideScreen 添加 overlay 权限引导**

在无障碍设置说明下方添加悬浮窗权限部分:
```kotlin
Text(
    "悬浮窗权限",
    style = MaterialTheme.typography.titleMedium
)
Text(
    "AgentDroid 需要悬浮窗权限来显示执行进度和控制按钮。\n\n" +
    "请点击下方按钮授权",
    style = MaterialTheme.typography.bodyMedium
)
Button(onClick = {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
        data = android.net.Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}) {
    Text("授权悬浮窗权限")
}
```

- [ ] **Step 3: 编译验证**

Run: `cd AgentDroid && ./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 运行测试**

Run: `cd AgentDroid && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/ui/ChatScreen.kt app/src/main/java/com/appia/ai/ui/PermissionGuideScreen.kt
git commit -m "feat: add overlay permission check and guide"
```

---

## Task 6: 全量编译 + 全部测试

- [ ] **Step 1: 运行所有单元测试**

Run: `cd AgentDroid && ./gradlew :app:testDebugUnitTest 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL, all tests passed (34 existing + 7 new = 41 total)

- [ ] **Step 2: 编译 debug APK**

Run: `cd AgentDroid && ./gradlew assembleDebug 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 验证 APK**

Run: `ls -lh app/build/outputs/apk/debug/app-debug.apk`
Expected: 文件存在

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add -A
git commit -m "build: verify Phase 3 compilation and tests pass"
```
