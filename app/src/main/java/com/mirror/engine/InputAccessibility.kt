package com.mirror.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * 鏃犻殰纰嶆湇鍔★細娉ㄥ叆瑙︽懜銆佹寜閿簨浠? * 闇€瑕佺敤鎴峰湪 璁剧疆 鈫?鏃犻殰纰?涓墜鍔ㄥ紑鍚? */
class InputAccessibility : AccessibilityService() {

    companion object {
        var instance: InputAccessibility? = null
        private const val TAG = "InputAccessibility"
    }

    // 鐩稿榧犳爣妯″紡锛氱疮绉綅绉伙紝杞崲涓虹粷瀵硅Е鎽稿潗鏍?    private var cursorX = 540f  // 灞忓箷涓績
    private var cursorY = 960f
    private var screenW = 1080f
    private var screenH = 1920f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "鏃犻殰纰嶆湇鍔″凡杩炴帴")
    }

    fun setScreenSize(w: Float, h: Float) {
        screenW = w
        screenH = h
        cursorX = w / 2
        cursorY = h / 2
    }

    /** 鐩稿榧犳爣浣嶇Щ锛氭洿鏂板厜鏍囦綅缃?*/
    fun injectMouseDelta(dx: Int, dy: Int) {
        cursorX = (cursorX + dx).coerceIn(0f, screenW)
        cursorY = (cursorY + dy).coerceIn(0f, screenH)
    }

    /** 榧犳爣鎸夐敭锛氬乏=瑙︽懜鎸変笅/鎶捣锛屽彸=杩斿洖閿?*/
    fun injectMouseButton(button: Int, down: Boolean) {
        when (button) {
            1 -> injectTouch(if (down) 0 else 2, cursorX / screenW, cursorY / screenH)
            2 -> if (down) injectKey(KeyEvent.KEYCODE_BACK, true)
            3 -> if (down) injectKey(KeyEvent.KEYCODE_BACK, true)
        }
    }

    fun injectWheel(dy: Int) {
        // 婊氳疆锛氭殏鏃舵槧灏勪负涓婃粦/涓嬫粦鎵嬪娍
    }

    /** 缁濆瑙︽懜锛歺, y 褰掍竴鍖栧埌 0~1 */
    fun injectTouch(action: Int, x: Float, y: Float) {
        val px = x * screenW
        val py = y * screenH
        val path = Path().apply { moveTo(px, py) }
        val stroke = GestureDescription.StrokeDescription(path, 0, if (action == 2) 1 else 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /** 鎸夐敭娉ㄥ叆 */
    fun injectKey(keycode: Int, down: Boolean) {
        val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        val event = KeyEvent(action, keycode)
        onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        // 绯荤粺涓柇鏃犻殰纰嶆湇鍔℃椂璋冪敤
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
