package com.alexdupre.xxmnotifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        val button: Button = findViewById(R.id.button)
        if (NotificationService.running) button.text = getText(R.string.disable_notifications)
        button.setOnClickListener {
            if (button.text == getText(R.string.enable_notifications)) {
                startService(Intent(this, NotificationService::class.java))
                button.text = getText(R.string.disable_notifications)
            } else {
                stopService(Intent(this, NotificationService::class.java))
                button.text = getText(R.string.enable_notifications)
            }
        }
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val id = getString(R.string.channel_id)
            val name = getString(R.string.channel_name)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(id, name, importance).apply {
                enableLights(true)
                enableVibration(true)
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
