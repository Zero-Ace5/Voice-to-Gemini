package com.poc.voicetogemini

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

class VoicePolishAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoicePolishService"
        const val PREFS_NAME = "bubble_prefs"
        const val KEY_POS_X = "bubble_pos_x"
        const val KEY_POS_Y = "bubble_pos_y"
        const val KEY_HAS_POS = "has_custom_pos"

        var instance: VoicePolishAccessibilityService? = null
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var geminiClient: GeminiClient
    private lateinit var prefs: SharedPreferences

    private var floatingView: View? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private var lastFocusedNode: AccessibilityNodeInfo? = null

    // Views
    private var bubbleRoot: FrameLayout? = null
    private var ivSparkle: ImageView? = null
    private var progressSpinner: ProgressBar? = null
    private var ivCheck: ImageView? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        geminiClient = GeminiClient(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        initFloatingBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val source = event.source
                if (source != null && source.isEditable) {
                    lastFocusedNode = source
                }
                checkKeyboardAndFocusState()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                checkKeyboardAndFocusState()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    private fun checkKeyboardAndFocusState() {
        // Keep visible if actively processing
        if (progressSpinner?.visibility == View.VISIBLE) {
            floatingView?.visibility = View.VISIBLE
            return
        }

        val isTyping = isInputActive()
        if (isTyping) {
            applyPosition()
            floatingView?.visibility = View.VISIBLE
        } else {
            floatingView?.visibility = View.GONE
        }
    }

    private fun isInputActive(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            lastFocusedNode = focused
            return true
        }

        // Check if IME window is genuinely visible
        val ime = findImeWindow()
        if (ime != null) {
            val rect = Rect()
            ime.getBoundsInScreen(rect)
            val dm = resources.displayMetrics
            if (rect.height() > 200 && rect.top > 0 && rect.top < dm.heightPixels) {
                return true
            }
        }

        return false
    }

    private fun findImeWindow(): AccessibilityWindowInfo? {
        return try {
            windows?.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        } catch (e: Exception) {
            null
        }
    }

    private fun applyPosition() {
        val params = windowParams ?: return
        val dm = resources.displayMetrics
        val screenHeight = dm.heightPixels
        val screenWidth = dm.widthPixels

        if (prefs.getBoolean(KEY_HAS_POS, false)) {
            params.x = prefs.getInt(KEY_POS_X, screenWidth - (dm.density * 60).toInt())
            params.y = prefs.getInt(KEY_POS_Y, screenHeight - (dm.density * 440).toInt())
        } else {
            val imeWindow = findImeWindow()
            val bubbleSize = (dm.density * 44).toInt()

            var keyboardTop = 0
            if (imeWindow != null) {
                val rect = Rect()
                imeWindow.getBoundsInScreen(rect)
                if (rect.top > 200 && rect.top < screenHeight) {
                    keyboardTop = rect.top
                }
            }

            if (keyboardTop <= 200) {
                keyboardTop = screenHeight - (dm.density * 430).toInt()
            }

            // Default position: Right-aligned above keyboard
            params.x = screenWidth - bubbleSize - (dm.density * 16).toInt()
            params.y = (keyboardTop - bubbleSize - (dm.density * 8).toInt()).coerceAtLeast(60)
        }

        try {
            if (floatingView?.isAttachedToWindow == true) {
                windowManager.updateViewLayout(floatingView, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply bubble layout", e)
        }
    }

    @SuppressLint("InflateParams")
    private fun initFloatingBubble() {
        if (floatingView != null) return

        try {
            val inflater = LayoutInflater.from(this)
            floatingView = inflater.inflate(R.layout.floating_bubble, null)

            bubbleRoot = floatingView?.findViewById(R.id.bubbleRoot)
            ivSparkle = floatingView?.findViewById(R.id.ivSparkle)
            progressSpinner = floatingView?.findViewById(R.id.progressSpinner)
            ivCheck = floatingView?.findViewById(R.id.ivCheck)

            floatingView?.visibility = View.GONE

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val dm = resources.displayMetrics
            val defaultX = prefs.getInt(KEY_POS_X, dm.widthPixels - (dm.density * 60).toInt())
            val defaultY = prefs.getInt(KEY_POS_Y, dm.heightPixels - (dm.density * 440).toInt())

            windowParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = defaultX
                y = defaultY
            }

            setupDirectStarTouch()

            windowManager.addView(floatingView, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing floating bubble", e)
        }
    }

    fun resetSavedPosition() {
        prefs.edit().remove(KEY_HAS_POS).remove(KEY_POS_X).remove(KEY_POS_Y).apply()
        applyPosition()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDirectStarTouch() {
        val root = bubbleRoot ?: return

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDrag = false
        val touchSlop = 10f

        root.setOnTouchListener { _, event ->
            val params = windowParams ?: return@setOnTouchListener false
            val dm = resources.displayMetrics

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDrag = false
                    // Visual feedback on press
                    ivSparkle?.scaleX = 0.88f
                    ivSparkle?.scaleY = 0.88f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDrag = true
                        params.x = (initialX + dx.toInt()).coerceIn(0, dm.widthPixels - 50)
                        params.y = (initialY + dy.toInt()).coerceIn(40, dm.heightPixels - 50)
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    ivSparkle?.scaleX = 1.0f
                    ivSparkle?.scaleY = 1.0f

                    if (!isDrag) {
                        // Short tap on the Star -> Polish text!
                        onPolishClicked()
                    } else {
                        // Dragged and released -> save position permanently!
                        prefs.edit()
                            .putInt(KEY_POS_X, params.x)
                            .putInt(KEY_POS_Y, params.y)
                            .putBoolean(KEY_HAS_POS, true)
                            .apply()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    ivSparkle?.scaleX = 1.0f
                    ivSparkle?.scaleY = 1.0f
                    true
                }
                else -> false
            }
        }
    }

    private fun onPolishClicked() {
        if (progressSpinner?.visibility == View.VISIBLE) return

        val targetNode = getActiveEditableNode()
        val currentText = targetNode?.text?.toString() ?: ""

        val textToPolish = if (currentText.isNotBlank()) {
            currentText
        } else {
            getClipboardText()
        }

        if (textToPolish.isBlank()) {
            flashError()
            return
        }

        showLoading(true)

        serviceScope.launch {
            val result = geminiClient.polishText(textToPolish)
            showLoading(false)

            result.onSuccess { polishedText ->
                if (targetNode != null) {
                    val arguments = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, polishedText)
                    }
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }

                copyToClipboard(polishedText)
                showSuccess()
            }.onFailure { error ->
                Log.e(TAG, "Polish failed", error)
                flashError()
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            ivSparkle?.visibility = View.GONE
            ivCheck?.visibility = View.GONE
            progressSpinner?.visibility = View.VISIBLE
        } else {
            progressSpinner?.visibility = View.GONE
        }
    }

    private fun showSuccess() {
        serviceScope.launch {
            progressSpinner?.visibility = View.GONE
            ivSparkle?.visibility = View.GONE
            ivCheck?.visibility = View.VISIBLE
            delay(1200)
            ivCheck?.visibility = View.GONE
            ivSparkle?.visibility = View.VISIBLE
        }
    }

    private fun flashError() {
        serviceScope.launch {
            progressSpinner?.visibility = View.GONE
            ivCheck?.visibility = View.GONE
            ivSparkle?.visibility = View.VISIBLE
            ivSparkle?.setColorFilter(0xFFFF5252.toInt())
            delay(1400)
            ivSparkle?.clearColorFilter()
        }
    }

    private fun getActiveEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return lastFocusedNode?.takeIf { it.isEditable }
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            lastFocusedNode = focused
            return focused
        }
        return lastFocusedNode?.takeIf { it.isEditable }
    }

    private fun getClipboardText(): String {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            return clip.getItemAt(0).text?.toString() ?: ""
        }
        return ""
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Polished Text", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating view", e)
            }
        }
    }
}
