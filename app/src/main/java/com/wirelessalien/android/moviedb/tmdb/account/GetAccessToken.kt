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
import android.content.SharedPreferences
import android.os.Handler
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class GetAccessToken(
    private val apiKey: String,
    private val requestToken: String,
    private val context: Context,
    private val handler: Handler?,
    private val listener: OnTokenReceivedListener?
) {
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    interface OnTokenReceivedListener {
        fun onTokenReceived(accessToken: String?)
    }

    suspend fun fetchAccessToken() {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()

                // With an approved request token, create a session
                val postBody = JSONObject().apply {
                    put("request_token", requestToken)
                }
                val body = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    postBody.toString()
                )
                val sessionRequest = Request.Builder()
                    .url("https://api.themoviedb.org/4/auth/access_token")
                    .post(body)
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("authorization", "Bearer $apiKey")
                    .build()
                val sessionResponse = client.newCall(sessionRequest).execute()
                val sessionResponseBody = sessionResponse.body()!!.string()
                if (sessionResponse.isSuccessful) {
                    handler?.post {
                        Toast.makeText(
                            context,
                            R.string.login_successful,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    handler?.post {
                        Toast.makeText(context, R.string.login_failed, Toast.LENGTH_SHORT).show()
                    }
                }
                val sessionResponseObject = JSONObject(sessionResponseBody)
                val accessToken = sessionResponseObject.getString("access_token")
                val accountId = sessionResponseObject.getString("account_id")
                preferences.edit().putString("access_token", accessToken).apply()
                preferences.edit().putString("account_id", accountId).apply()
                listener?.onTokenReceived(accessToken)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}