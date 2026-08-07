# AgentDroid Phase 1 — Service 基础层 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 实现 AccessibilityService 层，使 App 能读取屏幕元素并执行基本操作（tap/input/back/home/scroll/wait）

**Architecture:** 三层结构 — 数据模型层（AgentAction/ScreenElement/TaskPlan）、Service 层（AgentAccessibilityService/ServiceBridge）、UI 层（权限引导）。核心解析和匹配逻辑提取为可单元测试的纯函数。

**Tech Stack:** Kotlin + Jetpack Compose + AccessibilityService + JUnit4 + MockK

---

## File Structure

### Create:
- `model/AgentAction.kt` — sealed class + Direction/StepStatus enums
- `model/ScreenElement.kt` — 屏幕元素数据类
- `model/TaskPlan.kt` — TaskPlan + ExecutionStep
- `service/AccessibilityTreeParser.kt` — 无障碍树解析（纯函数，可测试）
- `service/ElementMatcher.kt` — 元素模糊匹配（纯函数，可测试）
- `service/ServiceBridge.kt` — singleton 桥接
- `service/AgentAccessibilityService.kt` — AccessibilityService 实现
- `ui/PermissionGuideScreen.kt` — 权限引导页面
- `res/xml/accessibility_service_config.xml` — 服务配置

### Modify:
- `gradle/libs.versions.toml` — 添加测试依赖
- `app/build.gradle.kts` — 添加 testImplementation
- `AndroidManifest.xml` — 注册 service
- `ui/ChatScreen.kt` — 接入权限检查
- `ui/ChatViewModel.kt` — 接入 ServiceBridge
- `MainActivity.kt` — 添加 permission 路由

---

## Task 1: 添加测试依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 在 libs.versions.toml 添加测试库版本**

在 `[versions]` 节添加:
```toml
junit = "4.13.2"
mockk = "1.13.12"
```

在 `[libraries]` 节添加:
```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
```

- [ ] **Step 2: 在 app/build.gradle.kts 添加 testImplementation**

在 dependencies 块末尾添加:
```kotlin
testImplementation(libs.junit)
testImplementation(libs.mockk)
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
```

- [ ] **Step 3: 同步 Gradle 验证**

Run: `cd AgentDroid && ./gradlew dependencies --configuration debugUnitTestRuntimeClasspath 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add test dependencies (junit + mockk)"
```

---

## Task 2: 数据模型 — AgentAction

**Files:**
- Create: `app/src/main/java/com/appia/ai/model/AgentAction.kt`
- Test: `app/src/test/java/com/appia/ai/model/AgentActionTest.kt`

- [ ] **Step 1: 写 AgentAction sealed class**

```kotlin
package com.appia.ai.model

enum class Direction { UP, DOWN, LEFT, RIGHT }

enum class StepStatus { PENDING, RUNNING, DONE, FAILED, SKIPPED }

sealed class AgentAction {
    data class Tap(val x: Int, val y: Int) : AgentAction()
    data class Input(val text: String) : AgentAction()
    object Back : AgentAction()
    object Home : AgentAction()
    data class Scroll(val direction: Direction) : AgentAction()
    data class Wait(val seconds: Float) : AgentAction()

    val actionName: String
        get() = when (this) {
            is Tap -> "tap"
            is Input -> "input"
            is Back -> "back"
            is Home -> "home"
            is Scroll -> "scroll"
            is Wait -> "wait"
        }
}
```

- [ ] **Step 2: 写测试**

```kotlin
package com.appia.ai.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentActionTest {
    @Test
    fun tap_has_correct_action_name() {
        val action = AgentAction.Tap(100, 200)
        assertEquals("tap", action.actionName)
    }

    @Test
    fun scroll_stores_direction() {
        val action = AgentAction.Scroll(Direction.DOWN)
        assertEquals(Direction.DOWN, action.direction)
        assertEquals("scroll", action.actionName)
    }

    @Test
    fun back_is_singleton() {
        assertEquals(AgentAction.Back, AgentAction.Back)
        assertEquals("back", AgentAction.Back.actionName)
    }

    @Test
    fun wait_stores_seconds() {
        val action = AgentAction.Wait(1.5f)
        assertEquals(1.5f, action.seconds, 0.01f)
    }
}
```

- [ ] **Step 3: 运行测试验证通过**

Run: `cd AgentDroid && ./gradlew test --tests "com.appia.ai.model.AgentActionTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/model/AgentAction.kt app/src/test/java/com/appia/ai/model/AgentActionTest.kt
git commit -m "feat: add AgentAction sealed class with 6 action types"
```

---

## Task 3: 数据模型 — ScreenElement + TaskPlan

**Files:**
- Create: `app/src/main/java/com/appia/ai/model/ScreenElement.kt`
- Create: `app/src/main/java/com/appia/ai/model/TaskPlan.kt`
- Test: `app/src/test/java/com/appia/ai/model/ScreenElementTest.kt`

- [ ] **Step 1: 写 ScreenElement**

```kotlin
package com.appia.ai.model

import android.graphics.Rect

data class ScreenElement(
    val text: String = "",
    val resourceId: String = "",
    val contentDesc: String = "",
    val className: String = "",
    val bounds: Rect = Rect(),
    val clickable: Boolean = false,
    val index: Int = 0
) {
    val centerX: Int get() = (bounds.left + bounds.right) / 2
    val centerY: Int get() = (bounds.top + bounds.bottom) / 2

    val displayText: String
        get() = when {
            text.isNotEmpty() -> text
            contentDesc.isNotEmpty() -> contentDesc
            resourceId.isNotEmpty() -> resourceId
            else -> "(empty)"
        }

    val hasIdentifier: Boolean
        get() = text.isNotEmpty() || contentDesc.isNotEmpty() || resourceId.isNotEmpty()
}
```

- [ ] **Step 2: 写 TaskPlan + ExecutionStep**

```kotlin
package com.appia.ai.model

data class ExecutionStep(
    val action: AgentAction,
    val description: String,
    val target: String? = null,
    val status: StepStatus = StepStatus.PENDING
)

data class TaskPlan(
    val goal: String,
    val steps: List<ExecutionStep>
)
```

- [ ] **Step 3: 写测试**

```kotlin
package com.appia.ai.model

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenElementTest {
    @Test
    fun center_calculated_from_bounds() {
        val el = ScreenElement(bounds = Rect(0, 0, 100, 200))
        assertEquals(50, el.centerX)
        assertEquals(100, el.centerY)
    }

    @Test
    fun display_text_prefers_text_over_content_desc() {
        val el = ScreenElement(text = "Hello", contentDesc = "World")
        assertEquals("Hello", el.displayText)
    }

    @Test
    fun display_text_falls_back_to_content_desc() {
        val el = ScreenElement(contentDesc = "World")
        assertEquals("World", el.displayText)
    }

    @Test
    fun has_identifier_true_when_any_field_present() {
        assertTrue(ScreenElement(text = "x").hasIdentifier)
        assertTrue(ScreenElement(resourceId = "id").hasIdentifier)
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `cd AgentDroid && ./gradlew test --tests "com.appia.ai.model.ScreenElementTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/model/ app/src/test/java/com/appia/ai/model/ScreenElementTest.kt
git commit -m "feat: add ScreenElement and TaskPlan data models"
```

---

## Task 4: 无障碍树解析（纯函数）

**Files:**
- Create: `app/src/main/java/com/appia/ai/service/AccessibilityTreeParser.kt`
- Test: `app/src/test/java/com/appia/ai/service/AccessibilityTreeParserTest.kt`

- [ ] **Step 1: 写 AccessibilityTreeParser**

```kotlin
package com.appia.ai.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.appia.ai.model.ScreenElement

object AccessibilityTreeParser {

    fun parse(root: AccessibilityNodeInfo?): List<ScreenElement> {
        if (root == null) return emptyList()
        val result = mutableListOf<ScreenElement>()
        parseNode(root, result)
        return result
    }

    private fun parseNode(
        node: AccessibilityNodeInfo,
        result: MutableList<ScreenElement>
    ) {
        val bounds = Rect().apply { node.getBoundsInScreen(this) }
        val element = ScreenElement(
            text = node.text?.toString() ?: "",
            resourceId = node.viewIdResourceName ?: "",
            contentDesc = node.contentDescription?.toString() ?: "",
            className = node.className?.toString() ?: "",
            bounds = bounds,
            clickable = node.isClickable,
            index = result.size
        )
        result.add(element)

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { parseNode(it, result) }
        }
    }

    fun filterClickable(elements: List<ScreenElement>): List<ScreenElement> {
        return elements.filter { it.clickable && it.hasIdentifier }
    }

    fun toDescription(elements: List<ScreenElement>): String {
        return elements.joinToString("\n") { el ->
            "[${el.index}] text=${el.displayText} class=${el.className} id=${el.resourceId} center=(${el.centerX},${el.centerY})"
        }
    }
}
```

- [ ] **Step 2: 写测试**

```kotlin
package com.appia.ai.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityTreeParserTest {
    @Test
    fun parse_null_root_returns_empty() {
        val result = AccessibilityTreeParser.parse(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun parse_single_node_extracts_fields() {
        val node = mockk<AccessibilityNodeInfo>()
        every { node.text } = "Login"
        every { node.viewIdResourceName } = "com.app:id/btn_login"
        every { node.contentDescription } = null
        every { node.className } = "android.widget.Button"
        every { node.isClickable } = true
        every { node.childCount } = 0
        every { node.getBoundsInScreen(any()) } answers { (it.invoker as Rect).set(0, 0, 100, 50) }

        val result = AccessibilityTreeParser.parse(node)
        assertEquals(1, result.size)
        assertEquals("Login", result[0].text)
        assertEquals("com.app:id/btn_login", result[0].resourceId)
        assertTrue(result[0].clickable)
    }

    @Test
    fun filter_clickable_returns_only_clickable_with_id() {
        val elements = listOf(
            ScreenElement(text = "Btn", clickable = true, index = 0),
            ScreenElement(text = "", clickable = true, index = 1),
            ScreenElement(text = "Text", clickable = false, index = 2)
        )
        val filtered = AccessibilityTreeParser.filterClickable(elements)
        assertEquals(1, filtered.size)
        assertEquals("Btn", filtered[0].text)
    }

    @Test
    fun to_description_includes_index_and_text() {
        val elements = listOf(
            ScreenElement(text = "Hello", className = "TextView", index = 0)
        )
        val desc = AccessibilityTreeParser.toDescription(elements)
        assertTrue(desc.contains("[0]"))
        assertTrue(desc.contains("Hello"))
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `cd AgentDroid && ./gradlew test --tests "com.appia.ai.service.AccessibilityTreeParserTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/service/AccessibilityTreeParser.kt app/src/test/java/com/appia/ai/service/AccessibilityTreeParserTest.kt
git commit -m "feat: add AccessibilityTreeParser for screen element extraction"
```

---

## Task 5: 元素模糊匹配（纯函数）

**Files:**
- Create: `app/src/main/java/com/appia/ai/service/ElementMatcher.kt`
- Test: `app/src/test/java/com/appia/ai/service/ElementMatcherTest.kt`

- [ ] **Step 1: 写 ElementMatcher**

```kotlin
package com.appia.ai.service

import com.appia.ai.model.ScreenElement

object ElementMatcher {

    fun find(target: String, elements: List<ScreenElement>): ScreenElement? {
        if (target.isBlank() || elements.isEmpty()) return null

        // 1. 精确匹配 text
        elements.firstOrNull { it.text.equals(target, ignoreCase = true) }
            ?.let { return it }

        // 2. 精确匹配 contentDesc
        elements.firstOrNull { it.contentDesc.equals(target, ignoreCase = true) }
            ?.let { return it }

        // 3. 包含匹配 text
        elements.firstOrNull { it.text.contains(target, ignoreCase = true) }
            ?.let { return it }

        // 4. 包含匹配 contentDesc
        elements.firstOrNull { it.contentDesc.contains(target, ignoreCase = true) }
            ?.let { return it }

        // 5. resourceId 后缀匹配
        elements.firstOrNull {
            it.resourceId.substringAfter("/").equals(target, ignoreCase = true)
        }?.let { return it }

        return null
    }

    fun findAll(targets: List<String>, elements: List<ScreenElement>): List<ScreenElement> {
        return targets.mapNotNull { find(it, elements) }
    }
}
```

- [ ] **Step 2: 写测试**

```kotlin
package com.appia.ai.service

import com.appia.ai.model.ScreenElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ElementMatcherTest {
    private val elements = listOf(
        ScreenElement(text = "Login", contentDesc = "", resourceId = "com.app:id/btn_login", index = 0),
        ScreenElement(text = "", contentDesc = "Settings", resourceId = "com.app:id/settings", index = 1),
        ScreenElement(text = "Submit Form", contentDesc = "", resourceId = "com.app:id/submit", index = 2)
    )

    @Test
    fun find_exact_text_match() {
        val result = ElementMatcher.find("Login", elements)
        assertNotNull(result)
        assertEquals(0, result!!.index)
    }

    @Test
    fun find_exact_content_desc_match() {
        val result = ElementMatcher.find("Settings", elements)
        assertNotNull(result)
        assertEquals(1, result!!.index)
    }

    @Test
    fun find_partial_text_match() {
        val result = ElementMatcher.find("Submit", elements)
        assertNotNull(result)
        assertEquals(2, result!!.index)
    }

    @Test
    fun find_resource_id_suffix_match() {
        val result = ElementMatcher.find("btn_login", elements)
        assertNotNull(result)
        assertEquals(0, result!!.index)
    }

    @Test
    fun find_returns_null_when_no_match() {
        val result = ElementMatcher.find("Nonexistent", elements)
        assertNull(result)
    }

    @Test
    fun find_returns_null_for_blank_target() {
        val result = ElementMatcher.find("", elements)
        assertNull(result)
    }

    @Test
    fun find_all_returns_multiple_matches() {
        val results = ElementMatcher.findAll(listOf("Login", "Settings"), elements)
        assertEquals(2, results.size)
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `cd AgentDroid && ./gradlew test --tests "com.appia.ai.service.ElementMatcherTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, 7 tests passed

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/service/ElementMatcher.kt app/src/test/java/com/appia/ai/service/ElementMatcherTest.kt
git commit -m "feat: add ElementMatcher for fuzzy element finding"
```

---

## Task 6: ServiceBridge 单例

**Files:**
- Create: `app/src/main/java/com/appia/ai/service/ServiceBridge.kt`

- [ ] **Step 1: 写 ServiceBridge**

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/service/ServiceBridge.kt
git commit -m "feat: add ServiceBridge singleton for service-UI communication"
```

---

## Task 7: AgentAccessibilityService

**Files:**
- Create: `app/src/main/java/com/appia/ai/service/AgentAccessibilityService.kt`

- [ ] **Step 1: 写 AgentAccessibilityService**

```kotlin
package com.appia.ai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.appia.ai.model.AgentAction
import com.appia.ai.model.Direction
import com.appia.ai.model.ScreenElement
import kotlinx.coroutines.delay

class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceBridge.bind(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Phase 1: 被动查询模式，不需要事件处理
    }

    override fun onInterrupt() {
        ServiceBridge.unbind()
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceBridge.unbind()
    }

    fun getScreenElements(): List<ScreenElement> {
        val root = rootInActiveWindow ?: return emptyList()
        return AccessibilityTreeParser.parse(root)
    }

    fun getClickableElements(): List<ScreenElement> {
        return AccessibilityTreeParser.filterClickable(getScreenElements())
    }

    fun findElement(target: String): ScreenElement? {
        return ElementMatcher.find(target, getScreenElements())
    }

    suspend fun executeAction(action: AgentAction): Boolean {
        return when (action) {
            is AgentAction.Tap -> performTap(action.x, action.y)
            is AgentAction.Input -> performInput(action.text)
            is AgentAction.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
            is AgentAction.Home -> performGlobalAction(GLOBAL_ACTION_HOME)
            is AgentAction.Scroll -> performScroll(action.direction)
            is AgentAction.Wait -> { delay((action.seconds * 1000).toLong()); true }
        }
    }

    private fun performTap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun performInput(text: String): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun performScroll(direction: Direction): Boolean {
        val (startY, endY) = when (direction) {
            Direction.UP -> 300 to 1000
            Direction.DOWN -> 1000 to 300
            else -> 500 to 500
        }
        val (startX, endX) = when (direction) {
            Direction.LEFT -> 1000 to 300
            Direction.RIGHT -> 300 to 1000
            else -> 500 to 500
        }
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/service/AgentAccessibilityService.kt
git commit -m "feat: add AgentAccessibilityService with screen reading and action execution"
```

---

## Task 8: 注册 Service + 配置

**Files:**
- Create: `app/src/main/res/xml/accessibility_service_config.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 写 accessibility_service_config.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:description="AgentDroid 需要无障碍权限来读取屏幕元素并执行操作"
    android:notificationTimeout="100" />
```

- [ ] **Step 2: 在 AndroidManifest.xml 注册 service**

在 `<application>` 块内、`<activity>` 之后添加:

```xml
<service
    android:name=".service.AgentAccessibilityService"
    android:exported="false"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

- [ ] **Step 3: Commit**

```bash
cd AgentDroid && git add app/src/main/res/xml/accessibility_service_config.xml app/src/main/AndroidManifest.xml
git commit -m "feat: register AgentAccessibilityService in manifest"
```

---

## Task 9: 权限引导 UI

**Files:**
- Create: `app/src/main/java/com/appia/ai/ui/PermissionGuideScreen.kt`

- [ ] **Step 1: 写 PermissionGuideScreen**

```kotlin
package com.appia.ai.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "需要无障碍权限",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "AgentDroid 需要无障碍服务权限来读取屏幕元素并执行操作。\n\n" +
                "请按以下步骤开启：\n" +
                "1. 点击下方按钮进入无障碍设置\n" +
                "2. 找到 AgentDroid 并开启\n" +
                "3. 返回 App 即可使用",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            }) {
                Text("前往无障碍设置")
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/ui/PermissionGuideScreen.kt
git commit -m "feat: add PermissionGuideScreen for accessibility permission"
```

---

## Task 10: ChatScreen + ChatViewModel 接入权限检查

**Files:**
- Modify: `app/src/main/java/com/appia/ai/ui/ChatViewModel.kt`
- Modify: `app/src/main/java/com/appia/ai/ui/ChatScreen.kt`
- Modify: `app/src/main/java/com/appia/ai/MainActivity.kt`

- [ ] **Step 1: ChatViewModel 添加 isAccessibilityReady 状态**

在 `ChatUiState` data class 添加字段:

```kotlin
val isAccessibilityReady: Boolean = false
```

在 `ChatViewModel` 类中添加 import 和方法:

```kotlin
import com.appia.ai.service.ServiceBridge

fun checkAccessibilityReady() {
    _uiState.value = _uiState.value.copy(isAccessibilityReady = ServiceBridge.isReady.value)
}
```

- [ ] **Step 2: ChatScreen 添加权限状态提示**

在 `ChatScreen` 参数添加 `onNavigateToPermission: () -> Unit`。

在 Scaffold topBar actions 中，当 `!state.isAccessibilityReady` 时显示警告图标:

```kotlin
if (!state.isAccessibilityReady) {
    IconButton(onClick = onNavigateToPermission) {
        Icon(Icons.Default.Warning, contentDescription = "权限")
    }
}
```

同时在 ChatScreen body 中添加提示文字:

```kotlin
if (!state.isAccessibilityReady) {
    Text(
        "无障碍服务未开启",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    )
}
```

- [ ] **Step 3: MainActivity 添加 permission 路由**

在 NavHost 中添加:

```kotlin
composable("permission") {
    PermissionGuideScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

在 ChatScreen 调用处添加:

```kotlin
onNavigateToPermission = { navController.navigate("permission") }
```

- [ ] **Step 4: 编译验证**

Run: `cd AgentDroid && ./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
cd AgentDroid && git add app/src/main/java/com/appia/ai/ui/ app/src/main/java/com/appia/ai/MainActivity.kt
git commit -m "feat: integrate accessibility permission check into chat UI"
```

---

## Task 11: 全量编译 + 全部测试

- [ ] **Step 1: 运行所有单元测试**

Run: `cd AgentDroid && ./gradlew test 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL, all tests passed

- [ ] **Step 2: 编译 debug APK**

Run: `cd AgentDroid && ./gradlew assembleDebug 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 验证 APK 生成**

Run: `ls -lh app/build/outputs/apk/debug/app-debug.apk`
Expected: 文件存在，大小 > 50MB

- [ ] **Step 4: Commit**

```bash
cd AgentDroid && git add -A
git commit -m "build: verify Phase 1 compilation and tests pass"
```
