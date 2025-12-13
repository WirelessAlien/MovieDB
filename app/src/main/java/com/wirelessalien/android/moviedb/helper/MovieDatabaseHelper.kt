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
package com.wirelessalien.android.moviedb.helper

import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.ExportActivity
import com.wirelessalien.android.moviedb.data.EpisodeDbDetails
import com.wirelessalien.android.moviedb.helper.DirectoryHelper.getExportDirectory
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * This class provides some (basic) database functionality.
 */
class MovieDatabaseHelper (context: Context?) : SQLiteOpenHelper(context, databaseFileName, null, DATABASE_VERSION), AutoCloseable {

//    private fun getEpisodesForMovie(movieId: Int, database: SQLiteDatabase): JSONArray {
//        val selectQuery =
//            "SELECT * FROM $TABLE_EPISODES WHERE $COLUMN_MOVIES_ID = $movieId"
//        val cursor = database.rawQuery(selectQuery, null)
//
//        // Convert the database to JSON
//        val episodesSet = JSONArray()
//        cursor.moveToFirst()
//        while (!cursor.isAfterLast) {
//            val totalColumn = cursor.columnCount
//            val rowObject = JSONObject()
//            for (i in 0 until totalColumn) {
//                if (cursor.getColumnName(i) != null) {
//                    try {
//                        if (cursor.getString(i) != null) {
//                            rowObject.put(cursor.getColumnName(i), cursor.getString(i))
//                        } else {
//                            rowObject.put(cursor.getColumnName(i), "")
//                        }
//                    } catch (e: JSONException) {
//                        e.printStackTrace()
//                    }
//                }
//            }
//            episodesSet.put(rowObject)
//            cursor.moveToNext()
//        }
//        cursor.close()
//        return episodesSet
//    }

    suspend fun getJSONExportString(database: SQLiteDatabase): String = withContext(Dispatchers.IO) {
        val moviesArray = JSONArray()

        // Get all movies
        val moviesCsr = database.query(
            TABLE_MOVIES,
            null,
            null,
            null,
            null,
            null,
            null
        )

        moviesCsr.use { moviesCursor ->
            while (moviesCursor.moveToNext()) {
                val movieObject = JSONObject()

                // Add movie data
                for (i in 0 until moviesCursor.columnCount) {
                    val columnName = moviesCursor.getColumnName(i)
                    when (moviesCursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> movieObject.put(columnName, JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> movieObject.put(columnName, moviesCursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> movieObject.put(columnName, moviesCursor.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> movieObject.put(columnName, moviesCursor.getString(i))
                        Cursor.FIELD_TYPE_BLOB -> movieObject.put(columnName, Base64.encodeToString(moviesCursor.getBlob(i), Base64.DEFAULT))
                    }
                }

                // Get episodes for this movie
                val movieId = moviesCursor.getLong(moviesCursor.getColumnIndexOrThrow(COLUMN_MOVIES_ID))
                movieObject.put("episodes", getEpisodesForMovie(movieId, database))

                moviesArray.put(movieObject)
            }
        }

        moviesArray.toString(4)
    }

    private fun getEpisodesForMovie(movieId: Long, database: SQLiteDatabase): JSONArray {
        val episodesArray = JSONArray()

        val episodesCsr = database.query(
            TABLE_EPISODES,
            null,
            "$COLUMN_MOVIES_ID = ?",
            arrayOf(movieId.toString()),
            null,
            null,
            "$COLUMN_SEASON_NUMBER ASC, $COLUMN_EPISODE_NUMBER ASC"
        )

        episodesCsr.use { episodesCursor ->
            while (episodesCursor.moveToNext()) {
                val episodeObject = JSONObject()

                for (i in 0 until episodesCursor.columnCount) {
                    val columnName = episodesCursor.getColumnName(i)
                    when (episodesCursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> episodeObject.put(columnName, JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> episodeObject.put(columnName, episodesCursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> episodeObject.put(columnName, episodesCursor.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> episodeObject.put(columnName, episodesCursor.getString(i))
                        Cursor.FIELD_TYPE_BLOB -> episodeObject.put(columnName, Base64.encodeToString(episodesCursor.getBlob(i), Base64.DEFAULT))
                    }
                }

                episodesArray.put(episodeObject)
            }
        }

        return episodesArray
    }

    suspend fun getCSVExportString(database: SQLiteDatabase, moviesOnly: Boolean): String = withContext(Dispatchers.IO) {
        val csvBuilder = StringBuilder()

        val query = if (moviesOnly) {
            """
            SELECT 
                m.$COLUMN_MOVIES_ID as tmdb_id,
                m.$COLUMN_TITLE as title,
                m.$COLUMN_SUMMARY as summary,
                m.$COLUMN_RATING as tmdb_rating,
                m.$COLUMN_IMAGE as image,
                m.$COLUMN_ICON as icon,
                m.$COLUMN_GENRES as genres,
                m.$COLUMN_GENRES_IDS as genres_ids,
                m.$COLUMN_PERSONAL_RATING as rating,
                m.$COLUMN_RELEASE_DATE as release_date,
                m.$COLUMN_PERSONAL_START_DATE as start_date,
                m.$COLUMN_PERSONAL_FINISH_DATE as finish_date,
                m.$COLUMN_MOVIE_REVIEW as movie_review,
                m.$COLUMN_MOVIE as is_movie,
                m.$COLUMN_CATEGORIES as categories
            FROM $TABLE_MOVIES m
            ORDER BY m.$COLUMN_TITLE
            """
        } else {
            """
            SELECT 
                m.$COLUMN_MOVIES_ID as tmdb_id,
                m.$COLUMN_TITLE as title,
                m.$COLUMN_SUMMARY as summary,
                m.$COLUMN_RATING as tmdb_rating,
                m.$COLUMN_IMAGE as image,
                m.$COLUMN_ICON as icon,
                m.$COLUMN_GENRES as genres,
                m.$COLUMN_GENRES_IDS as genres_ids,
                m.$COLUMN_PERSONAL_RATING as rating,
                m.$COLUMN_RELEASE_DATE as release_date,
                m.$COLUMN_PERSONAL_START_DATE as start_date,
                m.$COLUMN_PERSONAL_FINISH_DATE as finish_date,
                m.$COLUMN_MOVIE_REVIEW as movie_review,
                m.$COLUMN_MOVIE as is_movie,
                m.$COLUMN_CATEGORIES as categories,
                e.$COLUMN_SEASON_NUMBER as season,
                e.$COLUMN_EPISODE_NUMBER as episode,
                e.$COLUMN_EPISODE_RATING as episode_rating,
                e.$COLUMN_EPISODE_WATCH_DATE as episode_watch_date,
                e.$COLUMN_EPISODE_REVIEW as episode_review
            FROM $TABLE_MOVIES m
            LEFT JOIN $TABLE_EPISODES e ON m.$COLUMN_MOVIES_ID = e.$COLUMN_MOVIES_ID
            ORDER BY m.$COLUMN_TITLE, e.$COLUMN_SEASON_NUMBER, e.$COLUMN_EPISODE_NUMBER
            """
        }

        val csr = database.rawQuery(query, null)

        csr.use { cursor ->
            val headers = cursor.columnNames
            csvBuilder.append(headers.joinToString(",")).append("\n")

            // Write data rows
            while (cursor.moveToNext()) {
                val row = (0 until cursor.columnCount).joinToString(",") { colIndex ->
                    val value = when (cursor.getType(colIndex)) {
                        Cursor.FIELD_TYPE_NULL -> ""
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(colIndex).toString()
                        Cursor.FIELD_TYPE_FLOAT -> "%.1f".format(cursor.getDouble(colIndex))
                        Cursor.FIELD_TYPE_STRING -> {
                            val str = cursor.getString(colIndex)
                            if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                                // Escape quotes and wrap in quotes
                                "\"${str.replace("\"", "\"\"")}\""
                            } else {
                                str
                            }
                        }
                        else -> ""
                    }
                    value
                }
                csvBuilder.append(row).append("\n")
            }
        }

        csvBuilder.toString()
    }

    /**
     * Writes the database in the chosen format to the downloads directory.
     *
     * @param context the context needed for the dialogs and toasts.
     */
    fun exportDatabase(context: Context, exportDirectoryUri: String?) {
        val builder = MaterialAlertDialogBuilder(context)
        val inflater = LayoutInflater.from(context)
        val customView = inflater.inflate(R.layout.export_dialog, null)
        val jsonChip = customView.findViewById<Chip>(R.id.chip_json)
        val dbChip = customView.findViewById<Chip>(R.id.chip_db)
        val csvChip = customView.findViewById<Chip>(R.id.chip_csv)
        val csvExportOptions = customView.findViewById<RadioGroup>(R.id.csv_export_options)

        csvChip.setOnCheckedChangeListener { _, isChecked ->
            csvExportOptions.isVisible = isChecked
            if (isChecked) {
                csvExportOptions.check(R.id.radio_movies_only)
            }
        }

        builder.setView(customView)
        builder.setTitle(context.resources.getString(R.string.choose_export_file))
            .setPositiveButton(context.getString(R.string.export)) { _, _ ->
                val exportDirectory = getExportDirectory(context)
                val isJson = jsonChip.isChecked
                val isDb = dbChip.isChecked
                val isCsv = csvChip.isChecked
                val isMoviesOnly = csvExportOptions.checkedRadioButtonId == R.id.radio_movies_only

                if (isCsv && csvExportOptions.checkedRadioButtonId == -1) {
                    Toast.makeText(context, context.getString(R.string.no_csv_export_option_selected), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (exportDirectoryUri != null) {
                    exportToUri(context, exportDirectoryUri, isJson, isDb, isCsv, isMoviesOnly)
                    Log.d("Export", "Exporting to URI")
                } else if (exportDirectory != null) {
                    exportToDirectory(context, exportDirectory, isJson, isDb, isCsv, isMoviesOnly)
                    Log.d("Export", "Exporting to DIR")
                } else {
                    promptUserToSaveFile(context, isJson, isDb, isCsv, isMoviesOnly)
                }
            }
            .setNegativeButton(context.getString(R.string.cancel)) { dialogInterface, _ -> dialogInterface.cancel() }
        builder.show()
    }

    private fun exportToUri(context: Context, exportDirectoryUri: String, isJson: Boolean, isDb: Boolean, isCsv: Boolean, isMoviesOnly: Boolean) {
        val documentFile = DocumentFile.fromTreeUri(context, Uri.parse(exportDirectoryUri))
        val simpleDateFormat = SimpleDateFormat("dd-MM-yy-kk-mm", Locale.US)
        when {
            isJson -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val fileContent = getJSONExportString(readableDatabase)
                    val fileExtension = ".json"
                    val fileName = DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                    try {
                        val newFile = documentFile?.createFile("application/json", fileName)
                        val outputStream = context.contentResolver.openOutputStream(newFile!!.uri)
                        outputStream?.use {
                            it.write(fileContent.toByteArray())
                            it.flush()
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.resources.getString(R.string.write_to_external_storage_as) + fileName, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            (context as ExportActivity).promptUserToSaveFile(fileName, isJson = true, isCsv = false, isMovieOnly = false)
                        }
                    }
                }
            }
            isDb -> {
                val fileExtension = ".db"
                val exportDBPath = DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dynamicDbPath = context.getDatabasePath(databaseFileName).absolutePath
                        val currentDb = File(dynamicDbPath)

                        if (!currentDb.exists()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.resources.getString(R.string.database_not_found), Toast.LENGTH_SHORT).show()
                            }
                        }

                        val newFile = documentFile?.createFile("application/octet-stream", exportDBPath)
                        val outputStream = context.contentResolver.openOutputStream(newFile!!.uri)
                        val fileChannel = FileInputStream(currentDb).channel
                        val buffer = ByteBuffer.allocate(fileChannel.size().toInt())
                        fileChannel.read(buffer)
                        buffer.flip()
                        val byteArray = ByteArray(buffer.remaining())
                        buffer[byteArray]
                        outputStream?.use {
                            it.write(byteArray)
                            it.flush()
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.resources.getString(R.string.write_to_external_storage_as) + exportDBPath, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            if (context is ExportActivity) {
                                context.promptUserToSaveFile(exportDBPath, isJson = false, isCsv = false, isMovieOnly = false)
                            }
                        }
                    }
                }
            }
            isCsv -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val fileContent = getCSVExportString(readableDatabase, isMoviesOnly)
                    val fileExtension = ".csv"
                    val fileName = DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                    try {
                        val newFile = documentFile?.createFile("text/csv", fileName)
                        val outputStream = context.contentResolver.openOutputStream(newFile!!.uri)
                        outputStream?.use {
                            it.write(fileContent.toByteArray())
                            it.flush()
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.resources.getString(R.string.write_to_external_storage_as) + fileName, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            (context as ExportActivity).promptUserToSaveFile(fileName, isJson = false, isCsv = true, isMovieOnly = isMoviesOnly)
                        }
                    }
                }
            }
        }
    }

    private fun exportToDirectory(context: Context, exportDirectory: File, isJson: Boolean, isDb: Boolean, isCsv: Boolean, isMoviesOnly: Boolean) {
        val simpleDateFormat = SimpleDateFormat("dd-MM-yy-kk-mm-ss-SSS", Locale.US)
        when {
            isJson -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val fileContent = getJSONExportString(readableDatabase)
                    val fileExtension = ".json"
                    val fileName = DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                    try {
                        val file = File(exportDirectory, fileName)
                        val bufferedOutputStream = BufferedOutputStream(FileOutputStream(file))
                        bufferedOutputStream.write(fileContent.toByteArray())
                        bufferedOutputStream.flush()
                        bufferedOutputStream.close()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.resources.getString(R.string.write_to_external_storage_as) + fileName, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            (context as ExportActivity).promptUserToSaveFile(fileName, isJson = true, isCsv = false, isMovieOnly = false)
                        }
                    }
                }
            }
            isDb -> {
                val fileExtension = ".db"
                val exportDBPath = DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dynamicDbPath = context.getDatabasePath(databaseFileName).absolutePath
                        val currentDb = File(dynamicDbPath)

                        if (!currentDb.exists()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.resources.getString(R.string.database_not_found), Toast.LENGTH_SHORT).show()
                            }
                        }

                        val exportDB = File(exportDirectory, exportDBPath)
                        val bufferedOutputStream = BufferedOutputStream(FileOutputStream(exportDB))
                        val fileChannel = FileInputStream(currentDb).channel
                        val buffer = ByteBuffer.allocate(fileChannel.size().toInt())
                        fileChannel.read(buffer)
                        buffer.flip()
                        val byteArray = ByteArray(buffer.remaining())
                        buffer[byteArray]
                        bufferedOutputStream.write(byteArray)
                        bufferedOutputStream.flush()
                        bufferedOutputStream.close()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.resources.getString(R.string.write_to_external_storage_as) + exportDBPath, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            if (context is ExportActivity) {
                                context.promptUserToSaveFile(exportDBPath, isJson = false, isCsv = false, isMovieOnly = false)
                            }
                        }
                    }
                }
            }
            isCsv -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val fileContent = getCSVExportString(readableDatabase, isMoviesOnly)
                    val fileExtension = ".csv"
                    val fileName = DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                    try {
                        val file = File(exportDirectory, fileName)
                        val bufferedOutputStream = BufferedOutputStream(FileOutputStream(file))
                        bufferedOutputStream.write(fileContent.toByteArray())
                        bufferedOutputStream.flush()
                        bufferedOutputStream.close()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.resources.getString(R.string.write_to_external_storage_as) + fileName, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            (context as ExportActivity).promptUserToSaveFile(fileName, isJson = false, isCsv = true, isMovieOnly = isMoviesOnly)
                        }
                    }
                }
            }
        }
    }

    private fun promptUserToSaveFile(context: Context, isJson: Boolean, isDb: Boolean, isCsv: Boolean, isMoviesOnly: Boolean) {
        val simpleDateFormat = SimpleDateFormat("dd-MM-yy-kk-mm", Locale.US)
        when {
            isJson -> {
                val fileExtension = ".json"
                val fileName = DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                (context as ExportActivity).promptUserToSaveFile(fileName, isJson = true, isCsv = false, isMovieOnly = false)
            }
            isDb -> {
                val fileExtension = ".db"
                val fileName = DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                (context as ExportActivity).promptUserToSaveFile(fileName, isJson = false, isCsv = false, isMovieOnly = false)
            }
            isCsv -> {
                val fileExtension = ".csv"
                val fileName = DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                (context as ExportActivity).promptUserToSaveFile(fileName, isJson = false, isCsv = true, isMovieOnly = isMoviesOnly)
            }
        }
    }

    /**
     * Displays a dialog with possible files to import and imports the chosen file.
     * The current database will be dropped in that case.
     *
     * @param context the context needed for the dialog.
     */
    fun importDatabase(context: Context, listener: AdapterDataChangedListener) {
        // Ask the user which file to import
        val downloadPath = context.cacheDir.path
        val directory = File(downloadPath)
        val files = directory.listFiles { pathname: File ->
            // Only show database and csv
            val name = pathname.name
            name.endsWith(".db") || name.endsWith(".csv")
        }
        val fileAdapter = ArrayAdapter<String>(context, android.R.layout.select_dialog_singlechoice)
        for (file in files) {
            fileAdapter.add(file.name)
        }
        val fileDialog = MaterialAlertDialogBuilder(context)
        fileDialog.setTitle(R.string.choose_file)
        fileDialog.setNegativeButton(R.string.import_cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }

        // Show the files that can be imported.
        fileDialog.setAdapter(fileAdapter) { _: DialogInterface?, which: Int ->
            val path = File(context.cacheDir.path)
            try {
                val exportDBPath = fileAdapter.getItem(which)
                if (exportDBPath == null) {
                    Toast.makeText(context, context.resources.getString(R.string.file_not_found_exception), Toast.LENGTH_SHORT).show()
                } else if (fileAdapter.getItem(which)!!.endsWith(".db")) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Import the file selected in the dialog.
                            val currentDBPath = context.getDatabasePath(databaseFileName).absolutePath
                            val currentDB = File(currentDBPath)
                            val importDB = File(path, exportDBPath)
                            val src = FileInputStream(importDB).channel
                            val dst = FileOutputStream(currentDB).channel
                            dst.transferFrom(src, 0, src.size())
                            src.close()
                            dst.close()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, R.string.database_import_successful, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else if (fileAdapter.getItem(which)!!.endsWith(".csv")) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val csvFile = File(path, fileAdapter.getItem(which)!!)
                            val database = context.openOrCreateDatabase(databaseFileName, Context.MODE_PRIVATE, null)
                            importCSVToDatabase(database, csvFile)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, R.string.database_import_successful, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, R.string.database_not_imported, Toast.LENGTH_SHORT).show()
                            }
                        }
                        listener.onAdapterDataChangedListener()
                    }
                }
            } catch (npe: NullPointerException) {
                npe.printStackTrace()
                Toast.makeText(context, context.resources.getString(R.string.file_not_found_exception), Toast.LENGTH_SHORT).show()
            }
            listener.onAdapterDataChangedListener()
        }
        fileDialog.show()
    }

    private fun importCSVToDatabase(database: SQLiteDatabase, csvFile: File) {
        try {
            val bufferedReader = BufferedReader(FileReader(csvFile))
            var line: String? = bufferedReader.readLine()
            val csvColumns = line?.split(",")?.map { it.trim('"') } ?: return


            database.beginTransaction()
            try {
                while (bufferedReader.readLine().also { line = it } != null) {
                    val values = line?.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())?.map {
                        it.trim('"').replace("\"\"", "\"")
                    } ?: continue

                    if (values.size != csvColumns.size) continue

                    val movieValues = ContentValues().apply {
                        // Required fields with defaults
                        put(COLUMN_MOVIES_ID, values.getOrNull(csvColumns.indexOf("tmdb_id"))?.toLongOrNull() ?: 0L)
                        put(COLUMN_RATING, values.getOrNull(csvColumns.indexOf("tmdb_rating"))?.toDoubleOrNull() ?: 0.0)
                        put(COLUMN_IMAGE, values.getOrNull(csvColumns.indexOf("image")) ?: "")
                        put(COLUMN_ICON, values.getOrNull(csvColumns.indexOf("icon")) ?: "")
                        put(COLUMN_TITLE, values.getOrNull(csvColumns.indexOf("title")) ?: "")
                        put(COLUMN_SUMMARY, values.getOrNull(csvColumns.indexOf("summary")) ?: "")
                        put(COLUMN_GENRES, values.getOrNull(csvColumns.indexOf("genres")) ?: "")
                        put(COLUMN_GENRES_IDS, values.getOrNull(csvColumns.indexOf("genres_ids")) ?: "")
                        put(COLUMN_CATEGORIES, values.getOrNull(csvColumns.indexOf("categories"))?.toIntOrNull() ?: 0)
                        put(COLUMN_MOVIE, values.getOrNull(csvColumns.indexOf("isMovie"))?.toIntOrNull() ?: 1)

                        // Optional fields
                        values.getOrNull(csvColumns.indexOf("rating"))?.toDoubleOrNull()?.let {
                            put(COLUMN_PERSONAL_RATING, it)
                        }
                        values.getOrNull(csvColumns.indexOf("release_date"))?.let {
                            put(COLUMN_RELEASE_DATE, it)
                        }
                        values.getOrNull(csvColumns.indexOf("start_date"))?.let {
                            put(COLUMN_PERSONAL_START_DATE, it)
                        }
                        values.getOrNull(csvColumns.indexOf("finish_date"))?.let {
                            put(COLUMN_PERSONAL_FINISH_DATE, it)
                        }
                        values.getOrNull(csvColumns.indexOf("movie_review"))?.let {
                            put(COLUMN_MOVIE_REVIEW, it)
                        }
                    }

                    val episodeValues = ContentValues().apply {
                        // Required fields with defaults
                        put(COLUMN_MOVIES_ID, movieValues.getAsLong(COLUMN_MOVIES_ID) ?: 0L)
                        put(COLUMN_SEASON_NUMBER, values.getOrNull(csvColumns.indexOf("season"))?.toIntOrNull() ?: 1)
                        put(COLUMN_EPISODE_NUMBER, values.getOrNull(csvColumns.indexOf("episode"))?.toIntOrNull() ?: 1)

                        // Optional fields
                        values.getOrNull(csvColumns.indexOf("episode_rating"))?.toDoubleOrNull()?.let {
                            put(COLUMN_EPISODE_RATING, it)
                        }
                        values.getOrNull(csvColumns.indexOf("episode_watch_date"))?.let {
                            put(COLUMN_EPISODE_WATCH_DATE, it)
                        }
                        values.getOrNull(csvColumns.indexOf("episode_review"))?.let {
                            put(COLUMN_EPISODE_REVIEW, it)
                        }
                    }

                    // Insert movie data
                    val movieId = movieValues.getAsLong(COLUMN_MOVIES_ID)
                    if (movieId != null && movieId != 0L) {
                        database.insertWithOnConflict(
                            TABLE_MOVIES,
                            null,
                            movieValues,
                            SQLiteDatabase.CONFLICT_REPLACE
                        )

                        if (episodeValues.getAsInteger(COLUMN_SEASON_NUMBER) != null &&
                            episodeValues.getAsInteger(COLUMN_EPISODE_NUMBER) != null) {
                            database.insertWithOnConflict(
                                TABLE_EPISODES,
                                null,
                                episodeValues,
                                SQLiteDatabase.CONFLICT_REPLACE
                            )
                        }
                    }
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            bufferedReader.close()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override fun onCreate(database: SQLiteDatabase) {
        // Create the database with the database creation statement.
        val DATABASE_CREATE = ("CREATE TABLE IF NOT EXISTS " +
                TABLE_MOVIES + "(" + COLUMN_ID + " integer primary key autoincrement, " +
                COLUMN_MOVIES_ID + " integer not null, " + COLUMN_RATING +
                " REAL not null, " + COLUMN_PERSONAL_RATING + " REAL, " +
                COLUMN_IMAGE + " text not null, " + COLUMN_ICON + " text not null, " +
                COLUMN_TITLE + " text not null, " + COLUMN_SUMMARY + " text not null, " +
                COLUMN_GENRES + " text not null, " + COLUMN_GENRES_IDS
                + " text not null, " + COLUMN_RELEASE_DATE + " text, " + COLUMN_PERSONAL_START_DATE + " text, " +
                COLUMN_PERSONAL_FINISH_DATE + " text, " + COLUMN_PERSONAL_REWATCHED +
                " integer, " + COLUMN_CATEGORIES + " integer not null, " + COLUMN_MOVIE +
                " integer not null, " + COLUMN_PERSONAL_EPISODES + " integer, " +
                COLUMN_MOVIE_REVIEW + " TEXT);")
        database.execSQL(DATABASE_CREATE)
        val CREATE_EPISODES_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_EPISODES + "(" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_MOVIES_ID + " INTEGER NOT NULL, " +
                COLUMN_SEASON_NUMBER + " INTEGER NOT NULL, " +
                COLUMN_EPISODE_NUMBER + " INTEGER NOT NULL, " +
                COLUMN_EPISODE_RATING + " REAL, " +
                COLUMN_EPISODE_WATCH_DATE + " TEXT, " +
                COLUMN_EPISODE_REVIEW + " TEXT);"
        database.execSQL(CREATE_EPISODES_TABLE)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(
            MovieDatabaseHelper::class.java.name, "Upgrading database from version "
                    + oldVersion + " to " + newVersion +
                    ", database will be temporarily exported to a JSON string and imported after the upgrade."
        )
        if (oldVersion < 12) {
            val CREATE_EPISODES_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_EPISODES + "(" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_MOVIES_ID + " INTEGER NOT NULL, " +
                    COLUMN_SEASON_NUMBER + " INTEGER NOT NULL, " +
                    COLUMN_EPISODE_NUMBER + " INTEGER NOT NULL);"
            database.execSQL(CREATE_EPISODES_TABLE)
        }
        if (oldVersion < 13) {
            var ALTER_EPISODES_TABLE =
                "ALTER TABLE $TABLE_EPISODES ADD COLUMN $COLUMN_EPISODE_RATING REAL;"
            database.execSQL(ALTER_EPISODES_TABLE)
            ALTER_EPISODES_TABLE =
                "ALTER TABLE $TABLE_EPISODES ADD COLUMN $COLUMN_EPISODE_WATCH_DATE TEXT;"
            database.execSQL(ALTER_EPISODES_TABLE)
        }
        if (oldVersion < 14) {
            // Add the movie_review column to the old table if it doesn't exist
            if (isColumnExists(database, TABLE_MOVIES, COLUMN_MOVIE_REVIEW)) {
                val ADD_MOVIE_REVIEW_COLUMN =
                    "ALTER TABLE $TABLE_MOVIES ADD COLUMN $COLUMN_MOVIE_REVIEW TEXT;"
                database.execSQL(ADD_MOVIE_REVIEW_COLUMN)
            }

            // Create a new table with the desired schema
            val CREATE_NEW_MOVIES_TABLE = "CREATE TABLE movies_new (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_MOVIES_ID + " INTEGER NOT NULL, " +
                    COLUMN_RATING + " REAL NOT NULL, " +
                    COLUMN_PERSONAL_RATING + " REAL, " +
                    COLUMN_IMAGE + " TEXT NOT NULL, " +
                    COLUMN_ICON + " TEXT NOT NULL, " +
                    COLUMN_TITLE + " TEXT NOT NULL, " +
                    COLUMN_SUMMARY + " TEXT NOT NULL, " +
                    COLUMN_GENRES + " TEXT NOT NULL, " +
                    COLUMN_GENRES_IDS + " TEXT NOT NULL, " +
                    COLUMN_RELEASE_DATE + " TEXT, " +
                    COLUMN_PERSONAL_START_DATE + " TEXT, " +
                    COLUMN_PERSONAL_FINISH_DATE + " TEXT, " +
                    COLUMN_PERSONAL_REWATCHED + " INTEGER, " +
                    COLUMN_CATEGORIES + " INTEGER NOT NULL, " +
                    COLUMN_MOVIE + " INTEGER NOT NULL, " +
                    COLUMN_PERSONAL_EPISODES + " INTEGER, " +
                    COLUMN_MOVIE_REVIEW + " TEXT);"
            database.execSQL(CREATE_NEW_MOVIES_TABLE)

            // Copy data from the old table to the new table
            val COPY_DATA = "INSERT INTO movies_new (" +
                    COLUMN_ID + ", " +
                    COLUMN_MOVIES_ID + ", " +
                    COLUMN_RATING + ", " +
                    COLUMN_PERSONAL_RATING + ", " +
                    COLUMN_IMAGE + ", " +
                    COLUMN_ICON + ", " +
                    COLUMN_TITLE + ", " +
                    COLUMN_SUMMARY + ", " +
                    COLUMN_GENRES + ", " +
                    COLUMN_GENRES_IDS + ", " +
                    COLUMN_RELEASE_DATE + ", " +
                    COLUMN_PERSONAL_START_DATE + ", " +
                    COLUMN_PERSONAL_FINISH_DATE + ", " +
                    COLUMN_PERSONAL_REWATCHED + ", " +
                    COLUMN_CATEGORIES + ", " +
                    COLUMN_MOVIE + ", " +
                    COLUMN_PERSONAL_EPISODES + ", " +
                    COLUMN_MOVIE_REVIEW + ") " +
                    "SELECT " +
                    COLUMN_ID + ", " +
                    COLUMN_MOVIES_ID + ", " +
                    COLUMN_RATING + ", " +
                    COLUMN_PERSONAL_RATING + ", " +
                    COLUMN_IMAGE + ", " +
                    COLUMN_ICON + ", " +
                    COLUMN_TITLE + ", " +
                    COLUMN_SUMMARY + ", " +
                    COLUMN_GENRES + ", " +
                    COLUMN_GENRES_IDS + ", " +
                    COLUMN_RELEASE_DATE + ", " +
                    COLUMN_PERSONAL_START_DATE + ", " +
                    COLUMN_PERSONAL_FINISH_DATE + ", " +
                    COLUMN_PERSONAL_REWATCHED + ", " +
                    COLUMN_CATEGORIES + ", " +
                    COLUMN_MOVIE + ", " +
                    COLUMN_PERSONAL_EPISODES + ", " +
                    COLUMN_MOVIE_REVIEW + " FROM " + TABLE_MOVIES + ";"
            database.execSQL(COPY_DATA)

            // Drop the old table
            val DROP_OLD_TABLE = "DROP TABLE $TABLE_MOVIES;"
            database.execSQL(DROP_OLD_TABLE)

            // Rename the new table to the old table's name
            val RENAME_TABLE = "ALTER TABLE movies_new RENAME TO $TABLE_MOVIES;"
            database.execSQL(RENAME_TABLE)
        }
        if (oldVersion < 15) {
            if (isColumnExists(database, TABLE_MOVIES, COLUMN_MOVIE_REVIEW)) {
                val ALTER_MOVIES_TABLE =
                    "ALTER TABLE $TABLE_MOVIES ADD COLUMN $COLUMN_MOVIE_REVIEW TEXT;"
                database.execSQL(ALTER_MOVIES_TABLE)
            }
            if (isColumnExists(database, TABLE_EPISODES, COLUMN_EPISODE_REVIEW)) {
                val ALTER_EPISODES_TABLE =
                    "ALTER TABLE $TABLE_EPISODES ADD COLUMN $COLUMN_EPISODE_REVIEW TEXT;"
                database.execSQL(ALTER_EPISODES_TABLE)
            }
        }
        onCreate(database)
    }

    private fun isColumnExists(
        database: SQLiteDatabase,
        tableName: String,
        columnName: String
    ): Boolean {
        val cursor = database.rawQuery("PRAGMA table_info($tableName)", null)
        val columnIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(columnIndex) == columnName) {
                cursor.close()
                return false
            }
        }
        cursor.close()
        return true
    }

    fun addOrUpdateEpisode(
        movieId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        rating: Float,
        watchDate: String?,
        review: String?
    ) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_MOVIES_ID, movieId)
        values.put(COLUMN_SEASON_NUMBER, seasonNumber)
        values.put(COLUMN_EPISODE_NUMBER, episodeNumber)
        // Only add rating and watch date if they are not null
        if (rating.toDouble() != 0.0) {
            values.put(COLUMN_EPISODE_RATING, rating)
        }
        if (watchDate != null) {
            values.put(COLUMN_EPISODE_WATCH_DATE, watchDate)
        }
        if (review != null) {
            values.put(COLUMN_EPISODE_REVIEW, review)
        }

        // Check if the episode already exists
        val exists = isEpisodeInDatabase(movieId, seasonNumber, listOf(episodeNumber))
        if (exists) {
            // Update existing episode
            val whereClause =
                "$COLUMN_MOVIES_ID=? AND $COLUMN_SEASON_NUMBER=? AND $COLUMN_EPISODE_NUMBER=?"
            val whereArgs =
                arrayOf(movieId.toString(), seasonNumber.toString(), episodeNumber.toString())
            db.update(TABLE_EPISODES, values, whereClause, whereArgs)
        } else {
            // Insert new episode
            db.insert(TABLE_EPISODES, null, values)
        }
    }

    fun updateMovieCategory(movieId: Int, category: Int) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_CATEGORIES, category)
        db.update(TABLE_MOVIES, values, "$COLUMN_MOVIES_ID = ?", arrayOf(movieId.toString()))
    }

    fun getEpisodeDetails(movieId: Int, seasonNumber: Int, episodeNumber: Int): EpisodeDbDetails? {
        val db = this.readableDatabase
        val columns = arrayOf(COLUMN_EPISODE_RATING, COLUMN_EPISODE_WATCH_DATE, COLUMN_EPISODE_REVIEW)
        val selection = "$COLUMN_MOVIES_ID = ? AND $COLUMN_SEASON_NUMBER = ? AND $COLUMN_EPISODE_NUMBER = ?"
        val selectionArgs = arrayOf(movieId.toString(), seasonNumber.toString(), episodeNumber.toString())
        val cursor = db.query(TABLE_EPISODES, columns, selection, selectionArgs, null, null, null)
        var details: EpisodeDbDetails? = null
        if (cursor.moveToFirst()) {
            val rating = if (cursor.isNull(0)) null else cursor.getFloat(0)
            val watchDate = cursor.getString(1)
            val review = cursor.getString(2) ?: "Review: Not added"
            details = EpisodeDbDetails(rating, watchDate, review)
        }
        cursor.close()
        return details
    }

    fun addEpisodeNumber(movieId: Int, seasonNumber: Int, episodeNumbers: List<Int?>, watchDate: String?) {
        val db = this.writableDatabase
        val values = ContentValues()
        for (episodeNumber in episodeNumbers) {
            values.put(COLUMN_MOVIES_ID, movieId)
            values.put(COLUMN_SEASON_NUMBER, seasonNumber)
            values.put(COLUMN_EPISODE_NUMBER, episodeNumber)
            if (watchDate != null) {
                values.put(COLUMN_EPISODE_WATCH_DATE, watchDate)
            }
            db.insert(TABLE_EPISODES, null, values)
        }
    }

    fun removeEpisodeNumber(movieId: Int, seasonNumber: Int, episodeNumbers: List<Int>) {
        val db = this.writableDatabase
        for (episodeNumber in episodeNumbers) {
            db.delete(
                TABLE_EPISODES,
                "$COLUMN_MOVIES_ID = ? AND $COLUMN_SEASON_NUMBER = ? AND $COLUMN_EPISODE_NUMBER = ?",
                arrayOf(movieId.toString(), seasonNumber.toString(), episodeNumber.toString())
            )
        }
    }

    fun isEpisodeInDatabase(movieId: Int, seasonNumber: Int, episodeNumbers: List<Int>): Boolean {
        val db = this.readableDatabase
        val selection =
            "$COLUMN_MOVIES_ID = ? AND $COLUMN_SEASON_NUMBER = ? AND $COLUMN_EPISODE_NUMBER = ?"
        var exists = false
        for (episodeNumber in episodeNumbers) {
            val cursor = db.query(
                TABLE_EPISODES,
                null,
                selection,
                arrayOf(movieId.toString(), seasonNumber.toString(), episodeNumber.toString()),
                null,
                null,
                null
            )
            if (cursor.count > 0) {
                exists = true
            }
            cursor.close()
        }
        return exists
    }

    fun getSeenEpisodesCount(movieId: Int): Int {
        val db = this.readableDatabase
        val countQuery =
            "SELECT * FROM $TABLE_EPISODES WHERE $COLUMN_MOVIES_ID = $movieId"
        val cursor = db.rawQuery(countQuery, null)
        val count = cursor.count
        cursor.close()
        return count
    }

    fun isShowInDatabase(movieId: Int): Boolean {
        val db = this.readableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "SELECT * FROM $TABLE_MOVIES WHERE $COLUMN_MOVIES_ID=? LIMIT 1",
                arrayOf(movieId.toString())
            )
            return (cursor?.count ?: 0) > 0
        } finally {
            cursor?.close()
        }
    }


    /**
     * * Checks if a movie/show exists and returns its internal row ID.
     * * @param tmdbId The TMDB ID of the movie/show.
     * * @param isMovie True if it's a movie, false if it's a TV show.
     * * @return The internal database row ID (long) if found, otherwise null.
     */
    fun getMovieRowId(tmdbId: Long, isMovie: Boolean): Long? {
        val db = this.readableDatabase
        var rowId: Long? = null
        val selection = "$COLUMN_MOVIES_ID = ? AND $COLUMN_MOVIE = ?"
        val selectionArgs = arrayOf(tmdbId.toString(), if (isMovie) "1" else "0")
        val cursor = db.query(
            TABLE_MOVIES,
            arrayOf(COLUMN_ID),
            selection,
            selectionArgs,
            null,
            null,
            null,
            "1"
        )
        if (cursor.moveToFirst()) {
            rowId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
        }
        cursor.close()
        return rowId
    }

    /**
     * Checks if an episode exists and returns its internal row ID.
     * @param tmdbId The TMDB ID of the parent movie/show.
     * @param seasonNumber The season number.
     * @param episodeNumber The episode number.
     * @return The internal database row ID (long) if found, otherwise null.
     */
    fun getEpisodeRowId(tmdbId: Long, seasonNumber: Int, episodeNumber: Int): Long? {
        val db = this.readableDatabase
        var rowId: Long? = null
        val selection = "$COLUMN_MOVIES_ID = ? AND $COLUMN_SEASON_NUMBER = ? AND $COLUMN_EPISODE_NUMBER = ?"
        val selectionArgs = arrayOf(tmdbId.toString(), seasonNumber.toString(), episodeNumber.toString())
        val cursor = db.query(
            TABLE_EPISODES,
            arrayOf(COLUMN_ID),
            selection,
            selectionArgs,
            null,
            null,
            null,
            "1"
        )
        if (cursor.moveToFirst()) {
            rowId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
        }
        cursor.close()
        return rowId
    }

    companion object {
        const val TABLE_MOVIES = "movies"
        const val TABLE_EPISODES = "episodes"
        const val COLUMN_ID = "id"
        const val COLUMN_MOVIES_ID = "movie_id"
        const val COLUMN_RATING = "rating"
        const val COLUMN_PERSONAL_RATING = "personal_rating"
        const val COLUMN_IMAGE = "image"
        const val COLUMN_ICON = "icon"
        const val COLUMN_TITLE = "title"
        const val COLUMN_SUMMARY = "summary"
        const val COLUMN_GENRES = "genres"
        const val COLUMN_GENRES_IDS = "genres_ids"
        const val COLUMN_RELEASE_DATE = "release_date"
        const val COLUMN_PERSONAL_START_DATE = "personal_start_date"
        const val COLUMN_PERSONAL_FINISH_DATE = "personal_finish_date"
        const val COLUMN_PERSONAL_REWATCHED = "personal_rewatched"
        const val COLUMN_PERSONAL_EPISODES = "personal_episodes"
        const val COLUMN_EPISODE_NUMBER = "episode_number"
        const val COLUMN_SEASON_NUMBER = "season_number"
        const val COLUMN_EPISODE_WATCH_DATE = "episode_watch_date"
        const val COLUMN_EPISODE_RATING = "episode_rating"
        const val COLUMN_CATEGORIES = "watched"
        const val COLUMN_MOVIE = "movie"
        const val COLUMN_MOVIE_REVIEW = "movie_review"
        const val COLUMN_EPISODE_REVIEW = "episode_review"
        const val CATEGORY_WATCHING = 2
        const val CATEGORY_PLAN_TO_WATCH = 0
        const val CATEGORY_WATCHED = 1
        const val CATEGORY_ON_HOLD = 3
        const val CATEGORY_DROPPED = 4

        const val databaseFileName = "movies.db"
        private const val DATABASE_FILE_NAME = "movies"
        private const val DATABASE_FILE_EXT = ".db"
        private const val DATABASE_VERSION = 15
    }

    override fun close() {
        super.close()
    }

    fun getLastWatchedEpisode(movieId: Int): JSONObject? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_EPISODES,
            arrayOf(COLUMN_SEASON_NUMBER, COLUMN_EPISODE_NUMBER),
            "$COLUMN_MOVIES_ID = ?",
            arrayOf(movieId.toString()),
            null,
            null,
            "$COLUMN_SEASON_NUMBER DESC, $COLUMN_EPISODE_NUMBER DESC",
            "1"
        )
        var lastWatched: JSONObject? = null
        if (cursor.moveToFirst()) {
            lastWatched = JSONObject()
            lastWatched.put(COLUMN_SEASON_NUMBER, cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SEASON_NUMBER)))
            lastWatched.put(COLUMN_EPISODE_NUMBER, cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_EPISODE_NUMBER)))
        }
        cursor.close()
        return lastWatched
    }

    fun getWatchedEpisodesForSeason(movieId: Int, seasonNumber: Int): List<Int> {
        val db = this.readableDatabase
        val episodes = mutableListOf<Int>()
        val cursor = db.query(
            TABLE_EPISODES,
            arrayOf(COLUMN_EPISODE_NUMBER),
            "$COLUMN_MOVIES_ID = ? AND $COLUMN_SEASON_NUMBER = ?",
            arrayOf(movieId.toString(), seasonNumber.toString()),
            null,
            null,
            "$COLUMN_EPISODE_NUMBER ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                episodes.add(c.getInt(c.getColumnIndexOrThrow(COLUMN_EPISODE_NUMBER)))
            }
        }
        return episodes
    }

    fun getMediaType(movieId: Int): String? {
        val db = this.readableDatabase
        var mediaType: String? = null
        val cursor = db.query(
            TABLE_MOVIES,
            arrayOf(COLUMN_MOVIE),
            "$COLUMN_MOVIES_ID = ?",
            arrayOf(movieId.toString()),
            null,
            null,
            null,
            "1"
        )
        if (cursor.moveToFirst()) {
            val typeInt = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MOVIE))
            mediaType = if (typeInt == 1) "movie" else "episode"
        }
        cursor.close()
        return mediaType
    }

    fun getMovieCursor(movieId: Int): Cursor {
        val db = this.readableDatabase
        return db.rawQuery(
            "SELECT * FROM " +
                    TABLE_MOVIES +
                    " WHERE " + COLUMN_MOVIES_ID +
                    "=" + movieId + " LIMIT 1", null
        )
    }

    fun deleteMovie(movieId: Int) {
        val db = this.writableDatabase
        db.delete(
            TABLE_MOVIES,
            "$COLUMN_MOVIES_ID = ?",
            arrayOf(movieId.toString())
        )
    }

    fun deleteEpisodesForMovie(movieId: Int) {
        val db = this.writableDatabase
        db.delete(
            TABLE_EPISODES,
            "$COLUMN_MOVIES_ID = ?",
            arrayOf(movieId.toString())
        )
    }
}
