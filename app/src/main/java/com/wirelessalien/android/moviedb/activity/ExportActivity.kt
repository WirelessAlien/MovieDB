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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.appbar.MaterialToolbar
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ActivityExportBinding
import com.wirelessalien.android.moviedb.helper.CrashHelper
import com.wirelessalien.android.moviedb.helper.DirectoryHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.work.DatabaseBackupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class ExportActivity : AppCompatActivity() {
    private lateinit var context: Context
    private lateinit var binding: ActivityExportBinding
    private var isJson: Boolean = false
    private var isCsv: Boolean = false
    private var backupDirectoryUri: Uri? = null
    private var exportDirectoryUri: Uri? = null
    private lateinit var preferences: SharedPreferences
    private val predefinedValues = arrayOf(
        "15 minutes", "30 minutes", "1 hour", "6 hours", "12 hours", "24 hours", "1 week", "1 month"
    )
    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri: Uri? ->
        uri?.let {
            saveFileToUri(it, isJson, isCsv)
        }
    }
    private val openBackupDirectoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            backupDirectoryUri = it
            preferences.edit().putString("db_backup_directory", it.toString()).commit()
            binding.backupBtn.icon = AppCompatResources.getDrawable(this, R.drawable.ic_check)
            binding.backupBtn.text = getString(R.string.backup_directory_selected)
            scheduleDatabaseExport()
        }
    }
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

        val isAutoBackupEnabled = preferences.getBoolean("auto_backup_enabled", false)
        binding.autoBackupSwitch.isChecked = isAutoBackupEnabled
        binding.backupBtn.isEnabled = isAutoBackupEnabled
        binding.backupFrequencyET.isEnabled = isAutoBackupEnabled

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
            val databaseHelper = MovieDatabaseHelper(applicationContext)
            databaseHelper.exportDatabase(context, exportDirUri)
        }

        binding.backupBtn.setOnClickListener {
            openBackupDirectoryLauncher.launch(null)
        }

        binding.editIcon.setOnClickListener {
            openExportDirectoryLauncher.launch(null)
        }

        val backupDirectoryUri = preferences.getString("db_backup_directory", null)

        if (backupDirectoryUri != null) {
            binding.backupBtn.icon = AppCompatResources.getDrawable(this, R.drawable.ic_check)
            binding.backupBtn.text = getString(R.string.backup_directory_selected)
            scheduleDatabaseExport()
        }

        binding.autoBackupSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("auto_backup_enabled", isChecked).apply()
            binding.backupBtn.isEnabled = isChecked
            binding.backupFrequencyET.isEnabled = isChecked

            if (isChecked) {
                if (backupDirectoryUri != null) {
                    binding.backupBtn.icon = AppCompatResources.getDrawable(this, R.drawable.ic_check)
                }
                scheduleDatabaseExport()
            } else {
                preferences.edit().remove("db_backup_directory").apply()
                binding.backupBtn.icon = null
                binding.backupBtn.text = getString(R.string.auto_backup_directory_selection)
                WorkManager.getInstance(this).cancelAllWorkByTag("DatabaseBackupWorker")
            }
        }

        // Check if backup frequency is set, if not set it to default 1440 minutes
        if (!preferences.contains("backup_frequency")) {
            preferences.edit().putInt("backup_frequency", 1440).commit()
        }

        val backupFrequency = preferences.getInt("backup_frequency", 1440)
        val timeUnitString = convertMinutesToLargestUnit(backupFrequency)
        binding.backupFrequencyET.setText(timeUnitString)

        binding.backupFrequencyET.setOnTouchListener { _, _ ->
            binding.backupFrequencyET.showDropDown()
            false
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, predefinedValues)
        binding.backupFrequencyET.setAdapter(adapter)

        binding.backupFrequencyET.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.backupFrequencyET.clearFocus()
                saveBackupFrequency()

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.backupFrequencyET.windowToken, 0)

                // Get the current frequency value and convert it
                val frequencyInMinutes = preferences.getInt("backup_frequency", 1440)
                val convertedValue = convertMinutesToLargestUnit(frequencyInMinutes)
                binding.backupFrequencyET.setText(convertedValue)

                true
            } else {
                false
            }
        }

        createNotificationChannel()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun convertMinutesToLargestUnit(minutes: Int): String {
        val months = minutes / 43200
        val remainingMinutesAfterMonths = minutes % 43200
        val days = remainingMinutesAfterMonths / 1440
        val remainingMinutesAfterDays = remainingMinutesAfterMonths % 1440
        val hours = remainingMinutesAfterDays / 60
        val remainingMinutes = remainingMinutesAfterDays % 60

        return buildString {
            if (months > 0) append("$months month(s) ")
            if (days > 0) append("$days day(s) ")
            if (hours > 0) append("$hours hour(s) ")
            if (remainingMinutes > 0) append("$remainingMinutes minute(s)")
        }.trim()
    }

    private fun saveBackupFrequency() {
        val frequencyInMinutes = when (val frequencyText = binding.backupFrequencyET.text.toString()) {
            "15 minutes" -> 15
            "30 minutes" -> 30
            "1 hour" -> 60
            "6 hours" -> 360
            "12 hours" -> 720
            "24 hours" -> 1440
            "1 week" -> 10080
            "1 month" -> 43200
            else -> frequencyText.toIntOrNull()
        }

        if (frequencyInMinutes == null || frequencyInMinutes < 15) {
            binding.backupFrequencyET.error = getString(R.string.backup_fequency_error_text)
            binding.backupFrequencyET.requestFocus()
            return
        }

        preferences.edit().putInt("backup_frequency", frequencyInMinutes).apply()

        // Restart the worker with the new frequency
        val dbBackupDirectory = preferences.getString("db_backup_directory", null)
        if (dbBackupDirectory != null) {
            val directoryUri = Uri.parse(dbBackupDirectory)
            val inputData = workDataOf("directoryUri" to directoryUri.toString())

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()


            val exportWorkRequest = PeriodicWorkRequestBuilder<DatabaseBackupWorker>(frequencyInMinutes.toLong(), TimeUnit.MINUTES)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag("DatabaseBackupWorker")
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "DatabaseBackupWorker",
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                exportWorkRequest
            )

        } else {
            Log.e("ExportActivity", "saveBackupFrequency: No backup directory selected")
        }
    }

    private fun scheduleDatabaseExport() {
        val dbBackupDirectory = preferences.getString("db_backup_directory", null)
        if (dbBackupDirectory != null) {
            val directoryUri = Uri.parse(dbBackupDirectory)
            val inputData = workDataOf("directoryUri" to directoryUri.toString())

            val frequency = preferences.getInt("backup_frequency", 1440)

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val exportWorkRequest = PeriodicWorkRequestBuilder<DatabaseBackupWorker>(frequency.toLong(), TimeUnit.MINUTES)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag("DatabaseBackupWorker")
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "DatabaseBackupWorker",
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                exportWorkRequest
            )

        } else {
            Log.e(  "ExportActivity", "scheduleDatabaseExport: No backup directory selected")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.export_channel_name)
            val descriptionText = getString(R.string.export_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("db_backup_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun promptUserToSaveFile(fileName: String, isJson: Boolean, isCsv: Boolean) {
        this.isJson = isJson
        this.isCsv = isCsv
        createFileLauncher.launch(fileName)
    }

    private fun saveFileToUri(uri: Uri, isJson: Boolean, isCsv: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    if (isJson) {
                        val databaseHelper = MovieDatabaseHelper(applicationContext)
                        val db = databaseHelper.readableDatabase
                        val json = MovieDatabaseHelper.jSONExport(db)
                        outputStream.write(json.toByteArray())
                    } else if (isCsv) {
                        val databaseHelper = MovieDatabaseHelper(applicationContext)
                        val db = databaseHelper.readableDatabase
                        val csv = MovieDatabaseHelper.cSVExport(db)
                        outputStream.write(csv.toByteArray())
                    } else {
                        val dynamicPath = context.getDatabasePath(MovieDatabaseHelper.databaseFileName).absolutePath
                        val currentDB = File(dynamicPath)

                        if (!currentDB.exists()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ExportActivity, getString(R.string.database_not_found), Toast.LENGTH_SHORT).show()
                            }
                        }

                        val fileChannel = FileInputStream(currentDB).channel
                        val buffer = ByteBuffer.allocate(fileChannel.size().toInt())
                        fileChannel.read(buffer)
                        buffer.flip()
                        val byteArray = ByteArray(buffer.remaining())
                        buffer[byteArray]
                        outputStream.write(byteArray)
                    }
                    outputStream.flush()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ExportActivity, getString(R.string.write_to_external_storage_as) + uri.path, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExportActivity, getString(R.string.error_saving_file), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
