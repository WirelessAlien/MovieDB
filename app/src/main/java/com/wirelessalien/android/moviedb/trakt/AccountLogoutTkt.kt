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
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AccountLogoutTkt(private val context: Context, private val handler: Handler) {
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                val client = com.wirelessalien.android.moviedb.NetworkClient.client
                val jSON = "application/json; charset=utf-8".toMediaTypeOrNull()
                val accessToken = preferences.getString("trakt_access_token", null)
                val clientId = ConfigHelper.getConfigValue(context, "client_id")
                if (accessToken != null && clientId != null) {
                    val json = JSONObject().apply {
                        put("token", accessToken)
                        put("client_id", clientId)
                    }
                    val body = json.toString().toRequestBody(jSON)
                    val request = Request.Builder()
                        .url("https://api.trakt.tv/oauth/revoke")
                        .post(body)
                        .addHeader("accept", "application/json")
                        .addHeader("content-type", "application/json")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()
                    try {
                        client.newCall(request).execute().use { response ->
                            Log.d("AccountLogoutTkt", "Revoke response code: ${response.code}")
                        }
                    } catch (e: Exception) {
                        Log.e("AccountLogoutTkt", "Error revoking token: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("AccountLogoutTkt", "Logout exception: ${e.message}")
            } finally {
                preferences.edit()
                    .remove("trakt_access_token")
                    .remove("trakt_refresh_token")
                    .remove("token_expires_in")
                    .remove("token_created_at")
                    .remove("trakt_code_verifier")
                    .remove("trakt_oauth_state")
                    .remove("tkt_last_movies_collected")
                    .remove("tkt_last_shows_collected")
                    .remove("tkt_last_movies_watched")
                    .remove("tkt_last_shows_watched")
                    .remove("tkt_last_history_watched")
                    .remove("tkt_last_ratings")
                    .remove("tkt_last_watchlist")
                    .remove("tkt_last_favorites")
                    .remove("tkt_last_lists")
                    .apply()

                try {
                    TraktDatabaseHelper(context).clearAllTraktData()
                } catch (e: Exception) {
                    Log.e("AccountLogoutTkt", "Error clearing Trakt database: ${e.message}")
                }

                handler.post {
                    Toast.makeText(
                        context,
                        R.string.logged_out_successfully,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}