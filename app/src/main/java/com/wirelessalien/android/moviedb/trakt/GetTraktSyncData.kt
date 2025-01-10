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

import android.content.ContentValues
import android.content.Context
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

class GetTraktSyncData(private val context: Context, private val accessToken: String?, private val clientId: String?) {

    private val client = OkHttpClient()
    private val dbHelper = TraktDatabaseHelper(context)

    fun fetchData() {
        CoroutineScope(Dispatchers.IO).launch {
            fetchCollectionData()
            fetchWatchedData()
            fetchHistoryData()
            fetchRatingData()
            fetchWatchlistData()
            fetchFavoriteData()
        }
    }

    private fun fetchFavoriteData() {
        val url = "https://api.trakt.tv/sync/favorites"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString("type")
                val values = ContentValues().apply {
                    put("rank", jsonObject.getInt("rank"))
                    put("listed_at", jsonObject.getString("listed_at"))
                    put("notes", jsonObject.getString("notes"))
                    put("type", type)
                    when (type) {
                        "movie" -> {
                            val movie = jsonObject.getJSONObject("movie")
                            put("title", movie.getString("title"))
                            put("year", movie.getInt("year"))
                            put("trakt_id", movie.getJSONObject("ids").getInt("trakt"))
                            put("slug", movie.getJSONObject("ids").getString("slug"))
                            put("imdb", movie.getJSONObject("ids").getString("imdb"))
                            put("tmdb", movie.getJSONObject("ids").getInt("tmdb"))
                        }
                        "show" -> {
                            val show = jsonObject.getJSONObject("show")
                            put("title", show.getString("title"))
                            put("year", show.getInt("year"))
                            put("trakt_id", show.getJSONObject("ids").getInt("trakt"))
                            put("slug", show.getJSONObject("ids").getString("slug"))
                            put("tvdb", show.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", show.getJSONObject("ids").getString("imdb"))
                            put("tmdb", show.getJSONObject("ids").getInt("tmdb"))
                        }
                    }
                }
                dbHelper.insertFavoriteData(values)
            }
        }
    }

    private fun fetchWatchlistData() {
        val url = "https://api.trakt.tv/sync/watchlist"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString("type")
                val values = ContentValues().apply {
                    put("rank", jsonObject.getInt("rank"))
                    put("listed_at", jsonObject.getString("listed_at"))
                    put("notes", jsonObject.getString("notes"))
                    put("type", type)
                    when (type) {
                        "movie" -> {
                            val movie = jsonObject.getJSONObject("movie")
                            put("title", movie.getString("title"))
                            put("year", movie.getInt("year"))
                            put("trakt_id", movie.getJSONObject("ids").getInt("trakt"))
                            put("slug", movie.getJSONObject("ids").getString("slug"))
                            put("imdb", movie.getJSONObject("ids").getString("imdb"))
                            put("tmdb", movie.getJSONObject("ids").getInt("tmdb"))
                        }
                        "show" -> {
                            val show = jsonObject.getJSONObject("show")
                            put("title", show.getString("title"))
                            put("year", show.getInt("year"))
                            put("trakt_id", show.getJSONObject("ids").getInt("trakt"))
                            put("slug", show.getJSONObject("ids").getString("slug"))
                            put("tvdb", show.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", show.getJSONObject("ids").getString("imdb"))
                            put("tmdb", show.getJSONObject("ids").getInt("tmdb"))
                        }
                        "season" -> {
                            val season = jsonObject.getJSONObject("season")
                            put("number", season.getInt("number"))
                            put("tvdb", season.getJSONObject("ids").getInt("tvdb"))
                            put("tmdb", season.getJSONObject("ids").getInt("tmdb"))
                        }
                        "episode" -> {
                            val episode = jsonObject.getJSONObject("episode")
                            put("season", episode.getInt("season"))
                            put("number", episode.getInt("number"))
                            put("title", episode.getString("title"))
                            put("trakt_id", episode.getJSONObject("ids").getInt("trakt"))
                            put("tvdb", episode.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", episode.getJSONObject("ids").getString("imdb"))
                            put("tmdb", episode.getJSONObject("ids").getInt("tmdb"))
                        }
                    }
                }
                dbHelper.insertWatchlistData(values)
            }
        }
    }

    private fun fetchRatingData() {
        val url = "https://api.trakt.tv/sync/ratings"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString("type")
                val values = ContentValues().apply {
                    put("rated_at", jsonObject.getString("rated_at"))
                    put("rating", jsonObject.getInt("rating"))
                    put("type", type)
                    when (type) {
                        "movie" -> {
                            val movie = jsonObject.getJSONObject("movie")
                            put("title", movie.getString("title"))
                            put("year", movie.getInt("year"))
                            put("trakt_id", movie.getJSONObject("ids").getInt("trakt"))
                            put("slug", movie.getJSONObject("ids").getString("slug"))
                            put("imdb", movie.getJSONObject("ids").getString("imdb"))
                            put("tmdb", movie.getJSONObject("ids").getInt("tmdb"))
                        }
                        "show" -> {
                            val show = jsonObject.getJSONObject("show")
                            put("title", show.getString("title"))
                            put("year", show.getInt("year"))
                            put("trakt_id", show.getJSONObject("ids").getInt("trakt"))
                            put("slug", show.getJSONObject("ids").getString("slug"))
                            put("tvdb", show.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", show.getJSONObject("ids").getString("imdb"))
                            put("tmdb", show.getJSONObject("ids").getInt("tmdb"))
                        }
                        "season" -> {
                            val season = jsonObject.getJSONObject("season")
                            put("number", season.getInt("number"))
                            put("tvdb", season.getJSONObject("ids").getInt("tvdb"))
                            put("tmdb", season.getJSONObject("ids").getInt("tmdb"))
                        }
                        "episode" -> {
                            val episode = jsonObject.getJSONObject("episode")
                            put("season", episode.getInt("season"))
                            put("number", episode.getInt("number"))
                            put("title", episode.getString("title"))
                            put("trakt_id", episode.getJSONObject("ids").getInt("trakt"))
                            put("tvdb", episode.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", episode.getJSONObject("ids").getString("imdb"))
                            put("tmdb", episode.getJSONObject("ids").getInt("tmdb"))
                        }
                    }
                }
                dbHelper.insertRatingData(values)
            }
        }
    }

    private fun fetchHistoryData() {
        val url = "https://api.trakt.tv/sync/history"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString("type")
                val values = ContentValues().apply {
                    put("watched_at", jsonObject.getString("watched_at"))
                    put("action_", jsonObject.getString("action"))
                    put("type", type)
                    when (type) {
                        "movie" -> {
                            val movie = jsonObject.getJSONObject("movie")
                            put("title", movie.getString("title"))
                            put("year", movie.getInt("year"))
                            put("trakt_id", movie.getJSONObject("ids").getInt("trakt"))
                            put("slug", movie.getJSONObject("ids").getString("slug"))
                            put("imdb", movie.getJSONObject("ids").getString("imdb"))
                            put("tmdb", movie.getJSONObject("ids").getInt("tmdb"))
                        }
                        "show" -> {
                            val show = jsonObject.getJSONObject("show")
                            put("title", show.getString("title"))
                            put("year", show.getInt("year"))
                            put("trakt_id", show.getJSONObject("ids").getInt("trakt"))
                            put("slug", show.getJSONObject("ids").getString("slug"))
                            put("tvdb", show.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", show.getJSONObject("ids").getString("imdb"))
                            put("tmdb", show.getJSONObject("ids").getInt("tmdb"))
                        }
                        "season" -> {
                            val season = jsonObject.getJSONObject("season")
                            put("number", season.getInt("number"))
                            put("tvdb", season.getJSONObject("ids").getInt("tvdb"))
                            put("tmdb", season.getJSONObject("ids").getInt("tmdb"))
                        }
                        "episode" -> {
                            val episode = jsonObject.getJSONObject("episode")
                            put("season", episode.getInt("season"))
                            put("number", episode.getInt("number"))
                            put("title", episode.getString("title"))
                            put("trakt_id", episode.getJSONObject("ids").getInt("trakt"))
                            put("tvdb", episode.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", episode.getJSONObject("ids").getString("imdb"))
                            put("tmdb", episode.getJSONObject("ids").getInt("tmdb"))
                        }
                    }
                }
                dbHelper.insertHistoryData(values)
            }
        }
    }

    private fun fetchWatchedData() {
        val url = "https://api.trakt.tv/sync/watched"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString("type")
                val values = ContentValues().apply {
                    put("plays", jsonObject.getInt("plays"))
                    put("last_watched_at", jsonObject.getString("last_watched_at"))
                    put("last_updated_at", jsonObject.getString("last_updated_at"))
                    put("type", type)
                    when (type) {
                        "movie" -> {
                            val movie = jsonObject.getJSONObject("movie")
                            put("title", movie.getString("title"))
                            put("year", movie.getInt("year"))
                            put("trakt_id", movie.getJSONObject("ids").getInt("trakt"))
                            put("slug", movie.getJSONObject("ids").getString("slug"))
                            put("imdb", movie.getJSONObject("ids").getString("imdb"))
                            put("tmdb", movie.getJSONObject("ids").getInt("tmdb"))
                        }
                        "show" -> {
                            val show = jsonObject.getJSONObject("show")
                            put("title", show.getString("title"))
                            put("year", show.getInt("year"))
                            put("trakt_id", show.getJSONObject("ids").getInt("trakt"))
                            put("slug", show.getJSONObject("ids").getString("slug"))
                            put("tvdb", show.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", show.getJSONObject("ids").getString("imdb"))
                            put("tmdb", show.getJSONObject("ids").getInt("tmdb"))
                        }
                    }
                }
                dbHelper.insertWatchedData(values)
            }
        }
    }

    private fun fetchCollectionData() {
        val url = "https://api.trakt.tv/sync/collection"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString("type")
                val values = ContentValues().apply {
                    put("collected_at", jsonObject.getString("collected_at"))
                    put("updated_at", jsonObject.getString("updated_at"))
                    put("type", type)
                    when (type) {
                        "movie" -> {
                            val movie = jsonObject.getJSONObject("movie")
                            put("title", movie.getString("title"))
                            put("year", movie.getInt("year"))
                            put("trakt_id", movie.getJSONObject("ids").getInt("trakt"))
                            put("slug", movie.getJSONObject("ids").getString("slug"))
                            put("imdb", movie.getJSONObject("ids").getString("imdb"))
                            put("tmdb", movie.getJSONObject("ids").getInt("tmdb"))
                        }
                        "show" -> {
                            val show = jsonObject.getJSONObject("show")
                            put("title", show.getString("title"))
                            put("year", show.getInt("year"))
                            put("trakt_id", show.getJSONObject("ids").getInt("trakt"))
                            put("slug", show.getJSONObject("ids").getString("slug"))
                            put("tvdb", show.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", show.getJSONObject("ids").getString("imdb"))
                            put("tmdb", show.getJSONObject("ids").getInt("tmdb"))
                        }
                    }
                }
                dbHelper.insertCollectionData(values)
            }
        }
    }

    private fun createRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .get()
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", clientId?:"")
            .build()
    }

    private fun executeRequest(request: Request, onResponse: (String) -> Unit) {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    onResponse(responseBody)
                }
            } else {
                // Handle the error
            }
        } catch (e: IOException) {
            e.printStackTrace()
            // Handle the exception
        }
    }
}