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
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.random.Random

class NotificationService : Service() {

    companion object {
        @Volatile
        var running = false

        @Volatile
        var lastTick = System.currentTimeMillis()

        @Volatile
        var notified = false

        const val livenessCheckTag = "LivenessCheck"
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
        scheduleLivenessCheck()
        Thread(lr).start()
        Toast.makeText(this, "Service started", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        WorkManager.getInstance(this).cancelAllWorkByTag(livenessCheckTag)
        lr.stop()
        Toast.makeText(this, "Service stopped", Toast.LENGTH_LONG).show()
    }

    fun scheduleLivenessCheck() {
        //WorkManager.getInstance(this).cancelAllWork()
        WorkManager.getInstance(this).cancelAllWorkByTag(livenessCheckTag)
        val wr = PeriodicWorkRequest.Builder(LivenessWorker::class.java, 15, TimeUnit.MINUTES)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .addTag(livenessCheckTag)
            .build()
        WorkManager.getInstance(this).enqueue(wr)
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
                        val startIdx = line.indexOf("[CLIENT]")
                        if (startIdx > 0) { // start of new log message
                            val partitionIdx = line.indexOf("Partition: [", startIdx)
                            if (partitionIdx > 0) { // start of new decrypted message
                                var bytesIdx = partitionIdx + 12
                                val bytesStr = StringBuilder()
                                while (line!!.indexOf("]", bytesIdx) == -1) {
                                    // concatenate multiple log messages
                                    bytesStr.append(line.substring(bytesIdx))
                                    line = br.readLine()
                                    bytesIdx = startIdx
                                }
                                bytesStr.append(line.substring(bytesIdx, line.indexOf("]", bytesIdx)))
                                Log.d("XXM", "Partition [$bytesStr]")
                                bytes = bytesStr.split(" ").map { it.toInt().toByte() }.toByteArray()
                                var i = 0
                                val idAndLen = parseInt(bytes, i)
                                val id = idAndLen.first
                                i += idAndLen.second
                                val pktIndex = bytes[i++].toInt() and 0xFF
                                val pktCount = bytes[i++].toInt() and 0xFF
                                Log.d(
                                    "XXM",
                                    "Packet [$id] ${pktIndex + 1}/${pktCount + 1} - Length: ${bytes.size - i}"
                                )
                                if (pktIndex > 0) {
                                    // should re-assemble
                                } else {
                                    val msgType = bytes[i++].toInt() and 0xFF
                                    when (msgType) {
                                        1 -> {
                                            if (bytes[i++].toInt() == 16 && bytes[i++].toInt() == 1 && bytes[i++].toInt() == 26) {
                                                val msgLenAndLen = parseInt(bytes, i)
                                                val msgLen = msgLenAndLen.first
                                                i += msgLenAndLen.second
                                                val partLen = minOf(msgLen, bytes.size - i)
                                                Log.d(
                                                    "XXM",
                                                    "Message length: $msgLen - First part length: $partLen"
                                                )
                                                val notificationLen = minOf(partLen, 300) // don't exceed 300 chars...
                                                var msg = String(bytes, i, notificationLen, Charsets.UTF_8)
                                                if (notificationLen < msgLen) msg += "..."
                                                Log.i("XXM", "Message: $msg")
                                                sendNotification(msg)
                                            } else {
                                                // weird 3 bytes
                                                Log.d(
                                                    "XXM",
                                                    "Received message with type 1 but unexpected first 3 bytes"
                                                )
                                            }
                                        }
                                        13 -> { // something related to contact addition
                                            Log.d("XXM", "Something related to contact addition")
                                        }
                                        17 -> { // result of search contact
                                            Log.d("XXM", "Result of search contact")
                                        }
                                        45 -> { // handshake final message
                                            Log.i("XXM", "You have a new contact!")
                                            sendNotification("You have a new contact!")
                                        }
                                        46 -> { // contact deleted
                                            Log.i("XXM", "Someone deleted you as contact")
                                            sendNotification("Someone deleted you as contact")
                                        }
                                        else -> { // another msg type
                                            Log.d("XXM", "Received message with type $msgType")
                                        }
                                    }
                                }
                            } else if (line.indexOf("Message did not decrypt properly", startIdx) != -1) {
                                sendNotification("A message was lost! (or another user added you)")
                            } else if (line.indexOf("Over the passed 30s gateway has been checked", startIdx) != -1) {
                                lastTick = System.currentTimeMillis()
                            }
                        }
                    } else {
                        running = false
                    }
                } catch (e: IOException) {
                    running = false
                } catch (t: Throwable) {
                    running = false
                    Log.e("XXM", "Unexpected error: ${t.message}", t)
                }
            } while (running)
            Log.i("XXM", "Stopped monitoring")
        }

        private fun parseInt(bytes: ByteArray, startIdx: Int): Pair<Int, Int> {
            var i = 0
            var result = 0
            while (bytes[startIdx + i] < 0) {
                result += (bytes[startIdx + i] + 128) * 128.0.pow(i).toInt()
                i++
            }
            result += bytes[startIdx + i] * 128.0.pow(i).toInt()
            return Pair(result, i + 1)
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