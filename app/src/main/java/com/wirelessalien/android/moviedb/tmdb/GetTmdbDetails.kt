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
import com.wirelessalien.android.moviedb.helper.RateLimiter
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GetTmdbDetails(private val context: Context, private val tmdbApiKey: String) {

    private val client = OkHttpClient()
    private val rateLimiter = RateLimiter(10, 1, TimeUnit.SECONDS)

    suspend fun fetchAndSaveTmdbDetails() {
        withContext(Dispatchers.IO) {
            val traktDbHelper = TraktDatabaseHelper(context)
            val traktDb = traktDbHelper.readableDatabase

            val tables = listOf(
                TraktDatabaseHelper.TABLE_COLLECTION,
                TraktDatabaseHelper.TABLE_WATCHLIST,
                TraktDatabaseHelper.TABLE_HISTORY,
                TraktDatabaseHelper.TABLE_FAVORITE,
                TraktDatabaseHelper.TABLE_RATING,
                TraktDatabaseHelper.TABLE_WATCHED,
                TraktDatabaseHelper.TABLE_LIST_ITEM,
                TraktDatabaseHelper.TABLE_CALENDER
            )
            val tmdbDbHelper = TmdbDetailsDatabaseHelper(context)
            val tmdbDb = tmdbDbHelper.readableDatabase

            for (table in tables) {
                val cursor = traktDb.query(
                    table,
                    arrayOf(TraktDatabaseHelper.COL_TMDB, TraktDatabaseHelper.COL_TYPE, TraktDatabaseHelper.COL_SHOW_TMDB),
                    null, null, null, null, null
                )

                while (cursor.moveToNext()) {
                    val tmdbId = cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TMDB))
                    val type = cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TYPE))
                    val showTmdbId = cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TMDB))

                    val idToCheck = if (type == "season" || type == "episode") showTmdbId else tmdbId

                    val tmdbCursor = tmdbDb.query(
                        TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
                        arrayOf(TmdbDetailsDatabaseHelper.COL_ID),
                        "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ?",
                        arrayOf(idToCheck.toString()),
                        null, null, null
                    )

                    if (!tmdbCursor.moveToFirst()) {
                        rateLimiter.acquire()
                        val tmdbDetails = fetchTmdbDetails(idToCheck, type)
                        saveTmdbDetailsToDb(tmdbDetails)
                    }
                    tmdbCursor.close()
                }
                cursor.close()
            }
            traktDb.close()
            tmdbDb.close()
        }
    }

    private suspend fun fetchTmdbDetails(tmdbId: Int, type: String): JSONObject {
        return withContext(Dispatchers.IO) {
            val url = when (type) {
                "movie" -> "https://api.themoviedb.org/3/movie/$tmdbId?api_key=$tmdbApiKey"
                "show", "season", "episode" -> "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$tmdbApiKey"
                else -> throw IllegalArgumentException("Unknown type: $type")
            }

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "")
        }
    }

    private fun saveTmdbDetailsToDb(details: JSONObject) {
        if (!details.has("id")) {
            Log.i("GetTmdbDetailsSaved", "Skipping save: No 'id' field found in JSON object.")
            return
        }

        val dbHelper = TmdbDetailsDatabaseHelper(context)
        val db = dbHelper.writableDatabase

        val type = if (details.has("title") && !details.optString("title").isNullOrEmpty()) "movie"
        else if (details.has("name") && !details.optString("name").isNullOrEmpty()) "show"
        else {
            Log.i("GetTmdbDetailsSaved", "Skipping save: No 'title' or 'name' field found in JSON object.")
            db.close()
            return
        }

        try {
            val contentValues = ContentValues().apply {
                put(TmdbDetailsDatabaseHelper.COL_TMDB_ID, details.getInt("id"))
                put(TmdbDetailsDatabaseHelper.COL_NAME, if (type == "movie") details.optString("title", "") else details.optString("name", ""))
                put(TmdbDetailsDatabaseHelper.COL_BACKDROP_PATH, details.optString("backdrop_path", ""))
                put(TmdbDetailsDatabaseHelper.COL_POSTER_PATH, details.optString("poster_path", ""))
                put(TmdbDetailsDatabaseHelper.COL_SUMMARY, details.optString("overview", ""))
                put(TmdbDetailsDatabaseHelper.COL_VOTE_AVERAGE, details.optDouble("vote_average", 0.0))
                put(
                    TmdbDetailsDatabaseHelper.COL_RELEASE_DATE,
                    if (type == "movie") details.optString("release_date", "")
                    else details.optString("first_air_date", "")) // Use first_air_date for shows

                val genresArray = details.optJSONArray("genres")
                if (genresArray != null) {
                    val ids = (0 until genresArray.length()).mapNotNull { i ->
                        genresArray.optJSONObject(i)?.optInt("id")?.toString()
                    }.joinToString(",")
                    put(TmdbDetailsDatabaseHelper.COL_GENRE_IDS, "[$ids]")
                } else {
                    put(TmdbDetailsDatabaseHelper.COL_GENRE_IDS, "[]")
                }

                put(TmdbDetailsDatabaseHelper.COL_TYPE, type)

                // Process seasons only if it's a show and seasons data is available
                if (type == "show" && details.has("seasons")) {
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
                    }
                }
            }

            db.insertWithOnConflict(
                TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
                null,
                contentValues,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) {
            Log.e("GetTmdbDetailsSaved", "Error saving TMDB details to database: ${e.message}")
        } finally {
            db.close()
        }
    }
}