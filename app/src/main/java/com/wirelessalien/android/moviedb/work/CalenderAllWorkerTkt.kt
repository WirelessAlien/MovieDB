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

package com.wirelessalien.android.moviedb.work

import android.content.ContentValues
import android.content.Context
import android.icu.text.SimpleDateFormat
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.Date
import java.util.Locale

class CalenderAllWorkerTkt(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val client = OkHttpClient()
    private val epDbHelper = EpisodeReminderDatabaseHelper(context)
    private val dbHelper = TraktDatabaseHelper(context)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val accessToken = preferences.getString("trakt_access_token", "")
    private val clientId = ConfigHelper.getConfigValue(context, "client_id")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            fetchCalendarData()
            fetchMyCalendarData()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun fetchCalendarData() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())

        epDbHelper.writableDatabase.delete(EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS, null, null)

        // Fetch shows calendar
        val showsUrl = "https://api.trakt.tv/calendars/all/shows/$today/7"
        val showsRequest = createRequest(showsUrl)
        executeRequest(showsRequest) { showResponse ->
            val showsArray = JSONArray(showResponse)
            for (i in 0 until showsArray.length()) {
                val item = showsArray.getJSONObject(i)
                val firstAired = item.optString("first_aired", "NULL")
                val episode = item.getJSONObject("episode")
                val show = item.getJSONObject("show")

                val values = ContentValues().apply {
                    put(EpisodeReminderDatabaseHelper.COL_TYPE, "episode")
                    put(EpisodeReminderDatabaseHelper.COLUMN_DATE, firstAired)
                    // Required fields with default values
                    put(EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID,
                        show.getJSONObject("ids").optInt("tmdb", 0))
                    put(EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME,
                        show.optString("title", "NULL"))
                    put(EpisodeReminderDatabaseHelper.COLUMN_NAME,
                        episode.optString("title", "NULL"))
                    put(EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER,
                        episode.optString("number", "NULL"))

                    // Episode details
                    put(EpisodeReminderDatabaseHelper.COL_SEASON,
                        episode.optInt("season"))
                    put(EpisodeReminderDatabaseHelper.COL_EPISODE_TRAKT_ID,
                        episode.getJSONObject("ids").optInt("trakt"))
                    put(EpisodeReminderDatabaseHelper.COL_EPISODE_TVDB,
                        episode.getJSONObject("ids").optInt("tvdb"))
                    put(EpisodeReminderDatabaseHelper.COL_EPISODE_IMDB,
                        episode.getJSONObject("ids").optString("imdb"))
                    put(EpisodeReminderDatabaseHelper.COL_EPISODE_TMDB,
                        episode.getJSONObject("ids").optInt("tmdb"))

                    // Show details
                    put(EpisodeReminderDatabaseHelper.COL_SHOW_YEAR,
                        show.optInt("year"))
                    put(EpisodeReminderDatabaseHelper.COL_SHOW_TRAKT_ID,
                        show.getJSONObject("ids").optInt("trakt"))
                    put(EpisodeReminderDatabaseHelper.COL_SHOW_SLUG,
                        show.getJSONObject("ids").optString("slug"))
                    put(EpisodeReminderDatabaseHelper.COL_SHOW_TVDB,
                        show.getJSONObject("ids").optInt("tvdb"))
                    put(EpisodeReminderDatabaseHelper.COL_SHOW_IMDB,
                        show.getJSONObject("ids").optString("imdb"))
                }
                epDbHelper.writableDatabase.insert(EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS, null, values)
            }
        }

        // Fetch movies calendar
        val moviesUrl = "https://api.trakt.tv/calendars/all/movies/$today/7"
        val moviesRequest = createRequest(moviesUrl)
        executeRequest(moviesRequest) { movieResponse ->
            val moviesArray = JSONArray(movieResponse)
            for (i in 0 until moviesArray.length()) {
                val item = moviesArray.getJSONObject(i)
                val releaseDate = item.optString("released", "NULL")
                val movie = item.getJSONObject("movie")

                val values = ContentValues().apply {
                    put(EpisodeReminderDatabaseHelper.COL_TYPE, "movie")
                    put(EpisodeReminderDatabaseHelper.COLUMN_DATE, releaseDate)
                    put(EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID,
                        movie.getJSONObject("ids").optInt("tmdb", 0))
                    put(EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME,
                        movie.optString("title", "NULL"))
                    put(EpisodeReminderDatabaseHelper.COL_YEAR,
                        movie.optInt("year"))
                    put(EpisodeReminderDatabaseHelper.COL_SHOW_TRAKT_ID,
                        movie.getJSONObject("ids").optInt("trakt"))
                    put(EpisodeReminderDatabaseHelper.COL_SLUG,
                        movie.getJSONObject("ids").optString("slug"))
                    put(EpisodeReminderDatabaseHelper.COL_IMDB,
                        movie.getJSONObject("ids").optString("imdb"))
                    put(EpisodeReminderDatabaseHelper.COLUMN_NAME, "")
                    put(EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER, "")
                }
                epDbHelper.writableDatabase.insert(EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS, null, values)
            }
        }
    }

    private fun fetchMyCalendarData() {
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

    private fun executeRequest(request: Request, onResponse: (String) -> Unit) {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { onResponse(it) }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}