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
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.R
import java.io.FileInputStream
import java.io.IOException

class DatabaseBackupWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val directoryUriString = inputData.getString("directoryUri") ?: return Result.failure()
        val directoryUri = Uri.parse(directoryUriString)
        val documentFile = DocumentFile.fromTreeUri(applicationContext, directoryUri)
        val databaseName = "movies.db"
        val existingFile = documentFile?.findFile(databaseName)

        return try {
            val currentDBPath = applicationContext.getDatabasePath(databaseName).absolutePath
            val inputStream = FileInputStream(currentDBPath)
            val outputStream = if (existingFile != null) {
                applicationContext.contentResolver.openOutputStream(existingFile.uri)
            } else {
                val newFile = documentFile?.createFile("application/octet-stream", databaseName)
                applicationContext.contentResolver.openOutputStream(newFile!!.uri)
            }

            inputStream.use { input ->
                outputStream?.use { output ->
                    input.copyTo(output)
                }
            }
            showNotification(applicationContext.getString(R.string.database_backup), applicationContext.getString(
                R.string.database_backup_successful
            ), true)
            Result.success()
        } catch (e: IOException) {
            e.printStackTrace()
            showNotification(applicationContext.getString(R.string.database_backup), applicationContext.getString(
                R.string.database_backup_failed
            ))
            Result.failure()
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