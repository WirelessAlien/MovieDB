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
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.WebDavHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WebDavBackupWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val webDavPrefs = WebDavHelper.getEncryptedSharedPreferences(applicationContext)
        val isWebDavEnabled = webDavPrefs.getBoolean(WebDavHelper.KEY_WEBDAV_ENABLED, false)

        if (!isWebDavEnabled) {
            return Result.failure()
        }

        var url = webDavPrefs.getString(WebDavHelper.KEY_WEBDAV_URL, "") ?: ""
        val username = webDavPrefs.getString(WebDavHelper.KEY_WEBDAV_USERNAME, "") ?: ""
        val password = webDavPrefs.getString(WebDavHelper.KEY_WEBDAV_PASSWORD, "") ?: ""

        if (url.isEmpty()) {
            return Result.failure()
        }

        val backupFileType = inputData.getString("backupFileType") ?: "DB"
        val fileName = when (backupFileType) {
            "JSON" -> "movies.json"
            "CSV (Movies and Shows)" -> "movies.csv"
            "CSV (All data)" -> "movies_with_episodes.csv"
            else -> "movies.db"
        }

        if (url.endsWith("/")) {
            url += fileName
        } else if (!url.endsWith(".db", true) && !url.endsWith(".json", true) && !url.endsWith(".csv", true)) {
            url += "/$fileName"
        }

        val tempBackupFile = File(applicationContext.cacheDir, fileName)

        return try {
            FileOutputStream(tempBackupFile).use { output ->
                when (backupFileType) {
                    "DB" -> {
                        val currentDBPath = applicationContext.getDatabasePath(MovieDatabaseHelper.databaseFileName).absolutePath
                        FileInputStream(currentDBPath).use { input ->
                            input.copyTo(output)
                        }
                    }
                    "JSON" -> {
                        val databaseHelper = MovieDatabaseHelper(applicationContext)
                        val json = databaseHelper.readableDatabase.use { db ->
                            databaseHelper.getJSONExportString(db)
                        }
                        output.write(json.toByteArray())
                    }
                    "CSV (Movies and Shows)" -> {
                        val databaseHelper = MovieDatabaseHelper(applicationContext)
                        val csv = databaseHelper.readableDatabase.use { db ->
                            databaseHelper.getCSVExportString(db, true)
                        }
                        output.write(csv.toByteArray())
                    }
                    "CSV (All data)" -> {
                        val databaseHelper = MovieDatabaseHelper(applicationContext)
                        val csv = databaseHelper.readableDatabase.use { db ->
                            databaseHelper.getCSVExportString(db, false)
                        }
                        output.write(csv.toByteArray())
                    }
                    else -> {
                        val currentDBPath = applicationContext.getDatabasePath(MovieDatabaseHelper.databaseFileName).absolutePath
                        FileInputStream(currentDBPath).use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            val webDavSuccess = WebDavHelper.uploadFile(url, username, password, tempBackupFile)
            tempBackupFile.delete()

            if (webDavSuccess) {
                showNotification(applicationContext.getString(R.string.database_backup), applicationContext.getString(R.string.webdav_backup_successful), true)
                val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                prefs.edit().putLong("last_webdav_backup_time", System.currentTimeMillis()).apply()
                Result.success()
            } else {
                showNotification(applicationContext.getString(R.string.database_backup), applicationContext.getString(R.string.webdav_backup_failed))
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tempBackupFile.delete()
            showNotification(applicationContext.getString(R.string.database_backup), applicationContext.getString(R.string.webdav_backup_failed))
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
        notificationManager.notify(2, notification) 
    }
}
