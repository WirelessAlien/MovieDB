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
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper.Companion.SEASONS_EPISODE_SHOW_TMDB
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GetTmdbDetails(private val context: Context, private val tmdbApiKey: String) {

    private val client = OkHttpClient()
    private val rateLimiter = RateLimiter(5, 1, TimeUnit.SECONDS)

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
                TraktDatabaseHelper.TABLE_LIST_ITEM
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
                        "tmdb_id = ?",
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
        val dbHelper = TmdbDetailsDatabaseHelper(context)
        val db = dbHelper.writableDatabase

        val type = if (details.has("title")) "movie" else if (details.has("name")) "show" else return

        val contentValues = ContentValues().apply {
            put(TmdbDetailsDatabaseHelper.COL_TMDB_ID, details.getInt("id"))
            put(TmdbDetailsDatabaseHelper.COL_NAME, if (details.has("title")) details.getString("title") else details.getString("name"))
            put(TmdbDetailsDatabaseHelper.COL_BACKDROP_PATH, details.getString("backdrop_path"))
            put(TmdbDetailsDatabaseHelper.COL_POSTER_PATH, details.getString("poster_path"))
            put(TmdbDetailsDatabaseHelper.COL_SUMMARY, details.getString("overview"))
            put(TmdbDetailsDatabaseHelper.COL_VOTE_AVERAGE, details.getDouble("vote_average"))
            put(TmdbDetailsDatabaseHelper.COL_RELEASE_DATE,
                if (details.has("release_date")) details.getString("release_date")
                else details.getString("first_air_date"))
            val genreIds = details.getJSONArray("genres").let { genresArray ->
                val ids = (0 until genresArray.length()).joinToString(",") { i ->
                    genresArray.getJSONObject(i).getInt("id").toString()
                }
                "[$ids]"
            }
            put(TmdbDetailsDatabaseHelper.COL_GENRE_IDS, genreIds)
            put(TmdbDetailsDatabaseHelper.COL_TYPE, type)
        }

        if (type == "show" && details.has("seasons")) {
            val seasons = details.getJSONArray("seasons")
            val seasonsEpisodes = StringBuilder()

            for (i in 0 until seasons.length()) {
                val season = seasons.getJSONObject(i)
                val seasonNumber = season.getInt("season_number")

                // Skip specials (season_number == 0)
                if (seasonNumber == 0) continue

                val episodeCount = season.getInt("episode_count")
                val episodesList = (1..episodeCount).toList()

                seasonsEpisodes.append("$seasonNumber{${episodesList.joinToString(",")}}")
                if (i < seasons.length() - 1) {
                    seasonsEpisodes.append(",")
                }
            }

            contentValues.put(SEASONS_EPISODE_SHOW_TMDB, seasonsEpisodes.toString())
        }

        db.insert(TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS, null, contentValues)
        db.close()
    }
}

class RateLimiter(private val permits: Int, private val period: Long, private val unit: TimeUnit) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()

    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            while (timestamps.size >= permits && now - timestamps.first() < unit.toMillis(period)) {
                val timeToWait = unit.toMillis(period) - (now - timestamps.first())
                delay(timeToWait)
            }
            if (timestamps.size >= permits) {
                timestamps.removeFirst()
            }
            timestamps.addLast(System.currentTimeMillis())
        }
    }
}