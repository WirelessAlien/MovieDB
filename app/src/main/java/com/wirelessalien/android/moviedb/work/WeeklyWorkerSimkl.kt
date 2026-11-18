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
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.service.SimklSyncService
import com.wirelessalien.android.moviedb.simkl.GetSimklSyncData
import com.wirelessalien.android.moviedb.tmdb.GetTmdbDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeeklyWorkerSimkl(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val accessToken = preferences.getString("simkl_access_token", null)
        val clientId = ConfigHelper.getConfigValue(applicationContext, "simkl_client_id")
        val tmdbApi = ConfigHelper.getConfigValue(applicationContext, "api_key")

        if (!accessToken.isNullOrEmpty() && !clientId.isNullOrEmpty()) {
            try {
                val syncData = GetSimklSyncData(applicationContext, accessToken, clientId)
                syncData.fetchData(forceFullSync = false)

                val tmdbDetails = GetTmdbDetails(applicationContext, tmdbApi ?: "")
                tmdbDetails.fetchAndSaveSimklTmdbDetails { _, _ -> }

                val intent = Intent(SimklSyncService.ACTION_SERVICE_COMPLETED).apply {
                    setPackage(applicationContext.packageName)
                }
                applicationContext.sendBroadcast(intent)
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        } else {
            Result.success()
        }
    }
}
