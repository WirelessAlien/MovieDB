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
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.CrashHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.PeopleDatabaseHelper
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class ImportActivity : AppCompatActivity(), AdapterDataChangedListener {

    private lateinit var context: Context
    private lateinit var pickFileLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)

        CrashHelper.setDefaultUncaughtExceptionHandler(applicationContext)

        context = this
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.title = getString(R.string.action_import)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)

        pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val archiveFileUri = result.data!!.data
                Toast.makeText(this, getString(R.string.file_picked_success), Toast.LENGTH_SHORT).show()
                try {
                    val inputStream = contentResolver.openInputStream(archiveFileUri!!)
                    val fileName = getArchiveFileName(archiveFileUri) ?: "imported_file"
                    val cacheFile = File(cacheDir, fileName)
                    val fileOutputStream = FileOutputStream(cacheFile)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var length: Int
                    while (inputStream!!.read(buffer).also { length = it } > 0) {
                        fileOutputStream.write(buffer, 0, length)
                    }
                    fileOutputStream.close()
                    inputStream.close()

                    // Check if file has extension, if not try to detect it
                    if (!cacheFile.name.endsWith(".db", true) && !cacheFile.name.endsWith(".csv", true)) {
                        if (isSQLite(cacheFile)) {
                            val newFile = File(cacheDir, "$fileName.db")
                            cacheFile.renameTo(newFile)
                        } else {
                            val mimeType = contentResolver.getType(archiveFileUri)
                            if (mimeType != null && (mimeType == "text/csv" || mimeType == "text/comma-separated-values")) {
                                val newFile = File(cacheDir, "$fileName.csv")
                                cacheFile.renameTo(newFile)
                            }
                        }
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } else {
                Toast.makeText(this, getString(R.string.file_picked_fail), Toast.LENGTH_SHORT).show()
            }
        }

        val pickFileButton = findViewById<Button>(R.id.pick_file_button)
        pickFileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            pickFileLauncher.launch(intent)
        }

        val importMovieDbButton = findViewById<Button>(R.id.import_movie_db_button)
        importMovieDbButton.setOnClickListener {
            val databaseHelper = MovieDatabaseHelper(applicationContext)
            databaseHelper.importDatabase(context, this)
        }

        val importPeopleDbButton = findViewById<Button>(R.id.import_people_db_button)
        importPeopleDbButton.setOnClickListener {
            val databaseHelper = PeopleDatabaseHelper(applicationContext)
            databaseHelper.importDatabase(context, this)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun getArchiveFileName(uri: Uri?): String? {
        var result: String? = null
        if (uri!!.scheme == "content") {
            contentResolver.query(uri, null, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            val path = uri.path
            val cut = path!!.lastIndexOf('/')
            if (cut != -1) {
                result = path.substring(cut + 1)
            }
        }
        return result
    }

    private fun isSQLite(file: File): Boolean {
        return try {
            java.io.FileInputStream(file).use { fis ->
                val header = ByteArray(16)
                if (fis.read(header) != 16) return false
                val expected = "SQLite format 3\u0000".toByteArray()
                header.contentEquals(expected)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    override fun onAdapterDataChangedListener() {
        // Do nothing
    }
}
