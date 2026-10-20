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

package com.wirelessalien.android.moviedb.simkl

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import com.wirelessalien.android.moviedb.BuildConfig
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class AccountLogoutSimkl(private val context: Context, private val handler: Handler) {
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                val client = com.wirelessalien.android.moviedb.NetworkClient.client
                val accessToken = preferences.getString("simkl_access_token", null)
                val clientId = ConfigHelper.getConfigValue(context, "simkl_client_id") ?: ""

                if (accessToken != null && clientId.isNotEmpty()) {
                    val url = "https://api.simkl.com/oauth2/revoke".toHttpUrl().newBuilder()
                        .addQueryParameter("client_id", clientId)
                        .addQueryParameter("app-name", "showcase")
                        .addQueryParameter("app-version", BuildConfig.VERSION_NAME)
                        .build()

                    val formBody = FormBody.Builder()
                        .add("token", accessToken)
                        .add("client_id", clientId)
                        .build()

                    val request = Request.Builder()
                        .url(url)
                        .post(formBody)
                        .addHeader("User-Agent", "ShowCase/${BuildConfig.VERSION_NAME}")
                        .addHeader("simkl-api-key", clientId)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()

                    try {
                        client.newCall(request).execute().close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                preferences.edit()
                    .remove("simkl_access_token")
                    .remove("simkl_refresh_token")
                    .remove("simkl_token_expires_in")
                    .remove("simkl_token_created_at")
                    .remove("simkl_code_verifier")
                    .remove("simkl_oauth_state")
                    .remove("simkl_user_id")
                    .apply()

                WorkManager.getInstance(context).cancelAllWorkByTag("simkl_token_refresh")

                handler.post {
                    Toast.makeText(
                        context,
                        R.string.logged_out_successfully,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
