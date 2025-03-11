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
import android.os.Build
import androidx.preference.PreferenceManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.TraktSyncService
import com.wirelessalien.android.moviedb.helper.ConfigHelper

class WeeklyWorkerTkt(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        val accessToken = preferences.getString("trakt_access_token", null)
        val clientId = ConfigHelper.getConfigValue(applicationContext, "client_id")
        val tmdbApi = ConfigHelper.getConfigValue(applicationContext, "api_key")

        if (accessToken != null && clientId != null) {
            val intent = Intent(applicationContext, TraktSyncService::class.java).apply {
                action = TraktSyncService.ACTION_START_SERVICE
                putExtra(TraktSyncService.EXTRA_ACCESS_TOKEN, accessToken)
                putExtra(TraktSyncService.EXTRA_CLIENT_ID, clientId)
                putExtra(TraktSyncService.EXTRA_TMDB_API_KEY, tmdbApi)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        }

        return Result.success()
    }
}