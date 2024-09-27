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

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.helper.CrashHelper
import com.wirelessalien.android.moviedb.tmdb.account.GetListDetails
import com.wirelessalien.android.moviedb.tmdb.account.GetListDetails.OnFetchListDetailsListener
import kotlinx.coroutines.launch
import org.json.JSONObject

class MyListDetailsActivity : AppCompatActivity(), OnFetchListDetailsListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ShowBaseAdapter
    private var listId = 0
    private var currentPage = 1
    private var isLoading = false
    private var mShowGenreList: HashMap<String, String?>? = null
    lateinit var preferences: SharedPreferences
    private var SHOWS_LIST_PREFERENCE = "key_show_shows_grid"
    private var GRID_SIZE_PREFERENCE = "key_grid_size_number"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_detail)

        CrashHelper.setDefaultUncaughtExceptionHandler(applicationContext)

        mShowGenreList = HashMap()
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        recyclerView = findViewById(R.id.recyclerView)

        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, false)) {
            recyclerView.layoutManager = GridLayoutManager(this, preferences.getInt(GRID_SIZE_PREFERENCE, 3))
        } else {
            recyclerView.layoutManager = LinearLayoutManager(this)
        }

        // Get the list ID from the intent
        listId = intent.getIntExtra("listId", 0)
        preferences.edit().putInt("listId", listId).apply()
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE
        loadListDetails(currentPage)

        adapter = ShowBaseAdapter(ArrayList(), mShowGenreList!!,  preferences.getBoolean(
            SHOWS_LIST_PREFERENCE, false), showDeleteButton = true)
        recyclerView.adapter = adapter

        // Add scroll listener for pagination
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager
                val totalItemCount = layoutManager?.itemCount ?: 0
                val lastVisibleItem = if (layoutManager is GridLayoutManager) {
                    layoutManager.findLastVisibleItemPosition()
                } else if (layoutManager is LinearLayoutManager) {
                    layoutManager.findLastVisibleItemPosition()
                } else {
                    0
                }

                if (!isLoading && totalItemCount <= (lastVisibleItem + 5)) {
                    currentPage++
                    loadListDetails(currentPage)
                    isLoading = true
                }
            }
        })
    }

    private fun loadListDetails(page: Int) {
        lifecycleScope.launch {
            val listDetailsCoroutineTMDb = GetListDetails(listId, this@MyListDetailsActivity, this@MyListDetailsActivity)
            listDetailsCoroutineTMDb.fetchListDetails(page)
        }
    }

    override fun onFetchListDetails(listDetailsData: ArrayList<JSONObject>?) {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.GONE
        if (listDetailsData != null) {
            adapter.addItems(listDetailsData)
        }
        isLoading = false
    }
}