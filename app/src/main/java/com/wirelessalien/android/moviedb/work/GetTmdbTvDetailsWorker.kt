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
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.RateLimiter
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GetTmdbTvDetailsWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient()
    private val rateLimiter = RateLimiter(10, 1, TimeUnit.SECONDS)
    private val tmdbApiKey = ConfigHelper.getConfigValue(applicationContext, "api_key")

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                fetchAndUpdateTmdbTvDetails()
                Result.success()
            } catch (e: Exception) {
                Log.e("GetTmdbTvDetailsWorker", "Error in doWork: ${e.message}", e)
                Result.failure()
            }
        }
    }

    private suspend fun fetchAndUpdateTmdbTvDetails() {
        val movieDbHelper = MovieDatabaseHelper(applicationContext)
        val tmdbDbHelper = TmdbDetailsDatabaseHelper(applicationContext)

        movieDbHelper.readableDatabase.use { movieDb ->
            tmdbDbHelper.writableDatabase.use { tmdbDb ->
                movieDb.query(
                    MovieDatabaseHelper.TABLE_MOVIES,
                    arrayOf(MovieDatabaseHelper.COLUMN_MOVIES_ID, MovieDatabaseHelper.COLUMN_MOVIE, MovieDatabaseHelper.COLUMN_TITLE),
                    "${MovieDatabaseHelper.COLUMN_MOVIE} = 0",
                    null, null, null, null
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val tmdbId = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIES_ID))

                        if (tmdbId > 0) {
                            rateLimiter.acquire()
                            try {
                                val tmdbDetails = fetchTmdbShowDetails(tmdbId)
                                updateTmdbDetailsInDb(tmdbDb, tmdbDetails)
                            } catch (e: Exception) {
                                Log.e("GetTmdbTvDetailsWorker", "Failed for TV ID $tmdbId: ${e.message}", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchTmdbShowDetails(tmdbId: Int): JSONObject {
        return withContext(Dispatchers.IO) {
            val url = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$tmdbApiKey&append_to_response=season_details"
            val request = Request.Builder().url(url).build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful) {
                        throw IOException("TMDB API request failed [${response.code}] for TV ID $tmdbId: ${response.message}")
                    }
                    if (body.isNullOrBlank()) {
                        throw IOException("Empty response from TMDB API for TV ID $tmdbId.")
                    }
                    return@use JSONObject(body)
                }
            } catch (e: IOException) {
                Log.e("GetTmdbTvDetailsWorker", "Network error fetching TMDB TV details for ID $tmdbId: ${e.message}", e)
                throw e
            } catch (e: JSONException) {
                Log.e("GetTmdbTvDetailsWorker", "JSON parsing error for TV ID $tmdbId: ${e.message}", e)
                throw e
            }
        }
    }

    private fun updateTmdbDetailsInDb(db: SQLiteDatabase, details: JSONObject) {
        if (!details.has("id") || details.optString("name").isNullOrEmpty()) return

        try {
            val tmdbId = details.getInt("id")

            val contentValues = ContentValues().apply {
                put(TmdbDetailsDatabaseHelper.COL_TMDB_ID, tmdbId)
                put(TmdbDetailsDatabaseHelper.COL_NAME, details.optString("name", ""))
                put(TmdbDetailsDatabaseHelper.COL_BACKDROP_PATH, details.optString("backdrop_path", ""))
                put(TmdbDetailsDatabaseHelper.COL_POSTER_PATH, details.optString("poster_path", ""))
                put(TmdbDetailsDatabaseHelper.COL_SUMMARY, details.optString("overview", ""))
                put(TmdbDetailsDatabaseHelper.COL_VOTE_AVERAGE, details.optDouble("vote_average", 0.0))
                put(TmdbDetailsDatabaseHelper.COL_RELEASE_DATE, details.optString("first_air_date", ""))

                val genres = details.optJSONArray("genres")
                val genreIds = (0 until (genres?.length() ?: 0)).mapNotNull { i ->
                    genres?.optJSONObject(i)?.optInt("id")?.toString()
                }.joinToString(",")
                put(TmdbDetailsDatabaseHelper.COL_GENRE_IDS, if (genreIds.isNotEmpty()) "[$genreIds]" else "[]")

                put(TmdbDetailsDatabaseHelper.COL_TYPE, "show")

                val seasonsString = buildSeasonsStringFromDetails(details)
                put(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB, seasonsString)
            }

            // Use insertWithOnConflict to update if exists, or insert if new
            val result = db.insertWithOnConflict(
                TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
                null,
                contentValues,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            if (result == -1L) {
                Log.e("GetTmdbTvDetailsWorker", "Failed to insert/update details for TV ID $tmdbId")
            } else {
                Log.i("GetTmdbTvDetailsWorker", "Successfully inserted/updated details for TV ID $tmdbId")
            }

        } catch (e: Exception) {
            Log.e("GetTmdbTvDetailsWorker", "Error updating DB for TV ID ${details.optInt("id", -1)}: ${e.message}", e)
        }
    }

    private fun buildSeasonsStringFromDetails(details: JSONObject): String {
        val seasonsArray = details.optJSONArray("seasons")
        if (seasonsArray == null) {
            Log.w("GetTmdbTvDetailsWorker", "No 'seasons' array in details for TMDB ID ${details.optInt("id")}")
            return ""
        }
        return buildSeasonsString(seasonsArray)
    }


    private fun buildSeasonsString(seasons: JSONArray?): String {
        if (seasons == null) return ""
        val builder = StringBuilder()
        var first = true

        for (i in 0 until seasons.length()) {
            val season = seasons.optJSONObject(i) ?: continue
            val seasonNumber = season.optInt("season_number", -1)
            val episodeCount = season.optInt("episode_count", 0)

            if (seasonNumber <= 0 || episodeCount <= 0) continue

            val episodes = (1..episodeCount).joinToString(",")
            if (!first) builder.append(",")
            builder.append("$seasonNumber{$episodes}")
            first = false
        }
        return builder.toString()
    }
}
