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

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ActivityUpdateBinding
import com.wirelessalien.android.moviedb.helper.UpdateUtils
import java.io.File

class UpdateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateBinding
    private lateinit var dialog: androidx.appcompat.app.AlertDialog
    private var downloadId: Long = -1

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (bytesTotal > 0) {
                    val progress = (bytesDownloaded * 100L / bytesTotal).toInt()
                    binding.downloadProgress.progress = progress
                }

                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED -> {
                        handler.removeCallbacks(this)
                        cursor.close()
                        return
                    }
                }
            }
            cursor?.close()
            handler.postDelayed(this, 500)
        }
    }

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == id) {
                binding.downloadProgress.visibility = View.GONE
                binding.installButton.visibility = View.VISIBLE
                Toast.makeText(this@UpdateActivity, getString(R.string.download_completed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityUpdateBinding.inflate(LayoutInflater.from(this))
        dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        val release = intent.getStringExtra("release")
        val downloadUrl = intent.getStringExtra("downloadUrl")
        val plusDownloadUrl = intent.getStringExtra("plusDownloadUrl")
        val changelog = intent.getStringExtra("changelog")

        binding.updateVersion.text = "v$release"
        binding.updateChangelog.text = changelog

        binding.downloadButton.setOnClickListener {
            val installedVersion = UpdateUtils.getInstalledVersionName(this)
            val url = if (installedVersion.endsWith("-full")) plusDownloadUrl else downloadUrl
            if (url != null) {
                downloadApk(url)
            }
        }

        binding.installButton.setOnClickListener {
            installApk()
        }

        binding.closeButton.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        } else {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onDownloadComplete)
        handler.removeCallbacks(progressRunnable)
    }

    private fun downloadApk(url: String) {
        binding.downloadButton.visibility = View.GONE
        binding.downloadProgress.visibility = View.VISIBLE
        binding.downloadProgress.progress = 0

        val fileName = "showcase-app-update.apk"
        val destination = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(getString(R.string.app_name) + getString(R.string.update))
            .setDescription(getString(R.string.downloading_update))
            .setDestinationUri(Uri.fromFile(destination))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        handler.post(progressRunnable)
    }

    private fun installApk() {
        val fileName = "showcase-app-update.apk"
        val destination = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        val uri = FileProvider.getUriForFile(this, applicationContext.packageName + ".provider", destination)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.failed_to_open_installer), Toast.LENGTH_SHORT).show()
        }
    }
}

