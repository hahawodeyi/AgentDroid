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
