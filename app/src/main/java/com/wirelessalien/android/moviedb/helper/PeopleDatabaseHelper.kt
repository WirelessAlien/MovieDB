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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PeopleDatabaseHelper(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY, " +
                COLUMN_NAME + " TEXT, " +
                COLUMN_BIRTHDAY + " TEXT, " +
                COLUMN_DEATHDAY + " TEXT, " +
                COLUMN_BIOGRAPHY + " TEXT, " +
                COLUMN_PLACE_OF_BIRTH + " TEXT, " +
                COLUMN_POPULARITY + " REAL, " +
                COLUMN_PROFILE_PATH + " TEXT, " +
                COLUMN_IMDB_ID + " TEXT, " +
                COLUMN_HOMEPAGE + " TEXT);"
        db.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(DROP_TABLE)
        onCreate(db)
    }

    fun deleteAll() {
        val db = this.writableDatabase
        db.execSQL(DELETE_ALL)
        db.close()
    }

    fun deleteById(id: Int) {
        val db = this.writableDatabase
        db.execSQL(DELETE_BY_ID, arrayOf(id.toString()))
        db.close()
    }

    fun personExists(actorId: Int): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_NAME WHERE $COLUMN_ID = $actorId",
            null
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun insert(
        id: Int, name: String?, birthday: String?, deathday: String?, biography: String?,
        placeOfBirth: String?, popularity: Double, profilePath: String?, imdbId: String?, homepage: String?
    ) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ID, id)
            put(COLUMN_NAME, name)
            put(COLUMN_BIRTHDAY, birthday)
            put(COLUMN_DEATHDAY, deathday)
            put(COLUMN_BIOGRAPHY, biography)
            put(COLUMN_PLACE_OF_BIRTH, placeOfBirth)
            put(COLUMN_POPULARITY, popularity)
            put(COLUMN_PROFILE_PATH, profilePath)
            put(COLUMN_IMDB_ID, imdbId)
            put(COLUMN_HOMEPAGE, homepage)
        }
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    private suspend fun getJSONExportString(database: SQLiteDatabase): String = withContext(Dispatchers.IO) {
        val cursor = database.rawQuery(SELECT_ALL, null)
        val stringBuilder = StringBuilder()
        stringBuilder.append("[")
        if (cursor.moveToFirst()) {
            do {
                stringBuilder.append("{")
                stringBuilder.append("\"$COLUMN_ID\": ${cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))},")
                stringBuilder.append("\"$COLUMN_NAME\": \"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME))}\",")
                stringBuilder.append("\"$COLUMN_BIRTHDAY\": \"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BIRTHDAY))}\",")
                stringBuilder.append("\"$COLUMN_DEATHDAY\": \"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEATHDAY))}\",")
                stringBuilder.append("\"$COLUMN_BIOGRAPHY\": \"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BIOGRAPHY))}\",")
                stringBuilder.append("\"$COLUMN_PLACE_OF_BIRTH\": \"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PLACE_OF_BIRTH))}\",")
                stringBuilder.append("\"$COLUMN_POPULARITY\": ${cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_POPULARITY))},")
                stringBuilder.append("\"$COLUMN_PROFILE_PATH\": \"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_PATH))}\",")
                stringBuilder.append("\"$COLUMN_IMDB_ID\": \"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMDB_ID))}\",")
                stringBuilder.append("\"$COLUMN_HOMEPAGE\": \"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HOMEPAGE))}\"")
                stringBuilder.append("},")
            } while (cursor.moveToNext())
        }
        cursor.close()
        stringBuilder.deleteCharAt(stringBuilder.length - 1)
        stringBuilder.append("]")
        stringBuilder.toString()
    }

    private suspend fun getCSVExportString(database: SQLiteDatabase): String = withContext(Dispatchers.IO) {
        val cursor = database.rawQuery(SELECT_ALL, null)
        val stringBuilder = StringBuilder()
        stringBuilder.append("$COLUMN_ID,$COLUMN_NAME,$COLUMN_BIRTHDAY,$COLUMN_DEATHDAY,$COLUMN_BIOGRAPHY,$COLUMN_PLACE_OF_BIRTH,$COLUMN_POPULARITY,$COLUMN_PROFILE_PATH,$COLUMN_IMDB_ID,$COLUMN_HOMEPAGE\n")
        if (cursor.moveToFirst()) {
            do {
                stringBuilder.append(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))).append(",")
                stringBuilder.append("\"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME))}\"").append(",")
                stringBuilder.append("\"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BIRTHDAY))}\"").append(",")
                stringBuilder.append("\"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEATHDAY))}\"").append(",")
                stringBuilder.append("\"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BIOGRAPHY))}\"").append(",")
                stringBuilder.append("\"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PLACE_OF_BIRTH))}\"").append(",")
                stringBuilder.append(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_POPULARITY))).append(",")
                stringBuilder.append("\"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_PATH))}\"").append(",")
                stringBuilder.append("\"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMDB_ID))}\"").append(",")
                stringBuilder.append("\"${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HOMEPAGE))}\"").append("\n")
            } while (cursor.moveToNext())
        }
        cursor.close()
        stringBuilder.toString()
    }

    fun exportDatabase(context: Context, exportDirectoryUri: String?) {
        val builder = MaterialAlertDialogBuilder(context)
        val inflater = LayoutInflater.from(context)
        val customView = inflater.inflate(R.layout.export_dialog, null)
        val jsonRadioButton = customView.findViewById<RadioButton>(R.id.radio_json)
        val dbRadioButton = customView.findViewById<RadioButton>(R.id.radio_db)
        val csvRadioButton = customView.findViewById<RadioButton>(R.id.radio_csv)
        builder.setView(customView)
        builder.setTitle(context.resources.getString(R.string.choose_export_file))
            .setPositiveButton(context.getString(R.string.export)) { _, _ ->
                val exportDirectory = DirectoryHelper.getExportDirectory(context)
                if (exportDirectoryUri != null && exportDirectory != null) {
                    exportToUri(context, exportDirectoryUri, jsonRadioButton, dbRadioButton, csvRadioButton)
                } else if (exportDirectory != null) {
                    exportToDirectory(context, exportDirectory, jsonRadioButton, dbRadioButton, csvRadioButton)
                } else if (exportDirectoryUri !== null) {
                    exportToUri(context, exportDirectoryUri, jsonRadioButton, dbRadioButton, csvRadioButton)
                } else {
                    Toast.makeText(context, context.resources.getString(R.string.error_saving_file), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(context.getString(R.string.cancel)) { dialogInterface, _ -> dialogInterface.cancel() }
        builder.show()
    }

    private fun exportToUri(context: Context, exportDirectoryUri: String, jsonRadioButton: RadioButton, dbRadioButton: RadioButton, csvRadioButton: RadioButton) {
        val documentFile = DocumentFile.fromTreeUri(context, Uri.parse(exportDirectoryUri))
        val simpleDateFormat = SimpleDateFormat("dd-MM-yy-kk-mm", Locale.US)
        when {
            jsonRadioButton.isChecked -> {
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
                    }
                }
            }
            dbRadioButton.isChecked -> {
                val fileExtension = ".db"
                val exportDBPath = DATABASE_FILE_NAME + simpleDateFormat.format(
                    Date()
                ) + fileExtension
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dynamicDbPath = context.getDatabasePath(DATABASE_NAME).absolutePath
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
                    }
                }
            }
            csvRadioButton.isChecked -> {
                CoroutineScope(Dispatchers.IO).launch {
                val fileContent = getCSVExportString(readableDatabase)
                val fileExtension = ".csv"
                val fileName = DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                    try {
                        val newFile = documentFile?.createFile("text/csv", fileName)
                        val outputStream =
                            context.contentResolver.openOutputStream(newFile!!.uri)
                        outputStream?.use {
                            it.write(fileContent.toByteArray())
                            it.flush()
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.resources.getString(R.string.write_to_external_storage_as) + fileName,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun exportToDirectory(context: Context, exportDirectory: File, jsonRadioButton: RadioButton, dbRadioButton: RadioButton, csvRadioButton: RadioButton) {
        val simpleDateFormat = SimpleDateFormat("dd-MM-yy-kk-mm", Locale.US)
        when {
            jsonRadioButton.isChecked -> {
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
                    }
                }
            }
            dbRadioButton.isChecked -> {
                val fileExtension = ".db"
                val exportDBPath = DATABASE_FILE_NAME + simpleDateFormat.format(
                    Date()
                ) + fileExtension
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dynamicDbPath = context.getDatabasePath(DATABASE_NAME).absolutePath
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
                    }
                }
            }
            csvRadioButton.isChecked -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val fileContent = getCSVExportString(readableDatabase)
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
                    }
                }
            }
        }
    }

    fun importDatabase(context: Context, listener: AdapterDataChangedListener) {
        // Ask the user which file to import
        val downloadPath = context.cacheDir.path
        val directory = File(downloadPath)
        val files = directory.listFiles { pathname: File ->
            // Only show database
            val name = pathname.name
            name.endsWith(".db")
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
                    throw NullPointerException()
                } else if (fileAdapter.getItem(which)!!.endsWith(".db")) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Import the file selected in the dialog.
                            val currentDBPath = context.getDatabasePath(DATABASE_NAME).absolutePath
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
                }
            } catch (npe: NullPointerException) {
                npe.printStackTrace()
                Toast.makeText(context, context.resources.getString(R.string.file_not_found_exception), Toast.LENGTH_SHORT).show()
            }
            listener.onAdapterDataChangedListener()
        }
        fileDialog.show()
    }


    fun update(id: Int, name: String, birthday: String, deathday: String, biography: String, placeOfBirth: String, popularity: Double, profilePath: String, imdbId: String, homepage: String) {
        val db = this.writableDatabase
        db.execSQL(
            UPDATE,
            arrayOf(name, birthday, deathday, biography, placeOfBirth, popularity.toString(), profilePath, imdbId, homepage, id.toString())
        )
        db.close()
    }

    companion object {
        const val TABLE_NAME = "people"
        const val COLUMN_ID = "_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_BIRTHDAY = "birthday"
        const val COLUMN_DEATHDAY = "deathday"
        const val COLUMN_BIOGRAPHY = "biography"
        const val COLUMN_PLACE_OF_BIRTH = "place_of_birth"
        const val COLUMN_POPULARITY = "popularity"
        const val COLUMN_PROFILE_PATH = "profile_path"
        const val COLUMN_IMDB_ID = "imdb_id"
        const val COLUMN_HOMEPAGE = "homepage"
        const val DATABASE_NAME = "people.db"
        private const val DATABASE_FILE_NAME = "people"
        const val DATABASE_VERSION = 2
        const val DROP_TABLE = "DROP TABLE IF EXISTS $TABLE_NAME"
        const val DELETE_BY_ID = "DELETE FROM $TABLE_NAME WHERE $COLUMN_ID = ?"
        const val DELETE_ALL = "DELETE FROM $TABLE_NAME"
        const val UPDATE = "UPDATE " + TABLE_NAME + " SET " + COLUMN_NAME + " = ?, " +
                COLUMN_BIRTHDAY + " = ?, " +
                COLUMN_DEATHDAY + " = ?, " +
                COLUMN_BIOGRAPHY + " = ?, " +
                COLUMN_PLACE_OF_BIRTH + " = ?, " +
                COLUMN_POPULARITY + " = ?, " +
                COLUMN_PROFILE_PATH + " = ?, " +
                COLUMN_IMDB_ID + " = ?, " +
                COLUMN_HOMEPAGE + " = ? WHERE " + COLUMN_ID + " = ?"
        const val SELECT_ALL = "SELECT * FROM $TABLE_NAME"
        const val SELECT_ALL_SORTED_BY_NAME =
            "SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_NAME ASC"
    }
}
