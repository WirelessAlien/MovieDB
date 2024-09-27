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
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.data.TVSeason
import com.wirelessalien.android.moviedb.helper.ConfigHelper.getConfigValue
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GetTVSeason(private val tvShowId: Int, context: Context?) : Thread() {
    private lateinit var seasons: MutableList<TVSeason>
    private var tvShowName: String? = null
    private val preferences: SharedPreferences
    private val apiKey: String?

    init {
        preferences = PreferenceManager.getDefaultSharedPreferences(context!!)
        apiKey = getConfigValue(context, "api_key")
    }

    override fun run() {
        try {
            val url = URL("https://api.themoviedb.org/3/tv/$tvShowId?api_key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            var line: String?
            val builder = StringBuilder()
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
            val jsonResponse = JSONObject(builder.toString())
            tvShowName = jsonResponse.getString("name")
            val response = jsonResponse.getJSONArray("seasons")
            seasons = ArrayList()
            for (i in 0 until response.length()) {
                val seasonJson = response.getJSONObject(i)
                val seasonNumber = seasonJson.getInt("season_number")
                if (seasonNumber > 0) {
                    val season = TVSeason()
                    season.tvShowName = tvShowName
                    season.airDate = seasonJson.getString("air_date")
                    season.episodeCount = seasonJson.getInt("episode_count")
                    season.id = seasonJson.getInt("id")
                    season.name = seasonJson.getString("name")
                    season.overview = seasonJson.getString("overview")
                    season.setPosterPath(seasonJson.getString("poster_path"))
                    season.seasonNumber = seasonNumber
                    season.voteAverage = seasonJson.getDouble("vote_average")
                    seasons.add(season)
                }
            }
            val editor = preferences.edit()
            editor.putInt("tvShowId", tvShowId)
            editor.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSeasons(): List<TVSeason>? {
        return seasons
    }
}