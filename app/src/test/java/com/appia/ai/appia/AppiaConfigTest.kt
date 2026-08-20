package com.appia.ai.appia

import org.junit.Assert.assertEquals
import org.junit.Test

class AppiaConfigTest {

    @Test
    fun `normalizeServerUrl adds https when scheme missing`() {
        assertEquals("https://im.example.com", AppiaConfigStore.normalizeServerUrl("im.example.com"))
    }

    @Test
    fun `normalizeServerUrl keeps existing scheme and trims slash`() {
        assertEquals("http://10.0.0.1:3000", AppiaConfigStore.normalizeServerUrl("http://10.0.0.1:3000/"))
        assertEquals("https://im.example.com", AppiaConfigStore.normalizeServerUrl("https://im.example.com/"))
    }

    @Test
    fun `normalizeServerUrl trims whitespace`() {
        assertEquals("https://im.example.com", AppiaConfigStore.normalizeServerUrl("  im.example.com  "))
    }

    @Test
    fun `isConfigured requires all three fields`() {
        assertEquals(false, AppiaConfig().isConfigured)
        assertEquals(false, AppiaConfig("https://a.com", "uid", "").isConfigured)
        assertEquals(true, AppiaConfig("https://a.com", "uid", "token").isConfigured)
    }

    @Test
    fun `extractError reads error field`() {
        assertEquals("Invalid token", AppiaClient.extractError("""{"success":false,"error":"Invalid token"}"""))
    }

    @Test
    fun `extractError falls back to raw body`() {
        assertEquals("", AppiaClient.extractError(""))
        assertEquals("<html>502</html>", AppiaClient.extractError("<html>502</html>"))
    }
}
