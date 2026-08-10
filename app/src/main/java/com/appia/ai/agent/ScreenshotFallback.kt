package com.appia.ai.agent

import android.graphics.Bitmap
import com.appia.ai.llm.ModelConfig
import com.appia.ai.llm.VisionProvider
import com.appia.ai.service.AgentAccessibilityService

class ScreenshotFallback(
    private val service: AgentAccessibilityService,
    private val visionProvider: VisionProvider = VisionProvider()
) {
    suspend fun findElementByScreenshot(
        target: String,
        config: ModelConfig
    ): Pair<Int, Int>? {
        if (!config.supportsVision) return null

        val screenshot = service.captureScreen() ?: return null
        val result = try {
            visionProvider.findElementByScreenshot(screenshot, target, config)
        } catch (_: Exception) {
            null
        }
        screenshot.recycle()
        return result
    }

    fun isAvailable(config: ModelConfig): Boolean {
        return config.supportsVision && config.isValid
    }
}
