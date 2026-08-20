package com.appia.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStoreTest {

    @Test
    fun `encode and decode round trip`() {
        val entries = listOf(
            MemoryEntry("favorite_drink", "喜欢喝冰美式", 1724200000000L),
            MemoryEntry("wake_up_time", "工作日 7:30 起床", 1724200001000L)
        )
        val decoded = MemoryStore.decode(MemoryStore.encode(entries))
        assertEquals(entries, decoded)
    }

    @Test
    fun `decode returns empty list for null and blank`() {
        assertTrue(MemoryStore.decode(null).isEmpty())
        assertTrue(MemoryStore.decode("").isEmpty())
        assertTrue(MemoryStore.decode("   ").isEmpty())
    }

    @Test
    fun `decode tolerates malformed json`() {
        assertTrue(MemoryStore.decode("not json").isEmpty())
        assertTrue(MemoryStore.decode("[{\"key\":1}]").isEmpty())
        assertTrue(MemoryStore.decode("[{\"key\":\"a\"}]").isEmpty())
    }

    @Test
    fun `decode skips entries missing required fields`() {
        val raw = """[{"key":"a","content":"x","updatedAt":1},{"key":"b"}]"""
        val decoded = MemoryStore.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals("a", decoded[0].key)
    }
}
