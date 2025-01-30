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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.wirelessalien.android.moviedb.adapter.ShowPagingAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityListItemBinding
import com.wirelessalien.android.moviedb.helper.CrashHelper
import com.wirelessalien.android.moviedb.pagingSource.MyListDetailsPagingSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ListItemActivityTmdb : AppCompatActivity() {

    private lateinit var adapter: ShowPagingAdapter
    private var listId = 0
    private var listName: String? = null
    lateinit var preferences: SharedPreferences
    private var SHOWS_LIST_PREFERENCE = "key_show_shows_grid"
    private var GRID_SIZE_PREFERENCE = "key_grid_size_number"
    private lateinit var binding: ActivityListItemBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListItemBinding.inflate(layoutInflater)
        setContentView(binding.root)
        CrashHelper.setDefaultUncaughtExceptionHandler(applicationContext)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, false)) {
            binding.recyclerView.layoutManager = GridLayoutManager(this, preferences.getInt(GRID_SIZE_PREFERENCE, 3))
        } else {
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
        }

        listId = intent.getIntExtra("listId", 0)
        listName = intent.getStringExtra("listName")
        preferences.edit().putInt("listId", listId).apply()

        binding.toolbar.title = listName

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val genreList = HashMap<String, String?>()
        adapter = ShowPagingAdapter(genreList, preferences.getBoolean(SHOWS_LIST_PREFERENCE, false), true)
        binding.recyclerView.adapter = adapter

        loadListDetails()
    }

    private fun loadListDetails() {
        val accessToken = preferences.getString("access_token", "") ?: ""
        val pagingSource = MyListDetailsPagingSource(listId, accessToken, this)

        val pager = Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { pagingSource }
        )

        lifecycleScope.launch {
            pager.flow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
    }
}