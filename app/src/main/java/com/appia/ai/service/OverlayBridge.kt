package com.appia.ai.service

object OverlayBridge {
    @Volatile
    private var onPauseClick: (() -> Unit)? = null

    @Volatile
    private var onStopClick: (() -> Unit)? = null

    @Volatile
    private var onResumeClick: (() -> Unit)? = null

    fun bind(
        onPause: () -> Unit,
        onResume: () -> Unit,
        onStop: () -> Unit
    ) {
        onPauseClick = onPause
        onResumeClick = onResume
        onStopClick = onStop
    }

    fun unbind() {
        onPauseClick = null
        onResumeClick = null
        onStopClick = null
    }

    fun pause() { onPauseClick?.invoke() }
    fun resume() { onResumeClick?.invoke() }
    fun stop() { onStopClick?.invoke() }
}
