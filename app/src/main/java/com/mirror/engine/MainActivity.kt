package com.mirror.engine

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var startBtn: Button
    private lateinit var keepAliveBtn: Button
    private var isRunning = false

    private val REQUEST_MEDIA_PROJECTION = 100
    private val REQUEST_NOTIFICATION = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        ipText = findViewById(R.id.ip_text)
        startBtn = findViewById(R.id.start_btn)
        keepAliveBtn = findViewById(R.id.keepalive_btn)

        ipText.text = "IP: ${getLocalIp()}"

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION)
            }
        }

        startBtn.setOnClickListener {
            if (!isRunning) {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
            } else {
                stopService(Intent(this, CaptureService::class.java))
                isRunning = false
                startBtn.text = "Start"
                statusText.text = "Stopped"
            }
        }

        keepAliveBtn.setOnClickListener {
            openKeepAliveSettings()
        }
    }

    private fun openKeepAliveSettings() {
        try {
            // Try vivo/iQOO autostart settings first
            val intent = Intent()
            intent.setClassName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
            startActivity(intent)
            Toast.makeText(this, "Please add this app to whitelist", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            try {
                // Fallback: open app details settings
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
                Toast.makeText(this, "Please enable autostart and battery whitelist", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Cannot open settings, please find manually", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, CaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startForegroundService(intent)
            isRunning = true
            startBtn.text = "Stop"
            statusText.text = "Running. Connect to ${getLocalIp()}:8888"
            Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        return addr.hostAddress ?: "unknown"
                    }
                }
            }
        } catch (_: Exception) {}
        return "unknown"
    }
}
