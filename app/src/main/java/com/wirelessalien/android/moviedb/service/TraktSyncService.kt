/*
 *     This file is part of "ShowCase" formerly Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     ShowCase is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ShowCase is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with "ShowCase".  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.service


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.tmdb.GetTmdbDetails
import com.wirelessalien.android.moviedb.trakt.GetTraktSyncData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TraktSyncService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var getTraktSyncData: GetTraktSyncData? = null

    companion object {
        const val CHANNEL_ID = "TraktSyncServiceChannel"
        const val NOTIFICATION_ID = 4
        const val ACTION_START_SERVICE = "START_SERVICE"
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        const val EXTRA_ACCESS_TOKEN = "access_token"
        const val EXTRA_CLIENT_ID = "client_id"
        const val EXTRA_TMDB_API_KEY = "tmdb_api_key"
        const val ACTION_SERVICE_COMPLETED = "SERVICE_COMPLETED"

    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                val accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN)
                val clientId = intent.getStringExtra(EXTRA_CLIENT_ID)
                val tmdbApi = intent.getStringExtra(EXTRA_TMDB_API_KEY)
                startForegroundService(accessToken, clientId, tmdbApi)
            }
            ACTION_STOP_SERVICE -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService(accessToken: String?, clientId: String?, tmdbApiKey: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fun updateNotification(message: String) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.refresh))
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_refresh)
                .setOngoing(true)
                .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        val initialNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.refresh))
            .setContentText(getString(R.string.fetching_data))
            .setSmallIcon(R.drawable.ic_refresh)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, initialNotification)

        getTraktSyncData = GetTraktSyncData(this, accessToken, clientId)

        serviceScope.launch {
            updateNotification(getString(R.string.fetching_trakt_data))
            getTraktSyncData?.fetchData()

            updateNotification(getString(R.string.fetching_tmdb_data1))
            val tmdbDetails = GetTmdbDetails(this@TraktSyncService, tmdbApiKey ?: "")
            tmdbDetails.fetchAndSaveTmdbDetails { progressData, _ ->
                updateNotification(progressData)
            }

            updateNotification(getString(R.string.sync_completed))
            val intent = Intent(ACTION_SERVICE_COMPLETED)
            sendBroadcast(intent)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.auto_fetch),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}