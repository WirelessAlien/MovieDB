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

package com.wirelessalien.android.moviedb.trakt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class GetAccountDetailsTkt(private val context: Context?, private val clientId: String, private val callback: AccountDataCallback?) {
    private val accessToken: String?
    private val client: OkHttpClient

    interface AccountDataCallback {
        fun onAccountDataReceived(
            username: String?,
            name: String?,
            avatarUrl: String?,
            location: String?,
            joinedAt: String?
        )
    }

    init {
        val preferences = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        accessToken = preferences?.getString("trakt_access_token", "") ?: ""
        client = com.wirelessalien.android.moviedb.NetworkClient.client
    }

    suspend fun fetchAccountDetails() {
        if (accessToken.isNullOrEmpty()) {
            Log.w("GetAccountDetailsTkt", "No access token")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.trakt.tv/users/settings")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("trakt-api-version", "2")
                    .addHeader("trakt-api-key", clientId)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (!responseBody.isNullOrEmpty()) {
                            val jsonResponse = JSONObject(responseBody)
                            val user = jsonResponse.optJSONObject("user")
                            if (user != null) {
                                val username = user.optString("username", null)
                                val name = user.optString("name", null)
                                val images = user.optJSONObject("images")
                                val avatar = images?.optJSONObject("avatar")
                                val avatarUrl = avatar?.optString("full", null)
                                val location = user.optString("location", null)
                                val joinedAt = user.optString("joined_at", null)

                                withContext(Dispatchers.Main) {
                                    callback?.onAccountDataReceived(username, name, avatarUrl, location, joinedAt)
                                }
                            }
                        }
                    } else {
                        Log.e("GetAccountDetailsTkt", "Failed to get account details, code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("GetAccountDetailsTkt", "Error fetching account details: ${e.message}")
            }
        }
    }
}