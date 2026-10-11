package com.mirror.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * 无障碍服务：注入触摸、按键事件
 * 需要用户在 设置 → 无障碍 中手动开启
 */
class InputAccessibility : AccessibilityService() {

    companion object {
        var instance: InputAccessibility? = null
        private const val TAG = "InputAccessibility"
    }

    // 相对鼠标模式：累积位移，转换为绝对触摸坐标
    private var cursorX = 540f  // 屏幕中心
    private var cursorY = 960f
    private var screenW = 1080f
    private var screenH = 1920f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "无障碍服务已连接")
    }

    fun setScreenSize(w: Float, h: Float) {
        screenW = w
        screenH = h
        cursorX = w / 2
        cursorY = h / 2
    }

    /** 相对鼠标位移：更新光标位置 */
    fun injectMouseDelta(dx: Int, dy: Int) {
        cursorX = (cursorX + dx).coerceIn(0f, screenW)
        cursorY = (cursorY + dy).coerceIn(0f, screenH)
    }

    /** 鼠标按键：左=触摸按下/抬起，右=返回键 */
    fun injectMouseButton(button: Int, down: Boolean) {
        when (button) {
            1 -> injectTouch(if (down) 0 else 2, cursorX / screenW, cursorY / screenH)
            2 -> if (down) injectKey(KeyEvent.KEYCODE_BACK, true)
            3 -> if (down) injectKey(KeyEvent.KEYCODE_BACK, true)
        }
    }

    fun injectWheel(dy: Int) {
        // 滚轮：暂时映射为上滑/下滑手势
    }

    /** 绝对触摸：x, y 归一化到 0~1 */
    fun injectTouch(action: Int, x: Float, y: Float) {
        val px = x * screenW
        val py = y * screenH
        val path = Path().apply { moveTo(px, py) }
        val stroke = GestureDescription.StrokeDescription(path, 0, if (action == 2) 1 else 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /** 按键注入 */
    fun injectKey(keycode: Int, down: Boolean) {
        val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        val event = KeyEvent(action, keycode)
        onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
