package com.mirror.engine

import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.media.ImageReader
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val jpegQuality: Int = 60,
    private val fps: Int = 30
) {
    var host: String = "0.0.0.0"
    var videoPort: Int = 8889

    private val running = AtomicBoolean(false)
    private var serverThread: Thread? = null
    private val frameQueue = java.util.concurrent.LinkedBlockingQueue<ByteArray>(3)
    private var imageReader: ImageReader? = null

    companion object {
        private const val TAG = "VideoEncoder"
        private const val FRAME_MAGIC = 0x4D495231
    }

    fun start() {
        running.set(true)
        // Create ImageReader to receive frames from VirtualDisplay
        imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    while (frameQueue.size >= 2) frameQueue.poll()
                    frameQueue.offer(data)
                } catch (e: Exception) {
                    Log.e(TAG, "Image error: ${e.message}")
                } finally {
                    image.close()
                }
            }, null)
        }

        serverThread = Thread({ acceptLoop() }, "VideoServer").apply { start() }
    }

    fun getInputSurface() = imageReader?.surface

    private fun acceptLoop() {
        try {
            val server = ServerSocket(videoPort)
            Log.i(TAG, "Video waiting on $videoPort")
            while (running.get()) {
                val client = server.accept()
                Log.i(TAG, "Video connected: ${client.inetAddress}")
                try {
                    sendLoop(client)
                } catch (e: Exception) {
                    Log.e(TAG, "Video send error: ${e.message}")
                }
            }
            server.close()
        } catch (e: Exception) {
            Log.e(TAG, "Video error: ${e.message}")
        }
    }

    private fun sendLoop(client: Socket) {
        val out = DataOutputStream(client.getOutputStream())
        val info = ByteBuffer.allocate(4).putShort(width.toShort()).putShort(height.toShort()).array()
        sendPacket(out, 0x03, info)

        while (running.get() && !client.isClosed) {
            val frame = frameQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS) ?: continue
            sendPacket(out, 0x01, frame)
        }
        client.close()
    }

    private fun sendPacket(out: DataOutputStream, type: Int, data: ByteArray) {
        val header = ByteBuffer.allocate(8)
            .putInt(FRAME_MAGIC)
            .put(type.toByte())
            .put(byteArrayOf((data.size shr 16).toByte(), (data.size shr 8).toByte(), data.size.toByte()))
            .array()
        out.write(header)
        out.write(data)
        out.flush()
    }

    fun stop() {
        running.set(false)
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
    }
}
