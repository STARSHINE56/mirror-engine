package com.mirror.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int,
    private val fps: Int
) {
    private var codec: MediaCodec? = null
    var inputSurface: Surface? = null
    var host: String = "0.0.0.0"
    var videoPort: Int = 8889

    private val running = AtomicBoolean(false)
    private var serverThread: Thread? = null
    private val frameQueue = java.util.concurrent.LinkedBlockingQueue<ByteArray>(3)
    private var spsPpsData: ByteArray? = null

    companion object {
        private const val TAG = "VideoEncoder"
        private const val FRAME_MAGIC = 0x4D495231
    }

    fun start() {
        running.set(true)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec?.createInputSurface()
        codec?.start()

        serverThread = Thread({ acceptLoop() }, "VideoServer").apply { start() }
        Thread({ encodeLoop() }, "VideoEncode").apply { start() }
    }

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

        // Send SPS/PPS first so decoder can initialize
        spsPpsData?.let {
            sendPacket(out, 0x02, it)
            Log.i(TAG, "Sent SPS/PPS to new client: ${it.size} bytes")
        }

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

    private fun encodeLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        val startCode = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        var configSent = false
        while (running.get()) {
            try {
                val outIndex = codec?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1
                when (outIndex) {
                    -2 -> { // INFO_OUTPUT_FORMAT_CHANGED
                        val format = codec?.outputFormat
                        val csd0 = format?.getByteBuffer("csd-0")
                        val csd1 = format?.getByteBuffer("csd-1")
                        if (csd0 != null && csd1 != null) {
                            val sps = ByteArray(csd0.remaining()); csd0.get(sps)
                            val pps = ByteArray(csd1.remaining()); csd1.get(pps)
                            spsPpsData = ByteArray(sps.size + pps.size)
                            System.arraycopy(sps, 0, spsPpsData!!, 0, sps.size)
                            System.arraycopy(pps, 0, spsPpsData!!, sps.size, pps.size)
                            Log.i(TAG, "Saved SPS/PPS: ${sps.size} + ${pps.size}")
                        }
                    }
                    -1 -> { continue }
                    else -> {
                        if (outIndex >= 0) {
                            val buf = codec?.getOutputBuffer(outIndex) ?: continue
                            val rawData = ByteArray(bufferInfo.size)
                            buf.get(rawData)
                            val data = ByteArray(rawData.size + 4)
                            System.arraycopy(startCode, 0, data, 0, 4)
                            System.arraycopy(rawData, 0, data, 4, rawData.size)
                            while (frameQueue.size >= 2) frameQueue.poll()
                            frameQueue.offer(data)
                            codec?.releaseOutputBuffer(outIndex, false)
                        }
                    }
                }
            } catch (e: Exception) {
                break
            }
        }
    }

    fun stop() {
        running.set(false)
        try { codec?.stop(); codec?.release() } catch (_: Exception) {}
        inputSurface?.release()
    }
}
