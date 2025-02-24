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

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.data.TktTokenResponse
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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

                val tokenResponse = TktTokenResponse(
                    accessToken = jsonObject?.getString("access_token") ?: "",
                    refreshToken = jsonObject?.getString("refresh_token") ?: "",
                    expiresIn = jsonObject?.getLong("expires_in") ?: 0L,
                    createdAt = jsonObject?.getLong("created_at") ?: 0L
                )

                // Save new token data
                preferences.edit().apply {
                    putString("trakt_access_token", tokenResponse.accessToken)
                    putString("trakt_refresh_token", tokenResponse.refreshToken)
                    putLong("token_expires_in", tokenResponse.expiresIn)
                    putLong("token_created_at", tokenResponse.createdAt)
                    apply()
                }

                // Schedule next refresh
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val refreshDelay = tokenResponse.expiresIn - 3600 // Refresh 1 hour before expiration

                val nextRefreshWork = OneTimeWorkRequestBuilder<TktTokenRefreshWorker>()
                    .setInitialDelay(refreshDelay, TimeUnit.SECONDS)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(applicationContext).apply {
                    cancelAllWorkByTag("token_refresh")
                    enqueueUniqueWork(
                        "token_refresh",
                        ExistingWorkPolicy.REPLACE,
                        nextRefreshWork
                    )
                }

                return@withContext Result.success()
            } else {
                return@withContext Result.failure()
            }
        } else {
            return@withContext Result.failure()
        }
    }
}