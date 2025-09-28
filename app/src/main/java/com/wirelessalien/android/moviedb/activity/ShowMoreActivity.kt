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

package com.wirelessalien.android.moviedb.activity

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ShowPagingAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityShowMoreBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.pagingSource.ShowPagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ShowMoreActivity : BaseActivity() {

    private lateinit var binding: ActivityShowMoreBinding
    private lateinit var pagingAdapter: ShowPagingAdapter
    private var apiKey: String? = null
    private var listType: String? = null
    private lateinit var mShowGenreList: HashMap<String, String?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShowMoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        apiKey = ConfigHelper.getConfigValue(this, "api_key")
        listType = intent.getStringExtra(EXTRA_LIST_TYPE)

        setupToolbar()
        setupRecyclerView()
        loadData()
    }

    private fun setupToolbar() {
        binding.toolbar.title = getToolbarTitle()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun getToolbarTitle(): String {
        return when (listType) {
            "now_playing" -> getString(R.string.now_playing)
            "airing_today" -> getString(R.string.airing_today)
            "upcoming" -> getString(R.string.upcoming)
            "on_the_air" -> getString(R.string.on_the_air)
            else -> getString(R.string.app_name)
        }
    }

    private fun setupRecyclerView() {
        mShowGenreList = HashMap()
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        pagingAdapter = ShowPagingAdapter(
            mShowGenreList,
            preferences.getBoolean(SHOWS_LIST_PREFERENCE, false),
            false
        )
        binding.showRecyclerView.adapter = pagingAdapter

        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
            val gridLayoutManager = GridLayoutManager(
                this,
                preferences.getInt(GRID_SIZE_PREFERENCE, 3)
            )
            binding.showRecyclerView.layoutManager = gridLayoutManager
        } else {
            binding.showRecyclerView.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        }
    }

    private fun loadData() {
        val (listType, filterParameter) = getListAndFilter()
        if (listType.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid list type", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        fetchGenreList(listType)

        lifecycleScope.launch {
            Pager(PagingConfig(pageSize = 20)) {
                ShowPagingSource(listType, filterParameter, apiKey, this@ShowMoreActivity)
            }.flow.collectLatest { pagingData ->
                pagingAdapter.submitData(pagingData)
            }
        }

        pagingAdapter.addLoadStateListener { loadState ->
            when (loadState.source.refresh) {
                is LoadState.Loading -> {
                    binding.showRecyclerView.visibility = View.GONE
                    binding.shimmerFrameLayout.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.startShimmer()
                }
                is LoadState.NotLoading -> {
                    binding.showRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                }
                is LoadState.Error -> {
                    binding.showRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                    val errorMessage = (loadState.source.refresh as LoadState.Error).error.message
                    Toast.makeText(
                        this,
                        getString(R.string.error_loading_data) + ": " + errorMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun getListAndFilter(): Pair<String?, String> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val date = Date()
        val calendar: Calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val dateAfterWeek = calendar.time

        val type: String?
        val filter: String
        when (listType) {
            "now_playing" -> {
                type = "movie"
                filter = "movie/now_playing?page=1&" + getRegionParameter(this)
            }
            "airing_today" -> {
                type = "tv"
                filter = "discover/tv?page=1&sort_by=popularity.desc&" + getRegionParameter2(this) + "&with_watch_monetization_types=flatrate|free|ads|rent|buy&air_date.lte=" + dateFormat.format(date) + "&air_date.gte=" + dateFormat.format(date) + "&" + getTimeZoneParameter(this)
            }
            "upcoming" -> {
                type = "movie"
                filter = "movie/upcoming?page=1&" + getRegionParameter(this)
            }
            "on_the_air" -> {
                type = "tv"
                filter = "discover/tv?page=1&sort_by=popularity.desc&" + getRegionParameter2(this)  + "&with_watch_monetization_types=flatrate|free|ads|rent|buy&air_date.lte=" + dateFormat.format(dateAfterWeek) + "&air_date.gte=" + dateFormat.format(date) + "&" + getTimeZoneParameter(this)
            }
            else -> {
                type = null
                filter = ""
            }
        }
        return Pair(type, filter)
    }

    /**
     * Uses Coroutine to retrieve the id to genre mapping.
     */
    private fun fetchGenreList(mGenreType: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val response = fetchGenreListFromNetwork(mGenreType)
            handleResponse(response, mGenreType)
        }
    }

    private suspend fun fetchGenreListFromNetwork(mGenreType: String): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.themoviedb.org/3/genre/$mGenreType/list?api_key=$apiKey")
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

    private fun handleResponse(response: String?, mGenreType: String) {
        if (!response.isNullOrEmpty()) {
            val sharedPreferences = applicationContext
                .getSharedPreferences("GenreList", Context.MODE_PRIVATE)
            val prefsEditor = sharedPreferences.edit()
            try {
                val reader = JSONObject(response)
                val genreArray = reader.getJSONArray("genres")
                for (i in 0 until genreArray.length()) {
                    val websiteData = genreArray.getJSONObject(i)
                    mShowGenreList[websiteData.getString("id")] = websiteData.getString("name")
                    prefsEditor.putString(websiteData.getString("id"), websiteData.getString("name"))
                }
                prefsEditor.putString("${mGenreType}GenreJSONArrayList", genreArray.toString())
                prefsEditor.apply()

                pagingAdapter.notifyDataSetChanged()

            } catch (je: JSONException) {
                je.printStackTrace()
            }
        }
    }

    companion object {
        const val EXTRA_LIST_TYPE = "extra_list_type"
        const val SHOWS_LIST_PREFERENCE = "key_show_shows_grid"
        const val GRID_SIZE_PREFERENCE = "key_grid_size_number"
    }
}