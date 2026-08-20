# AgentDroid

Android + AI Agent 融合项目 — 用自然语言操控手机的智能助手。

## 项目结构

```
app/src/main/java/com/appia/ai/
├── App.kt                  # Application
├── MainActivity.kt         # 入口 Activity (Compose + Navigation)
├── llm/                    # LLM Provider 抽象层
│   ├── LLMProvider.kt      # Provider 接口
│   ├── ChatMessage.kt      # 消息模型
│   ├── ToolCall.kt         # 工具调用模型 + 流事件
│   ├── ModelConfig.kt      # 模型配置
│   ├── OpenAICompatibleProvider.kt  # OpenAI/Qwen/DeepSeek/Ollama
│   ├── AnthropicProvider.kt         # Claude
│   └── ModelRegistry.kt   # Provider 注册 + 预设
├── agent/                  # Agent 核心
│   ├── AgentEngine.kt      # ReAct 循环（原生 function calling）
│   ├── PromptJsonEngine.kt # 对照实现（prompt 约定 JSON + 手写解析）
│   ├── PromptJsonParser.kt # JSON 输出解析器（容错剥离代码围栏）
│   ├── ToolCallExecutor.kt # 两个引擎共用的工具执行层
│   ├── AgentMode.kt        # 工具调用模式枚举
│   ├── TaskPlanner.kt      # 任务规划（旧路径，已休眠）
│   └── ExecutionLoop.kt    # 无障碍执行循环（旧路径，已休眠）
├── tool/                   # 工具系统
│   ├── Tool.kt             # Tool 接口 + function spec 生成
│   ├── ToolRegistry.kt     # 工具注册表
│   ├── ToolResult.kt       # 成功 / 失败 / 需要权限
│   ├── ToolSettingsStore.kt # 按工具持久化开关
│   ├── PermissionChecker.kt # 统一权限检查（含无障碍）
│   ├── SetAlarmTool.kt / PostNotificationTool.kt / OpenAppTool.kt
│   ├── MemoryTools.kt      # 记忆三件套：save / recall / forget
│   └── ScreenTools.kt      # read_screen（读屏幕）/ screen_action（屏幕操作）
├── memory/
│   └── MemoryStore.kt      # 长期记忆存储（SharedPreferences + JSON）
├── trigger/                # 主动触发模块
│   ├── TriggerScheduler.kt # AlarmManager 每日定时
│   ├── AgentTriggerReceiver.kt # 触发后静默跑 Agent 并发通知
│   ├── BootReceiver.kt     # 开机重排闹钟
│   └── TriggerStore.kt     # 触发配置存储
├── data/
│   └── SettingsRepository.kt  # EncryptedSharedPreferences 配置存储
└── ui/
    ├── ChatViewModel.kt    # 对话逻辑
    ├── ChatScreen.kt      # 对话界面（含可折叠执行过程气泡）
    ├── ToolPermissionsScreen.kt # 工具权限墙
    ├── TriggerScreen.kt   # 主动提醒配置
    └── SettingsScreen.kt  # 模型配置界面
```

## Agent 架构

核心是 `AgentEngine` 的 ReAct 循环：LLM 决策 → 调用工具 → 结果回喂 → 再决策，直到给出最终回复（8 轮熔断）。提供两种工具调用模式，可在设置页热切换对比：

- **原生 function calling**（默认）：走 API 的 `tools` 参数，流式返回
- **prompt-JSON**：工具规格写进 system prompt，手写解析模型输出的 JSON 指令

### 内置工具

| 工具 | 说明 | 所需权限 |
|------|------|----------|
| set_alarm | 设置系统闹钟 | 无 |
| post_notification | 发送通知栏消息 | 通知 |
| open_app | 按名称打开已安装应用 | 无 |
| save_memory / recall_memory / forget_memory | 长期记忆读写删 | 无 |
| read_screen | 读取当前屏幕 UI 元素 | 无障碍 |
| screen_action | 点击/输入/滚动/返回等屏幕操作 | 无障碍 |

每个工具可在「设置 → 工具权限管理」独立开关；agent 调到未授权工具时会以普通回复引导用户去开启，不会静默失败。屏幕操作涉及支付、发消息、删除等不可逆动作前，agent 会先向用户确认。

### 主动提醒

「设置 → 主动提醒」可配置每天定时让 agent 静默执行一条指令（AlarmManager 触发），结果发到通知栏；开机自动重排。

### 记忆

agent 会通过 save_memory 主动记住用户偏好，长期记忆注入 system prompt；对话历史保留最近 20 条作为短期上下文。

## 支持的模型

| 模型 | Base URL | 说明 |
|------|----------|------|
| OpenAI | `https://api.openai.com/v1` | GPT-4o / GPT-4o-mini |
| DeepSeek | `https://api.deepseek.com/v1` | deepseek-chat |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | qwen-plus |
| Moonshot | `https://api.moonshot.cn/v1` | moonshot-v1-8k |
| Ollama | `http://10.0.2.2:11434/v1` | 本地模型 (10.0.2.2 = 模拟器访问宿主机) |
| Anthropic | `https://api.anthropic.com/v1` | Claude |

## 构建

1. Android Studio 打开项目，Sync Gradle
2. minSdk 28, targetSdk 34, Kotlin 2.0.20
3. 运行到模拟器或真机

## 使用

1. 打开 App → 点击右上角设置图标
2. 从预设模板添加配置，填入 API Key
3. 点击 ✓ 设为激活模型
4. 返回对话页面开始聊天
5. 试试：「明天早上 8 点叫我起床」「给我发条通知说该喝水了」「记住我喜欢喝冰美式」
6. 想体验屏幕操作：设置 → 工具权限管理 → 为「读取屏幕 / 屏幕操作」开启无障碍服务
