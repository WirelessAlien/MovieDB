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

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.wirelessalien.android.moviedb.R
import java.io.File

object DirectoryHelper {

    fun getExportDirectory(context: Context): File? {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            val selectionArgs = arrayOf(Environment.DIRECTORY_DOCUMENTS + "/ShowCase", "ShowCase")
            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Files.getContentUri("external"), projection, selection, selectionArgs, null)

            if (cursor != null && cursor.moveToFirst()) {
                cursor.close()
                return File(Environment.getExternalStorageDirectory(), "Documents/ShowCase")
            }

            cursor?.close()

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "MovieDB")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/ShowCase")
                put(MediaStore.MediaColumns.MIME_TYPE, "vnd.android.document/directory")
            }
            try {
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                if (uri == null) {
                    Toast.makeText(context, R.string.failed_to_create_directory, Toast.LENGTH_SHORT).show()
                    return null
                }
            } catch (e: SQLiteConstraintException) {
                Log.e("DirectoryHelper", "Directory already exists: ${e.message}")
            }
            return File(Environment.getExternalStorageDirectory(), "Documents/ShowCase")
        } else {
            val exportDirectory = File(Environment.getExternalStorageDirectory(), "Documents/ShowCase")
            if (!exportDirectory.exists()) {
                if (!exportDirectory.mkdirs()) {
                    return null
                }
            }
            return exportDirectory
        }
    }


    //create image download directory
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun downloadImage(context: Context, imageUrl: String?, fileName: String) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(imageUrl)
            val request = DownloadManager.Request(uri)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val downloadId = downloadManager.enqueue(request)

            // Register receiver to listen for download completion
            val receiver: BroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        val query = DownloadManager.Query()
                        query.setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                                Toast.makeText(context, R.string.image_download_successful, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, R.string.failed_to_download_image, Toast.LENGTH_SHORT).show()
                            }
                        }
                        cursor.close()
                        context.unregisterReceiver(this)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.error) + e, Toast.LENGTH_SHORT).show()
        }
    }
}
