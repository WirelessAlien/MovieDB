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
import android.util.Log
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

class MultiSearchPagingSource(
    private val apiKey: String,
    private val query: String,
    private val context: Context
) : PagingSource<Int, JSONObject>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, JSONObject> {
        return try {
            val page = params.key ?: 1
            val url = URL("https://api.themoviedb.org/3/search/multi?query=${query}&page=${page}" +
                    BaseActivity.getLanguageParameter2(context))

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            val responseBody = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(responseBody)
            val results = json.getJSONArray("results")

            Log.d("MultiSearchPagingSource", "load: $json")
            val items = mutableListOf<JSONObject>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                if (item.getString("media_type") != "person") {
                    items.add(item)
                    Log.d("MultiSearchPagingSource", "load: $item")
                }
            }

            val nextPage = if (page < json.getInt("total_pages")) page + 1 else null

            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = nextPage
            )
        } catch (e: Exception) {
            Log.d("MultiSearchPagingSource", "load: $e")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, JSONObject>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}