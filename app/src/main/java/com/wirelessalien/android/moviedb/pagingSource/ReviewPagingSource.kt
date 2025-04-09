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

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ReviewPagingSource(private val movieId: Int, private val type: String, private val apiRead: String) : PagingSource<Int, JSONObject>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, JSONObject> {
        return try {
            val page = params.key ?: 1
            val response = fetchReviewsFromApi(movieId, type, page)

            if (response.isNullOrEmpty()) {
                return LoadResult.Error(Exception("Failed to fetch data"))
            }

            val jsonResponse = JSONObject(response)
            val results = jsonResponse.getJSONArray("results")
            val totalPages = jsonResponse.getInt("total_pages")

            val reviews = mutableListOf<JSONObject>()
            for (i in 0 until results.length()) {
                reviews.add(results.getJSONObject(i))
            }

            LoadResult.Page(
                data = reviews,
                prevKey = if (page > 1) page - 1 else null,
                nextKey = if (page < totalPages) page + 1 else null
            )
        } catch (e: Exception) {
            Log.e("ReviewPagingSource", "Error loading reviews", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, JSONObject>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    private suspend fun fetchReviewsFromApi(movieId: Int, type: String, page: Int): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.themoviedb.org/3/$type/$movieId/reviews?page=$page")
                .get()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer $apiRead")
                .build()
            client.newCall(request).execute().use { response ->
                response.body?.string()
            }
        }
    }
}