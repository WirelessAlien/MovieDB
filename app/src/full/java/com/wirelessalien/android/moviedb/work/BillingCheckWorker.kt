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
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.data.PurchaseStatus
import com.wirelessalien.android.moviedb.helper.BillingHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class BillingCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        
        val billingHelper = BillingHelper(applicationContext, scope) { _, _ -> }
        
        try {
            val status = billingHelper.checkPurchasesSuspend()
            val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

            when (status) {
                PurchaseStatus.PURCHASED -> {
                    preferences.edit().apply {
                        putBoolean("user_has_active_purchase", true)
                        putBoolean("user_is_subscribed", true)
                        putLong("billing_error_timestamp", 0)
                        apply()
                    }
                }
                PurchaseStatus.NOT_PURCHASED -> {
                    preferences.edit().apply {
                        putBoolean("user_has_active_purchase", false)
                        putBoolean("user_is_subscribed", false)
                        putLong("billing_error_timestamp", 0)
                        apply()
                    }
                }
                PurchaseStatus.ERROR -> {
                    handleError(preferences)
                }
                else -> {
                    // Do nothing for PENDING to preserve last known state
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            handleError(preferences)
            return Result.retry()
        } finally {
            billingHelper.endConnection()
            scope.cancel()
        }

        return Result.success()
    }

    private fun handleError(preferences: android.content.SharedPreferences) {
        val errorTimestamp = preferences.getLong("billing_error_timestamp", 0)
        if (errorTimestamp == 0L) {
            preferences.edit { putLong("billing_error_timestamp", System.currentTimeMillis()) }
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - errorTimestamp > BillingHelper.GRACE_PERIOD_MILLIS) {
                preferences.edit().apply {
                    putBoolean("user_has_active_purchase", false)
                        putBoolean("user_is_subscribed", false)
                    putLong("billing_error_timestamp", 0)
                    apply()
                }
            }
        }
    }
}