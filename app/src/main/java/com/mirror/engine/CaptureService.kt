package com.mirror.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

class CaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: VideoEncoder? = null
    private var controlServer: ControlServer? = null
    private var resultCode = 0
    private var resultData: Intent? = null

    companion object {
        const val CHANNEL_ID = "MirrorEngine"
        var instance: CaptureService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(1, buildNotification())

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // 需要从 Activity 传入的授权结果；这里用简化版：直接请求
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    fun startCapture(code: Int, data: Intent) {
        resultCode = code
        resultData = data
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, resultData!!)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        videoEncoder = VideoEncoder(width, height, 2000_000, 30)
        videoEncoder?.start()

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "MirrorCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            videoEncoder?.inputSurface,
            null, Looper.getMainLooper()
        )

        controlServer = ControlServer(8888)
        controlServer?.start()

        // 视频通道在 VideoEncoder 内部连接后启动
        videoEncoder?.videoPort = 8889
        videoEncoder?.host = "0.0.0.0"

        println("[Capture] 投屏已启动 ${width}x${height}")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "投屏引擎",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else Notification.Builder(this)
        return builder
            .setContentTitle("投屏引擎运行中")
            .setContentText("正在投屏到电脑")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        controlServer?.stop()
        videoEncoder?.stop()
        virtualDisplay?.release()
        mediaProjection?.stop()
        instance = null
        super.onDestroy()
    }
}
