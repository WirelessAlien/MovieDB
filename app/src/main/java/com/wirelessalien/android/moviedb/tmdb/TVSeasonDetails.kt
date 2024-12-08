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
package com.wirelessalien.android.moviedb.tmdb

import android.content.Context
import com.wirelessalien.android.moviedb.activity.BaseActivity.Companion.getLanguageParameter
import com.wirelessalien.android.moviedb.data.Episode
import com.wirelessalien.android.moviedb.helper.ConfigHelper.getConfigValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class TVSeasonDetails(private val tvShowId: Int, private val seasonNumber: Int, var context: Context) {

    private var episodes: MutableList<Episode> = mutableListOf()
    private var seasonName: String? = null
    private var seasonOverview: String? = null
    private var seasonPosterPath: String? = null
    private var seasonVoteAverage = 0.0
    private val apiKey: String? = getConfigValue(context, "api_key")
    interface SeasonDetailsCallback {
        fun onSeasonDetailsFetched(episodes: List<Episode>)
        fun onSeasonDetailsNotAvailable()
    }
    fun fetchSeasonDetails(callback: SeasonDetailsCallback) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val baseUrl = "https://api.themoviedb.org/3/tv/$tvShowId/season/$seasonNumber?api_key=$apiKey"
                val urlWithLanguage = baseUrl + getLanguageParameter(context)
                var jsonResponse = fetchSeasonDetailsFromNetwork(urlWithLanguage)

                // Check if overview is empty
                if (jsonResponse!!.getString("overview").isEmpty()) {
                    jsonResponse = fetchSeasonDetailsFromNetwork(baseUrl)
                }

                if (jsonResponse == null || jsonResponse.getJSONArray("episodes").length() == 0) {
                    callback.onSeasonDetailsNotAvailable()
                    return@launch
                }

                seasonName = if (seasonNumber == 0) "Specials" else jsonResponse.getString("name")
                seasonOverview = jsonResponse.getString("overview") ?: ""
                seasonPosterPath = jsonResponse.getString("poster_path") ?: ""
                seasonVoteAverage = jsonResponse.getDouble("vote_average")
                val response = jsonResponse.getJSONArray("episodes") ?: JSONArray()
                episodes = mutableListOf()
                for (i in 0 until response.length()) {
                    val episodeJson = response.getJSONObject(i)
                    val name = episodeJson.getString("name")
                    val overview = episodeJson.getString("overview")
                    val airDate = episodeJson.getString("air_date")
                    val episodeNumber = episodeJson.getInt("episode_number")
                    val runtime = if (episodeJson.isNull("runtime")) 0 else episodeJson.getInt("runtime")
                    val posterPath = episodeJson.getString("still_path")
                    val voteAverage = episodeJson.getDouble("vote_average")
                    episodes.add(Episode(airDate, episodeNumber, name, overview, runtime, posterPath, voteAverage))
                }
                callback.onSeasonDetailsFetched(episodes)
            } catch (e: Exception) {
                e.printStackTrace()
                callback.onSeasonDetailsNotAvailable()
            }
        }
    }

    private suspend fun fetchSeasonDetailsFromNetwork(urlString: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(urlString)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                var responseBody: String? = null
                if (response.body() != null) {
                    responseBody = response.body()!!.string()
                }
                if (responseBody != null) {
                    return@withContext JSONObject(responseBody)
                }
            }
            return@withContext null
        }
    }
}