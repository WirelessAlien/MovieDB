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
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateTvShowStatusWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                updateTvShowStatus()
                Result.success()
            } catch (e: Exception) {
                Log.e("UpdateTvShowStatusWorker", "Error updating TV show status", e)
                Result.failure()
            }
        }
    }

    private fun updateTvShowStatus() {
        val movieDbHelper = MovieDatabaseHelper(applicationContext)
        val tmdbDbHelper = TmdbDetailsDatabaseHelper(applicationContext)

        movieDbHelper.readableDatabase.use { movieDb ->
            val cursor = movieDb.query(
                MovieDatabaseHelper.TABLE_MOVIES,
                arrayOf(MovieDatabaseHelper.COLUMN_MOVIES_ID),
                "${MovieDatabaseHelper.COLUMN_MOVIE} = 0", // Select only TV shows
                null, null, null, null
            )

            while (cursor.moveToNext()) {
                val tmdbId = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIES_ID))

                val totalEpisodes = tmdbDbHelper.getTotalEpisodesForShow(tmdbId)
                val watchedEpisodes = movieDbHelper.getSeenEpisodesCount(tmdbId)

                if (watchedEpisodes in 1 until totalEpisodes) {
                    val values = ContentValues().apply {
                        put(MovieDatabaseHelper.COLUMN_CATEGORIES, MovieDatabaseHelper.CATEGORY_WATCHING)
                    }
                    val selection = "${MovieDatabaseHelper.COLUMN_MOVIES_ID} = ? AND ${MovieDatabaseHelper.COLUMN_MOVIE} = 0"
                    val selectionArgs = arrayOf(tmdbId.toString())
                    movieDb.update(MovieDatabaseHelper.TABLE_MOVIES, values, selection, selectionArgs)
                }
            }
            cursor.close()
        }
    }
}
