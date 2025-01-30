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

package com.wirelessalien.android.moviedb.pagingSource

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.wirelessalien.android.moviedb.activity.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URL

class PersonPagingSource(
    private val apiKey: String?,
    private val context: Context
) : PagingSource<Int, JSONObject>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, JSONObject> {
        return try {
            val page = params.key ?: 1
            val response = fetchPersonListFromApi(page)

            if (response.isNullOrEmpty()) {
                return LoadResult.Error(IOException("Failed to fetch data"))
            }

            val reader = JSONObject(response)
            val totalPages = reader.getInt("total_pages")
            val arrayData = reader.getJSONArray("results")
            val items = mutableListOf<JSONObject>()

            for (i in 0 until arrayData.length()) {
                val websiteData = arrayData.getJSONObject(i)
                items.add(websiteData)
            }

            LoadResult.Page(
                data = items,
                prevKey = if (page > 1) page - 1 else null,
                nextKey = if (page < totalPages) page + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, JSONObject>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    private suspend fun fetchPersonListFromApi(page: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.themoviedb.org/3/person/popular?page=$page&api_key=$apiKey${BaseActivity.getLanguageParameter(context)}")
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                client.newCall(request).execute().use { response ->
                    response.body?.string()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
    }
}