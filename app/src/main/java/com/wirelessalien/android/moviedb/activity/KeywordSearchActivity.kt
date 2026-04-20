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

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ExternalSearchAdapter
import com.wirelessalien.android.moviedb.adapter.ShowPagingAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityKeywordSearchBinding
import com.wirelessalien.android.moviedb.fragment.BaseFragment
import com.wirelessalien.android.moviedb.helper.ThemeHelper
import com.wirelessalien.android.moviedb.viewmodel.KeywordSearchViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class KeywordSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeywordSearchBinding
    private lateinit var searchAdapter: ShowPagingAdapter
    private var keywordId: Int = -1
    private var keywordName: String? = null
    private lateinit var preferences: SharedPreferences
    private val viewModel: KeywordSearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyAmoledTheme(this)

        super.onCreate(savedInstanceState)
        binding = ActivityKeywordSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        
        keywordId = intent.getIntExtra("keywordId", -1)
        keywordName = intent.getStringExtra("keywordName")

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = keywordName

        binding.appBar.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(this)

        setupRecyclerView()
        setupChips()
        
        if (keywordId != -1) {
            search("movie")
        }
    }

    private fun setupChips() {
        binding.chipMovie.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                search("movie")
            }
        }
        binding.chipShow.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                search("tv")
            }
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = ShowPagingAdapter(HashMap(), preferences.getBoolean(BaseFragment.SHOWS_LIST_PREFERENCE, false), false)
        binding.recyclerView.apply {
            layoutManager = if (preferences.getBoolean(BaseFragment.SHOWS_LIST_PREFERENCE, false)) GridLayoutManager(this@KeywordSearchActivity, preferences.getInt(
                BaseFragment.GRID_SIZE_PREFERENCE, 3).coerceAtLeast(1)) else LinearLayoutManager(this@KeywordSearchActivity)
            adapter = searchAdapter
        }
    }

    private fun search(mediaType: String) {
        lifecycleScope.launch {
            viewModel.search(keywordId, mediaType, this@KeywordSearchActivity).collectLatest {
                searchAdapter.submitData(it)
            }
        }

        searchAdapter.addLoadStateListener { loadState ->
            when (loadState.source.refresh) {
                is LoadState.Loading -> {
                    binding.recyclerView.visibility = View.GONE
                    binding.shimmerFrameLayout.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.startShimmer()
                }

                is LoadState.NotLoading -> {
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.stopShimmer()
                    binding.shimmerFrameLayout.visibility = View.GONE
                }

                is LoadState.Error -> {
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.stopShimmer()
                    binding.shimmerFrameLayout.visibility = View.GONE
                    val errorMessage = (loadState.source.refresh as LoadState.Error).error.message
                    Toast.makeText(this, getString(R.string.error_loading_data) + ": " + errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}