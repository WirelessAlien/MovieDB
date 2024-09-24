/*
 *     This file is part of Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     Movie DB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movie DB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movie DB.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.wirelessalien.android.moviedb.activity

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ActivityExportBinding
import com.wirelessalien.android.moviedb.helper.DirectoryHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.ByteBuffer

class ExportActivity : AppCompatActivity() {
    private lateinit var context: Context
    private lateinit var binding: ActivityExportBinding
    private var isJson: Boolean = false
    private var isCsv: Boolean = false

    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri: Uri? ->
        uri?.let {
            saveFileToUri(it, isJson, isCsv)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportBinding.inflate(
            layoutInflater
        )
        setContentView(binding.root)
        context = this
        Thread.setDefaultUncaughtExceptionHandler { thread: Thread?, throwable: Throwable ->
            val crashLog = StringWriter()
            val printWriter = PrintWriter(crashLog)
            throwable.printStackTrace(printWriter)
            val osVersion = Build.VERSION.RELEASE
            var appVersion = ""
            try {
                appVersion = applicationContext.packageManager.getPackageInfo(
                    applicationContext.packageName,
                    0
                ).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            printWriter.write("\nDevice OS Version: $osVersion")
            printWriter.write("\nApp Version: $appVersion")
            printWriter.close()
            try {
                val fileName = "Crash_Log.txt"
                val targetFile = File(applicationContext.filesDir, fileName)
                val fileOutputStream = FileOutputStream(targetFile, true)
                fileOutputStream.write((crashLog.toString() + "\n").toByteArray())
                fileOutputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            Process.killProcess(Process.myPid())
        }
        val exportDirectory = DirectoryHelper.getExportDirectory(context)
        if (exportDirectory != null) {
            binding.selectedDirectoryText.text =
                getString(R.string.directory_path) + exportDirectory.absolutePath
        }
        binding.exportButton.setOnClickListener {
            val databaseHelper = MovieDatabaseHelper(applicationContext)
            databaseHelper.exportDatabase(context)
        }
    }

    fun promptUserToSaveFile(fileName: String, isJson: Boolean, isCsv: Boolean) {
        this.isJson = isJson
        this.isCsv = isCsv
        createFileLauncher.launch(fileName)
    }


    private fun saveFileToUri(uri: Uri, isJson: Boolean, isCsv: Boolean) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                if (isJson) {
                    // Get the database instance
                    val databaseHelper = MovieDatabaseHelper(applicationContext)
                    val db = databaseHelper.readableDatabase
                    // Convert the database to JSON and write to the output stream
                    val json = MovieDatabaseHelper.jSONExport(db)
                    outputStream.write(json.toByteArray())
                } else if (isCsv) {
                    // Get the database instance
                    val databaseHelper = MovieDatabaseHelper(applicationContext)
                    val db = databaseHelper.readableDatabase
                    // Convert the database to CSV and write to the output stream
                    val csv = MovieDatabaseHelper.cSVExport(db)
                    outputStream.write(csv.toByteArray())
                } else {
                    // Save the database file as is
                    val data = Environment.getDataDirectory()
                    val currentDBPath = "/data/" + packageName + "/databases/" + MovieDatabaseHelper.databaseFileName
                    val currentDB = File(data, currentDBPath)
                    val fileChannel = FileInputStream(currentDB).channel
                    val buffer = ByteBuffer.allocate(fileChannel.size().toInt())
                    fileChannel.read(buffer)
                    buffer.flip()
                    val byteArray = ByteArray(buffer.remaining())
                    buffer[byteArray]
                    outputStream.write(byteArray)
                }
                outputStream.flush()
                Toast.makeText(this, getString(R.string.write_to_external_storage_as) + uri.path, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_saving_file), Toast.LENGTH_SHORT).show()
        }
    }
}
