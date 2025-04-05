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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
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

class GetTmdbDetailsSaved(private val context: Context, private val tmdbApiKey: String) {

    private val client = OkHttpClient()
    private val rateLimiter = RateLimiter(10, 1, TimeUnit.SECONDS)

    suspend fun fetchAndSaveTmdbDetails(updateProgress: (String, Int) -> Unit) {
        withContext(Dispatchers.IO) {
            val movieDbHelper = MovieDatabaseHelper(context)
            val tmdbDbHelper = TmdbDetailsDatabaseHelper(context)

            movieDbHelper.readableDatabase.use { movieDb ->
                tmdbDbHelper.readableDatabase.use { tmdbDb ->
                    movieDb.query(
                        MovieDatabaseHelper.TABLE_MOVIES,
                        arrayOf(MovieDatabaseHelper.COLUMN_MOVIES_ID, MovieDatabaseHelper.COLUMN_MOVIE, MovieDatabaseHelper.COLUMN_TITLE),
                        null, null, null, null, null
                    ).use { cursor ->
                        val totalMovies = cursor.count
                        var currentMovie = 0

                        while (cursor.moveToNext()) {
                            val tmdbId = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIES_ID))
                            val movieIndicator = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE))
                            val showTitle = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE))

                            if (movieIndicator != 1 && tmdbId > 0) {
                                val exists = tmdbDb.query(
                                    TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
                                    arrayOf(TmdbDetailsDatabaseHelper.COL_ID),
                                    "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ?",
                                    arrayOf(tmdbId.toString()),
                                    null, null, null
                                ).use { it.moveToFirst() }

                                if (!exists) {
                                    rateLimiter.acquire()
                                    try {
                                        val tmdbDetails = fetchTmdbShowDetails(tmdbId)
                                        saveTmdbDetailsToDb(tmdbDetails)
                                    } catch (e: Exception) {
                                        Log.e("GetTmdbDetailsSaved", "Failed for ID $tmdbId: ${e.message}", e)
                                    }
                                }
                            }

                            currentMovie++
                            val progress = (currentMovie * 100) / totalMovies
                            updateProgress(showTitle, progress)
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchTmdbShowDetails(tmdbId: Int): JSONObject {
        return withContext(Dispatchers.IO) {
            val url = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$tmdbApiKey"
            val request = Request.Builder().url(url).build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful) {
                        throw IOException("TMDB API request failed [${response.code}] for ID $tmdbId: ${response.message}")
                    }
                    if (body.isNullOrBlank()) {
                        throw IOException("Empty response from TMDB API for ID $tmdbId.")
                    }
                    return@use JSONObject(body)
                }
            } catch (e: IOException) {
                Log.e("GetTmdbDetailsSaved", "Network error fetching TMDB TV details for ID $tmdbId: ${e.message}", e)
                throw e
            } catch (e: JSONException) {
                Log.e("GetTmdbDetailsSaved", "JSON parsing error for ID $tmdbId: ${e.message}", e)
                throw e
            }
        }
    }

    private fun saveTmdbDetailsToDb(details: JSONObject) {
        if (!details.has("id") || details.optString("name").isNullOrEmpty()) return

        val dbHelper = TmdbDetailsDatabaseHelper(context)
        dbHelper.writableDatabase.use { db ->
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

                    val seasonsString = buildSeasonsString(details.optJSONArray("seasons"))
                    put(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB, seasonsString)
                }

                db.insertWithOnConflict(
                    TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
                    null,
                    contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            } catch (e: Exception) {
                Log.e("GetTmdbDetailsSaved", "Error saving to DB: ${e.message}", e)
            }
        }
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
