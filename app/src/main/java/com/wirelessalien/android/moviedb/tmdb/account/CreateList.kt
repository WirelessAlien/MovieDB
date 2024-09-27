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
import android.content.Context
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class CreateList(
    private val listName: String,
    private val description: String,
    private val isPublic: Boolean,
    private val context: Context?
) {
    private val accessToken: String?

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context!!)
        accessToken = preferences.getString("access_token", "")
    }

    suspend fun createList() {
        var success = false
        try {
            val client = OkHttpClient()
            val mediaType = MediaType.parse("application/json")
            val jsonParam = JSONObject().apply {
                put("name", listName)
                put("description", description)
                put("iso_3166_1", "US")
                put("iso_639_1", "en")
                put("public", isPublic)
            }
            val body = RequestBody.create(mediaType, jsonParam.toString())
            val request = Request.Builder()
                .url("https://api.themoviedb.org/4/list")
                .post(body)
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            val jsonResponse = JSONObject(response.body()!!.string())
            success = jsonResponse.getBoolean("success")
            if (success) {
                ListDatabaseHelper(context).addList(jsonResponse.getInt("id"), listName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val finalSuccess = success
        if (context is Activity) {
            context.runOnUiThread {
                if (finalSuccess) {
                    Toast.makeText(context, R.string.list_created_successfully, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.failed_to_create_list, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}