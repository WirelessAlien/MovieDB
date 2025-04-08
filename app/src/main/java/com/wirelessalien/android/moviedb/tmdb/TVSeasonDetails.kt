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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class TVSeasonDetails(
    private val tvShowId: Int,
    private val seasonNumber: Int,
    private val context: Context
) {
    private val apiKey: String? = getConfigValue(context, "api_key")

    data class SeasonDetails(
        val episodes: List<Episode>,
        val seasonName: String,
        val seasonOverview: String,
        val seasonPosterPath: String,
        val seasonVoteAverage: Double
    )

    suspend fun fetchSeasonDetails(): SeasonDetails? = coroutineScope {
        try {
            val baseUrl = "https://api.themoviedb.org/3/tv/$tvShowId/season/$seasonNumber?api_key=$apiKey"
            val urlWithLanguage = baseUrl + getLanguageParameter(context)

            var jsonResponse = fetchSeasonDetailsFromNetwork(urlWithLanguage)

            if (jsonResponse?.getString("overview").isNullOrEmpty()) {
                jsonResponse = fetchSeasonDetailsFromNetwork(baseUrl)
            }

            if (jsonResponse == null || jsonResponse.getJSONArray("episodes").length() == 0) {
                return@coroutineScope null
            }

            return@coroutineScope processSeasonDetails(jsonResponse)
        } catch (e: Exception) {
            e.printStackTrace()
            return@coroutineScope null
        }
    }

    private suspend fun fetchSeasonDetailsFromNetwork(urlString: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(urlString)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.use { body ->
                        val responseBody = body.string()
                        if (responseBody.isNotEmpty()) {
                            return@withContext JSONObject(responseBody)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }

    private fun processSeasonDetails(jsonResponse: JSONObject): SeasonDetails {
        val seasonName = if (seasonNumber == 0) "Specials" else jsonResponse.optString("name")
        val seasonOverview = jsonResponse.optString("overview", "")
        val seasonPosterPath = jsonResponse.optString("poster_path", "")
        val seasonVoteAverage = jsonResponse.optDouble("vote_average", 0.0)

        val episodes = mutableListOf<Episode>()
        val episodesArray = jsonResponse.optJSONArray("episodes") ?: JSONArray()

        for (i in 0 until episodesArray.length()) {
            val episodeJson = episodesArray.optJSONObject(i) ?: continue
            episodes.add(createEpisodeFromJson(episodeJson))
        }

        return SeasonDetails(
            episodes = episodes,
            seasonName = seasonName,
            seasonOverview = seasonOverview,
            seasonPosterPath = seasonPosterPath,
            seasonVoteAverage = seasonVoteAverage
        )
    }

    private fun createEpisodeFromJson(episodeJson: JSONObject): Episode {
        return Episode(
            id = episodeJson.optInt("id"),
            airDate = episodeJson.optString("air_date", ""),
            episodeNumber = episodeJson.optInt("episode_number"),
            name = episodeJson.optString("name", ""),
            overview = episodeJson.optString("overview", ""),
            runtime = episodeJson.optInt("runtime", 0),
            posterPath = episodeJson.optString("still_path", ""),
            voteAverage = episodeJson.optDouble("vote_average", 0.0)
        )
    }
}