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

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
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
import com.wirelessalien.android.moviedb.databinding.ActivityExternalSearchBinding
import com.wirelessalien.android.moviedb.fragment.BaseFragment
import com.wirelessalien.android.moviedb.helper.ThemeHelper
import com.wirelessalien.android.moviedb.viewmodel.ExternalSearchViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ExternalSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExternalSearchBinding
    private lateinit var searchAdapter: ExternalSearchAdapter
    private var query: String? = null
    private lateinit var preferences: SharedPreferences
    private val viewModel: ExternalSearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyAmoledTheme(this)

        super.onCreate(savedInstanceState)
        binding = ActivityExternalSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_refresh, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                query?.let { search(it) }
                true
            }

            R.id.action_open_app -> {
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = ExternalSearchAdapter(preferences.getBoolean(BaseFragment.SHOWS_LIST_PREFERENCE, false)) { result ->
            val mediaType = result.optString("media_type")
            if (mediaType == "movie" || mediaType == "tv") {
                val intent = Intent(this@ExternalSearchActivity, DetailActivity::class.java)
                intent.putExtra("movieObject", result.toString())
                intent.putExtra("isMovie", mediaType == "movie")
                startActivity(intent)
            }
        }
        binding.recyclerView.apply {
            layoutManager = if (preferences.getBoolean(BaseFragment.SHOWS_LIST_PREFERENCE, false)) GridLayoutManager(this@ExternalSearchActivity, preferences.getInt(
                BaseFragment.GRID_SIZE_PREFERENCE, 3)) else LinearLayoutManager(this@ExternalSearchActivity)
            adapter = searchAdapter
        }
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_PROCESS_TEXT == intent.action && "text/plain" == intent.type) {
            query = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            query?.let {
                search(it)
            }
        } else if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            query = intent.getStringExtra(Intent.EXTRA_TEXT)
            query?.let {
                search(it)
            }
        }
    }

    private fun search(query: String) {
        lifecycleScope.launch {
            viewModel.search(query, this@ExternalSearchActivity).collectLatest {
                searchAdapter.submitData(it)
            }
        }

        searchAdapter.addLoadStateListener { loadState ->
            when (loadState.source.refresh) {
                is LoadState.Loading -> {
                    binding.recyclerView.visibility = View.GONE
                    binding.shimmerFrameLayout1.visibility = View.VISIBLE
                    binding.shimmerFrameLayout1.startShimmer()
                }

                is LoadState.NotLoading -> {
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout1.stopShimmer()
                    binding.shimmerFrameLayout1.visibility = View.GONE
                }

                is LoadState.Error -> {
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout1.stopShimmer()
                    binding.shimmerFrameLayout1.visibility = View.GONE
                    val errorMessage = (loadState.source.refresh as LoadState.Error).error.message
                    Toast.makeText(this, getString(R.string.error_loading_data) + ": " + errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
