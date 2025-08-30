package com.wirelessalien.android.moviedb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.helper.ScheduledNotificationDatabaseHelper

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val episodeName = intent.getStringExtra("episodeName") ?: return
        val episodeNumber = intent.getStringExtra("episodeNumber") ?: return
        val notificationKey = intent.getStringExtra("notificationKey") ?: return
        val type = intent.getStringExtra("type") ?: return

        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (type == "episode") {
            context.getString(R.string.episode_airing_today, episodeNumber, episodeName)
        } else {
            context.getString(R.string.movie_released_today, title)
        }

        val notification = NotificationCompat.Builder(context, "episode_reminders")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notificationKey.hashCode(), notification)
        }

        val dbHelper = ScheduledNotificationDatabaseHelper(context)
        dbHelper.deleteScheduledNotification(notificationKey)
    }
}