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

package com.wirelessalien.android.moviedb.activity

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ActivityExportBinding
import com.wirelessalien.android.moviedb.helper.CrashHelper
import com.wirelessalien.android.moviedb.helper.DirectoryHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class ExportTktDbActivity : AppCompatActivity() {

    private lateinit var context: Context
    private lateinit var binding: ActivityExportBinding
    private var exportDirectoryUri: Uri? = null
    private lateinit var preferences: SharedPreferences

    private val openExportDirectoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            exportDirectoryUri = it
            preferences.edit().putString("db_export_directory", it.toString()).commit()
            binding.selectedDirectoryText.text = getString(R.string.directory_path, it.path)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CrashHelper.setDefaultUncaughtExceptionHandler(applicationContext)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        context = this

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.title = getString(R.string.action_export)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)

        binding.autoBackupSwitch.visibility = View.GONE
        binding.backupBtn.visibility = View.GONE
        binding.backupFrequencyET.visibility = View.GONE
        binding.backupFrequency.visibility = View.GONE

        binding.infoText.text = getString(R.string.tkt_export_info)

        val exportDirectory = DirectoryHelper.getExportDirectory(context)
        val exportDirectoryUri = preferences.getString("db_export_directory", null)

        if (exportDirectory != null && exportDirectoryUri != null) {
            binding.selectedDirectoryText.text = getString(R.string.directory_path, Uri.parse(exportDirectoryUri).path)
        } else if (exportDirectory != null) {
            binding.selectedDirectoryText.text = getString(R.string.directory_path, exportDirectory.absolutePath)
        } else if (exportDirectoryUri != null) {
            binding.selectedDirectoryText.text = getString(R.string.directory_path, Uri.parse(exportDirectoryUri).path)
        }

        binding.exportButton.setOnClickListener {
            val exportDirUri = preferences.getString("db_export_directory", null)
            if (exportDirUri == null) {
                Toast.makeText(this, getString(R.string.export_directory_not_selected), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.exportButton.isEnabled = false
            binding.exportProgress.visibility = View.VISIBLE

            exportAllDataInBackground(exportDirUri)
        }

        binding.editIcon.setOnClickListener {
            openExportDirectoryLauncher.launch(null)
        }
    }

    private fun exportAllDataInBackground(exportDirUri: String) {
        val dbHelper = TraktDatabaseHelper(applicationContext)
        val dataTypes = listOf("ratings", "favorites", "watched", "collection", "watchlist", "listsitem")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val directoryUri = Uri.parse(exportDirUri)
                var successCount = 0

                dataTypes.forEachIndexed { index, dataType ->
                    try {
                        val csvContent = generateConsistentCsv(dbHelper, dataType)

                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                            Date()
                        )
                        val fileName = "trakt_export_${dataType}_$timestamp.csv"

                        val success = saveCsvToDirectory(directoryUri, fileName, csvContent)

                        if (success) {
                            successCount++
                            withContext(Dispatchers.Main) {
                                binding.exportProgress.progress = ((index + 1) * 100 / dataTypes.size)
                            }

                        }
                    } catch (e: Exception) {
                        Log.e("ExportActivity", "Error exporting $dataType", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    // Show completion message
                    binding.exportProgress.visibility = View.GONE
                    binding.exportButton.isEnabled = true

                    val message = if (successCount == dataTypes.size) {
                        getString(R.string.export_success)
                    } else {
                        getString(R.string.export_partial_success, successCount, dataTypes.size)
                    }

                    Toast.makeText(this@ExportTktDbActivity, message, Toast.LENGTH_LONG).show()

                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.exportProgress.visibility = View.GONE
                    binding.exportButton.isEnabled = true
                    Toast.makeText(this@ExportTktDbActivity, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                    Log.e("ExportActivity", "Export failed", e)
                }
            }
        }
    }

    private fun saveCsvToDirectory(directoryUri: Uri, fileName: String, csvContent: String): Boolean {
        return try {
            val directory = DocumentFile.fromTreeUri(this, directoryUri)
            val file = directory?.createFile("text/csv", fileName)

            file?.uri?.let { fileUri ->
                contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                    outputStream.write(csvContent.toByteArray())
                    outputStream.flush()
                    true
                } ?: false
            } ?: false
        } catch (e: Exception) {
            Log.e("ExportActivity", "Error saving file $fileName", e)
            false
        }
    }


    private fun getStringOrEmpty(cursor: Cursor, columnName: String): String {
        val index = cursor.getColumnIndexOrThrow(columnName)
        return if (index >= 0 && !cursor.isNull(index)) {
            cursor.getString(index).replace(",", " ")
        } else {
            ""
        }
    }

    private fun getIntOrEmpty(cursor: Cursor, columnName: String): String {
        val index = cursor.getColumnIndexOrThrow(columnName)
        return if (index >= 0 && !cursor.isNull(index)) {
            cursor.getInt(index).toString()
        } else {
            ""
        }
    }


    private fun generateConsistentCsv(dbHelper: TraktDatabaseHelper, dataType: String): String {
        val tableName = when (dataType) {
            "ratings" -> TraktDatabaseHelper.TABLE_RATING
            "favorites" -> TraktDatabaseHelper.TABLE_FAVORITE
            "watched" -> TraktDatabaseHelper.TABLE_WATCHED
            "collection" -> TraktDatabaseHelper.TABLE_COLLECTION
            "watchlist" -> TraktDatabaseHelper.TABLE_WATCHLIST
            "listsitem" -> TraktDatabaseHelper.TABLE_LIST_ITEM
            else -> throw IllegalArgumentException("Invalid data type: $dataType")
        }

        val db = dbHelper.readableDatabase
        val cursor = db.query(tableName, null, null, null, null, null, null)

        val csvLines = mutableListOf<String>()
        csvLines.add("rated_at,type,title,year,trakt_rating,trakt_id,imdb_id,tmdb_id,tvdb_id,url,released,season_number,episode_number,episode_title,episode_released,episode_trakt_rating,episode_trakt_id,episode_imdb_id,episode_tmdb_id,episode_tvdb_id,genres,rating")

        while (cursor.moveToNext()) {
            val type = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_TYPE)
            val timestamp = when (dataType) {
                "ratings" -> getStringOrEmpty(cursor, TraktDatabaseHelper.COL_RATED_AT)
                "favorites" -> getStringOrEmpty(cursor, TraktDatabaseHelper.COL_LISTED_AT)
                "watched" -> getStringOrEmpty(cursor, TraktDatabaseHelper.COL_LAST_WATCHED_AT)
                "collection" -> getStringOrEmpty(cursor, TraktDatabaseHelper.COL_COLLECTED_AT)
                "watchlist" -> getStringOrEmpty(cursor, TraktDatabaseHelper.COL_LISTED_AT)
                "listsitem" -> getStringOrEmpty(cursor, TraktDatabaseHelper.COL_LISTED_AT)
                else -> ""
            }

            if (dataType == "ratings") {
                val userRating = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_RATING)

                when (type) {
                    "movie" -> {
                        val title = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_TITLE)
                        val year = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_YEAR)
                        val traktId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TRAKT_ID)
                        val imdbId = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_IMDB)
                        val tmdbId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TMDB)
                        val tvdbId = "" // Movies don't have TVDB ID
                        val slug = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_SLUG)
                        val url = if (slug.isNotEmpty()) "https://trakt.tv/movies/$slug" else ""

                        val line = listOf(
                            timestamp, type, title, year, "", traktId, imdbId, tmdbId, tvdbId, url,
                            "", "", "", "", "", "", "", "", "", "", "", userRating
                        ).joinToString(",")
                        csvLines.add(line)
                    }
                    "show" -> {
                        val title = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_TITLE)
                        val year = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_YEAR)
                        val traktId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TRAKT_ID)
                        val imdbId = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_IMDB)
                        val tmdbId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TMDB)
                        val tvdbId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TVDB)
                        val slug = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_SLUG)
                        val url = if (slug.isNotEmpty()) "https://trakt.tv/shows/$slug" else ""

                        val line = listOf(
                            timestamp, type, title, year, "", traktId, imdbId, tmdbId, tvdbId, url,
                            "", "", "", "", "", "", "", "", "", "", "", userRating
                        ).joinToString(",")
                        csvLines.add(line)
                    }
                    "episode" -> {
                        // For episodes, main fields use SHOW data, episode fields use EPISODE data
                        val showTitle = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_SHOW_TITLE)
                        val showYear = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_SHOW_YEAR)
                        val showTraktId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_SHOW_TRAKT_ID)
                        val showImdbId = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_SHOW_IMDB)
                        val showTmdbId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_SHOW_TMDB)
                        val showTvdbId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_SHOW_TVDB)
                        val showSlug = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_SHOW_SLUG)
                        val showUrl = if (showSlug.isNotEmpty()) "https://trakt.tv/shows/$showSlug" else ""

                        val seasonNumber = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_SEASON)
                        val episodeNumber = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_NUMBER)
                        val episodeTitle = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_TITLE) // Episode title is in COL_TITLE for episode type
                        val episodeTraktId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TRAKT_ID) // Episode Trakt ID is in COL_TRAKT_ID
                        val episodeImdbId = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_IMDB)   // Episode IMDb ID is in COL_IMDB
                        val episodeTmdbId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TMDB)   // Episode TMDB ID is in COL_TMDB
                        val episodeTvdbId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TVDB)   // Episode TVDB ID is in COL_TVDB

                        // Construct the line with show data in main slots and episode data in episode slots
                        val line = listOf(
                            timestamp, type, showTitle, showYear, "", showTraktId, showImdbId, showTmdbId, showTvdbId, showUrl,
                            "", // released (empty)
                            seasonNumber,
                            episodeNumber,
                            episodeTitle,
                            "", // episode_released (empty)
                            "", // episode_trakt_rating (empty)
                            episodeTraktId,
                            episodeImdbId,
                            episodeTmdbId,
                            episodeTvdbId,
                            "", // genres (empty)
                            userRating // The rating applies to this specific episode
                        ).joinToString(",")
                        csvLines.add(line)
                    }
                }
            } else {
                // For all other data types (favorites, watched, collection, watchlist, listsitem)
                if (type == "movie" || type == "show") {
                    val title: String
                    val year: String
                    val traktId: String
                    val imdbId: String
                    val tmdbId: String
                    val tvdbId: String
                    val slug: String
                    val url: String

                    if (type == "movie") {
                        title = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_TITLE)
                        year = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_YEAR)
                        traktId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TRAKT_ID)
                        imdbId = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_IMDB)
                        tmdbId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TMDB)
                        tvdbId = "" // Movies don't have TVDB ID
                        slug = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_SLUG)
                        url = if (slug.isNotEmpty()) "https://trakt.tv/movies/$slug" else ""
                    } else { // type == "show"
                        // Check if show details are stored directly or in COL_SHOW_* fields
                        title = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_TITLE)
                        year = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_YEAR)
                        traktId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TRAKT_ID)
                        imdbId = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_IMDB)
                        tmdbId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TMDB)
                        tvdbId = getIntOrEmpty(cursor, TraktDatabaseHelper.COL_TVDB)
                        slug = getStringOrEmpty(cursor, TraktDatabaseHelper.COL_SLUG)
                        url = if (slug.isNotEmpty()) "https://trakt.tv/shows/$slug" else ""
                    }

                    val line = listOf(
                        timestamp, type, title, year, "", traktId, imdbId, tmdbId, tvdbId, url,
                        "", "", "", "", "", "", "", "", "", "", "", ""
                    ).joinToString(",")
                    csvLines.add(line)
                }
            }
        }

        cursor.close()
        db.close()

        return csvLines.joinToString("\n")
    }
}
