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
│   ├── ModelConfig.kt      # 模型配置
│   ├── OpenAICompatibleProvider.kt  # OpenAI/Qwen/DeepSeek/Ollama
│   ├── AnthropicProvider.kt         # Claude
│   └── ModelRegistry.kt   # Provider 注册 + 预设
├── data/
│   └── SettingsRepository.kt  # EncryptedSharedPreferences 配置存储
└── ui/
    ├── ChatViewModel.kt    # 对话逻辑
    ├── ChatScreen.kt      # 对话界面
    └── SettingsScreen.kt  # 模型配置界面
```

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
