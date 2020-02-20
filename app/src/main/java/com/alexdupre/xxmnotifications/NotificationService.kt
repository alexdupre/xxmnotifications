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
import java.util.regex.Pattern
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
        Runtime.getRuntime().exec("logcat -c")
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
            val pattern =
                Pattern.compile("^.+Result of partition validation: \\[[^\\]]+\\], [0-9]+, [0-9]+, (.*).{4}$")
            do {
                try {
                    line = br.readLine()
                    if (line != null) {
                        val m = pattern.matcher(line)
                        if (m.matches()) {
                            val msg = m.group(1)
                            Log.v("XXM", "Decrypted: " + line)
                            Log.i("XXM", "Message: " + msg)

                            /*
                            val intent = Uri.parse("xxmessenger:/").let { uri ->
                                Intent(Intent.ACTION_VIEW, uri)
                            }
                            val pendingIntent: PendingIntent = PendingIntent.getActivity(s, 0, intent, 0)
                             */

                            val me = Person.Builder().setName("Me").build()
                            val style = NotificationCompat.MessagingStyle(me)
                                .addMessage(msg, System.currentTimeMillis(), null as Person?)
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

        fun stop() {
            p.destroy()
        }
    }


}