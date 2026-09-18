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

package com.wirelessalien.android.moviedb.work

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import kotlinx.coroutines.tasks.await
import java.io.File

class GoogleDriveBackupWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val accessToken = requestDriveAccessToken() ?: return Result.failure()


        val credential = GoogleCredential.Builder()
            .setTransport(NetHttpTransport())
            .setJsonFactory(GsonFactory.getDefaultInstance())
            .build()
            .setAccessToken(accessToken)

        val driveService = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(applicationContext.getString(R.string.app_name))
            .build()

        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val backupFileType = prefs.getString("backup_file_type", "DB")
        val isZip = backupFileType == "ZIP (Complete App Data)" || backupFileType == "ZIP"

        val fileName = if (isZip) "showcase_app_data_backup.zip" else "showcase_database_backup.db"
        val mimeType = if (isZip) "application/zip" else "application/octet-stream"

        var tempZipFile: File? = null
        return try {
            val uploadFile = if (isZip) {
                tempZipFile = File(applicationContext.cacheDir, "showcase_app_data_backup.zip")
                java.io.FileOutputStream(tempZipFile).use { output ->
                    com.wirelessalien.android.moviedb.helper.AppDataZipHelper.exportAppDataToZip(applicationContext, output)
                }
                tempZipFile
            } else {
                File(applicationContext.getDatabasePath(MovieDatabaseHelper.databaseFileName).absolutePath)
            }

            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = fileName
            val mediaContent = FileContent(mimeType, uploadFile)

            // Create a notification builder
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(applicationContext, "db_backup_channel")
                .setContentTitle(applicationContext.getString(R.string.database_backup))
                .setContentText(applicationContext.getString(R.string.upload_in_progress))
                .setSmallIcon(R.drawable.ic_notification)
                .setProgress(100, 0, false)
            notificationManager.notify(1, builder.build())

            // Search for the existing file in the app data folder
            val result = driveService.files().list()
                .setQ("name = '$fileName' and trashed = false and 'appDataFolder' in parents")
                .setSpaces("appDataFolder")
                .execute()
            val files = result.files

            val request = if (files != null && files.isNotEmpty()) {
                val fileId = files[0].id
                driveService.files().update(fileId, fileMetadata, mediaContent)
                    .setAddParents("appDataFolder")
            } else {
                fileMetadata.parents = listOf("appDataFolder")
                driveService.files().create(fileMetadata, mediaContent)
            }

            // Set up a progress listener
            request.mediaHttpUploader.setProgressListener { uploader ->
                val progress = (uploader.progress * 100).toInt()
                builder.setProgress(100, progress, false)
                notificationManager.notify(1, builder.build())
            }

            request.execute()

            // Upload complete
            builder.setContentText(applicationContext.getString(R.string.database_backup_successful))
                .setProgress(0, 0, false)
            notificationManager.notify(1, builder.build())
            prefs.edit().putLong("last_backup_time_drive", System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            showNotification(applicationContext.getString(R.string.database_backup), applicationContext.getString(R.string.database_backup_failed))
            Result.failure()
        } finally {
            tempZipFile?.delete()
        }
    }

    private suspend fun requestDriveAccessToken(): String? {
        return try {
            val requestedScopes = listOf(Scope(DriveScopes.DRIVE_FILE))
            val authorizationRequest = AuthorizationRequest.Builder()
                .setRequestedScopes(requestedScopes)
                .build()

            val authorizationResult = Identity.getAuthorizationClient(applicationContext)
                .authorize(authorizationRequest)
                .await()

            authorizationResult.accessToken
        } catch (e: Exception) {
            Log.e("GoogleDriveBackupWorker", "Failed to authorize", e)
            showNotification(applicationContext.getString(R.string.database_backup), applicationContext.getString(R.string.drive_access_failed))
            null
        }
    }

    private fun showNotification(title: String, message: String, autoDismiss: Boolean = false) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(applicationContext, "db_backup_channel")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)

        if (autoDismiss) {
            builder.setTimeoutAfter(5000)
        }

        val notification = builder.build()
        notificationManager.notify(1, notification)
    }
}