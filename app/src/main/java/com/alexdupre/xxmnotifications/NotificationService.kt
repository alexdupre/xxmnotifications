package com.alexdupre.xxmnotifications

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.random.Random

class NotificationService : Service() {

    companion object {
        @Volatile
        var running = false
    }

    lateinit var lr: LogReader

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.i("XXM", "Starting service")
        ProcessBuilder("logcat", "-c").start() // clean logcat buffer
        val p: Process = ProcessBuilder("logcat", "GoLog:V", "*:S").start()
        lr = LogReader(this, p)
        Thread(lr).start()
        Toast.makeText(this, "Service started", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        lr.stop()
        Toast.makeText(this, "Service stopped", Toast.LENGTH_LONG).show()
    }

    class LogReader(private val s: Service, private val p: Process) : Runnable {

        override fun run() {
            running = true
            val br = BufferedReader(InputStreamReader(p.inputStream))
            Log.i("XXM", "Monitoring logcat")
            var line: String?
            do {
                try {
                    line = br.readLine()
                    if (line != null && line.contains("Result of partition validation")) {
                        val bytes = line.toByteArray()
                        val pktStart = bytes.indexOf(1)
                        val msgLen = bytes[pktStart + 4].toInt() and 0xFF
                        val msgStart = pktStart + 5
                        val msg = String(bytes, msgStart, minOf(msgLen, bytes.size - msgStart))
                        Log.v("XXM", "Decrypted: " + line)
                        Log.i("XXM", "Message: " + msg)
                        sendNotification(msg)
                    } else if (line != null && line.contains("Message did not decrypt properly")) {
                        sendNotification("A message was lost!")
                    }
                } catch (e: IOException) {
                    running = false
                } catch (t: Throwable) {
                    running = false
                    Log.e("XXM", "Unexpected error: " + t.message, t)
                }
            } while (running)
            Log.i("XXM", "Stopped monitoring")
        }

        private fun sendNotification(msg: String) {
            val me = Person.Builder().setName("Me").build()
            val style = NotificationCompat.MessagingStyle(me)
                .addMessage(msg, System.currentTimeMillis(), null as Person?)
            /*
            val intent = Uri.parse("xxmessenger:/").let { uri ->
                Intent(Intent.ACTION_VIEW, uri)
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(s, 0, intent, 0)
             */
            val n = NotificationCompat.Builder(s, s.getString(R.string.channel_id))
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE or Notification.DEFAULT_LIGHTS)
                .setStyle(style)
                //.setContentIntent(pendingIntent)
                .build()

            with(NotificationManagerCompat.from(s)) {
                // notificationId is a unique int for each notification that you must define
                notify(Random.nextInt(), n)
            }
        }

        fun stop() {
            p.destroy()
        }
    }


}