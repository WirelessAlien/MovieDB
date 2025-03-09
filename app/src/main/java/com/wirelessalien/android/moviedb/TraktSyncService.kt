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

package com.wirelessalien.android.moviedb


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
        const val NOTIFICATION_ID = 1
        const val ACTION_START_SERVICE = "START_SERVICE"
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        const val EXTRA_ACCESS_TOKEN = "access_token"
        const val EXTRA_CLIENT_ID = "client_id"
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
                startForegroundService(accessToken, clientId)
            }
            ACTION_STOP_SERVICE -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService(accessToken: String?, clientId: String?) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sync_with_trakt))
            .setContentText(getString(R.string.fetching_data))
            .setSmallIcon(R.drawable.ic_refresh)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        getTraktSyncData = GetTraktSyncData(this, accessToken, clientId)

        serviceScope.launch {
            getTraktSyncData?.fetchCurrentlyWatching { response ->
                val intent = Intent("TRAKT_WATCHING_UPDATE")
                intent.putExtra("response", response)
                sendBroadcast(intent)
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sync_with_trakt),
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