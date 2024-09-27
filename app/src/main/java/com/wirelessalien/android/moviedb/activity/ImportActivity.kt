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
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.CrashHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ImportActivity : AppCompatActivity(), AdapterDataChangedListener {
    private lateinit var context: Context
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data!!.data != null) {
                val archiveFileUri = data.data
                Toast.makeText(this, getString(R.string.file_picked_success), Toast.LENGTH_SHORT)
                    .show()
                try {
                    val inputStream = contentResolver.openInputStream(archiveFileUri!!)
                    val cacheFile = File(cacheDir, getArchiveFileName(archiveFileUri))
                    val fileOutputStream = FileOutputStream(cacheFile)
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream!!.read(buffer).also { length = it } > 0) {
                        fileOutputStream.write(buffer, 0, length)
                    }
                    Log.d("CacheFile", cacheFile.absolutePath)
                    fileOutputStream.close()
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }


                // Display the file name from the intent
//                String fileName = getArchiveFileName(archiveFileUri);
//                String selectedFileText = getString(R.string.selected_file_text, fileName);
//                fileNameTextView.setText(selectedFileText);
//                fileNameTextView.setSelected(true);
            } else {
                Toast.makeText(this, getString(R.string.file_picked_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getArchiveFileName(uri: Uri?): String? {
        var result: String? = null
        if (uri!!.scheme == "content") {
            contentResolver.query(uri, null, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    result =
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result!!.substring(cut + 1)
            }
        }
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)

        CrashHelper.setDefaultUncaughtExceptionHandler(applicationContext)

        context = this
        val pickFileButton = findViewById<Button>(R.id.pick_file_button)
        pickFileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.setType("*/*")
            startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
        }
        val importButton = findViewById<Button>(R.id.import_button)
        importButton.setOnClickListener {
            val databaseHelper = MovieDatabaseHelper(applicationContext)
            databaseHelper.importDatabase(context, this)
        }
    }

    override fun onAdapterDataChangedListener() {
        // Do nothing
    }

    companion object {
        private const val PICK_FILE_REQUEST_CODE = 1
    }
}
