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
