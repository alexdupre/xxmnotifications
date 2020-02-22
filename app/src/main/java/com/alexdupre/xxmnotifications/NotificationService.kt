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
import java.io.IOException
import kotlin.random.Random

class NotificationService : Service() {

    companion object {
        @Volatile
        var running = false

        const val maxMsgLen = 385
        const val maxInitialMsgLen = maxMsgLen - 6
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
            Log.i("XXM", "Monitoring logcat")
            var line: String?
            var bytes: ByteArray?
            val br = p.inputStream.bufferedReader(Charsets.ISO_8859_1)
            do {
                try {
                    line = br.readLine()
                    if (line != null) {
                        val idx = line.indexOf("Result of partition validation")
                        if (idx > 0) {
                            bytes = (line + '\n').toByteArray(Charsets.ISO_8859_1)
                            var idx1 = line.indexOf(',', idx)
                            var idx2 = line.indexOf(',', idx1 + 1)
                            val msgIndex = line.substring(idx1 + 2, idx2).toInt()
                            idx1 = idx2
                            idx2 = line.indexOf(',', idx1 + 1)
                            val msgTotal = line.substring(idx1 + 2, idx2).toInt()
                            val pktStart = idx2 + 2
                            Log.i(
                                "XXM",
                                "Partial message " + (msgIndex + 1) + " of " + (msgTotal + 1) + " - Partial length (with padding): " + (bytes.size - pktStart)
                            )
                            /*
                            Log.v("XXM", "Lenght: " + bytes.size)
                            for (i in pktStart until bytes.size) Log.v(
                                "XXM",
                                "Char " + i + ": " + (bytes[i].toInt() and 0xFF)
                            )
                             */
                            if (msgIndex == 0 && bytes[pktStart] == 1.toByte() && bytes[pktStart + 1] == 16.toByte() && bytes[pktStart + 2] == 1.toByte() && bytes[pktStart + 3] == 26.toByte()) {
                                var totalMsgLen = bytes[pktStart + 4].toInt() and 0xFF
                                if (totalMsgLen > 127) totalMsgLen =
                                    (totalMsgLen - 128) + (bytes[pktStart + 5].toInt() and 0xFF) * 128
                                Log.i("XXM", "Total message length: " + totalMsgLen)
                                var msgLen = minOf(totalMsgLen, maxInitialMsgLen)
                                var msgStart = pktStart + if (totalMsgLen > 127) 6 else 5
                                val msg = StringBuilder()
                                var curLen = minOf(msgLen, bytes.size - msgStart)
                                msg.append(String(bytes, msgStart, curLen, Charsets.UTF_8))
                                msgLen -= curLen
                                while (msgLen > 0) {
                                    line = br.readLine()
                                    bytes = (line + '\n').toByteArray(Charsets.ISO_8859_1)
                                    msgStart = line.indexOf(':', line.indexOf("GoLog")) + 2
                                    curLen = minOf(msgLen, bytes.size - msgStart)
                                    msg.append(String(bytes, msgStart, curLen, Charsets.UTF_8))
                                    msgLen -= curLen
                                }
                                Log.i("XXM", "Message: " + msg)
                                sendNotification(msg.toString())
                            }
                        } else if (line.contains("Message did not decrypt properly")) {
                            sendNotification("A message was lost! (or another user added you)")
                        }
                    } else {
                        running = false
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