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
package com.wirelessalien.android.moviedb

import android.app.Application
import android.content.Context
import com.google.android.material.color.DynamicColors
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        fetchAndStoreGenres()
    }

    private fun fetchAndStoreGenres() {
        val sharedPreferences = applicationContext.getSharedPreferences("GenreList", Context.MODE_PRIVATE)
        val movieGenresFetched = sharedPreferences.contains("movieGenreListResponse")
        val tvGenresFetched = sharedPreferences.contains("tvGenreListResponse")

        if (!movieGenresFetched || !tvGenresFetched) {
            CoroutineScope(Dispatchers.IO).launch {
                if (!movieGenresFetched) {
                    fetchGenreList("movie")
                }
                if (!tvGenresFetched) {
                    fetchGenreList("tv")
                }
            }
        }
    }

    private suspend fun fetchGenreList(genreType: String) {
        val apiKey = ConfigHelper.getConfigValue(applicationContext, "api_key")
        val response = fetchGenreListFromNetwork(genreType, apiKey)
        handleResponse(response, genreType)
    }

    private suspend fun fetchGenreListFromNetwork(genreType: String, apiKey: String?): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.themoviedb.org/3/genre/$genreType/list?api_key=$apiKey")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun handleResponse(response: String?, genreType: String) {
        if (!response.isNullOrEmpty()) {
            val sharedPreferences = applicationContext.getSharedPreferences("GenreList", Context.MODE_PRIVATE)
            val prefsEditor = sharedPreferences.edit()
            try {
                val reader = JSONObject(response)
                val genreArray = reader.getJSONArray("genres")
                for (i in 0 until genreArray.length()) {
                    val websiteData = genreArray.getJSONObject(i)
                    prefsEditor.putString(websiteData.getString("id"), websiteData.getString("name"))
                }
                prefsEditor.putString("${genreType}GenreJSONArrayList", genreArray.toString())
                prefsEditor.putString("${genreType}GenreListResponse", response)
                prefsEditor.apply()
            } catch (je: JSONException) {
                je.printStackTrace()
            }
        }
    }
}