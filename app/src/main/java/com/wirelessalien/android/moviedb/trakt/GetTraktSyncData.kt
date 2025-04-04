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
import android.icu.text.SimpleDateFormat
import android.util.Log
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper.Companion.USER_LISTS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Date
import java.util.Locale

class GetTraktSyncData(context: Context, private val accessToken: String?, private val clientId: String?) {

    private val client = OkHttpClient()
    private val dbHelper = TraktDatabaseHelper(context)

    fun fetchData() {
        CoroutineScope(Dispatchers.IO).launch {
            fetchCollectionData()
            fetchCollectionShowData()
            fetchWatchedDataMovie()
            fetchWatchedDataShow()
            fetchHistoryData()
            fetchRatingData()
            fetchWatchlistData()
            fetchFavoriteData()
            fetchUserLists()
            fetchAllListItems()
            fetchCalendarData()
        }
    }

    fun fetchAllListItems() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                USER_LISTS,
                arrayOf(TraktDatabaseHelper.COL_TRAKT_ID),
                null,
                null,
                null,
                null,
                null
            )

            if (cursor.moveToFirst()) {
                do {
                    val listId = cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TRAKT_ID)).toString()
                    fetchListItemData(listId)
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
    }

    private fun fetchListItemData(listId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            getListsItems(listId)
        }
    }

    fun fetchFavoriteData() {
        val url = "https://api.trakt.tv/sync/favorites"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.delete(TraktDatabaseHelper.TABLE_FAVORITE, null, null)

            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString("type")
                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_RANK, jsonObject.getInt("rank"))
                    put(TraktDatabaseHelper.COL_LISTED_AT, jsonObject.getString("listed_at"))
                    put(TraktDatabaseHelper.COL_NOTES, jsonObject.getString("notes"))
                    put(TraktDatabaseHelper.COL_TYPE, type)
                    when (type) {
                        "movie" -> {
                            val movie = jsonObject.getJSONObject("movie")
                            put(TraktDatabaseHelper.COL_TITLE, movie.getString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, movie.getInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, movie.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SLUG, movie.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_IMDB, movie.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, movie.getJSONObject("ids").getInt("tmdb"))
                        }
                        "show" -> {
                            val show = jsonObject.getJSONObject("show")
                            put(TraktDatabaseHelper.COL_TITLE, show.getString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, show.getInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SLUG, show.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_TVDB, show.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_IMDB, show.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, show.getJSONObject("ids").getInt("tmdb"))
                        }
                    }
                }
                dbHelper.insertFavoriteData(values)
            }
        }
    }

    fun fetchWatchlistData() {
        val url = "https://api.trakt.tv/sync/watchlist"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.delete(TraktDatabaseHelper.TABLE_WATCHLIST, null, null)

            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString("type")
                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_RANK, jsonObject.getInt("rank"))
                    put(TraktDatabaseHelper.COL_LISTED_AT, jsonObject.getString("listed_at"))
                    put(TraktDatabaseHelper.COL_NOTES, jsonObject.getString("notes"))
                    put(TraktDatabaseHelper.COL_TYPE, type)
                    when (type) {
                        "movie" -> {
                            val movie = jsonObject.getJSONObject("movie")
                            put(TraktDatabaseHelper.COL_TITLE, movie.getString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, movie.getInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, movie.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SLUG, movie.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_IMDB, movie.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, movie.getJSONObject("ids").getInt("tmdb"))
                        }
                        "show" -> {
                            val show = jsonObject.getJSONObject("show")
                            put(TraktDatabaseHelper.COL_TITLE, show.getString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, show.getInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SLUG, show.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_TVDB, show.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_IMDB, show.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, show.getJSONObject("ids").getInt("tmdb"))
                        }
                        "season" -> {
                            val season = jsonObject.getJSONObject("season")
                            val show = jsonObject.getJSONObject("show")
                            put(TraktDatabaseHelper.COL_SEASON, season.getInt("number"))
                            put(TraktDatabaseHelper.COL_TVDB, season.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_TMDB, season.getJSONObject("ids").getInt("tmdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TITLE, show.getString("title"))
                            put(TraktDatabaseHelper.COL_SHOW_YEAR, show.getInt("year"))
                            put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SHOW_SLUG, show.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_SHOW_TVDB, show.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_SHOW_IMDB, show.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TMDB, show.getJSONObject("ids").getInt("tmdb"))
                        }
                        "episode" -> {
                            val episode = jsonObject.getJSONObject("episode")
                            val show = jsonObject.getJSONObject("show")
                            put(TraktDatabaseHelper.COL_SEASON, episode.getInt("season"))
                            put(TraktDatabaseHelper.COL_NUMBER, episode.getInt("number"))
                            put(TraktDatabaseHelper.COL_TITLE, episode.getString("title"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, episode.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_TVDB, episode.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_IMDB, episode.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, episode.getJSONObject("ids").getInt("tmdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TITLE, show.getString("title"))
                            put(TraktDatabaseHelper.COL_SHOW_YEAR, show.getInt("year"))
                            put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SHOW_SLUG, show.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_SHOW_TVDB, show.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_SHOW_IMDB, show.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TMDB, show.getJSONObject("ids").getInt("tmdb"))
                        }
                    }
                }
                dbHelper.insertWatchlistData(values)
            }
        }
    }

    fun fetchRatingData() {
        val url = "https://api.trakt.tv/sync/ratings"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.delete(TraktDatabaseHelper.TABLE_RATING, null, null)

            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString("type")
                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_RATED_AT, jsonObject.getString("rated_at"))
                    put(TraktDatabaseHelper.COL_RATING, jsonObject.getInt("rating"))
                    put(TraktDatabaseHelper.COL_TYPE, type)
                    when (type) {
                        "movie" -> {
                            val movie = jsonObject.getJSONObject("movie")
                            put(TraktDatabaseHelper.COL_TITLE, movie.getString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, movie.getInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, movie.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SLUG, movie.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_IMDB, movie.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, movie.getJSONObject("ids").getInt("tmdb"))
                        }
                        "show" -> {
                            val show = jsonObject.getJSONObject("show")
                            put(TraktDatabaseHelper.COL_TITLE, show.getString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, show.getInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SLUG, show.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_TVDB, show.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_IMDB, show.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, show.getJSONObject("ids").getInt("tmdb"))
                        }
                        "season" -> {
                            val season = jsonObject.getJSONObject("season")
                            val show = jsonObject.getJSONObject("show")
                            put(TraktDatabaseHelper.COL_SEASON, season.getInt("number"))
                            put(TraktDatabaseHelper.COL_TVDB, season.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_TMDB, season.getJSONObject("ids").getInt("tmdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TITLE, show.getString("title"))
                            put(TraktDatabaseHelper.COL_SHOW_YEAR, show.getInt("year"))
                            put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SHOW_SLUG, show.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_SHOW_TVDB, show.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_SHOW_IMDB, show.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TMDB, show.getJSONObject("ids").getInt("tmdb"))
                        }
                        "episode" -> {
                            val episode = jsonObject.getJSONObject("episode")
                            val show = jsonObject.getJSONObject("show")
                            put(TraktDatabaseHelper.COL_SEASON, episode.getInt("season"))
                            put(TraktDatabaseHelper.COL_NUMBER, episode.getInt("number"))
                            put(TraktDatabaseHelper.COL_TITLE, episode.getString("title"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, episode.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_TVDB, episode.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_IMDB, episode.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, episode.getJSONObject("ids").getInt("tmdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TITLE, show.getString("title"))
                            put(TraktDatabaseHelper.COL_SHOW_YEAR, show.getInt("year"))
                            put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SHOW_SLUG, show.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_SHOW_TVDB, show.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_SHOW_IMDB, show.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TMDB, show.getJSONObject("ids").getInt("tmdb"))
                        }
                    }
                }
                dbHelper.insertRatingData(values)
            }
        }
    }

    fun fetchHistoryData() {
        val url = "https://api.trakt.tv/sync/history?page=1&limit=50"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.delete(TraktDatabaseHelper.TABLE_HISTORY, null, null)

            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString("type")
                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_WATCHED_AT, jsonObject.getString("watched_at"))
                    put(TraktDatabaseHelper.COL_ACTION, jsonObject.getString("action"))
                    put(TraktDatabaseHelper.COL_TYPE, type)
                    when (type) {
                        "movie" -> {
                            val movie = jsonObject.getJSONObject("movie")
                            put(TraktDatabaseHelper.COL_TITLE, movie.getString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, movie.getInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, movie.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SLUG, movie.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_IMDB, movie.getJSONObject("ids").optString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, movie.getJSONObject("ids").optInt("tmdb", -1))
                        }
                        "episode" -> {
                            val episode = jsonObject.getJSONObject("episode")
                            val show = jsonObject.getJSONObject("show")
                            put(TraktDatabaseHelper.COL_SEASON, episode.getInt("season"))
                            put(TraktDatabaseHelper.COL_NUMBER, episode.getInt("number"))
                            put(TraktDatabaseHelper.COL_TITLE, episode.getString("title"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, episode.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_TVDB, episode.getJSONObject("ids").optInt("tvdb", -1))
                            put(TraktDatabaseHelper.COL_IMDB, episode.getJSONObject("ids").optString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, episode.getJSONObject("ids").optInt("tmdb", -1))
                            put(TraktDatabaseHelper.COL_SHOW_TITLE, show.getString("title"))
                            put(TraktDatabaseHelper.COL_SHOW_YEAR, show.getInt("year"))
                            put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SHOW_SLUG, show.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_SHOW_TVDB, show.getJSONObject("ids").optInt("tvdb", -1))
                            put(TraktDatabaseHelper.COL_SHOW_IMDB, show.getJSONObject("ids").optString("imdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TMDB, show.getJSONObject("ids").optInt("tmdb", -1))
                        }
                    }
                }
                dbHelper.insertHistoryData(values)
            }
        }
    }

    fun fetchWatchedDataMovie() {
        val url = "https://api.trakt.tv/sync/watched/movies"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.delete(TraktDatabaseHelper.TABLE_WATCHED, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("movie"))

            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_PLAYS, jsonObject.getInt("plays"))
                    put(TraktDatabaseHelper.COL_LAST_WATCHED_AT, jsonObject.getString("last_watched_at"))
                    put(TraktDatabaseHelper.COL_LAST_UPDATED_AT, jsonObject.getString("last_updated_at"))
                }

                if (jsonObject.has("movie")) {
                    val movie = jsonObject.getJSONObject("movie")
                    values.apply {
                        put(TraktDatabaseHelper.COL_TYPE, "movie")
                        put(TraktDatabaseHelper.COL_TITLE, movie.getString("title"))
                        put(TraktDatabaseHelper.COL_YEAR, movie.getInt("year"))
                        put(TraktDatabaseHelper.COL_TRAKT_ID, movie.getJSONObject("ids").getInt("trakt"))
                        put(TraktDatabaseHelper.COL_SLUG, movie.getJSONObject("ids").getString("slug"))
                        put(TraktDatabaseHelper.COL_IMDB, movie.getJSONObject("ids").getString("imdb"))
                        put(TraktDatabaseHelper.COL_TMDB, movie.getJSONObject("ids").optInt("tmdb", -1))
                    }
                    dbHelper.insertWatchedData(values)
                }
            }
        }
    }

    fun fetchWatchedDataShow() {
        val url = "https://api.trakt.tv/sync/watched/shows"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.delete(TraktDatabaseHelper.TABLE_WATCHED, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("show"))

            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_PLAYS, jsonObject.getInt("plays"))
                    put(TraktDatabaseHelper.COL_LAST_WATCHED_AT, jsonObject.getString("last_watched_at"))
                    put(TraktDatabaseHelper.COL_LAST_UPDATED_AT, jsonObject.getString("last_updated_at"))
                }

                if (jsonObject.has("show")) {
                    val show = jsonObject.getJSONObject("show")
                    val showTraktId = show.getJSONObject("ids").getInt("trakt")
                    val showTmdbId = show.getJSONObject("ids").optInt("tmdb", -1)

                    val showValues = ContentValues(values).apply {
                        put(TraktDatabaseHelper.COL_TYPE, "show")
                        put(TraktDatabaseHelper.COL_TITLE, show.getString("title"))
                        put(TraktDatabaseHelper.COL_YEAR, show.getInt("year"))
                        put(TraktDatabaseHelper.COL_TRAKT_ID, showTraktId)
                        put(TraktDatabaseHelper.COL_SLUG, show.getJSONObject("ids").getString("slug"))
                        put(TraktDatabaseHelper.COL_TVDB, show.getJSONObject("ids").optInt("tvdb", -1))
                        put(TraktDatabaseHelper.COL_IMDB, show.getJSONObject("ids").optString("imdb"))
                        put(TraktDatabaseHelper.COL_TMDB, showTmdbId)
                    }

                    dbHelper.insertWatchedShowData(showValues)

                    db.delete(TraktDatabaseHelper.TABLE_SEASON_EPISODE_WATCHED, "${TraktDatabaseHelper.COL_SHOW_TRAKT_ID} = ?", arrayOf(showTraktId.toString()))

                    val seasons = jsonObject.getJSONArray("seasons")
                    for (j in 0 until seasons.length()) {
                        val season = seasons.getJSONObject(j)
                        val seasonNumber = season.getInt("number")
                        val episodes = season.getJSONArray("episodes")

                        for (k in 0 until episodes.length()) {
                            val episode = episodes.getJSONObject(k)
                            val episodeNumber = episode.getInt("number")

                            val episodeValues = ContentValues().apply {
                                put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, showTraktId)
                                put(TraktDatabaseHelper.COL_SHOW_TMDB_ID, showTmdbId)
                                put(TraktDatabaseHelper.COL_SEASON_NUMBER, seasonNumber)
                                put(TraktDatabaseHelper.COL_EPISODE_NUMBER, episodeNumber)
                                put(TraktDatabaseHelper.COL_PLAYS, episode.getInt("plays"))
                                put(TraktDatabaseHelper.COL_LAST_WATCHED_AT, episode.getString("last_watched_at"))
                            }
                            dbHelper.insertSeasonEpisodeWatchedData(episodeValues)
                        }
                    }
                }
            }
        }
    }

    fun fetchCollectionData() {
        val url = "https://api.trakt.tv/sync/collection/movies?extended=metadata"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.delete(TraktDatabaseHelper.TABLE_COLLECTION, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("movie"))
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_COLLECTED_AT, jsonObject.getString("collected_at"))
                    put(TraktDatabaseHelper.COL_UPDATED_AT, jsonObject.getString("updated_at"))
                }

                if (jsonObject.has("movie")) {
                    val movie = jsonObject.getJSONObject("movie")
                    values.apply {
                        put(TraktDatabaseHelper.COL_TYPE, "movie")
                        put(TraktDatabaseHelper.COL_TITLE, movie.getString("title"))
                        put(TraktDatabaseHelper.COL_YEAR, movie.getInt("year"))
                        put(TraktDatabaseHelper.COL_TRAKT_ID, movie.getJSONObject("ids").getInt("trakt"))
                        put(TraktDatabaseHelper.COL_SLUG, movie.getJSONObject("ids").getString("slug"))
                        put(TraktDatabaseHelper.COL_IMDB, movie.getJSONObject("ids").getString("imdb"))
                        put(TraktDatabaseHelper.COL_TMDB, movie.getJSONObject("ids").getInt("tmdb"))
                    }
                }

                if (jsonObject.has("metadata")) {
                    val metadata = jsonObject.getJSONObject("metadata")
                    values.apply {
                        put(TraktDatabaseHelper.COL_MEDIA_TYPE, metadata.getString("media_type"))
                        put(TraktDatabaseHelper.COL_RESOLUTION, metadata.getString("resolution"))
                        put(TraktDatabaseHelper.COL_HDR, metadata.getString("hdr"))
                        put(TraktDatabaseHelper.COL_AUDIO, metadata.getString("audio"))
                        put(TraktDatabaseHelper.COL_AUDIO_CHANNELS, metadata.getString("audio_channels"))
                        put(TraktDatabaseHelper.COL_THD, metadata.getBoolean("3d"))
                    }
                }

                dbHelper.insertCollectionData(values)
            }
        }
    }
    fun fetchCollectionShowData() {
        val url = "https://api.trakt.tv/sync/collection/shows?extended=metadata"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.delete(TraktDatabaseHelper.TABLE_COLLECTION, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("show"))

            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_COLLECTED_AT, jsonObject.getString("last_collected_at"))
                    put(TraktDatabaseHelper.COL_UPDATED_AT, jsonObject.getString("last_updated_at"))
                }

                if (jsonObject.has("show")) {
                    val show = jsonObject.getJSONObject("show")
                    val showValues = ContentValues(values).apply {
                        put(TraktDatabaseHelper.COL_TYPE, "show")
                        put(TraktDatabaseHelper.COL_TITLE, show.getString("title"))
                        put(TraktDatabaseHelper.COL_YEAR, show.getInt("year"))
                        put(TraktDatabaseHelper.COL_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                        put(TraktDatabaseHelper.COL_SLUG, show.getJSONObject("ids").getString("slug"))
                        put(TraktDatabaseHelper.COL_TVDB, show.getJSONObject("ids").getInt("tvdb"))
                        put(TraktDatabaseHelper.COL_IMDB, show.getJSONObject("ids").getString("imdb"))
                        put(TraktDatabaseHelper.COL_TMDB, show.getJSONObject("ids").getInt("tmdb"))
                    }

                    val seasons = jsonObject.getJSONArray("seasons")
                    for (j in 0 until seasons.length()) {
                        val season = seasons.getJSONObject(j)
                        val seasonNumber = season.getInt("number")
                        val episodes = season.getJSONArray("episodes")
                        for (k in 0 until episodes.length()) {
                            val episode = episodes.getJSONObject(k)
                            val episodeValues = ContentValues(showValues).apply {
                                put(TraktDatabaseHelper.COL_SEASON, seasonNumber)
                                put(TraktDatabaseHelper.COL_NUMBER, episode.getInt("number"))
                                put(TraktDatabaseHelper.COL_COLLECTED_AT, episode.getString("collected_at"))
                            }

                            if (episode.has("metadata")) {
                                val metadata = episode.getJSONObject("metadata")
                                episodeValues.apply {
                                    put(TraktDatabaseHelper.COL_MEDIA_TYPE, metadata.getString("media_type"))
                                    put(TraktDatabaseHelper.COL_RESOLUTION, metadata.getString("resolution"))
                                    put(TraktDatabaseHelper.COL_HDR, metadata.getString("hdr"))
                                    put(TraktDatabaseHelper.COL_AUDIO, metadata.getString("audio"))
                                    put(TraktDatabaseHelper.COL_AUDIO_CHANNELS, metadata.getString("audio_channels"))
                                    put(TraktDatabaseHelper.COL_THD, metadata.getBoolean("3d"))
                                }
                            }

                            dbHelper.insertCollectionData(episodeValues)
                        }
                    }
                }
            }
        }
    }

    fun fetchUserLists() {
        val url = "https://api.trakt.tv/users/me/lists"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.delete(USER_LISTS, null, null)
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_NAME, jsonObject.getString("name"))
                    put(TraktDatabaseHelper.COL_DESCRIPTION, jsonObject.getString("description"))
                    put(TraktDatabaseHelper.COL_PRIVACY, jsonObject.getString("privacy"))
                    put(TraktDatabaseHelper.COL_SHARE_LINK, jsonObject.getString("share_link"))
                    put(TraktDatabaseHelper.COL_TYPE, jsonObject.getString("type"))
                    put(TraktDatabaseHelper.COL_DISPLAY_NUMBERS, jsonObject.getBoolean("display_numbers"))
                    put(TraktDatabaseHelper.COL_ALLOW_COMMENTS, jsonObject.getBoolean("allow_comments"))
                    put(TraktDatabaseHelper.COL_SORT_BY, jsonObject.getString("sort_by"))
                    put(TraktDatabaseHelper.COL_SORT_HOW, jsonObject.getString("sort_how"))
                    put(TraktDatabaseHelper.COL_CREATED_AT, jsonObject.getString("created_at"))
                    put(TraktDatabaseHelper.COL_UPDATED_AT, jsonObject.getString("updated_at"))
                    put(TraktDatabaseHelper.COL_ITEM_COUNT, jsonObject.getInt("item_count"))
                    put(TraktDatabaseHelper.COL_COMMENT_COUNT, jsonObject.getInt("comment_count"))
                    put(TraktDatabaseHelper.COL_LIKES, jsonObject.getInt("likes"))
                    val ids = jsonObject.getJSONObject("ids")
                    put(TraktDatabaseHelper.COL_TRAKT_ID, ids.getInt("trakt"))
                    put(TraktDatabaseHelper.COL_SLUG, ids.getString("slug"))
                }
                dbHelper.insertUserListData(values)
            }
        }
    }

    private fun getListsItems(listId: String) {
        val url = "https://api.trakt.tv/users/me/lists/$listId/items/"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.delete(
                TraktDatabaseHelper.TABLE_LIST_ITEM,
                "${TraktDatabaseHelper.COL_LIST_ID} = ?",
                arrayOf(listId)
            )
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString("type")
                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_LIST_ID, listId)
                    put(TraktDatabaseHelper.COL_RANK, jsonObject.getInt("rank"))
                    put(TraktDatabaseHelper.COL_LISTED_AT, jsonObject.getString("listed_at"))
                    put(TraktDatabaseHelper.COL_NOTES, jsonObject.optString("notes"))
                    put(TraktDatabaseHelper.COL_TYPE, type)
                    when (type) {
                        "movie" -> {
                            val movie = jsonObject.getJSONObject("movie")
                            put(TraktDatabaseHelper.COL_TITLE, movie.getString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, movie.getInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, movie.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SLUG, movie.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_IMDB, movie.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, movie.getJSONObject("ids").getInt("tmdb"))
                        }
                        "show" -> {
                            val show = jsonObject.getJSONObject("show")
                            put(TraktDatabaseHelper.COL_TITLE, show.getString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, show.getInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SLUG, show.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_TVDB, show.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_IMDB, show.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, show.getJSONObject("ids").getInt("tmdb"))
                        }
                        "season" -> {
                            val season = jsonObject.getJSONObject("season")
                            val show = jsonObject.getJSONObject("show")
                            put(TraktDatabaseHelper.COL_SEASON, season.getInt("number"))
                            put(TraktDatabaseHelper.COL_TVDB, season.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_TMDB, season.getJSONObject("ids").getInt("tmdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TITLE, show.getString("title"))
                            put(TraktDatabaseHelper.COL_SHOW_YEAR, show.getInt("year"))
                            put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SHOW_SLUG, show.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_SHOW_TVDB, show.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_SHOW_IMDB, show.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TMDB, show.getJSONObject("ids").getInt("tmdb"))
                        }
                        "episode" -> {
                            val episode = jsonObject.getJSONObject("episode")
                            val show = jsonObject.getJSONObject("show")
                            put(TraktDatabaseHelper.COL_SEASON, episode.getInt("season"))
                            put(TraktDatabaseHelper.COL_NUMBER, episode.getInt("number"))
                            put(TraktDatabaseHelper.COL_TITLE, episode.getString("title"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, episode.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_TVDB, episode.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_IMDB, episode.getJSONObject("ids").optString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, episode.getJSONObject("ids").getInt("tmdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TITLE, show.getString("title"))
                            put(TraktDatabaseHelper.COL_SHOW_YEAR, show.getInt("year"))
                            put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SHOW_SLUG, show.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_SHOW_TVDB, show.getJSONObject("ids").getInt("tvdb"))
                            put(TraktDatabaseHelper.COL_SHOW_IMDB, show.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_SHOW_TMDB, show.getJSONObject("ids").getInt("tmdb"))
                        }
                        "person" -> {
                            val person = jsonObject.getJSONObject("person")
                            put(TraktDatabaseHelper.COL_NAME, person.getString("name"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, person.getJSONObject("ids").getInt("trakt"))
                            put(TraktDatabaseHelper.COL_SLUG, person.getJSONObject("ids").getString("slug"))
                            put(TraktDatabaseHelper.COL_IMDB, person.getJSONObject("ids").getString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, person.getJSONObject("ids").getInt("tmdb"))
                        }
                    }
                }
                dbHelper.insertListItemData(values)
            }
        }
    }

    fun fetchCalendarData() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())

        // Fetch shows calendar
        val showsUrl = "https://api.trakt.tv/calendars/my/shows/$today/7"
        val showsRequest = createRequest(showsUrl)
        executeRequest(showsRequest) { showResponse ->
            val db = dbHelper.writableDatabase
            db.delete(TraktDatabaseHelper.TABLE_CALENDER, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("episode"))

            val showsArray = JSONArray(showResponse)
            for (i in 0 until showsArray.length()) {
                val item = showsArray.getJSONObject(i)
                val firstAired = item.getString("first_aired")
                val episode = item.getJSONObject("episode")
                val show = item.getJSONObject("show")

                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_TYPE, "episode")
                    put(TraktDatabaseHelper.COL_AIR_DATE, firstAired)
                    // Episode details
                    put(TraktDatabaseHelper.COL_SEASON, episode.getInt("season"))
                    put(TraktDatabaseHelper.COL_NUMBER, episode.getInt("number"))
                    put(TraktDatabaseHelper.COL_EPISODE_TITLE, episode.getString("title"))
                    put(TraktDatabaseHelper.COL_EPISODE_TRAKT_ID, episode.getJSONObject("ids").getInt("trakt"))
                    put(TraktDatabaseHelper.COL_EPISODE_TVDB, episode.getJSONObject("ids").optInt("tvdb"))
                    put(TraktDatabaseHelper.COL_EPISODE_IMDB, episode.getJSONObject("ids").optString("imdb"))
                    put(TraktDatabaseHelper.COL_EPISODE_TMDB, episode.getJSONObject("ids").optInt("tmdb"))
                    // Show details
                    put(TraktDatabaseHelper.COL_SHOW_TITLE, show.getString("title"))
                    put(TraktDatabaseHelper.COL_SHOW_YEAR, show.getInt("year"))
                    put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, show.getJSONObject("ids").getInt("trakt"))
                    put(TraktDatabaseHelper.COL_SHOW_SLUG, show.getJSONObject("ids").getString("slug"))
                    put(TraktDatabaseHelper.COL_SHOW_TVDB, show.getJSONObject("ids").optInt("tvdb"))
                    put(TraktDatabaseHelper.COL_SHOW_IMDB, show.getJSONObject("ids").optString("imdb"))
                    put(TraktDatabaseHelper.COL_SHOW_TMDB, show.getJSONObject("ids").optInt("tmdb"))
                }
                dbHelper.insertCalendarData(values)
            }
        }

        // Fetch movies calendar
        val moviesUrl = "https://api.trakt.tv/calendars/my/movies/$today/7"
        val moviesRequest = createRequest(moviesUrl)
        executeRequest(moviesRequest) { movieResponse ->
            val db = dbHelper.writableDatabase
            db.delete(TraktDatabaseHelper.TABLE_CALENDER, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("movie"))

            val moviesArray = JSONArray(movieResponse)
            for (i in 0 until moviesArray.length()) {
                val item = moviesArray.getJSONObject(i)
                val releaseDate = item.getString("released")
                val movie = item.getJSONObject("movie")

                val values = ContentValues().apply {
                    put(TraktDatabaseHelper.COL_TYPE, "movie")
                    put(TraktDatabaseHelper.COL_AIR_DATE, releaseDate)
                    put(TraktDatabaseHelper.COL_TITLE, movie.getString("title"))
                    put(TraktDatabaseHelper.COL_YEAR, movie.getInt("year"))
                    put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, movie.getJSONObject("ids").getInt("trakt"))
                    put(TraktDatabaseHelper.COL_SLUG, movie.getJSONObject("ids").getString("slug"))
                    put(TraktDatabaseHelper.COL_IMDB, movie.getJSONObject("ids").optString("imdb"))
                    put(TraktDatabaseHelper.COL_TMDB, movie.getJSONObject("ids").optInt("tmdb"))
                }
                dbHelper.insertCalendarData(values)
            }
        }
    }

    fun fetchCurrentlyWatching(onResponse: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = "https://api.trakt.tv/users/me/watching"
            val request = createRequest(url)
            executeRequest(request, onResponse)
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (!responseBody.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            onResponse(responseBody)
                        }
                    } else {
                        Log.e("API", "Empty response body")
                    }
                } else {
                    Log.e("API", "Request failed: ${response.code} ${response.message}")
                }
            } catch (e: UnknownHostException) {
                Log.e("API", "Network error: No internet connection", e)
            } catch (e: SocketTimeoutException) {
                Log.e("API", "Network error: Request timed out", e)
            } catch (e: IOException) {
                Log.e("API", "API request failed", e)
            } catch (e: Exception) {
                Log.e("API", "Unexpected error", e)
            }
        }
    }
}