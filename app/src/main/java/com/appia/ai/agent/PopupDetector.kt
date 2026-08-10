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

        val hasPopupClass = elements.any { el ->
            popupClassNames.any { cls -> el.className.contains(cls, ignoreCase = true) }
        }

        val dismissButton = elements.firstOrNull { el ->
            el.clickable && popupKeywords.any { kw ->
                el.text.contains(kw, ignoreCase = true) ||
                el.contentDesc.contains(kw, ignoreCase = true)
            }
        }

        val dismissText = dismissButton?.text?.ifEmpty { dismissButton.contentDesc }

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
