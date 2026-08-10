package com.appia.ai.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null

    private var totalSteps = 0
    private var currentStep = 0
    private var stepDescription = ""

    private var isPaused = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            totalSteps = it.getIntExtra(EXTRA_TOTAL_STEPS, 0)
            currentStep = 0
            showBubble()
        }
        return START_NOT_STICKY
    }

    fun updateProgress(step: Int, total: Int, description: String) {
        currentStep = step
        totalSteps = total
        stepDescription = description
        updateBubbleText()
        updatePanelText()
    }


    private fun getLayoutType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun showBubble() {
        if (bubbleView != null) return

        val dp = resources.displayMetrics.density
        val size = (48 * dp).toInt()

        val container = FrameLayout(this).apply {
            setBackgroundColor(0xFF6750A4.toInt())
        }

        val textView = TextView(this).apply {
            text = "0/$totalSteps"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            gravity = android.view.Gravity.CENTER
        }

        container.addView(textView, FrameLayout.LayoutParams(size, size).apply {
            gravity = android.view.Gravity.CENTER
        })

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    if (panelView == null) showPanel() else hidePanel()
                    v.performClick()
                }
            }
            false
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 200
        }

        windowManager.addView(container, params)
        bubbleView = container
    }

    private fun showPanel() {
        if (panelView != null) return

        val dp = resources.displayMetrics.density
        val width = (280 * dp).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF0FFFFFF.toInt())
            setPadding(
                (16 * dp).toInt(), (12 * dp).toInt(),
                (16 * dp).toInt(), (12 * dp).toInt()
            )
        }

        val titleView = TextView(this).apply {
            text = "AgentDroid 执行中"
            textSize = 14f
            setTextColor(0xFF333333.toInt())
        }

        val progressView = TextView(this).apply {
            text = "步骤 $currentStep/$totalSteps"
            textSize = 13f
            setTextColor(0xFF666666.toInt())
        }

        val descView = TextView(this).apply {
            text = stepDescription
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            maxLines = 2
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val pauseButton = Button(this).apply {
            text = if (isPaused) "恢复" else "暂停"
            setOnClickListener {
                isPaused = !isPaused
                this.text = if (isPaused) "恢复" else "暂停"
                if (isPaused) OverlayBridge.pause() else OverlayBridge.resume()
            }
        }

        val stopButton = Button(this).apply {
            text = "停止"
            setOnClickListener {
                OverlayBridge.stop()
            }
        }

        val btnParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        buttonRow.addView(pauseButton, btnParams)
        buttonRow.addView(stopButton, btnParams)

        container.addView(titleView)
        container.addView(progressView)
        container.addView(descView)
        container.addView(buttonRow)

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    hidePanel()
                    v.performClick()
                }
            }
            false
        }

        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 260
        }

        windowManager.addView(container, params)
        panelView = container
    }

    private fun hidePanel() {
        panelView?.let {
            windowManager.removeView(it)
            panelView = null
        }
    }

    private fun updateBubbleText() {
        bubbleView?.let { view ->
            (view as? FrameLayout)?.getChildAt(0)?.let { child ->
                (child as? TextView)?.text = "$currentStep/$totalSteps"
            }
        }
    }

    private fun updatePanelText() {
        panelView?.let { view ->
            (view as? LinearLayout)?.let { layout ->
                if (layout.childCount >= 3) {
                    (layout.getChildAt(1) as? TextView)?.text = "步骤 $currentStep/$totalSteps"
                    (layout.getChildAt(2) as? TextView)?.text = stepDescription
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hidePanel()
        bubbleView?.let {
            windowManager.removeView(it)
            bubbleView = null
        }
    }

    companion object {
        const val EXTRA_TOTAL_STEPS = "total_steps"

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Settings.canDrawOverlays(context)
            else true
        }

        fun start(context: Context, totalSteps: Int) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                putExtra(EXTRA_TOTAL_STEPS, totalSteps)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }
}
