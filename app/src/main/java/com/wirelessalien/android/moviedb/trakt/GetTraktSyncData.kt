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
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.util.Log
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper.Companion.USER_LISTS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Date
import java.util.Locale

class GetTraktSyncData(private val context: Context, private val accessToken: String?, private val clientId: String?) {

    private val client = com.wirelessalien.android.moviedb.NetworkClient.client
    private val dbHelper = TraktDatabaseHelper(context)
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        private const val TAG = "GetTraktSyncData"
        private const val PREF_LAST_MOVIES_COLLECTED = "tkt_last_movies_collected"
        private const val PREF_LAST_SHOWS_COLLECTED = "tkt_last_shows_collected"
        private const val PREF_LAST_MOVIES_WATCHED = "tkt_last_movies_watched"
        private const val PREF_LAST_SHOWS_WATCHED = "tkt_last_shows_watched"
        private const val PREF_LAST_HISTORY_WATCHED = "tkt_last_history_watched"
        private const val PREF_LAST_RATINGS = "tkt_last_ratings"
        private const val PREF_LAST_WATCHLIST = "tkt_last_watchlist"
        private const val PREF_LAST_FAVORITES = "tkt_last_favorites"
        private const val PREF_LAST_LISTS = "tkt_last_lists"
    }

    suspend fun fetchData(forceSync: Boolean = false) = withContext(Dispatchers.IO) {
        if (accessToken.isNullOrEmpty()) {
            Log.w(TAG, "Cannot sync: access token is missing")
            return@withContext
        }

        val lastActivities = if (!forceSync) fetchLastActivities() else null

        if (lastActivities != null) {
            val moviesObj = lastActivities.optJSONObject("movies")
            val showsObj = lastActivities.optJSONObject("shows")
            val historyObj = lastActivities.optJSONObject("history")
            val ratingsObj = lastActivities.optJSONObject("ratings")
            val watchlistObj = lastActivities.optJSONObject("watchlist")
            val favoritesObj = lastActivities.optJSONObject("favorites")
            val listsObj = lastActivities.optJSONObject("lists")

            val moviesCollectedAt = moviesObj?.optString("collected_at")
            val showsCollectedAt = showsObj?.optString("collected_at")
            val moviesWatchedAt = moviesObj?.optString("watched_at")
            val showsWatchedAt = showsObj?.optString("watched_at")
            val historyWatchedAt = historyObj?.optString("watched_at")
            val ratingsRatedAt = ratingsObj?.optString("rated_at")
            val watchlistAt = watchlistObj?.optString("watchlist_at")
            val favoritesAt = favoritesObj?.optString("favorited_at")
            val listsUpdatedAt = listsObj?.optString("updated_at")

            if (moviesCollectedAt != preferences.getString(PREF_LAST_MOVIES_COLLECTED, null)) {
                fetchCollectionData()
                preferences.edit().putString(PREF_LAST_MOVIES_COLLECTED, moviesCollectedAt).apply()
            }

            if (showsCollectedAt != preferences.getString(PREF_LAST_SHOWS_COLLECTED, null)) {
                fetchCollectionShowData()
                preferences.edit().putString(PREF_LAST_SHOWS_COLLECTED, showsCollectedAt).apply()
            }

            if (moviesWatchedAt != preferences.getString(PREF_LAST_MOVIES_WATCHED, null)) {
                fetchWatchedDataMovie()
                preferences.edit().putString(PREF_LAST_MOVIES_WATCHED, moviesWatchedAt).apply()
            }

            if (showsWatchedAt != preferences.getString(PREF_LAST_SHOWS_WATCHED, null)) {
                fetchWatchedDataShow()
                preferences.edit().putString(PREF_LAST_SHOWS_WATCHED, showsWatchedAt).apply()
            }

            if (historyWatchedAt != preferences.getString(PREF_LAST_HISTORY_WATCHED, null)) {
                fetchHistoryData()
                preferences.edit().putString(PREF_LAST_HISTORY_WATCHED, historyWatchedAt).apply()
            }

            if (ratingsRatedAt != preferences.getString(PREF_LAST_RATINGS, null)) {
                fetchRatingData()
                preferences.edit().putString(PREF_LAST_RATINGS, ratingsRatedAt).apply()
            }

            if (watchlistAt != preferences.getString(PREF_LAST_WATCHLIST, null)) {
                fetchWatchlistData()
                preferences.edit().putString(PREF_LAST_WATCHLIST, watchlistAt).apply()
            }

            if (favoritesAt != preferences.getString(PREF_LAST_FAVORITES, null)) {
                fetchFavoriteData()
                preferences.edit().putString(PREF_LAST_FAVORITES, favoritesAt).apply()
            }

            if (listsUpdatedAt != preferences.getString(PREF_LAST_LISTS, null)) {
                fetchUserLists()
                fetchAllListItems()
                preferences.edit().putString(PREF_LAST_LISTS, listsUpdatedAt).apply()
            }
        } else {
            // Full fallback / force sync
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
        }

        // Always refresh upcoming 30-day calendar
        fetchCalendarData()
    }

    private fun fetchLastActivities(): JSONObject? {
        val url = "https://api.trakt.tv/sync/last_activities"
        val request = createRequest(url)
        var result: JSONObject? = null
        executeRequest(request) { responseBody ->
            try {
                result = JSONObject(responseBody)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse last_activities: ${e.message}")
            }
        }
        return result
    }

    fun fetchAllListItems() {
        val db = dbHelper.readableDatabase
        val listIds = mutableListOf<String>()
        val cursor = db.query(
            USER_LISTS,
            arrayOf(TraktDatabaseHelper.COL_TRAKT_ID),
            null,
            null,
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val listId = it.getInt(it.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TRAKT_ID)).toString()
                    listIds.add(listId)
                } while (it.moveToNext())
            }
        }

        for (listId in listIds) {
            getListsItems(listId)
        }
    }

    fun fetchFavoriteData() {
        val url = "https://api.trakt.tv/sync/favorites"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TraktDatabaseHelper.TABLE_FAVORITE, null, null)

                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val type = jsonObject.optString("type")
                    val values = ContentValues().apply {
                        put(TraktDatabaseHelper.COL_RANK, jsonObject.optInt("rank"))
                        put(TraktDatabaseHelper.COL_LISTED_AT, jsonObject.optString("listed_at"))
                        put(TraktDatabaseHelper.COL_NOTES, jsonObject.optString("notes"))
                        put(TraktDatabaseHelper.COL_TYPE, type)
                        when (type) {
                            "movie" -> {
                                val movie = jsonObject.optJSONObject("movie")
                                if (movie != null) {
                                    val ids = movie.optJSONObject("ids")
                                    put(TraktDatabaseHelper.COL_TITLE, movie.optString("title"))
                                    put(TraktDatabaseHelper.COL_YEAR, movie.optInt("year"))
                                    put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                                    put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                                    put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                                    put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                                }
                            }
                            "show" -> {
                                val show = jsonObject.optJSONObject("show")
                                if (show != null) {
                                    val ids = show.optJSONObject("ids")
                                    put(TraktDatabaseHelper.COL_TITLE, show.optString("title"))
                                    put(TraktDatabaseHelper.COL_YEAR, show.optInt("year"))
                                    put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                                    put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                                    put(TraktDatabaseHelper.COL_TVDB, ids?.optInt("tvdb", -1))
                                    put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                                    put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                                }
                            }
                        }
                    }
                    db.insert(TraktDatabaseHelper.TABLE_FAVORITE, null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun fetchWatchlistData() {
        val url = "https://api.trakt.tv/sync/watchlist"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TraktDatabaseHelper.TABLE_WATCHLIST, null, null)

                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val type = jsonObject.optString("type")
                    val values = ContentValues().apply {
                        put(TraktDatabaseHelper.COL_RANK, jsonObject.optInt("rank"))
                        put(TraktDatabaseHelper.COL_LISTED_AT, jsonObject.optString("listed_at"))
                        put(TraktDatabaseHelper.COL_NOTES, jsonObject.optString("notes"))
                        put(TraktDatabaseHelper.COL_TYPE, type)
                        when (type) {
                            "movie" -> {
                                val movie = jsonObject.optJSONObject("movie")
                                if (movie != null) {
                                    val ids = movie.optJSONObject("ids")
                                    put(TraktDatabaseHelper.COL_TITLE, movie.optString("title"))
                                    put(TraktDatabaseHelper.COL_YEAR, movie.optInt("year"))
                                    put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                                    put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                                    put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                                    put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                                }
                            }
                            "show" -> {
                                val show = jsonObject.optJSONObject("show")
                                if (show != null) {
                                    val ids = show.optJSONObject("ids")
                                    put(TraktDatabaseHelper.COL_TITLE, show.optString("title"))
                                    put(TraktDatabaseHelper.COL_YEAR, show.optInt("year"))
                                    put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                                    put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                                    put(TraktDatabaseHelper.COL_TVDB, ids?.optInt("tvdb", -1))
                                    put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                                    put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                                }
                            }
                            "season" -> {
                                val season = jsonObject.optJSONObject("season")
                                val show = jsonObject.optJSONObject("show")
                                val seasonIds = season?.optJSONObject("ids")
                                val showIds = show?.optJSONObject("ids")
                                put(TraktDatabaseHelper.COL_SEASON, season?.optInt("number"))
                                put(TraktDatabaseHelper.COL_TVDB, seasonIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_TMDB, seasonIds?.optInt("tmdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_TITLE, show?.optString("title"))
                                put(TraktDatabaseHelper.COL_SHOW_YEAR, show?.optInt("year"))
                                put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, showIds?.optInt("trakt", -1))
                                put(TraktDatabaseHelper.COL_SHOW_SLUG, showIds?.optString("slug"))
                                put(TraktDatabaseHelper.COL_SHOW_TVDB, showIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_IMDB, showIds?.optString("imdb"))
                                put(TraktDatabaseHelper.COL_SHOW_TMDB, showIds?.optInt("tmdb", -1))
                            }
                            "episode" -> {
                                val episode = jsonObject.optJSONObject("episode")
                                val show = jsonObject.optJSONObject("show")
                                val epIds = episode?.optJSONObject("ids")
                                val showIds = show?.optJSONObject("ids")
                                put(TraktDatabaseHelper.COL_SEASON, episode?.optInt("season"))
                                put(TraktDatabaseHelper.COL_NUMBER, episode?.optInt("number"))
                                put(TraktDatabaseHelper.COL_TITLE, episode?.optString("title"))
                                put(TraktDatabaseHelper.COL_TRAKT_ID, epIds?.optInt("trakt", -1))
                                put(TraktDatabaseHelper.COL_TVDB, epIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_IMDB, epIds?.optString("imdb"))
                                put(TraktDatabaseHelper.COL_TMDB, epIds?.optInt("tmdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_TITLE, show?.optString("title"))
                                put(TraktDatabaseHelper.COL_SHOW_YEAR, show?.optInt("year"))
                                put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, showIds?.optInt("trakt", -1))
                                put(TraktDatabaseHelper.COL_SHOW_SLUG, showIds?.optString("slug"))
                                put(TraktDatabaseHelper.COL_SHOW_TVDB, showIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_IMDB, showIds?.optString("imdb"))
                                put(TraktDatabaseHelper.COL_SHOW_TMDB, showIds?.optInt("tmdb", -1))
                            }
                        }
                    }
                    db.insert(TraktDatabaseHelper.TABLE_WATCHLIST, null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun fetchRatingData() {
        val url = "https://api.trakt.tv/sync/ratings"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TraktDatabaseHelper.TABLE_RATING, null, null)

                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val type = jsonObject.optString("type")
                    val values = ContentValues().apply {
                        put(TraktDatabaseHelper.COL_RATED_AT, jsonObject.optString("rated_at"))
                        put(TraktDatabaseHelper.COL_RATING, jsonObject.optInt("rating"))
                        put(TraktDatabaseHelper.COL_TYPE, type)
                        when (type) {
                            "movie" -> {
                                val movie = jsonObject.optJSONObject("movie")
                                if (movie != null) {
                                    val ids = movie.optJSONObject("ids")
                                    put(TraktDatabaseHelper.COL_TITLE, movie.optString("title"))
                                    put(TraktDatabaseHelper.COL_YEAR, movie.optInt("year"))
                                    put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                                    put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                                    put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                                    put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                                }
                            }
                            "show" -> {
                                val show = jsonObject.optJSONObject("show")
                                if (show != null) {
                                    val ids = show.optJSONObject("ids")
                                    put(TraktDatabaseHelper.COL_TITLE, show.optString("title"))
                                    put(TraktDatabaseHelper.COL_YEAR, show.optInt("year"))
                                    put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                                    put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                                    put(TraktDatabaseHelper.COL_TVDB, ids?.optInt("tvdb", -1))
                                    put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                                    put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                                }
                            }
                            "season" -> {
                                val season = jsonObject.optJSONObject("season")
                                val show = jsonObject.optJSONObject("show")
                                val seasonIds = season?.optJSONObject("ids")
                                val showIds = show?.optJSONObject("ids")
                                put(TraktDatabaseHelper.COL_SEASON, season?.optInt("number"))
                                put(TraktDatabaseHelper.COL_TVDB, seasonIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_TMDB, seasonIds?.optInt("tmdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_TITLE, show?.optString("title"))
                                put(TraktDatabaseHelper.COL_SHOW_YEAR, show?.optInt("year"))
                                put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, showIds?.optInt("trakt", -1))
                                put(TraktDatabaseHelper.COL_SHOW_SLUG, showIds?.optString("slug"))
                                put(TraktDatabaseHelper.COL_SHOW_TVDB, showIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_IMDB, showIds?.optString("imdb"))
                                put(TraktDatabaseHelper.COL_SHOW_TMDB, showIds?.optInt("tmdb", -1))
                            }
                            "episode" -> {
                                val episode = jsonObject.optJSONObject("episode")
                                val show = jsonObject.optJSONObject("show")
                                val epIds = episode?.optJSONObject("ids")
                                val showIds = show?.optJSONObject("ids")
                                put(TraktDatabaseHelper.COL_SEASON, episode?.optInt("season"))
                                put(TraktDatabaseHelper.COL_NUMBER, episode?.optInt("number"))
                                put(TraktDatabaseHelper.COL_TITLE, episode?.optString("title"))
                                put(TraktDatabaseHelper.COL_TRAKT_ID, epIds?.optInt("trakt", -1))
                                put(TraktDatabaseHelper.COL_TVDB, epIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_IMDB, epIds?.optString("imdb"))
                                put(TraktDatabaseHelper.COL_TMDB, epIds?.optInt("tmdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_TITLE, show?.optString("title"))
                                put(TraktDatabaseHelper.COL_SHOW_YEAR, show?.optInt("year"))
                                put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, showIds?.optInt("trakt", -1))
                                put(TraktDatabaseHelper.COL_SHOW_SLUG, showIds?.optString("slug"))
                                put(TraktDatabaseHelper.COL_SHOW_TVDB, showIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_IMDB, showIds?.optString("imdb"))
                                put(TraktDatabaseHelper.COL_SHOW_TMDB, showIds?.optInt("tmdb", -1))
                            }
                        }
                    }
                    db.insert(TraktDatabaseHelper.TABLE_RATING, null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun fetchHistoryData() {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TraktDatabaseHelper.TABLE_HISTORY, null, null)

            // Fetch up to 5 pages of history (250 items) to prevent wiping user history
            var page = 1
            var hasMorePages = true
            while (hasMorePages && page <= 5) {
                val url = "https://api.trakt.tv/sync/history?page=$page&limit=50"
                val request = createRequest(url)
                var itemsInPage = 0

                executeRequest(request) { response ->
                    val jsonArray = JSONArray(response)
                    itemsInPage = jsonArray.length()
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val type = jsonObject.optString("type")
                        val values = ContentValues().apply {
                            put(TraktDatabaseHelper.COL_WATCHED_AT, jsonObject.optString("watched_at"))
                            put(TraktDatabaseHelper.COL_ACTION, jsonObject.optString("action"))
                            put(TraktDatabaseHelper.COL_TYPE, type)
                            when (type) {
                                "movie" -> {
                                    val movie = jsonObject.optJSONObject("movie")
                                    if (movie != null) {
                                        val ids = movie.optJSONObject("ids")
                                        put(TraktDatabaseHelper.COL_TITLE, movie.optString("title"))
                                        put(TraktDatabaseHelper.COL_YEAR, movie.optInt("year"))
                                        put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                                        put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                                        put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                                        put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                                    }
                                }
                                "episode" -> {
                                    val episode = jsonObject.optJSONObject("episode")
                                    val show = jsonObject.optJSONObject("show")
                                    val epIds = episode?.optJSONObject("ids")
                                    val showIds = show?.optJSONObject("ids")
                                    put(TraktDatabaseHelper.COL_SEASON, episode?.optInt("season"))
                                    put(TraktDatabaseHelper.COL_NUMBER, episode?.optInt("number"))
                                    put(TraktDatabaseHelper.COL_TITLE, episode?.optString("title"))
                                    put(TraktDatabaseHelper.COL_TRAKT_ID, epIds?.optInt("trakt", -1))
                                    put(TraktDatabaseHelper.COL_TVDB, epIds?.optInt("tvdb", -1))
                                    put(TraktDatabaseHelper.COL_IMDB, epIds?.optString("imdb"))
                                    put(TraktDatabaseHelper.COL_TMDB, epIds?.optInt("tmdb", -1))
                                    put(TraktDatabaseHelper.COL_SHOW_TITLE, show?.optString("title"))
                                    put(TraktDatabaseHelper.COL_SHOW_YEAR, show?.optInt("year"))
                                    put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, showIds?.optInt("trakt", -1))
                                    put(TraktDatabaseHelper.COL_SHOW_SLUG, showIds?.optString("slug"))
                                    put(TraktDatabaseHelper.COL_SHOW_TVDB, showIds?.optInt("tvdb", -1))
                                    put(TraktDatabaseHelper.COL_SHOW_IMDB, showIds?.optString("imdb"))
                                    put(TraktDatabaseHelper.COL_SHOW_TMDB, showIds?.optInt("tmdb", -1))
                                }
                            }
                        }
                        db.insert(TraktDatabaseHelper.TABLE_HISTORY, null, values)
                    }
                }

                if (itemsInPage < 50) {
                    hasMorePages = false
                } else {
                    page++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun fetchWatchedDataMovie() {
        val url = "https://api.trakt.tv/sync/watched/movies"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TraktDatabaseHelper.TABLE_WATCHED, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("movie"))

                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val values = ContentValues().apply {
                        put(TraktDatabaseHelper.COL_PLAYS, jsonObject.optInt("plays"))
                        put(TraktDatabaseHelper.COL_LAST_WATCHED_AT, jsonObject.optString("last_watched_at"))
                        put(TraktDatabaseHelper.COL_LAST_UPDATED_AT, jsonObject.optString("last_updated_at"))
                    }

                    val movie = jsonObject.optJSONObject("movie")
                    if (movie != null) {
                        val ids = movie.optJSONObject("ids")
                        values.apply {
                            put(TraktDatabaseHelper.COL_TYPE, "movie")
                            put(TraktDatabaseHelper.COL_TITLE, movie.optString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, movie.optInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                            put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                            put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                        }
                        db.insert(TraktDatabaseHelper.TABLE_WATCHED, null, values)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun fetchWatchedDataShow() {
        val url = "https://api.trakt.tv/sync/watched/shows"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TraktDatabaseHelper.TABLE_WATCHED, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("show"))

                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val values = ContentValues().apply {
                        put(TraktDatabaseHelper.COL_PLAYS, jsonObject.optInt("plays"))
                        put(TraktDatabaseHelper.COL_LAST_WATCHED_AT, jsonObject.optString("last_watched_at"))
                        put(TraktDatabaseHelper.COL_LAST_UPDATED_AT, jsonObject.optString("last_updated_at"))
                    }

                    val show = jsonObject.optJSONObject("show")
                    if (show != null) {
                        val ids = show.optJSONObject("ids")
                        val showTraktId = ids?.optInt("trakt", -1) ?: -1
                        val showTmdbId = ids?.optInt("tmdb", -1) ?: -1

                        val showValues = ContentValues(values).apply {
                            put(TraktDatabaseHelper.COL_TYPE, "show")
                            put(TraktDatabaseHelper.COL_TITLE, show.optString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, show.optInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, showTraktId)
                            put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                            put(TraktDatabaseHelper.COL_TVDB, ids?.optInt("tvdb", -1))
                            put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, showTmdbId)
                        }

                        db.insert(TraktDatabaseHelper.TABLE_WATCHED, null, showValues)

                        db.delete(TraktDatabaseHelper.TABLE_SEASON_EPISODE_WATCHED, "${TraktDatabaseHelper.COL_SHOW_TRAKT_ID} = ?", arrayOf(showTraktId.toString()))

                        val seasons = jsonObject.optJSONArray("seasons")
                        if (seasons != null) {
                            for (j in 0 until seasons.length()) {
                                val season = seasons.getJSONObject(j)
                                val seasonNumber = season.optInt("number")
                                val episodes = season.optJSONArray("episodes")

                                if (episodes != null) {
                                    for (k in 0 until episodes.length()) {
                                        val episode = episodes.getJSONObject(k)
                                        val episodeNumber = episode.optInt("number")

                                        val episodeValues = ContentValues().apply {
                                            put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, showTraktId)
                                            put(TraktDatabaseHelper.COL_SHOW_TMDB_ID, showTmdbId)
                                            put(TraktDatabaseHelper.COL_SEASON_NUMBER, seasonNumber)
                                            put(TraktDatabaseHelper.COL_EPISODE_NUMBER, episodeNumber)
                                            put(TraktDatabaseHelper.COL_PLAYS, episode.optInt("plays"))
                                            put(TraktDatabaseHelper.COL_LAST_WATCHED_AT, episode.optString("last_watched_at"))
                                        }
                                        db.insert(TraktDatabaseHelper.TABLE_SEASON_EPISODE_WATCHED, null, episodeValues)
                                    }
                                }
                            }
                        }
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun fetchCollectionData() {
        val url = "https://api.trakt.tv/sync/collection/movies?extended=metadata"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TraktDatabaseHelper.TABLE_COLLECTION, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("movie"))
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val values = ContentValues().apply {
                        put(TraktDatabaseHelper.COL_COLLECTED_AT, jsonObject.optString("collected_at"))
                        put(TraktDatabaseHelper.COL_UPDATED_AT, jsonObject.optString("updated_at"))
                    }

                    val movie = jsonObject.optJSONObject("movie")
                    if (movie != null) {
                        val ids = movie.optJSONObject("ids")
                        values.apply {
                            put(TraktDatabaseHelper.COL_TYPE, "movie")
                            put(TraktDatabaseHelper.COL_TITLE, movie.optString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, movie.optInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                            put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                            put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                        }
                    }

                    val metadata = jsonObject.optJSONObject("metadata")
                    if (metadata != null) {
                        values.apply {
                            put(TraktDatabaseHelper.COL_MEDIA_TYPE, metadata.optString("media_type"))
                            put(TraktDatabaseHelper.COL_RESOLUTION, metadata.optString("resolution"))
                            put(TraktDatabaseHelper.COL_HDR, metadata.optString("hdr"))
                            put(TraktDatabaseHelper.COL_AUDIO, metadata.optString("audio"))
                            put(TraktDatabaseHelper.COL_AUDIO_CHANNELS, metadata.optString("audio_channels"))
                            put(TraktDatabaseHelper.COL_THD, metadata.optBoolean("3d"))
                        }
                    }

                    db.insert(TraktDatabaseHelper.TABLE_COLLECTION, null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun fetchCollectionShowData() {
        val url = "https://api.trakt.tv/sync/collection/shows?extended=metadata"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TraktDatabaseHelper.TABLE_COLLECTION, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("show"))

                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val values = ContentValues().apply {
                        put(TraktDatabaseHelper.COL_COLLECTED_AT, jsonObject.optString("last_collected_at"))
                        put(TraktDatabaseHelper.COL_UPDATED_AT, jsonObject.optString("last_updated_at"))
                    }

                    val show = jsonObject.optJSONObject("show")
                    if (show != null) {
                        val ids = show.optJSONObject("ids")
                        val showValues = ContentValues(values).apply {
                            put(TraktDatabaseHelper.COL_TYPE, "show")
                            put(TraktDatabaseHelper.COL_TITLE, show.optString("title"))
                            put(TraktDatabaseHelper.COL_YEAR, show.optInt("year"))
                            put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                            put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                            put(TraktDatabaseHelper.COL_TVDB, ids?.optInt("tvdb", -1))
                            put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                            put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                        }

                        val seasons = jsonObject.optJSONArray("seasons")
                        if (seasons != null) {
                            for (j in 0 until seasons.length()) {
                                val season = seasons.getJSONObject(j)
                                val seasonNumber = season.optInt("number")
                                val episodes = season.optJSONArray("episodes")
                                if (episodes != null) {
                                    for (k in 0 until episodes.length()) {
                                        val episode = episodes.getJSONObject(k)
                                        val episodeValues = ContentValues(showValues).apply {
                                            put(TraktDatabaseHelper.COL_SEASON, seasonNumber)
                                            put(TraktDatabaseHelper.COL_NUMBER, episode.optInt("number"))
                                            put(TraktDatabaseHelper.COL_COLLECTED_AT, episode.optString("collected_at"))
                                        }

                                        val metadata = episode.optJSONObject("metadata")
                                        if (metadata != null) {
                                            episodeValues.apply {
                                                put(TraktDatabaseHelper.COL_MEDIA_TYPE, metadata.optString("media_type"))
                                                put(TraktDatabaseHelper.COL_RESOLUTION, metadata.optString("resolution"))
                                                put(TraktDatabaseHelper.COL_HDR, metadata.optString("hdr"))
                                                put(TraktDatabaseHelper.COL_AUDIO, metadata.optString("audio"))
                                                put(TraktDatabaseHelper.COL_AUDIO_CHANNELS, metadata.optString("audio_channels"))
                                                put(TraktDatabaseHelper.COL_THD, metadata.optBoolean("3d"))
                                            }
                                        }

                                        db.insert(TraktDatabaseHelper.TABLE_COLLECTION, null, episodeValues)
                                    }
                                }
                            }
                        }
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun fetchUserLists() {
        val url = "https://api.trakt.tv/users/me/lists"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(USER_LISTS, null, null)
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val ids = jsonObject.optJSONObject("ids")
                    val values = ContentValues().apply {
                        put(TraktDatabaseHelper.COL_NAME, jsonObject.optString("name"))
                        put(TraktDatabaseHelper.COL_DESCRIPTION, jsonObject.optString("description"))
                        put(TraktDatabaseHelper.COL_PRIVACY, jsonObject.optString("privacy"))
                        put(TraktDatabaseHelper.COL_SHARE_LINK, jsonObject.optString("share_link"))
                        put(TraktDatabaseHelper.COL_TYPE, jsonObject.optString("type"))
                        put(TraktDatabaseHelper.COL_DISPLAY_NUMBERS, jsonObject.optBoolean("display_numbers"))
                        put(TraktDatabaseHelper.COL_ALLOW_COMMENTS, jsonObject.optBoolean("allow_comments"))
                        put(TraktDatabaseHelper.COL_SORT_BY, jsonObject.optString("sort_by"))
                        put(TraktDatabaseHelper.COL_SORT_HOW, jsonObject.optString("sort_how"))
                        put(TraktDatabaseHelper.COL_CREATED_AT, jsonObject.optString("created_at"))
                        put(TraktDatabaseHelper.COL_UPDATED_AT, jsonObject.optString("updated_at"))
                        put(TraktDatabaseHelper.COL_ITEM_COUNT, jsonObject.optInt("item_count"))
                        put(TraktDatabaseHelper.COL_COMMENT_COUNT, jsonObject.optInt("comment_count"))
                        put(TraktDatabaseHelper.COL_LIKES, jsonObject.optInt("likes"))
                        put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                        put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                    }
                    db.insert(USER_LISTS, null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun getListsItems(listId: String) {
        val url = "https://api.trakt.tv/users/me/lists/$listId/items/"
        val request = createRequest(url)
        executeRequest(request) { response ->
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(
                    TraktDatabaseHelper.TABLE_LIST_ITEM,
                    "${TraktDatabaseHelper.COL_LIST_ID} = ?",
                    arrayOf(listId)
                )
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val type = jsonObject.optString("type")
                    val values = ContentValues().apply {
                        put(TraktDatabaseHelper.COL_LIST_ID, listId)
                        put(TraktDatabaseHelper.COL_RANK, jsonObject.optInt("rank"))
                        put(TraktDatabaseHelper.COL_LISTED_AT, jsonObject.optString("listed_at"))
                        put(TraktDatabaseHelper.COL_NOTES, jsonObject.optString("notes"))
                        put(TraktDatabaseHelper.COL_TYPE, type)
                        when (type) {
                            "movie" -> {
                                val movie = jsonObject.optJSONObject("movie")
                                if (movie != null) {
                                    val ids = movie.optJSONObject("ids")
                                    put(TraktDatabaseHelper.COL_TITLE, movie.optString("title"))
                                    put(TraktDatabaseHelper.COL_YEAR, movie.optInt("year"))
                                    put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                                    put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                                    put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                                    put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                                }
                            }
                            "show" -> {
                                val show = jsonObject.optJSONObject("show")
                                if (show != null) {
                                    val ids = show.optJSONObject("ids")
                                    put(TraktDatabaseHelper.COL_TITLE, show.optString("title"))
                                    put(TraktDatabaseHelper.COL_YEAR, show.optInt("year"))
                                    put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                                    put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                                    put(TraktDatabaseHelper.COL_TVDB, ids?.optInt("tvdb", -1))
                                    put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                                    put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                                }
                            }
                            "season" -> {
                                val season = jsonObject.optJSONObject("season")
                                val show = jsonObject.optJSONObject("show")
                                val seasonIds = season?.optJSONObject("ids")
                                val showIds = show?.optJSONObject("ids")
                                put(TraktDatabaseHelper.COL_SEASON, season?.optInt("number"))
                                put(TraktDatabaseHelper.COL_TVDB, seasonIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_TMDB, seasonIds?.optInt("tmdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_TITLE, show?.optString("title"))
                                put(TraktDatabaseHelper.COL_SHOW_YEAR, show?.optInt("year"))
                                put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, showIds?.optInt("trakt", -1))
                                put(TraktDatabaseHelper.COL_SHOW_SLUG, showIds?.optString("slug"))
                                put(TraktDatabaseHelper.COL_SHOW_TVDB, showIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_IMDB, showIds?.optString("imdb"))
                                put(TraktDatabaseHelper.COL_SHOW_TMDB, showIds?.optInt("tmdb", -1))
                            }
                            "episode" -> {
                                val episode = jsonObject.optJSONObject("episode")
                                val show = jsonObject.optJSONObject("show")
                                val epIds = episode?.optJSONObject("ids")
                                val showIds = show?.optJSONObject("ids")
                                put(TraktDatabaseHelper.COL_SEASON, episode?.optInt("season"))
                                put(TraktDatabaseHelper.COL_NUMBER, episode?.optInt("number"))
                                put(TraktDatabaseHelper.COL_TITLE, episode?.optString("title"))
                                put(TraktDatabaseHelper.COL_TRAKT_ID, epIds?.optInt("trakt", -1))
                                put(TraktDatabaseHelper.COL_TVDB, epIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_IMDB, epIds?.optString("imdb"))
                                put(TraktDatabaseHelper.COL_TMDB, epIds?.optInt("tmdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_TITLE, show?.optString("title"))
                                put(TraktDatabaseHelper.COL_SHOW_YEAR, show?.optInt("year"))
                                put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, showIds?.optInt("trakt", -1))
                                put(TraktDatabaseHelper.COL_SHOW_SLUG, showIds?.optString("slug"))
                                put(TraktDatabaseHelper.COL_SHOW_TVDB, showIds?.optInt("tvdb", -1))
                                put(TraktDatabaseHelper.COL_SHOW_IMDB, showIds?.optString("imdb"))
                                put(TraktDatabaseHelper.COL_SHOW_TMDB, showIds?.optInt("tmdb", -1))
                            }
                            "person" -> {
                                val person = jsonObject.optJSONObject("person")
                                if (person != null) {
                                    val ids = person.optJSONObject("ids")
                                    put(TraktDatabaseHelper.COL_NAME, person.optString("name"))
                                    put(TraktDatabaseHelper.COL_TRAKT_ID, ids?.optInt("trakt", -1))
                                    put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                                    put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                                    put(TraktDatabaseHelper.COL_TMDB, ids?.optInt("tmdb", -1))
                                }
                            }
                        }
                    }
                    db.insert(TraktDatabaseHelper.TABLE_LIST_ITEM, null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun fetchCalendarData() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())

        val showsUrl = "https://api.trakt.tv/calendars/my/shows/$today/30"
        val showsRequest = createRequest(showsUrl)
        executeRequest(showsRequest) { showResponse ->
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TraktDatabaseHelper.TABLE_CALENDER, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("episode"))

                val showsArray = JSONArray(showResponse)
                for (i in 0 until showsArray.length()) {
                    val item = showsArray.getJSONObject(i)
                    val firstAired = item.optString("first_aired")
                    val episode = item.optJSONObject("episode")
                    val show = item.optJSONObject("show")
                    val epIds = episode?.optJSONObject("ids")
                    val showIds = show?.optJSONObject("ids")

                    val values = ContentValues().apply {
                        put(TraktDatabaseHelper.COL_TYPE, "episode")
                        put(TraktDatabaseHelper.COL_AIR_DATE, firstAired)
                        put(TraktDatabaseHelper.COL_SEASON, episode?.optInt("season"))
                        put(TraktDatabaseHelper.COL_NUMBER, episode?.optInt("number"))
                        put(TraktDatabaseHelper.COL_EPISODE_TITLE, episode?.optString("title"))
                        put(TraktDatabaseHelper.COL_EPISODE_TRAKT_ID, epIds?.optInt("trakt", -1))
                        put(TraktDatabaseHelper.COL_EPISODE_TVDB, epIds?.optInt("tvdb", -1))
                        put(TraktDatabaseHelper.COL_EPISODE_IMDB, epIds?.optString("imdb"))
                        put(TraktDatabaseHelper.COL_EPISODE_TMDB, epIds?.optInt("tmdb", -1))
                        put(TraktDatabaseHelper.COL_SHOW_TITLE, show?.optString("title"))
                        put(TraktDatabaseHelper.COL_SHOW_YEAR, show?.optInt("year"))
                        put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, showIds?.optInt("trakt", -1))
                        put(TraktDatabaseHelper.COL_SHOW_SLUG, showIds?.optString("slug"))
                        put(TraktDatabaseHelper.COL_SHOW_TVDB, showIds?.optInt("tvdb", -1))
                        put(TraktDatabaseHelper.COL_SHOW_IMDB, showIds?.optString("imdb"))
                        put(TraktDatabaseHelper.COL_SHOW_TMDB, showIds?.optInt("tmdb", -1))
                    }
                    db.insert(TraktDatabaseHelper.TABLE_CALENDER, null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        val moviesUrl = "https://api.trakt.tv/calendars/my/movies/$today/30"
        val moviesRequest = createRequest(moviesUrl)
        executeRequest(moviesRequest) { movieResponse ->
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TraktDatabaseHelper.TABLE_CALENDER, "${TraktDatabaseHelper.COL_TYPE} = ?", arrayOf("movie"))

                val moviesArray = JSONArray(movieResponse)
                for (i in 0 until moviesArray.length()) {
                    val item = moviesArray.getJSONObject(i)
                    val releaseDate = item.optString("released")
                    val movie = item.optJSONObject("movie")
                    val ids = movie?.optJSONObject("ids")

                    val values = ContentValues().apply {
                        put(TraktDatabaseHelper.COL_TYPE, "movie")
                        put(TraktDatabaseHelper.COL_AIR_DATE, releaseDate)
                        put(TraktDatabaseHelper.COL_TITLE, movie?.optString("title"))
                        put(TraktDatabaseHelper.COL_YEAR, movie?.optInt("year"))
                        put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, ids?.optInt("trakt", -1))
                        put(TraktDatabaseHelper.COL_SLUG, ids?.optString("slug"))
                        put(TraktDatabaseHelper.COL_IMDB, ids?.optString("imdb"))
                        put(TraktDatabaseHelper.COL_SHOW_TMDB, ids?.optInt("tmdb", -1))
                    }
                    db.insert(TraktDatabaseHelper.TABLE_CALENDER, null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
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
            .addHeader("trakt-api-key", clientId ?: "")
            .build()
    }

    private fun executeRequest(
        request: Request,
        onSuccess: (String) -> Unit,
    ) {
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 1L
                    Log.w(TAG, "Rate limited (429) on ${request.url}. Retry-After: $retryAfter s")
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (!responseBody.isNullOrEmpty()) {
                        onSuccess(responseBody)
                    } else {
                        Log.d(TAG, "Empty response body for ${request.url}")
                    }
                } else {
                    Log.e(TAG, "Request failed with code: ${response.code} for ${request.url}")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing request: ${e.message}")
        }
    }
}