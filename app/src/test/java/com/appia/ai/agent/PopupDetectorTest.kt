package com.appia.ai.agent

import com.appia.ai.model.ScreenElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertNull(result.dismissText)
    }
}
