# AgentDroid 架构设计文档

> 日期：2026-08-07
> 状态：已确认
> 范围：AgentDroid AccessibilityService 层 + Agent 任务执行层

## 1. 概述

AgentDroid 是一个运行在 Android 设备上的 AI Agent 助手。用户通过自然语言指令操控手机，Agent 负责理解意图、生成操作计划、逐步执行并自适应调整。

当前项目已有 LLM Provider 抽象层（支持 OpenAI/DeepSeek/通义千问/Moonshot/Ollama/Anthropic 一键切换）和基础 Chat UI。本设计描述在此基础上新增的 AccessibilityService 层和 Agent 任务执行层。

## 2. 六大架构决策

### 2.1 执行模型：混合模式（Plan-then-Execute + Step-by-Step 自适应）

LLM 先一次性生成完整操作计划，用户确认后逐步执行。每步执行后 LLM 验证屏幕状态是否符合预期：符合则继续下一步，不符合则 LLM 重新规划剩余步骤。

- 优点：速度与可靠性兼顾，用户可见计划，能处理意外
- 缺点：实现复杂度稍高

### 2.2 屏幕感知：无障碍树为主 + 截图为辅

默认通过 AccessibilityService 读取屏幕元素的结构化数据（text/resource-id/content-desc/bounds/clickable）。当无障碍树为空或 LLM 无法理解时，降级到截图 + 视觉 LLM 定位。

- 优点：日常操作快速低成本，关键时刻有视觉兜底，架构可扩展
- 降级触发条件：无障碍树为空、元素文字全为空、LLM 明确请求截图

### 2.3 操作集：基础 6 种 + 可扩展 sealed class

Phase 1 实现核心 6 种动作：

| 动作 | 参数 | 说明 |
|------|------|------|
| `Tap` | x, y | 点击指定坐标 |
| `Input` | text | 在当前焦点输入框输入文字 |
| `Back` | — | 返回键 |
| `Home` | — | 回桌面 |
| `Scroll` | direction | 上下左右滚动 |
| `Wait` | seconds | 等待页面加载 |

`AgentAction` 设计为 sealed class，新增动作只需添加子类 + 执行逻辑，LLM prompt 自动同步可用动作列表。未来扩展：LongPress、Swipe、LaunchApp、TapElement(by resource-id)。

### 2.4 安全控制：三层防护

1. **计划确认** — 执行前展示完整计划，用户确认/修改/取消后才开始
2. **随时可停** — 悬浮窗显示暂停/停止按钮，任何时刻可干预
3. **敏感操作拦截** — 发送消息/删除/转账等操作前弹确认，普通导航不拦截

敏感操作规则：SafetyGuard 维护敏感动作关键词列表（发送、删除、确认支付、转账等），匹配到时返回 confirm 决策，UI 弹出确认卡片。30s 未响应自动暂停。

### 2.5 模式切换：意图分类 → 路由

用户消息先经过 IntentClassifier 判断意图：

- **关键词预筛**：命中"打开/发送/设置/搜索/拨打/删除"等动词 → 直接判定 AGENT，跳过 LLM 调用
- **LLM 辅助分类**：预筛未命中时，轻量 LLM 调用判断 chat / agent / ambiguous
- **路由**：
  - chat → 直接对话回答（复用现有 ChatViewModel 逻辑）
  - agent → 生成操作计划 → 确认 → 执行
  - ambiguous → 反问用户"你想让我操作手机吗？"

### 2.6 悬浮窗：悬浮球 + 可展开面板

- **收起态**：角落小球，显示步骤号（如 2/5）+ 状态图标，不遮挡操作
- **展开态**：向上展开面板，显示当前步骤描述、进度条、步骤列表、暂停/停止按钮
- **交互**：默认右下角，可拖拽，展开/收起平滑过渡
- **实现**：FloatingOverlayService + WindowManager

## 3. 系统架构

### 3.1 三层结构

```
┌─────────────────────────────────────────────────┐
│                    UI 层                         │
│  ChatScreen · FloatingOverlay · SettingsScreen  │
│  PlanConfirmCard · PermissionGuide              │
├─────────────────────────────────────────────────┤
│                  Agent 层                       │
│  IntentClassifier · TaskPlanner                 │
│  ExecutionLoop · SafetyGuard                    │
├─────────────────────────────────────────────────┤
│                 Service 层                      │
│  AgentAccessibilityService · ScreenshotProvider │
│  ServiceBridge · FloatingOverlayService         │
├─────────────────────────────────────────────────┤
│              LLM 层（已实现）                    │
│  LLMProvider · OpenAICompatibleProvider         │
│  AnthropicProvider · ModelRegistry              │
│  SettingsRepository                             │
├─────────────────────────────────────────────────┤
│                  数据模型                        │
│  ChatMessage · ModelConfig · AgentAction        │
│  TaskPlan · ScreenElement · ExecutionStep       │
└─────────────────────────────────────────────────┘
```

### 3.2 Service ↔ ViewModel 通信

`ServiceBridge` 是一个 singleton object，持有 `AgentAccessibilityService` 的引用。

- Service 启动时（onServiceConnected）调用 `ServiceBridge.bind(service)` 注册自身
- Service 关闭时调用 `ServiceBridge.unbind()` 清除引用
- UI 层通过 `ServiceBridge.service` 访问 Service 能力
- `ServiceBridge.isReady: StateFlow<Boolean>` 暴露 Service 是否可用，UI 可观察

## 4. 核心类设计

### 4.1 Agent 层 (`agent/`)

```kotlin
// 意图分类
class IntentClassifier {
    fun classify(text: String): Intent  // CHAT | AGENT | AMBIGUOUS
    // 关键词预筛 + 必要时 LLM 辅助
}

// 任务规划
class TaskPlanner {
    suspend fun plan(instruction: String, screenContext: String): TaskPlan
    // 调用 LLM 生成 JSON 操作计划
}

// 执行循环
class ExecutionLoop(
    private val service: AgentAccessibilityService,
    private val planner: TaskPlanner,
    private val guard: SafetyGuard
) {
    suspend fun execute(plan: TaskPlan, callbacks: ExecutionCallbacks): ExecutionResult
    fun pause()
    fun resume()
    fun stop()
    // 逐步执行 + 每步验证 + 自适应重规划
}

// 安全守卫
class SafetyGuard {
    fun check(action: AgentAction): SafetyDecision  // ALLOW | CONFIRM | BLOCK
    // 敏感操作规则匹配
}
```

### 4.2 Service 层 (`service/`)

```kotlin
// 无障碍服务
class AgentAccessibilityService : AccessibilityService() {
    fun getScreenElements(): List<ScreenElement>
    fun executeAction(action: AgentAction): ActionResult
    fun findElement(target: String): ScreenElement?
    // 按 text/resource-id/content-desc 模糊匹配
}

// Service 桥接
object ServiceBridge {
    var service: AgentAccessibilityService?
    val isReady: StateFlow<Boolean>
    fun bind(service: AgentAccessibilityService)
    fun unbind()
}

// 悬浮窗服务
class FloatingOverlayService : Service() {
    fun show(plan: TaskPlan)
    fun updateProgress(step: ExecutionStep)
    fun dismiss()
}
```

### 4.3 数据模型

```kotlin
// 操作指令（sealed class，可扩展）
sealed class AgentAction {
    data class Tap(val x: Int, val y: Int) : AgentAction()
    data class Input(val text: String) : AgentAction()
    object Back : AgentAction()
    object Home : AgentAction()
    data class Scroll(val direction: Direction) : AgentAction()
    data class Wait(val seconds: Float) : AgentAction()
}

// 屏幕元素
data class ScreenElement(
    val text: String,
    val resourceId: String,
    val contentDesc: String,
    val className: String,
    val bounds: Rect,
    val clickable: Boolean,
    val index: Int
)

// 任务计划
data class TaskPlan(
    val goal: String,
    val steps: List<ExecutionStep>
)

// 执行步骤
data class ExecutionStep(
    val action: AgentAction,       // 动作类型
    val description: String,       // 人类可读描述（展示给用户）
    val target: String? = null,    // 目标元素文字描述（LLM 返回，如搜索按钮）
    val status: StepStatus = StepStatus.PENDING  // PENDING | RUNNING | DONE | FAILED | SKIPPED
)

// target 解析机制：
// LLM 返回的 target 是文字描述（如发送按钮、搜索结果-妈妈）
// ExecutionLoop 执行前调用 service.findElement(target) 在无障碍树中模糊匹配
// 匹配到元素后取其 bounds 中心坐标，生成 Tap(x, y) 再执行
// 找不到则触发降级策略（重试 → 截图 → 重规划）
```

## 5. 端到端数据流

以"给妈妈发微信说今晚不回家吃饭"为例：

1. **用户输入** → ChatViewModel.send(text)
2. **意图分类** → IntentClassifier.classify("发微信"命中关键词) → AGENT
3. **生成计划** → TaskPlanner.plan() → LLM 返回 6 步 JSON 计划
4. **计划确认** → ChatScreen 展示计划卡片 → 用户点击确认 → 启动 FloatingOverlay
5. **逐步执行循环**：
   - a. AccessibilityService.getScreenElements() → 无障碍树
   - b. 匹配 step.target → 解析为坐标（找不到 → 截图降级 → 视觉 LLM）
   - c. SafetyGuard.check(action) → 敏感操作拦截（"发送"→确认）
   - d. AccessibilityService.executeAction(action) → 执行
   - e. LLM 验证屏幕 → 符合则下一步，不符合则重新规划
   - f. FloatingOverlay.updateProgress()
   - g. 检查暂停/停止
6. **完成反馈** → ChatScreen 展示执行摘要 → 关闭悬浮窗

## 6. 错误处理

| 场景 | 处理策略 |
|------|----------|
| 权限未授予 | UI 检测 ServiceBridge.isReady，引导去系统设置 |
| 元素找不到 | 重试(1s) → 截图降级 → 重规划 → 超 3 次暂停问用户 |
| 意外弹窗 | LLM 判断后自动关闭，无法关闭则暂停 |
| 超时/卡住 | 每步 15s 超时 → 重试 1 次 → 重规划 → 连续 2 次暂停 |
| LLM 请求失败 | 重试 2 次，保留已执行进度，提示检查配置 |
| 敏感操作未确认 | 30s 超时自动暂停，用户拒绝则跳过该步 |

**处理原则**：
1. 尽量自动恢复（重试、降级、重规划）
2. 无法恢复时暂停任务，保留进度，等用户决策
3. 所有错误记录到执行日志，最终展示给用户
4. 敏感操作永远不自动跳过，必须用户明确确认

## 7. 实现阶段规划

### Phase 1 — Service 基础（当前）
- AgentAccessibilityService + ServiceBridge
- ScreenElement 无障碍树解析
- AgentAction sealed class + 6 种动作执行
- AndroidManifest 注册 + 权限引导 UI
- 编译验证
- 交付：App 能读取屏幕元素，手动触发动能执行 tap/input 等

### Phase 2 — Agent 核心
- IntentClassifier（关键词预筛 + LLM 分类）
- TaskPlanner（LLM 生成 JSON 计划）
- ExecutionLoop（逐步执行 + 屏幕验证）
- ChatViewModel 接入意图路由
- 计划确认 UI 卡片
- 交付：用户输入指令 → 生成计划 → 确认 → 自动执行

### Phase 3 — 安全 + 悬浮窗
- SafetyGuard（敏感操作规则 + 拦截确认）
- FloatingOverlayService（悬浮球 + 展开面板）
- 暂停/恢复/停止控制
- 执行日志 + 进度展示
- 交付：执行过程可视化，用户随时可控

### Phase 4 — 鲁棒性 + 扩展
- 截图降级感知（视觉 LLM 定位）
- 意外弹窗检测与自动关闭
- 超时/重试/重规划完整错误链
- 新增动作扩展（long_press/swipe/launch_app）
- 交付：在微信/设置/电话等真实 App 上可靠执行

## 8. 文件结构规划

```
app/src/main/java/com/appia/ai/
├── App.kt
├── MainActivity.kt
├── llm/                          # 已实现
│   ├── LLMProvider.kt
│   ├── ChatMessage.kt
│   ├── ModelConfig.kt
│   ├── OpenAICompatibleProvider.kt
│   ├── AnthropicProvider.kt
│   └── ModelRegistry.kt
├── data/                         # 已实现
│   └── SettingsRepository.kt
├── agent/                        # Phase 2+
│   ├── IntentClassifier.kt
│   ├── TaskPlanner.kt
│   ├── ExecutionLoop.kt
│   └── SafetyGuard.kt
├── service/                      # Phase 1
│   ├── AgentAccessibilityService.kt
│   ├── ServiceBridge.kt
│   └── FloatingOverlayService.kt  # Phase 3
├── model/                        # Phase 1
│   ├── AgentAction.kt
│   ├── ScreenElement.kt
│   └── TaskPlan.kt
└── ui/                           # 已有 + 扩展
    ├── ChatScreen.kt             # 已有
    ├── ChatViewModel.kt          # 已有，扩展
    ├── SettingsScreen.kt         # 已有
    ├── PlanConfirmCard.kt        # Phase 2
    ├── PermissionGuideScreen.kt  # Phase 1
    └── FloatingOverlayView.kt    # Phase 3
```
