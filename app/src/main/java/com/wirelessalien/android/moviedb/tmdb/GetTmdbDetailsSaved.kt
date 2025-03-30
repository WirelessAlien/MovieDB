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
import android.util.Log
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.RateLimiter
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GetTmdbDetailsSaved(private val context: Context, private val tmdbApiKey: String) {

    private val client = OkHttpClient()
    private val rateLimiter = RateLimiter(10, 1, TimeUnit.SECONDS)

    suspend fun fetchAndSaveTmdbDetails(updateProgress: (String, Int) -> Unit) {
        withContext(Dispatchers.IO) {
            val movieDbHelper = MovieDatabaseHelper(context)
            val movieDb = movieDbHelper.readableDatabase

            val tmdbDbHelper = TmdbDetailsDatabaseHelper(context)
            val tmdbDb = tmdbDbHelper.readableDatabase

            val cursor = movieDb.query(
                MovieDatabaseHelper.TABLE_MOVIES,
                arrayOf(MovieDatabaseHelper.COLUMN_MOVIES_ID, MovieDatabaseHelper.COLUMN_MOVIE, MovieDatabaseHelper.COLUMN_TITLE),
                null, null, null, null, null
            )

            val totalMovies = cursor.count
            var currentMovie = 0

            while (cursor.moveToNext()) {
                val tmdbId = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIES_ID))
                val movieIndicator = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE))
                val showTitle = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE))

                if (movieIndicator != 1) {
                    val tmdbCursor = tmdbDb.query(
                        TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
                        arrayOf(TmdbDetailsDatabaseHelper.COL_ID),
                        "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ?",
                        arrayOf(tmdbId.toString()),
                        null, null, null
                    )

                    val exists = tmdbCursor.moveToFirst()
                    tmdbCursor.close()

                    if (!exists && tmdbId > 0) {
                        rateLimiter.acquire()
                        try {
                            val tmdbDetails = fetchTmdbShowDetails(tmdbId)
                            saveTmdbDetailsToDb(tmdbDetails)
                        } catch (e: Exception) {
                            Log.e("GetTmdbDetailsSaved", "Error fetching/saving TMDB show details for ID $tmdbId: ${e.message}", e)
                        }
                    }
                }

                currentMovie++
                val progress = (currentMovie * 100) / totalMovies
                updateProgress(showTitle, progress)
            }
            cursor.close()

            movieDb.close()
            tmdbDb.close()
        }
    }

    private suspend fun fetchTmdbShowDetails(tmdbId: Int): JSONObject {
        return withContext(Dispatchers.IO) {
            val url = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$tmdbApiKey"
            val request = Request.Builder().url(url).build()
            val responseString: String?

            try {
                val response = client.newCall(request).execute()
                responseString = response.body?.string()

                if (!response.isSuccessful) {
                    throw Exception("TMDB API request failed for TV $tmdbId with code ${response.code}: ${response.message}. URL: $url")
                }
                if (responseString.isNullOrBlank()) {
                    throw Exception("TMDB API returned empty body for TV $tmdbId. URL: $url")
                }
                JSONObject(responseString)
            } catch (e: Exception) {
                Log.e("GetTmdbDetailsSaved", "Error fetching TMDB TV details for ID $tmdbId from URL $url: ${e.message}")
                throw e
            }
        }
    }

    private fun saveTmdbDetailsToDb(details: JSONObject) {
        if (!details.has("id")) {
            return
        }
        if (!details.has("name") || details.optString("name").isNullOrEmpty()) {
            return
        }


        val dbHelper = TmdbDetailsDatabaseHelper(context)
        val db = dbHelper.writableDatabase

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

                val genresArray = details.optJSONArray("genres")
                if (genresArray != null) {
                    val ids = (0 until genresArray.length()).mapNotNull { i ->
                        genresArray.optJSONObject(i)?.optInt("id")?.toString()
                    }.joinToString(",")
                    put(TmdbDetailsDatabaseHelper.COL_GENRE_IDS, "[$ids]")
                } else {
                    put(TmdbDetailsDatabaseHelper.COL_GENRE_IDS, "[]")
                }

                put(TmdbDetailsDatabaseHelper.COL_TYPE, "show")

                if (details.has("seasons")) {
                    val seasons = details.optJSONArray("seasons")
                    if (seasons != null) {
                        val seasonsEpisodes = StringBuilder()
                        var firstSeason = true

                        for (i in 0 until seasons.length()) {
                            val season = seasons.optJSONObject(i) ?: continue // Skip if null
                            val seasonNumber = season.optInt("season_number", -1)
                            val episodeCount = season.optInt("episode_count", 0)

                            // Skip specials (season_number == 0) or invalid seasons/episodes
                            if (seasonNumber <= 0 || episodeCount <= 0) continue

                            val episodesList = (1..episodeCount).toList()

                            if (!firstSeason) {
                                seasonsEpisodes.append(",")
                            }
                            seasonsEpisodes.append("$seasonNumber{${episodesList.joinToString(",")}}")
                            firstSeason = false
                        }
                        put(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB, seasonsEpisodes.toString())
                    } else {
                        put(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB, "")
                    }
                } else {
                    put(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB, "")
                }
            }

            db.insertWithOnConflict(
                TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
                null,
                contentValues,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) {
            Log.e("GetTmdbDetailsSaved", "Error saving TMDB show details to database for ID ${details.optInt("id", -1)}: ${e.message}", e)
        } finally {
            db.close()
        }
    }
}