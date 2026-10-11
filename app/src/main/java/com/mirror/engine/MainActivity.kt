package com.mirror.engine

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var startBtn: Button
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        ipText = findViewById(R.id.ip_text)
        startBtn = findViewById(R.id.start_btn)

        ipText.text = "IP: ${getLocalIp()}"

        startBtn.setOnClickListener {
            if (!isRunning) {
                val intent = Intent(this, CaptureService::class.java)
                startForegroundService(intent)
                isRunning = true
                startBtn.text = "Stop"
                statusText.text = "Running. Connect to ${getLocalIp()}:8888"
            } else {
                stopService(Intent(this, CaptureService::class.java))
                isRunning = false
                startBtn.text = "Start"
                statusText.text = "Stopped"
            }
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
