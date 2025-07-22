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

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.wirelessalien.android.moviedb.data.Release
import com.wirelessalien.android.moviedb.helper.ReleaseNotificationHelper
import com.wirelessalien.android.moviedb.helper.UpdateUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class UpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val release = getLatestRelease()
            if (release != null) {
                val installedVersion = UpdateUtils.getInstalledVersionName(applicationContext)
                if (UpdateUtils.isNewVersionAvailable(installedVersion, release.version)) {
                    ReleaseNotificationHelper(applicationContext).showUpdateNotification(release)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun getLatestRelease(): Release? {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.github.com/repos/WirelessAlien/MovieDB/releases/latest")
            .build()
        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val releaseInfo = Gson().fromJson(responseBody, GithubRelease::class.java)
                val assets = releaseInfo.assets
                val downloadUrl = assets.find { it.name.endsWith(".apk") && !it.name.contains("plus") }?.browserDownloadUrl
                val plusDownloadUrl = assets.find { it.name.endsWith("-plus.apk") }?.browserDownloadUrl
                if (downloadUrl != null && plusDownloadUrl != null) {
                    Release(releaseInfo.tagName.replace("v", ""), downloadUrl, plusDownloadUrl)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: IOException) {
            null
        }
    }

    private data class GithubRelease(
        val tagName: String,
        val assets: List<Asset>
    )

    private data class Asset(
        val name: String,
        val browserDownloadUrl: String
    )
}
