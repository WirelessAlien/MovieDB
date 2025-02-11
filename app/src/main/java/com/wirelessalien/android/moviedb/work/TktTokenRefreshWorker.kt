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

package com.wirelessalien.android.moviedb.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class TktTokenRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val refreshToken = preferences.getString("trakt_refresh_token", null)
        val clientId = ConfigHelper.getConfigValue(applicationContext, "client_id")
        val clientSecret = ConfigHelper.getConfigValue(applicationContext, "client_secret")
        val redirectUri = "trakt.wirelessalien.showcase://callback"

        if (refreshToken != null && clientId != null && clientSecret != null) {
            val requestBody = FormBody.Builder()
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("redirect_uri", redirectUri)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url("https://api.trakt.tv/oauth/token")
                .post(requestBody)
                .build()

            val client = OkHttpClient()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val jsonObject = responseBody?.let { JSONObject(it) }
                val newAccessToken = jsonObject?.getString("access_token")
                val newRefreshToken = jsonObject?.getString("refresh_token")

                preferences.edit().putString("trakt_access_token", newAccessToken).apply()
                preferences.edit().putString("trakt_refresh_token", newRefreshToken).apply()
                preferences.edit().putInt("failure_count", 0).apply()

                return@withContext Result.success()
            } else {
                handleFailure(preferences)
                return@withContext Result.retry()
            }
        } else {
            handleFailure(preferences)
            return@withContext Result.failure()
        }
    }

    private fun handleFailure(preferences: SharedPreferences) {
        val failureCount = preferences.getInt("failure_count", 0) + 1
        preferences.edit().putInt("failure_count", failureCount).apply()

        if (failureCount >= 3) {
            showNotification()
        }
    }

    private fun showNotification() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "general_notification_channel"
        val channelName = applicationContext.getString(R.string.general_notifications)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.failed_to_get_trakt_access))
            .setContentText(applicationContext.getString(R.string.please_log_in_again_for_trakt))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(1, notification)
    }
}