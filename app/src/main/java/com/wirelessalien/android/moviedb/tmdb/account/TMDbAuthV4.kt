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
package com.wirelessalien.android.moviedb.tmdb.account

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.helper.ConfigHelper.getConfigValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class TMDbAuthV4(private val context: Context) {
    private val apiKey: String? = getConfigValue(context, "api_read_access_token")
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    suspend fun authenticate(): String? {
        return withContext(Dispatchers.IO) {
            var accessToken: String? = null
            try {
                val client = OkHttpClient()
                val JSON = MediaType.parse("application/json; charset=utf-8")

                // Create JSON object with redirect_to URL
                val json = JSONObject()
                json.put("redirect_to", "com.wirelessalien.android.moviedb://callback")

                // Generate a new request token
                val requestTokenRequest = Request.Builder()
                    .url("https://api.themoviedb.org/4/auth/request_token")
                    .post(
                        RequestBody.create(
                            JSON,
                            json.toString()
                        )
                    ) // provide JSON object as request body
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("authorization", "Bearer $apiKey")
                    .build()
                val requestTokenResponse = client.newCall(requestTokenRequest).execute()
                val responseBody = requestTokenResponse.body()!!.string()

                // Parse the JSON response body
                val jsonObject = JSONObject(responseBody)
                val requestToken = jsonObject.getString("request_token")
                preferences.edit().putString("request_token", requestToken).apply()
                val uri =
                    Uri.parse("https://www.themoviedb.org/auth/access?request_token=$requestToken")
                val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(browserIntent)
                accessToken = requestToken
            } catch (e: Exception) {
                e.printStackTrace()
            }
            accessToken
        }
    }
}