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

package com.wirelessalien.android.moviedb.simkl

import android.content.Context
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.util.Log
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.SimklAutoSyncDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SimklAutoSyncManager(val context: Context) {

    data class LocalSimklMediaItem(
        val id: Int,
        val title: String,
        val year: Int,
        val isMovie: Boolean,
        val category: Int,
        val simklStatus: String,
        val watchedAt: String
    )

    private val movieDbHelper = MovieDatabaseHelper(context)
    private val simklSyncDbHelper = SimklAutoSyncDatabaseHelper(context)
    private val simklAccessToken = PreferenceManager.getDefaultSharedPreferences(context)
        .getString("simkl_access_token", null)

    suspend fun syncMediaToSimkl() = withContext(Dispatchers.IO) {
        if (simklAccessToken.isNullOrEmpty()) return@withContext

        val allMediaItems = getAllLocalMediaItems()
        val unSyncedItems = allMediaItems.filter { item ->
            !simklSyncDbHelper.isItemSynced(item.id, item.isMovie, item.simklStatus)
        }

        if (unSyncedItems.isEmpty()) return@withContext

        val simklSync = SimklSync(simklAccessToken, context)

        // Batch array writes (up to 50 items per POST) without double-POSTing watched items
        unSyncedItems.chunked(MAX_BATCH_SIZE).forEach { batch ->
            val (watchedBatch, listOnlyBatch) = batch.partition {
                it.category == MovieDatabaseHelper.CATEGORY_WATCHED
            }

            val listSuccess = if (listOnlyBatch.isNotEmpty()) {
                val addToListJson = createAddToListPayload(listOnlyBatch)
                sendToSimkl(simklSync, "sync/add-to-list", addToListJson)
            } else {
                true
            }

            val historySuccess = if (watchedBatch.isNotEmpty()) {
                val historyJson = createHistoryPayload(watchedBatch)
                sendToSimkl(simklSync, "sync/history", historyJson)
            } else {
                true
            }

            if (listSuccess) {
                listOnlyBatch.forEach { item ->
                    simklSyncDbHelper.addSyncedItem(item.id, item.isMovie, item.simklStatus)
                }
            }
            if (historySuccess) {
                watchedBatch.forEach { item ->
                    simklSyncDbHelper.addSyncedItem(item.id, item.isMovie, item.simklStatus)
                }
            }
        }
    }

    private fun mapCategoryToSimklStatus(category: Int, isMovie: Boolean): String {
        // Simkl movies only accept plantowatch, completed, and dropped (movies skip watching and hold)
        return when (category) {
            MovieDatabaseHelper.CATEGORY_WATCHING -> if (isMovie) "plantowatch" else "watching"
            MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH -> "plantowatch"
            MovieDatabaseHelper.CATEGORY_WATCHED -> "completed"
            MovieDatabaseHelper.CATEGORY_ON_HOLD -> if (isMovie) "plantowatch" else "hold"
            MovieDatabaseHelper.CATEGORY_DROPPED -> "dropped"
            else -> "plantowatch"
        }
    }

    private fun getAllLocalMediaItems(): List<LocalSimklMediaItem> {
        val items = mutableListOf<LocalSimklMediaItem>()

        movieDbHelper.readableDatabase.use { db ->
            db.query(
                MovieDatabaseHelper.TABLE_MOVIES,
                arrayOf(
                    MovieDatabaseHelper.COLUMN_MOVIES_ID,
                    MovieDatabaseHelper.COLUMN_TITLE,
                    MovieDatabaseHelper.COLUMN_RELEASE_DATE,
                    MovieDatabaseHelper.COLUMN_MOVIE,
                    MovieDatabaseHelper.COLUMN_CATEGORIES,
                    MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE
                ),
                null,
                null,
                null,
                null,
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIES_ID))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE)) ?: ""
                    val releaseDate = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RELEASE_DATE)) ?: ""
                    val isMovie = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE)) == 1
                    val category = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_CATEGORIES))
                    val watchedDate = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE))

                    val year = releaseDate.split("-").firstOrNull()?.toIntOrNull() ?: 0
                    val watchAt = formatWatchedAt(watchedDate)
                    val simklStatus = mapCategoryToSimklStatus(category, isMovie)

                    items.add(
                        LocalSimklMediaItem(
                            id = id,
                            title = title,
                            year = year,
                            isMovie = isMovie,
                            category = category,
                            simklStatus = simklStatus,
                            watchedAt = watchAt
                        )
                    )
                }
            }
        }
        return items
    }

    private fun formatWatchedAt(watchedDate: String?): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val defaultDate = "01-01"
        val currentDate = String.format(
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        val date = when {
            watchedDate.isNullOrEmpty() -> currentDate
            watchedDate.endsWith("-00-00") -> "${watchedDate.take(4)}-$defaultDate"
            watchedDate.endsWith("-00") -> "${watchedDate.substring(0, 7)}-01"
            else -> watchedDate
        }
        val time = String.format(
            "%02d:%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND)
        )
        return "${date}T${time}.000Z"
    }

    private fun createAddToListPayload(batch: List<LocalSimklMediaItem>): JSONObject {
        val movies = JSONArray()
        val shows = JSONArray()

        batch.forEach { item ->
            val mediaJson = JSONObject().apply {
                put("to", item.simklStatus)
                if (item.title.isNotEmpty()) put("title", item.title)
                if (item.year > 0) put("year", item.year)
                put("ids", JSONObject().apply {
                    put("tmdb", item.id)
                })
            }
            if (item.isMovie) {
                movies.put(mediaJson)
            } else {
                shows.put(mediaJson)
            }
        }

        return JSONObject().apply {
            if (movies.length() > 0) put("movies", movies)
            if (shows.length() > 0) put("shows", shows)
        }
    }

    private fun createHistoryPayload(batch: List<LocalSimklMediaItem>): JSONObject {
        val movies = JSONArray()
        val shows = JSONArray()

        batch.forEach { item ->
            val mediaJson = JSONObject().apply {
                put("watched_at", item.watchedAt)
                if (item.title.isNotEmpty()) put("title", item.title)
                if (item.year > 0) put("year", item.year)
                put("ids", JSONObject().apply {
                    put("tmdb", item.id)
                })
            }
            if (item.isMovie) {
                movies.put(mediaJson)
            } else {
                shows.put(mediaJson)
            }
        }

        return JSONObject().apply {
            if (movies.length() > 0) put("movies", movies)
            if (shows.length() > 0) put("shows", shows)
        }
    }

    private suspend fun sendToSimkl(
        simklSync: SimklSync,
        endpoint: String,
        jsonObject: JSONObject
    ): Boolean {
        return try {
            Log.d("SimklAutoSyncManager", "Sending to Simkl ($endpoint): $jsonObject")
            simklSync.executePostSync(endpoint, jsonObject).use { response ->
                Log.d("SimklAutoSyncManager", "Response ($endpoint): HTTP ${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SimklAutoSyncManager", "Failed sending batch to Simkl ($endpoint): ${e.message}")
            false
        }
    }

    companion object {
        const val MAX_BATCH_SIZE = 50
    }
}
