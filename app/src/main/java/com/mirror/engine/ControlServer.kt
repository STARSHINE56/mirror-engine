package com.mirror.engine

import android.util.Log
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

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
            Log.i(TAG, "Control waiting on $port")
            while (running.get()) {
                val client = server.accept()
                Log.i(TAG, "Control connected: ${client.inetAddress}")
                try {
                    handleClient(client)
                } catch (e: Exception) {
                    Log.e(TAG, "Control handler error: ${e.message}")
                }
            }
            server.close()
        } catch (e: Exception) {
            Log.e(TAG, "Control error: ${e.message}")
        }
    }

    private fun handleClient(client: Socket) {
        val input = DataInputStream(client.getInputStream())
        val accessibility = InputAccessibility.instance

        while (running.get() && !client.isClosed) {
            try {
                val type = input.readUnsignedByte()
                when (type) {
                    0x01 -> {
                        val dx = input.readShort().toInt()
                        val dy = input.readShort().toInt()
                        accessibility?.injectMouseDelta(dx, dy)
                    }
                    0x02 -> {
                        val btn = input.readUnsignedByte()
                        val down = input.readUnsignedByte() == 1
                        accessibility?.injectMouseButton(btn, down)
                    }
                    0x03 -> {
                        val dy = input.readShort().toInt()
                        accessibility?.injectWheel(dy)
                    }
                    0x04 -> {
                        val keycode = input.readUnsignedShort()
                        val down = input.readUnsignedByte() == 1
                        accessibility?.injectKey(keycode, down)
                    }
                    0x05 -> {
                        val action = input.readUnsignedByte()
                        val x = Float.fromBits(input.readInt())
                        val y = Float.fromBits(input.readInt())
                        accessibility?.injectTouch(action, x, y)
                    }
                    0x06 -> {
                        val mode = input.readUnsignedByte()
                        Log.i(TAG, "Mode: $mode")
                    }
                    else -> {
                        Log.w(TAG, "Unknown type: $type")
                    }
                }
            } catch (e: Exception) {
                Log.i(TAG, "Control disconnected")
                break
            }
        }
        client.close()
    }

    fun stop() {
        running.set(false)
    }
}
