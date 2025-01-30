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
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GetShowProgressTkt(
    private val showId: Int,
    private val seasonNumber: Int,
    context: Context?,
    private val listener: OnDataFetchedListener?
) {
    interface OnDataFetchedListener {
        fun onDataFetched(watchedEpisodes: Map<Int, Boolean>?)
    }

    private val accessToken: String?
    private val watchedEpisodes: MutableMap<Int, Boolean> = HashMap()

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context!!)
        accessToken = preferences.getString("trakt_access_token", "")
    }

    suspend fun fetchShowProgress() {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.trakt.tv/shows/$showId/progress/watched?hidden=false&specials=false&count_specials=false")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("trakt-api-version", "2")
                    .addHeader("trakt-api-key", "c72f55984ace5c7cfe876523fb867c0157b874148914c2685a2c9d0fd1ae7d3e")
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body!!.string()
                val jsonResponse = JSONObject(responseBody)
                val seasons = jsonResponse.getJSONArray("seasons")
                for (i in 0 until seasons.length()) {
                    val season = seasons.getJSONObject(i)
                    if (season.getInt("number") == seasonNumber) {
                        val episodes = season.getJSONArray("episodes")
                        for (j in 0 until episodes.length()) {
                            val episode = episodes.getJSONObject(j)
                            val episodeNumber = episode.getInt("number")
                            val completed = episode.getBoolean("completed")
                            watchedEpisodes[episodeNumber] = completed
                            Log.d("GetShowProgressTkt", "Episode $episodeNumber: $completed")
                        }
                        break
                    }
                }
                withContext(Dispatchers.Main) {
                    listener?.onDataFetched(watchedEpisodes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}