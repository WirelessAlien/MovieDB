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

import android.content.Context
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class UpdateList(
    private val listId: Int,
    private val name: String,
    private val description: String,
    private val isPublic: Boolean,
    private val context: Context,
    private val listener: OnListUpdatedListener
) {
    private val accessToken: String?

    interface OnListUpdatedListener {
        fun onListUpdated()
    }

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        accessToken = preferences.getString("api_read_access_token", "")
    }

    suspend fun updateList() {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val json = JSONObject()
                json.put("name", name)
                json.put("description", description)
                json.put("public", isPublic)
                val body = json.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("https://api.themoviedb.org/4/list/$listId")
                    .put(body)
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        listener.onListUpdated()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
