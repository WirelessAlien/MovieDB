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


import android.app.Activity
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GetAccountState(
    private val movieId: Int,
    private val typeCheck: String,
    activity: Activity?
) {
    var isInFavourites = false
        private set
    var isInWatchlist = false
        private set
    var rating = 0.0
        private set
    private val accessToken: String?

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity!!)
        accessToken = preferences.getString("access_token", "")
    }

    suspend fun fetchAccountState() {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.themoviedb.org/3/$typeCheck/$movieId/account_states")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body()!!.string()
                val jsonResponse = JSONObject(responseBody)
                isInFavourites = jsonResponse.getBoolean("favorite")
                isInWatchlist = jsonResponse.getBoolean("watchlist")
                if (!jsonResponse.isNull("rated")) {
                    val rated = jsonResponse["rated"]
                    if (rated is JSONObject) {
                        rating = rated.getDouble("value")
                    } else if (rated is Boolean && !rated) {
                        rating = 0.0
                    }
                } else {
                    rating = 0.0
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}