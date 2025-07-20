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
 *
 *     You should have received a copy of the GNU General Public License
 *     along with "ShowCase".  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.activity

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ExternalSearchAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityExternalSearchBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ExternalSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExternalSearchBinding
    private lateinit var searchAdapter: ExternalSearchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExternalSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        val intent = intent
        if (Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            if (query != null) {
                searchMulti(query)
            }
        } else if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                searchMulti(sharedText)
            }
        }
    }

    private fun searchMulti(query: String) {
        binding.shimmerFrameLayout1.startShimmer()
        binding.shimmerFrameLayout1.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val results = fetchSearchResults(query)
            withContext(Dispatchers.Main) {
                binding.shimmerFrameLayout1.stopShimmer()
                binding.shimmerFrameLayout1.visibility = View.GONE
                if (results != null) {
                    binding.recyclerView.visibility = View.VISIBLE
                    searchAdapter = ExternalSearchAdapter(results) { result ->
                        val mediaType = result.optString("media_type")
                        if (mediaType == "movie" || mediaType == "tv") {
                            val intent = Intent(this@ExternalSearchActivity, DetailActivity::class.java)
                            intent.putExtra("movieObject", result.toString())
                            intent.putExtra("isMovie", mediaType == "movie")
                            startActivity(intent)
                        }
                    }
                    binding.recyclerView.adapter = searchAdapter
                } else {
                    Toast.makeText(this@ExternalSearchActivity, getString(R.string.error_loading_data), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchSearchResults(query: String): JSONArray? {
        val client = OkHttpClient()
        val apiReadAccessToken = ConfigHelper.getConfigValue(this, "api_read_access_token")
        val url = "https://api.themoviedb.org/3/search/multi?query=$query"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("accept", "application/json")
            .addHeader("Authorization", "Bearer $apiReadAccessToken")
            .build()
        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonObject = JSONObject(response.body?.string().toString())
                jsonObject.getJSONArray("results")
            } else {
                null
            }
        } catch (e: IOException) {
            null
        }
    }
}
