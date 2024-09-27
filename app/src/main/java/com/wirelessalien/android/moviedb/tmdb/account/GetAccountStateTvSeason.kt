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
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GetAccountStateTvSeason(
    private val seriesId: Int,
    private val seasonId: Int,
    context: Context?,
    private val listener: OnDataFetchedListener?
) {
    interface OnDataFetchedListener {
        fun onDataFetched(episodeRatings: Map<Int, Double>?)
    }

    private val accessToken: String?
    private val episodeRatings: MutableMap<Int, Double> = HashMap()

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context!!)
        accessToken = preferences.getString("access_token", "")
    }

    suspend fun fetchAccountState() {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.themoviedb.org/3/tv/$seriesId/season/$seasonId/account_states")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body()!!.string()
                val jsonResponse = JSONObject(responseBody)
                val results = jsonResponse.getJSONArray("results")
                for (i in 0 until results.length()) {
                    val result = results.getJSONObject(i)
                    val episodeNumber = result.getInt("episode_number")
                    val rated = result["rated"]
                    var rating = 0.0
                    if (rated is JSONObject) {
                        rating = rated.getDouble("value")
                    }
                    episodeRatings[episodeNumber] = rating
                }
                withContext(Dispatchers.Main) {
                    listener?.onDataFetched(episodeRatings)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}