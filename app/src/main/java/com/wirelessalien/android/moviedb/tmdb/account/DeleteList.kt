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
package com.wirelessalien.android.moviedb.tmdb.account

import android.app.Activity
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class DeleteList(
    private val listId: Int,
    private val activity: Activity,
    private val onListDeletedListener: OnListDeletedListener
) {
    interface OnListDeletedListener {
        fun onListDeleted()
    }

    private val accessToken: String?

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        accessToken = preferences.getString("access_token", "")
    }

    suspend fun deleteList() {
        var success = false
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.themoviedb.org/4/list/$listId")
                .delete()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            val jsonResponse = withContext(Dispatchers.IO) {
                JSONObject(response.body()!!.string())
            }
            success = jsonResponse.getBoolean("success")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val finalSuccess = success
        activity.runOnUiThread {
            if (finalSuccess) {
                Toast.makeText(activity, R.string.list_delete_success, Toast.LENGTH_SHORT).show()
                onListDeletedListener.onListDeleted()
            } else {
                Toast.makeText(activity, R.string.failed_to_delete_list, Toast.LENGTH_SHORT).show()
            }
        }
    }
}