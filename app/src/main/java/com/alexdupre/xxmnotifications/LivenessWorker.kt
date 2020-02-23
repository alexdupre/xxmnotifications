package com.alexdupre.xxmnotifications

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlin.random.Random

class LivenessWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        if (!isStopped) {
            Log.v(
                "XXM",
                "Liveness check: last tick " + (System.currentTimeMillis() - NotificationService.lastTick) / 1000 + "s ago"
            )
            Log.v("XXM", "Notification service is running: " + NotificationService.running)
            if (System.currentTimeMillis() - NotificationService.lastTick > 60 * 1000L) {
                if (NotificationService.running) {
                    if (!NotificationService.notified) {
                        sendNotification()
                        NotificationService.notified = true
                    }
                } else {
                    WorkManager.getInstance(applicationContext)
                        .cancelAllWorkByTag(NotificationService.livenessCheckTag)
                }
            } else if (NotificationService.notified) {
                NotificationService.notified = false
            }
        } else {
            Log.v("XXM", "Cancelled job")
        }
        return Result.success()
    }

    private fun sendNotification() {
        val n = NotificationCompat.Builder(
            applicationContext,
            applicationContext.getString(R.string.channel_id)
        )
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Liveness Check")
            .setContentText("The xx messenger backend connection is not active.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE or Notification.DEFAULT_LIGHTS)
            .build()

        with(NotificationManagerCompat.from(applicationContext)) {
            // notificationId is a unique int for each notification that you must define
            notify(Random.nextInt(), n)
        }
    }

}