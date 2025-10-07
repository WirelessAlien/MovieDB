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

package com.wirelessalien.android.moviedb.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.CsvParserUtil
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.RateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ImportService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var notificationManager: NotificationManager
    private lateinit var movieDbHelper: MovieDatabaseHelper
    private val rateLimiter = RateLimiter(10, 1, TimeUnit.SECONDS)
    private val httpClient = OkHttpClient()
    private var tmdbApiKey: String? = null

    companion object {
        const val ACTION_START_IMPORT = "com.wirelessalien.android.moviedb.service.action.START_IMPORT"
        const val EXTRA_FILE_URI = "com.wirelessalien.android.moviedb.service.extra.FILE_URI"
        const val EXTRA_HEADER_MAPPING = "com.wirelessalien.android.moviedb.service.extra.HEADER_MAPPING"
        const val EXTRA_DELIMITER = "com.wirelessalien.android.moviedb.service.extra.DELIMITER"
        const val EXTRA_DEFAULT_IS_MOVIE_TYPE = "com.wirelessalien.android.moviedb.service.extra.DEFAULT_IS_MOVIE_TYPE"
        private const val NOTIFICATION_CHANNEL_ID = "ImportServiceChannel"
        private const val NOTIFICATION_ID = 101
        private const val TAG = "ImportService"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        movieDbHelper = MovieDatabaseHelper(applicationContext)
        tmdbApiKey = ConfigHelper.getConfigValue(applicationContext, "api_key")

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_IMPORT) {
            val fileUri = intent.getParcelableExtra<Uri>(EXTRA_FILE_URI)
            @Suppress("UNCHECKED_CAST")
            val headerMapping = intent.getSerializableExtra(EXTRA_HEADER_MAPPING) as? HashMap<String, String>
            val delimiter = intent.getCharExtra(EXTRA_DELIMITER, ',') // Get delimiter, default to ','
            // Get the default movie type, allow it to be not present (e.g. -1 if not sent)
            val defaultIsMovieType = intent.getIntExtra(EXTRA_DEFAULT_IS_MOVIE_TYPE, -1)


            if (fileUri != null && headerMapping != null) {
                startForeground(NOTIFICATION_ID, createNotification(getString(R.string.starting_import), 0, 0))
                serviceScope.launch {
                    processImport(fileUri, headerMapping, delimiter, if (defaultIsMovieType == -1) null else defaultIsMovieType)
                }
            } else {
                Log.e(TAG, "File URI or header mapping missing. Stopping service.")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun fetchTmdbData(tmdbId: Int, isMovie: Boolean): JSONObject? {
        if (tmdbApiKey.isNullOrBlank()) {
            Log.w(TAG, "Cannot fetch TMDB data: API key missing.")
            return null
        }
        if (tmdbId == 0) {
            Log.w(TAG, "Cannot fetch TMDB data: TMDB ID is 0.")
            return null
        }

        val type = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$tmdbApiKey&append_to_response=genres"


        Log.d(TAG, "Fetching TMDB data from URL: $url")
        val request = Request.Builder().url(url).build()

        return try {
            rateLimiter.acquire()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "TMDB API call failed for $type ID $tmdbId: HTTP ${response.code} - ${response.message}")
                response.body?.close()
                return null
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e(TAG, "Empty response from TMDB API for $type ID $tmdbId.")
                return null
            }
            JSONObject(responseBody)
        } catch (e: IOException) {
            Log.e(TAG, "IOException during TMDB API call for $type ID $tmdbId: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Exception during TMDB fetch process for $type ID $tmdbId: ${e.message}", e)
            null
        }
    }


    private suspend fun processImport(fileUri: Uri, headerMapping: Map<String, String>, delimiter: Char, defaultIsMovieType: Int?) {
        var processedRows = 0
        var totalRowsEstimate = 0

        try {
            applicationContext.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                totalRowsEstimate = inputStream.bufferedReader().useLines { lines -> lines.count() } - 1 // -1 for header
                if(totalRowsEstimate < 0) totalRowsEstimate = 0
            }
        } catch (e: Exception) { Log.e(TAG, "Error estimating total rows", e) }

        val defaultValues = mapOf(
            MovieDatabaseHelper.COLUMN_IMAGE to "",
            MovieDatabaseHelper.COLUMN_ICON to "",
            MovieDatabaseHelper.COLUMN_SUMMARY to "",
            MovieDatabaseHelper.COLUMN_GENRES to "",
            MovieDatabaseHelper.COLUMN_GENRES_IDS to "[]",
            MovieDatabaseHelper.COLUMN_RATING to 0.0,
            MovieDatabaseHelper.COLUMN_CATEGORIES to MovieDatabaseHelper.CATEGORY_WATCHED
        )

        // Determine the fallback value for is_movie if not provided by CSV and no specific default is passed
        val ultimateFallbackIsMovie = defaultIsMovieType ?: 1 // Default to Movie (1) if no specific default passed

        val success = CsvParserUtil.processCsvWithMapping(
            applicationContext,
            fileUri,
            headerMapping,
            defaultValues,
            delimiter
        ) { mappedRow ->
            processedRows++
            Log.d(TAG, "Processing CSV row $processedRows: $mappedRow")
            updateNotification(getString(R.string.importing), processedRows, totalRowsEstimate)

            serviceScope.launch {
                try {
                    val contentValues = ContentValues()

                    defaultValues.forEach { (key, defaultValue) ->
                        contentValues.put(key, defaultValue.toString()) // Start with defaults
                    }

                    mappedRow.forEach { (dbColumn, value) ->
                        if (value != null) { // Only override default if CSV provides a value
                            when (dbColumn) {
                                MovieDatabaseHelper.COLUMN_MOVIES_ID -> contentValues.put(
                                    dbColumn,
                                    value.toLongOrNull()
                                )

                                MovieDatabaseHelper.COLUMN_RATING,
                                MovieDatabaseHelper.COLUMN_PERSONAL_RATING,
                                MovieDatabaseHelper.COLUMN_EPISODE_RATING -> contentValues.put(
                                    dbColumn,
                                    value.toDoubleOrNull()
                                )

                                MovieDatabaseHelper.COLUMN_CATEGORIES,
                                MovieDatabaseHelper.COLUMN_SEASON_NUMBER,
                                MovieDatabaseHelper.COLUMN_EPISODE_NUMBER,
                                MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED -> contentValues.put(
                                    dbColumn,
                                    value.toIntOrNull()
                                )

                                MovieDatabaseHelper.COLUMN_MOVIE -> contentValues.put(
                                    dbColumn,
                                    value.toIntOrNull()
                                )

                                else -> contentValues.put(dbColumn, value)
                            }
                        }
                    }

                    // Determine isMovie status
                    val currentIsMovie = mappedRow[MovieDatabaseHelper.COLUMN_MOVIE]?.toIntOrNull()
                        ?: (defaultIsMovieType ?: ultimateFallbackIsMovie)
                    contentValues.put(MovieDatabaseHelper.COLUMN_MOVIE, currentIsMovie)
                    val isMovieForTmdbFetch = currentIsMovie == 1

                    val tmdbIdFromCsv =
                        contentValues.getAsLong(MovieDatabaseHelper.COLUMN_MOVIES_ID)
                    val titleFromCsv = contentValues.getAsString(MovieDatabaseHelper.COLUMN_TITLE)

                    if (tmdbApiKey != null && tmdbIdFromCsv != null && tmdbIdFromCsv != 0L) {
                        val needsTitle = titleFromCsv.isNullOrBlank()
                        val needsSummary =
                            contentValues.getAsString(MovieDatabaseHelper.COLUMN_SUMMARY) == "N/A" || contentValues.getAsString(
                                MovieDatabaseHelper.COLUMN_SUMMARY
                            ).isNullOrBlank()
                        val needsRating =
                            contentValues.getAsDouble(MovieDatabaseHelper.COLUMN_RATING) == 0.0
                        val needsReleaseDate =
                            contentValues.getAsString(MovieDatabaseHelper.COLUMN_RELEASE_DATE)
                                .isNullOrBlank()
                        val needsPoster = contentValues.getAsString(MovieDatabaseHelper.COLUMN_ICON)
                            .isNullOrBlank()
                        val needsBackdrop =
                            contentValues.getAsString(MovieDatabaseHelper.COLUMN_IMAGE)
                                .isNullOrBlank()
                        val needsGenres =
                            contentValues.getAsString(MovieDatabaseHelper.COLUMN_GENRES_IDS) == "[]" || contentValues.getAsString(
                                MovieDatabaseHelper.COLUMN_GENRES_IDS
                            ).isNullOrBlank()

                        if (needsTitle || needsSummary || needsRating || needsReleaseDate || needsPoster || needsBackdrop || needsGenres) {
                            val tmdbJson = fetchTmdbData(tmdbIdFromCsv.toInt(), isMovieForTmdbFetch)

                            if (tmdbJson != null) {
                                if (needsTitle) {
                                    val fetchedTitle =
                                        if (isMovieForTmdbFetch) tmdbJson.optString("title") else tmdbJson.optString(
                                            "name"
                                        )
                                    if (fetchedTitle.isNotBlank()) contentValues.put(
                                        MovieDatabaseHelper.COLUMN_TITLE,
                                        fetchedTitle
                                    )
                                }
                                if (needsSummary && tmdbJson.optString("overview").isNotBlank()) {
                                    contentValues.put(
                                        MovieDatabaseHelper.COLUMN_SUMMARY,
                                        tmdbJson.optString("overview")
                                    )
                                }
                                if (needsRating && tmdbJson.optDouble("vote_average", 0.0) > 0.0) {
                                    contentValues.put(
                                        MovieDatabaseHelper.COLUMN_RATING,
                                        tmdbJson.optDouble("vote_average")
                                    )
                                }
                                if (needsReleaseDate) {
                                    val date =
                                        if (isMovieForTmdbFetch) tmdbJson.optString("release_date") else tmdbJson.optString(
                                            "first_air_date"
                                        )
                                    if (date.isNotBlank()) contentValues.put(
                                        MovieDatabaseHelper.COLUMN_RELEASE_DATE,
                                        date
                                    )
                                }
                                if (needsPoster && tmdbJson.optString("poster_path").isNotBlank()) {
                                    contentValues.put(
                                        MovieDatabaseHelper.COLUMN_ICON,
                                        tmdbJson.optString("poster_path")
                                    )
                                }
                                if (needsBackdrop && tmdbJson.optString("backdrop_path")
                                        .isNotBlank()
                                ) {
                                    contentValues.put(
                                        MovieDatabaseHelper.COLUMN_IMAGE,
                                        tmdbJson.optString("backdrop_path")
                                    )
                                }
                                if (needsGenres) {
                                    tmdbJson.optJSONArray("genres")?.let { genresArray ->
                                        if (genresArray.length() > 0) {
                                            val genreIds =
                                                (0 until genresArray.length()).mapNotNull {
                                                    genresArray.optJSONObject(it)?.optInt("id")
                                                }
                                            val genreNames =
                                                (0 until genresArray.length()).mapNotNull {
                                                    genresArray.optJSONObject(it)?.optString("name")
                                                }
                                            if (genreIds.isNotEmpty()) {
                                                contentValues.put(
                                                    MovieDatabaseHelper.COLUMN_GENRES_IDS,
                                                    genreIds.joinToString(",", "[", "]")
                                                )
                                                contentValues.put(
                                                    MovieDatabaseHelper.COLUMN_GENRES,
                                                    genreNames.joinToString(", ")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val finalTmdbId = contentValues.getAsLong(MovieDatabaseHelper.COLUMN_MOVIES_ID)
                    val finalTitle = contentValues.getAsString(MovieDatabaseHelper.COLUMN_TITLE)

                    if ((finalTmdbId == null || finalTmdbId == 0L) || finalTitle.isNullOrBlank() || finalTitle == "N/A") {
                        Log.w(
                            TAG,
                            "Skipping insert for row due to missing TMDB ID or valid Title: $contentValues"
                        )
                        return@launch
                    }

                    val movieCV = ContentValues()
                    val episodeCV = ContentValues()
                    var hasEpisodeData = false

                    contentValues.keySet().forEach { key ->
                        val value = contentValues.get(key)
                        when (key) {
                            MovieDatabaseHelper.COLUMN_SEASON_NUMBER,
                            MovieDatabaseHelper.COLUMN_EPISODE_NUMBER,
                            MovieDatabaseHelper.COLUMN_EPISODE_RATING,
                            MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE,
                            MovieDatabaseHelper.COLUMN_EPISODE_REVIEW -> {
                                if (value != null && value.toString().isNotBlank()) {
                                    episodeCV.put(MovieDatabaseHelper.COLUMN_MOVIES_ID, finalTmdbId)
                                    episodeCV.put(key, value.toString())
                                    hasEpisodeData = true
                                }
                            }

                            else -> {
                                if (value != null) movieCV.put(key, value.toString())
                            }
                        }
                    }

                    val db = movieDbHelper.writableDatabase
                    db.beginTransaction()
                    try {
                        val existingMovieRowId = movieDbHelper.getMovieRowId(
                            finalTmdbId,
                            movieCV.getAsInteger(MovieDatabaseHelper.COLUMN_MOVIE) == 1
                        )
                        if (existingMovieRowId != null) {
                            db.update(
                                MovieDatabaseHelper.TABLE_MOVIES,
                                movieCV,
                                "${MovieDatabaseHelper.COLUMN_ID} = ?",
                                arrayOf(existingMovieRowId.toString())
                            )
                        } else {
                            db.insert(MovieDatabaseHelper.TABLE_MOVIES, null, movieCV)
                        }

                        if (hasEpisodeData &&
                            (episodeCV.getAsInteger(MovieDatabaseHelper.COLUMN_SEASON_NUMBER)
                                ?: 0) > 0 &&
                            (episodeCV.getAsInteger(MovieDatabaseHelper.COLUMN_EPISODE_NUMBER)
                                ?: 0) > 0
                        ) {
                            val existingEpisodeRowId = movieDbHelper.getEpisodeRowId(
                                episodeCV.getAsLong(MovieDatabaseHelper.COLUMN_MOVIES_ID),
                                episodeCV.getAsInteger(MovieDatabaseHelper.COLUMN_SEASON_NUMBER),
                                episodeCV.getAsInteger(MovieDatabaseHelper.COLUMN_EPISODE_NUMBER)
                            )
                            if (existingEpisodeRowId != null) {
                                db.update(
                                    MovieDatabaseHelper.TABLE_EPISODES,
                                    episodeCV,
                                    "${MovieDatabaseHelper.COLUMN_ID} = ?",
                                    arrayOf(existingEpisodeRowId.toString())
                                )
                            } else {
                                db.insert(MovieDatabaseHelper.TABLE_EPISODES, null, episodeCV)
                            }
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing mapped row $processedRows: $mappedRow", e)
                }
            }
        }

        if (success) {
            updateNotification(getString(R.string.import_complete), processedRows, if(totalRowsEstimate < processedRows) processedRows else totalRowsEstimate , true)
            Log.i(TAG, "Import completed. Processed $processedRows rows.")
        } else {
            updateNotification(getString(R.string.import_failed_or_finished_with_errors), processedRows, totalRowsEstimate, isFinished = true, isError = true)
            Log.e(TAG, "Import failed or finished with errors.")
        }
        // Delay stopping foreground and service to allow user to see final notification
        serviceScope.launch {
            delay(5000)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Import Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.csv_import_service_notifications)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String, progress: Int, maxProgress: Int, isFinished: Boolean = false, isError: Boolean = false): Notification {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val title = when {
            isError -> getString(R.string.import_error)
            isFinished -> getString(R.string.import_complete)
            else -> getString(R.string.importing_csv_data)
        }

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(!isFinished)
            .setOnlyAlertOnce(true)

        if (!isFinished) {
            if (maxProgress > 0) {
                builder.setProgress(maxProgress, progress, false)
            } else {
                builder.setProgress(0, 0, true)
            }
        }

        return builder.build()
    }

    private fun updateNotification(contentText: String, progress: Int, maxProgress: Int, isFinished: Boolean = false, isError: Boolean = false) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText, progress, maxProgress, isFinished, isError))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        if (::movieDbHelper.isInitialized) {
            movieDbHelper.close()
        }
        Log.d(TAG, "ImportService destroyed.")
    }
}


