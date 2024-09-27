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
import com.wirelessalien.android.moviedb.data.ListData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


class FetchList(
    context: Context?,
    private val listener: OnListFetchListener?
) {
    private val accessToken: String?
    private val accountId: String?

    interface OnListFetchListener {
        fun onListFetch(listData: List<ListData>?)
    }

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context!!)
        accessToken = preferences.getString("access_token", "")
        accountId = preferences.getString("account_id", "")
    }

    suspend fun fetchLists(): List<ListData>? {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.themoviedb.org/4/account/$accountId/lists")
                .get()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            val responseBody = withContext(Dispatchers.IO) {
                response.body()?.string()
            }
            val jsonResponse = JSONObject(responseBody!!)
            val results = jsonResponse.getJSONArray("results")
            val listData: MutableList<ListData> = ArrayList()
            for (i in 0 until results.length()) {
                val result = results.getJSONObject(i)
                listData.add(
                    ListData(
                        result.getInt("id"),
                        result.getString("name"),
                        result.getString("description"),
                        result.getInt("number_of_items"),
                        result.getDouble("average_rating")
                    )
                )
            }
            listener?.onListFetch(listData)
            listData
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}