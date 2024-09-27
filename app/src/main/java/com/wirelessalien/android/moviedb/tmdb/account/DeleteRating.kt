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
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class DeleteRating(
    private val movieId: Int,
    private val type: String,
    private val activity: Activity
) {
    private val accessToken: String?

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        accessToken = preferences.getString("access_token", "")
    }

    suspend fun deleteRating() {
        var success: Boolean
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.themoviedb.org/3/$type/$movieId/rating")
            .delete()
            .addHeader("accept", "application/json")
            .addHeader("Content-Type", "application/json;charset=utf-8")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body()!!.string()
                    val jsonResponse = JSONObject(responseBody)
                    val statusCode = jsonResponse.getInt("status_code")
                    success = statusCode == 13
                }
            }
            activity.runOnUiThread {
                if (success) {
                    Toast.makeText(activity, R.string.rating_delete_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, R.string.delete_rating_fail, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
}