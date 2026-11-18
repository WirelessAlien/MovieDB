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

import android.content.Context
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.util.Log
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.data.TraktMediaObject
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktAutoSyncDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TraktAutoSyncManager(val context: Context) {
    private val movieDbHelper = MovieDatabaseHelper(context)
    private val traktSyncDbHelper = TraktAutoSyncDatabaseHelper(context)
    private val tktaccessToken = PreferenceManager.getDefaultSharedPreferences(context).getString("trakt_access_token", null)

    suspend fun syncMediaToTrakt() = withContext(Dispatchers.IO) {
        if (tktaccessToken.isNullOrEmpty()) {
            Log.d("TraktAutoSyncManager", "No Trakt access token, skipping sync")
            return@withContext
        }

        val mediaObjects = getAllMediaObjects()

        // Filter only unsynchronized items
        val unSyncedMedia = mediaObjects.filter { mediaObject ->
            !traktSyncDbHelper.isItemSynced(mediaObject.id, mediaObject.isMovie, "history")
        }

        if (unSyncedMedia.isNotEmpty()) {
            val traktObject = createTraktObject(unSyncedMedia)
            val syncSuccess = sendToTrakt(traktObject)

            if (syncSuccess) {
                // Mark all items as synced only if request was successful
                unSyncedMedia.forEach { mediaObject ->
                    traktSyncDbHelper.addSyncedItem(mediaObject.id, mediaObject.isMovie, "history")
                }
                Log.d("TraktAutoSyncManager", "Successfully synced ${unSyncedMedia.size} items to Trakt")
            } else {
                Log.w("TraktAutoSyncManager", "Failed to sync items to Trakt")
            }
        }
    }

    private fun getAllMediaObjects(): List<TraktMediaObject> {
        val mediaObjects = mutableListOf<TraktMediaObject>()

        movieDbHelper.readableDatabase.use { db ->
            db.query(
                MovieDatabaseHelper.TABLE_MOVIES,
                arrayOf(
                    MovieDatabaseHelper.COLUMN_MOVIES_ID,
                    MovieDatabaseHelper.COLUMN_TITLE,
                    MovieDatabaseHelper.COLUMN_RELEASE_DATE,
                    MovieDatabaseHelper.COLUMN_MOVIE,
                    MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE
                ),
                "${MovieDatabaseHelper.COLUMN_CATEGORIES} = ?",
                arrayOf(MovieDatabaseHelper.CATEGORY_WATCHED.toString()),
                null,
                null,
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val releaseDate = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RELEASE_DATE))
                    val watchedDate = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE))
                    val year = if (!releaseDate.isNullOrEmpty() && releaseDate.contains("-")) {
                        releaseDate.split("-")[0]
                    } else {
                        ""
                    }
                    val watchAt = run {
                        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        val defaultDate = "01-01"

                        // Format current date in yyyy-MM-dd format
                        val currentDate = String.format(
                            "%04d-%02d-%02d",
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH) + 1,
                            calendar.get(Calendar.DAY_OF_MONTH)
                        )

                        // Use current date if watchedDate is null, otherwise process watchedDate
                        val date = when {
                            watchedDate == null -> currentDate
                            watchedDate.endsWith("-00-00") -> "${watchedDate.take(4)}-$defaultDate"
                            watchedDate.endsWith("-00") -> "${watchedDate.substring(0, 7)}-01"
                            else -> watchedDate
                        }

                        // Get current time
                        val time = String.format(
                            "%02d:%02d:%02d",
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            calendar.get(Calendar.SECOND)
                        )

                        "${date}T${time}.000Z"
                    }

                    mediaObjects.add(
                        TraktMediaObject(
                            id = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIES_ID)),
                            title = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE)),
                            releaseDate = year,
                            isMovie = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE)) == 1,
                            watchedAt = watchAt
                        )
                    )
                }
            }
        }
        return mediaObjects
    }

    private fun createTraktObject(mediaObjects: List<TraktMediaObject>): JSONObject {
        val movies = JSONArray()
        val shows = JSONArray()

        mediaObjects.forEach { media ->
            val mediaJson = JSONObject().apply {
                put("watched_at", media.watchedAt)
                put("title", media.title)
                val yearInt = media.releaseDate.toIntOrNull()
                if (yearInt != null && yearInt > 0) {
                    put("year", yearInt)
                }
                put("ids", JSONObject().apply {
                    put("tmdb", media.id)
                })
            }

            if (media.isMovie) {
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

    private suspend fun sendToTrakt(jsonObject: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val token = tktaccessToken ?: return@withContext false
        try {
            val traktSync = TraktSync(token, context)
            val endpoint = "sync/history"
            Log.d("TraktAutoSyncManager", "Sending to Trakt: $jsonObject")
            traktSync.postSuspend(endpoint, jsonObject)
        } catch (e: Exception) {
            Log.e("TraktAutoSyncManager", "sendToTrakt error: ${e.message}")
            false
        }
    }
}