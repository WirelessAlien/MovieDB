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
import android.content.SharedPreferences
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AccountLogoutTkt(private val context: Context, private val handler: Handler) {
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val jSON = "application/json; charset=utf-8".toMediaTypeOrNull()
                val accessToken = preferences.getString("trakt_access_token", null)
                val clientId = ConfigHelper.getConfigValue(context, "client_id")
                val clientSecret = ConfigHelper.getConfigValue(context, "client_secret")
                if (accessToken != null && clientId != null && clientSecret != null) {
                    val json = JSONObject()
                    json.put("token", accessToken)
                    json.put("client_id", clientId)
                    json.put("client_secret", clientSecret)
                    val body = json.toString().toRequestBody(jSON)
                    val request = Request.Builder()
                        .url("https://api.trakt.tv/oauth/revoke")
                        .post(body)
                        .addHeader("accept", "application/json")
                        .addHeader("content-type", "application/json")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        preferences.edit().remove("trakt_access_token").apply()
                        preferences.edit().remove("trakt_refresh_token").apply()
                        handler.post {
                            Toast.makeText(
                                context,
                                R.string.logged_out_successfully,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}