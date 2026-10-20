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
import com.wirelessalien.android.moviedb.BuildConfig
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SimklTokenRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val refreshToken = preferences.getString("simkl_refresh_token", null)
        val clientId = ConfigHelper.getConfigValue(applicationContext, "simkl_client_id")

        if (!refreshToken.isNullOrEmpty() && !clientId.isNullOrEmpty()) {
            val requestBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .build()

            val url = "https://api.simkl.com/oauth2/token".toHttpUrl().newBuilder()
                .addQueryParameter("client_id", clientId)
                .addQueryParameter("app-name", "showcase")
                .addQueryParameter("app-version", BuildConfig.VERSION_NAME)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("User-Agent", "ShowCase/${BuildConfig.VERSION_NAME}")
                .addHeader("simkl-api-key", clientId)
                .build()

            val client = com.wirelessalien.android.moviedb.NetworkClient.client
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val jsonObject = responseBody?.let { JSONObject(it) }

                val newAccessToken = jsonObject?.optString("access_token", "") ?: ""
                val newRefreshToken = jsonObject?.optString("refresh_token", refreshToken) ?: refreshToken
                val expiresIn = jsonObject?.optLong("expires_in", 604800L) ?: 604800L
                val createdAt = jsonObject?.optLong("created_at", System.currentTimeMillis() / 1000) ?: (System.currentTimeMillis() / 1000)

                if (newAccessToken.isNotEmpty()) {
                    preferences.edit().apply {
                        putString("simkl_access_token", newAccessToken)
                        putString("simkl_refresh_token", newRefreshToken)
                        putLong("simkl_token_expires_in", expiresIn)
                        putLong("simkl_token_created_at", createdAt)
                        apply()
                    }

                    scheduleRefresh(applicationContext, expiresIn)
                    return@withContext Result.success()
                }
            }
            return@withContext Result.failure()
        } else {
            return@withContext Result.failure()
        }
    }

    companion object {
        fun scheduleRefresh(context: Context, expiresIn: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val refreshDelay = (expiresIn - 3600).coerceAtLeast(60L)

            val nextRefreshWork = OneTimeWorkRequestBuilder<SimklTokenRefreshWorker>()
                .setInitialDelay(refreshDelay, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .addTag("simkl_token_refresh")
                .build()

            WorkManager.getInstance(context.applicationContext).apply {
                cancelAllWorkByTag("simkl_token_refresh")
                enqueueUniqueWork(
                    "simkl_token_refresh",
                    ExistingWorkPolicy.REPLACE,
                    nextRefreshWork
                )
            }
        }
    }
}
