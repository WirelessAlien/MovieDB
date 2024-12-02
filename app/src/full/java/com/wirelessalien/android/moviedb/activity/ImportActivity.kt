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
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.material.snackbar.Snackbar
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ActivityImportBinding
import com.wirelessalien.android.moviedb.helper.CrashHelper
import com.wirelessalien.android.moviedb.helper.GoogleCredSignIn
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.PeopleDatabaseHelper
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream


class ImportActivity : AppCompatActivity(), AdapterDataChangedListener {

    private lateinit var context: Context
    private lateinit var binding: ActivityImportBinding
    private lateinit var pickFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var authorizationLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var googleSignIn: GoogleCredSignIn
    private val serverClientId = "892148791583-lmjdmttoa7akrq1v7hnji8eb3p3h1ali.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CrashHelper.setDefaultUncaughtExceptionHandler(applicationContext)

        context = this
        binding.toolbar.title = getString(R.string.action_import)
        setSupportActionBar(binding.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)
        googleSignIn = GoogleCredSignIn(this, serverClientId)
        pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val archiveFileUri = result.data!!.data
                Toast.makeText(this, getString(R.string.file_picked_success), Toast.LENGTH_SHORT).show()
                try {
                    val inputStream = contentResolver.openInputStream(archiveFileUri!!)
                    val cacheFile = File(cacheDir, getArchiveFileName(archiveFileUri))
                    val fileOutputStream = FileOutputStream(cacheFile)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var length: Int
                    while (inputStream!!.read(buffer).also { length = it } > 0) {
                        fileOutputStream.write(buffer, 0, length)
                    }
                    fileOutputStream.close()
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } else {
                Toast.makeText(this, getString(R.string.file_picked_fail), Toast.LENGTH_SHORT).show()
            }
        }

        binding.pickFileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            pickFileLauncher.launch(intent)
        }

        binding.importMovieDbButton.setOnClickListener {
            val databaseHelper = MovieDatabaseHelper(applicationContext)
            databaseHelper.importDatabase(context, this)
        }

        binding.importPeopleDbButton.setOnClickListener {
            val databaseHelper = PeopleDatabaseHelper(applicationContext)
            databaseHelper.importDatabase(context, this)
        }

        authorizationLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val authorizationResult = Identity.getAuthorizationClient(this)
                    .getAuthorizationResultFromIntent(data)
                val accessToken = authorizationResult.accessToken
                if (accessToken != null) {
                    downloadFromDriveAppFolder(accessToken)
                }
            }
        }

        binding.importDriveDb.setOnClickListener {
            requestDrivePermissionsForDownload()
        }
    }

    private fun requestDrivePermissionsForDownload() {
        val requestedScopes = listOf(
            Scope(DriveScopes.DRIVE_APPDATA),
            Scope(DriveScopes.DRIVE_FILE)
        )
        val authorizationRequest = AuthorizationRequest.Builder()
            .setRequestedScopes(requestedScopes)
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    val pendingIntent = authorizationResult.pendingIntent
                    try {
                        if (pendingIntent != null) {
                            val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent).build()
                            authorizationLauncher.launch(intentSenderRequest)
                        }
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e("TAG", "Couldn't start Authorization UI: ${e.localizedMessage}")
                    }
                } else {
                    val accessToken = authorizationResult.accessToken
                    if (accessToken != null) {
                        downloadFromDriveAppFolder(accessToken)
                    } else {
                        googleSignIn.googleLogin {
                            requestDrivePermissionsForDownload()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("TAG", "Failed to authorize", e)
            }
    }

    private fun downloadFromDriveAppFolder(accessToken: String) {
        val credential = GoogleCredential.Builder()
            .setTransport(NetHttpTransport())
            .setJsonFactory(GsonFactory.getDefaultInstance())
            .build()
            .setAccessToken(accessToken)

        val driveService = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(getString(R.string.app_name))
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Search for the backup file in the app data folder
                val result = driveService.files().list()
                    .setQ("name = 'showcase_database_backup.db' and trashed = false and 'appDataFolder' in parents")
                    .setSpaces("appDataFolder")
                    .execute()
                val files = result.files

                if (files != null && files.isNotEmpty()) {
                    val fileId = files[0].id

                    // Download the file
                    val outputStream = FileOutputStream(File(cacheDir, "showcase_database_backup.db"))
                    val request = driveService.files().get(fileId)

                    // Set up a progress listener
                    request.mediaHttpDownloader.setProgressListener { downloader ->
                        val progress = (downloader.progress * 100).toInt()
                        CoroutineScope(Dispatchers.Main).launch {
                            binding.progressIndicator.visibility = View.VISIBLE
                            binding.progressIndicator.progress = progress
                        }
                    }

                    request.executeMediaAndDownloadTo(outputStream)
                    outputStream.close()

                    val dbFile = File(getDatabasePath(MovieDatabaseHelper.databaseFileName).absolutePath)
                    val inputStream: InputStream = File(cacheDir, "showcase_database_backup.db").inputStream()
                    val dbOutputStream = FileOutputStream(dbFile)

                    val totalSize = inputStream.available().toLong()
                    var bytesCopied: Long = 0
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        dbOutputStream.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        val progress = (bytesCopied * 100 / totalSize).toInt()
                        withContext(Dispatchers.Main) {
                            binding.progressIndicator.visibility = View.VISIBLE
                            binding.progressIndicator.progress = progress
                        }
                    }

                    inputStream.close()
                    dbOutputStream.close()

                    withContext(Dispatchers.Main) {
                        binding.progressIndicator.visibility = View.GONE
                        Snackbar.make(findViewById(android.R.id.content), getString(R.string.database_import_successful), Snackbar.LENGTH_INDEFINITE)
                            .setAction(getString(R.string.ok)) {
                                finishAffinity()
                                val intent = Intent(this@ImportActivity, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                            }
                            .show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.progressIndicator.visibility = View.GONE
                        Toast.makeText(this@ImportActivity, getString(R.string.file_not_found_exception), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressIndicator.visibility = View.GONE
                    Toast.makeText(this@ImportActivity, getString(R.string.could_not_import_backup, e.message), Toast.LENGTH_SHORT).show()
                }
            }
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

    override fun onAdapterDataChangedListener() {
        // Do nothing
    }
}
