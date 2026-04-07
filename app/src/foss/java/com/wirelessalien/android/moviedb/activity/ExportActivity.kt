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
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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
import com.wirelessalien.android.moviedb.helper.WebDavHelper
import android.view.View
import com.wirelessalien.android.moviedb.work.DatabaseBackupWorker
import androidx.work.NetworkType
import com.wirelessalien.android.moviedb.work.WebDavBackupWorker
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
    private var isMovieOnly: Boolean = false
    private var backupDirectoryUri: Uri? = null
    private var exportDirectoryUri: Uri? = null
    private lateinit var preferences: SharedPreferences
    private val createFileLauncher = registerForActivityResult(object : ActivityResultContracts.CreateDocument("*/*") {
        override fun createIntent(context: Context, input: String): Intent {
            val intent = super.createIntent(context, input)
            val mimeType = when {
                input.endsWith(".json", true) -> "application/json"
                input.endsWith(".csv", true) -> "text/csv"
                input.endsWith(".db", true) -> "application/x-sqlite3"
                else -> "application/octet-stream"
            }
            intent.type = mimeType
            return intent
        }
    }) { uri: Uri? ->
        uri?.let {
            saveFileToUri(it, isJson, isCsv, isMovieOnly)
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
        
        val bannerLayout = findViewById<View>(R.id.export_info_banner)
        val btnUnderstand = findViewById<View>(R.id.btn_understand_info)
        if (preferences.getBoolean("export_info_understood", false)) {
            bannerLayout.visibility = View.GONE
        }
        btnUnderstand.setOnClickListener {
            preferences.edit().putBoolean("export_info_understood", true).apply()
            bannerLayout.visibility = View.GONE
        }

        val lastBackupTime = preferences.getLong("last_backup_time", 0)
        if (lastBackupTime > 0) {
            binding.lastBackupTime.visibility = android.view.View.VISIBLE
            binding.lastBackupTime.text = getString(R.string.last_backup, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastBackupTime)))
        }

        val isAutoBackupEnabled = preferences.getBoolean("auto_backup_enabled", false)
        binding.autoBackupSwitch.isChecked = isAutoBackupEnabled
        binding.backupBtn.isEnabled = isAutoBackupEnabled

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
        }

        binding.autoBackupSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("auto_backup_enabled", isChecked).apply()
            binding.backupBtn.isEnabled = isChecked

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

        if (!preferences.contains("backup_file_type")) {
            preferences.edit().putString("backup_file_type", "DB").commit()
        }

        val backupFileType = preferences.getString("backup_file_type", "DB")
        when (backupFileType) {
            "DB" -> binding.chipDb.isChecked = true
            "JSON" -> binding.chipJson.isChecked = true
            "CSV (Movies and Shows)" -> binding.chipCsvMovies.isChecked = true
            "CSV (All data)" -> binding.chipCsvAll.isChecked = true
            else -> binding.chipDb.isChecked = true
        }

        binding.backupFileTypeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val fileType = when (checkedIds.first()) {
                    R.id.chip_db -> "DB"
                    R.id.chip_json -> "JSON"
                    R.id.chip_csv_movies -> "CSV (Movies and Shows)"
                    R.id.chip_csv_all -> "CSV (All data)"
                    else -> "DB"
                }
                preferences.edit().putString("backup_file_type", fileType).apply()
                scheduleDatabaseExport()
            }
        }

        // Initialize Slider
        if (!preferences.contains("backup_frequency")) {
            preferences.edit().putInt("backup_frequency", 1440).commit()
        }
        val backupFrequency = preferences.getInt("backup_frequency", 1440)
        
        val sliderValue = when (backupFrequency) {
            15 -> 0.0f
            30 -> 1.0f
            60 -> 2.0f
            360 -> 3.0f
            720 -> 4.0f
            1440 -> 5.0f
            10080 -> 6.0f
            43200 -> 7.0f
            else -> 5.0f
        }
        binding.backupFrequencySlider.value = sliderValue
        updateFrequencyText(sliderValue)

        binding.backupFrequencySlider.addOnChangeListener { _, value, _ ->
            val frequencyInMinutes = when (value) {
                0.0f -> 15
                1.0f -> 30
                2.0f -> 60
                3.0f -> 360
                4.0f -> 720
                5.0f -> 1440
                6.0f -> 10080
                7.0f -> 43200
                else -> 1440
            }
            updateFrequencyText(value)
            preferences.edit().putInt("backup_frequency", frequencyInMinutes).apply()
            WebDavHelper.getEncryptedSharedPreferences(this).edit().putInt("backup_frequency_webdav", frequencyInMinutes).apply()
            scheduleDatabaseExport()
            
            if (binding.webDavEnabledSwitch.isChecked) {
                scheduleWebDavBackup()
            }
        }
        
        // WebDAV Settings
        val webDavPrefs = WebDavHelper.getEncryptedSharedPreferences(this)
        val isWebDavEnabled = webDavPrefs.getBoolean(WebDavHelper.KEY_WEBDAV_ENABLED, false)
        binding.webDavEnabledSwitch.isChecked = isWebDavEnabled
        binding.webDavSettingsLayout.visibility = if (isWebDavEnabled) View.VISIBLE else View.GONE

        binding.webDavUrlET.setText(webDavPrefs.getString(WebDavHelper.KEY_WEBDAV_URL, ""))
        binding.webDavUsernameET.setText(webDavPrefs.getString(WebDavHelper.KEY_WEBDAV_USERNAME, ""))
        binding.webDavPasswordET.setText(webDavPrefs.getString(WebDavHelper.KEY_WEBDAV_PASSWORD, ""))

        binding.webDavEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            webDavPrefs.edit().putBoolean(WebDavHelper.KEY_WEBDAV_ENABLED, isChecked).apply()
            binding.webDavSettingsLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                WorkManager.getInstance(this).cancelAllWorkByTag("WebDavBackupWorker")
            }
        }

        val isWebDavSaved = webDavPrefs.getString(WebDavHelper.KEY_WEBDAV_URL, "")!!.isNotEmpty()
        if (isWebDavSaved) {
            binding.webDavInputFields.visibility = View.GONE
            binding.webDavSaveButton.visibility = View.GONE
            binding.webDavTestConnectionButton.visibility = View.GONE
            binding.webDavEditButton.visibility = View.VISIBLE
        }

        binding.webDavSaveButton.setOnClickListener {
            val url = binding.webDavUrlET.text.toString().trim()
            val username = binding.webDavUsernameET.text.toString().trim()
            val password = binding.webDavPasswordET.text.toString().trim()
            webDavPrefs.edit()
                .putString(WebDavHelper.KEY_WEBDAV_URL, url)
                .putString(WebDavHelper.KEY_WEBDAV_USERNAME, username)
                .putString(WebDavHelper.KEY_WEBDAV_PASSWORD, password)
                .apply()
            
            if (binding.webDavEnabledSwitch.isChecked) {
                scheduleWebDavBackup()
            }
            Toast.makeText(this, getString(R.string.webdav_settings_saved), Toast.LENGTH_SHORT).show()
            
            binding.webDavInputFields.visibility = View.GONE
            binding.webDavSaveButton.visibility = View.GONE
            binding.webDavTestConnectionButton.visibility = View.GONE
            binding.webDavEditButton.visibility = View.VISIBLE
        }

        binding.webDavEditButton.setOnClickListener {
            authenticateWithBiometrics()
        }

        binding.webDavTestConnectionButton.setOnClickListener {
            val url = binding.webDavUrlET.text.toString().trim()
            val username = binding.webDavUsernameET.text.toString().trim()
            val password = binding.webDavPasswordET.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, getString(R.string.please_enter_webdav_url), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.webDavProgressIndicator.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                val success = WebDavHelper.testConnection(url, username, password)
                withContext(Dispatchers.Main) {
                    binding.webDavProgressIndicator.visibility = View.GONE
                    if (success) {
                        Toast.makeText(this@ExportActivity, getString(R.string.connection_successful), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ExportActivity, getString(R.string.connection_failed), Toast.LENGTH_SHORT).show()
                    }
                }
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

    private fun authenticateWithBiometrics() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, getString(R.string.auth_error, errString), Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    binding.webDavInputFields.visibility = View.VISIBLE
                    binding.webDavSaveButton.visibility = View.VISIBLE
                    binding.webDavTestConnectionButton.visibility = View.VISIBLE
                    binding.webDavEditButton.visibility = View.GONE
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.auth_required_title))
            .setSubtitle(getString(R.string.auth_required_subtitle))
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun updateFrequencyText(value: Float) {
        val predefinedValues = resources.getStringArray(R.array.backup_frequency_entries)
        val text = when (value) {
            0.0f -> predefinedValues[0]
            1.0f -> predefinedValues[1]
            2.0f -> predefinedValues[2]
            3.0f -> predefinedValues[3]
            4.0f -> predefinedValues[4]
            5.0f -> predefinedValues[5]
            6.0f -> predefinedValues[6]
            7.0f -> predefinedValues[7]
            else -> predefinedValues[5]
        }
        binding.backupFrequencyText.text = text
    }

    private fun scheduleWebDavBackup() {
        val webDavPrefs = WebDavHelper.getEncryptedSharedPreferences(this)
        val isWebDavEnabled = webDavPrefs.getBoolean(WebDavHelper.KEY_WEBDAV_ENABLED, false)

        if (isWebDavEnabled) {
            val backupFileType = preferences.getString("backup_file_type", "DB")
            val inputData = workDataOf("backupFileType" to backupFileType)
            val frequency = webDavPrefs.getInt("backup_frequency_webdav", 1440)

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val exportWorkRequest = PeriodicWorkRequestBuilder<WebDavBackupWorker>(frequency.toLong(), TimeUnit.MINUTES)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag("WebDavBackupWorker")
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "WebDavBackupWorker",
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                exportWorkRequest
            )
        }
    }

    private fun scheduleDatabaseExport() {
        scheduleWebDavBackup()
        
        val dbBackupDirectory = preferences.getString("db_backup_directory", null)
        if (dbBackupDirectory != null) {
            val directoryUri = Uri.parse(dbBackupDirectory)
            val backupFileType = preferences.getString("backup_file_type", "DB")
            val inputData = workDataOf(
                "directoryUri" to directoryUri.toString(),
                "backupFileType" to backupFileType
            )

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

    fun promptUserToSaveFile(fileName: String, isJson: Boolean, isCsv: Boolean, isMovieOnly: Boolean) {
        this.isJson = isJson
        this.isCsv = isCsv
        this.isMovieOnly = isMovieOnly
        createFileLauncher.launch(fileName)
    }

    private fun saveFileToUri(uri: Uri, isJson: Boolean, isCsv: Boolean, isMovieOnly: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    if (isJson) {
                        val databaseHelper = MovieDatabaseHelper(applicationContext)
                        val json = databaseHelper.readableDatabase.use { db ->
                            databaseHelper.getJSONExportString(db)
                        }
                        outputStream.write(json.toByteArray())
                    } else if (isCsv) {
                        val databaseHelper = MovieDatabaseHelper(applicationContext)
                        val csv = databaseHelper.readableDatabase.use { db ->
                            databaseHelper.getCSVExportString(db, isMovieOnly)
                        }
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
