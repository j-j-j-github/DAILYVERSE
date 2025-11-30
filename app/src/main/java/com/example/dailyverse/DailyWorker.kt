package com.example.dailyverse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailyWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        try {
            // 1. Get today's verse (generates a new one if date changed)
            // This logic is idempotent; calling it multiple times a day is safe.
            val repo = VerseRepository(applicationContext)
            val verse = repo.getDailyVerse()

            // 2. Force widget refresh
            if (verse != null) {
                repo.updateWidgets(verse)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }

        // 3. Check if we should send a notification
        // We pass this flag from MainActivity when scheduling the work
        val isMidnightUpdate = inputData.getBoolean("IS_MIDNIGHT_UPDATE", false)

        if (!isMidnightUpdate) {
            triggerNotification()
        }

        return Result.success()
    }

    private fun triggerNotification() {
        val channelId = "daily_verse_channel"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        // Create channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Verse Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Daily reminders"
            notificationManager.createNotificationChannel(channel)
        }

        // Intent → open app when notification tapped
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_check) // Ensure you have this icon
            .setContentTitle("New Verse Available")
            .setContentText("Take a moment to read today's inspiration.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Show notification
        notificationManager.notify(1001, notification)
    }
}