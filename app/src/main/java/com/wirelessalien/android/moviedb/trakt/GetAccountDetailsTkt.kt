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

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class GetAccountDetailsTkt(private val context: Context?, clienId: String, private val callback: AccountDataCallback?) {
    private val accessToken: String?
    private val client: OkHttpClient
    private val clientId = clienId

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
        val preferences = PreferenceManager.getDefaultSharedPreferences(context!!)
        accessToken = preferences.getString("trakt_access_token", "")
        client = OkHttpClient()
    }

    suspend fun fetchAccountDetails() {
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
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(response.body!!.string())
                    val user = jsonResponse.getJSONObject("user")
                    val username = user.getString("username")
                    val name = user.getString("name")
                    val avatarUrl = user.getJSONObject("images").getJSONObject("avatar").getString("full")
                    val location = user.getString("location")
                    val joinedAt = user.getString("joined_at")

                    callback?.onAccountDataReceived(username, name, avatarUrl, location, joinedAt)
                    (context as Activity).runOnUiThread {
                        // Update UI or SharedPreferences if needed
                    }
                } else {
                    Log.e("GetAccountDetailsTkt", "Failed to get account details")
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }
}