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
package com.wirelessalien.android.moviedb.tmdb

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.adapter.MovieImageAdapter
import com.wirelessalien.android.moviedb.data.MovieImage
import com.wirelessalien.android.moviedb.helper.ConfigHelper.getConfigValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

class GetMovieImage(
    private val movieId: Int,
    private val type: String,
    private val context: Context,
    private val recyclerView: RecyclerView
) {
    private val apiKey: String? = getConfigValue(context, "api_read_access_token")

    fun fetchMovieImages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val locale = Locale.getDefault().language
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.themoviedb.org/3/$type/$movieId/images?language=$locale&include_image_language=en,null")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body()!!.string()
                val jsonObject = JSONObject(responseBody)
                val jsonArray = jsonObject.getJSONArray("backdrops")
                val movieImages: MutableList<MovieImage> = ArrayList()
                for (i in 0 until jsonArray.length()) {
                    val imageObject = jsonArray.getJSONObject(i)
                    movieImages.add(MovieImage(imageObject.getString("file_path")))
                }
                withContext(Dispatchers.Main) {
                    val adapter = MovieImageAdapter(context, movieImages)
                    recyclerView.adapter = adapter
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}