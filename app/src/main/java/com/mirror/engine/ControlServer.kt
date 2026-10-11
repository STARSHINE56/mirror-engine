package com.mirror.engine

import android.util.Log
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 控制通道 TCP 服务：接收 PC 端的键鼠指令，转发给无障碍服务注入
 */
class ControlServer(private val port: Int) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    companion object {
        private const val TAG = "ControlServer"
    }

    fun start() {
        running.set(true)
        thread = Thread({ loop() }, "ControlServer").apply { start() }
    }

    private fun loop() {
        try {
            val server = ServerSocket(port)
            Log.i(TAG, "控制通道等待连接 0.0.0.0:$port")
            val client = server.accept()
            Log.i(TAG, "控制通道已连接: ${client.inetAddress}")
            handleClient(client)
            server.close()
        } catch (e: Exception) {
            Log.e(TAG, "控制服务异常: ${e.message}")
        }
    }

    private fun handleClient(client: Socket) {
        val input = DataInputStream(client.getInputStream())
        val accessibility = InputAccessibility.instance

        while (running.get() && !client.isClosed) {
            try {
                val type = input.readUnsignedByte()
                when (type) {
                    0x01 -> {  // 鼠标相对位移
                        val dx = input.readShort().toInt()
                        val dy = input.readShort().toInt()
                        accessibility?.injectMouseDelta(dx, dy)
                    }
                    0x02 -> {  // 鼠标按键
                        val btn = input.readUnsignedByte()
                        val down = input.readUnsignedByte() == 1
                        accessibility?.injectMouseButton(btn, down)
                    }
                    0x03 -> {  // 滚轮
                        val dy = input.readShort().toInt()
                        accessibility?.injectWheel(dy)
                    }
                    0x04 -> {  // 键盘
                        val keycode = input.readUnsignedShort()
                        val down = input.readUnsignedByte() == 1
                        accessibility?.injectKey(keycode, down)
                    }
                    0x05 -> {  // 绝对触摸
                        val action = input.readUnsignedByte()
                        val x = Float.fromBits(input.readInt())
                        val y = Float.fromBits(input.readInt())
                        accessibility?.injectTouch(action, x, y)
                    }
                    0x06 -> {  // 模式切换
                        val mode = input.readUnsignedByte()
                        Log.i(TAG, "切换模式: $mode")
                    }
                    else -> {
                        Log.w(TAG, "未知包类型: $type")
                    }
                }
            } catch (e: Exception) {
                Log.i(TAG, "控制连接断开")
                break
            }
        }
        client.close()
    }

    fun stop() {
        running.set(false)
    }
}
