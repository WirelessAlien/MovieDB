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
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityTaggedListBinding
import com.wirelessalien.android.moviedb.fragment.BaseFragment
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class TaggedListActivity : BaseActivity() {

    private lateinit var binding: ActivityTaggedListBinding
    private lateinit var databaseHelper: MovieDatabaseHelper
    private val selectedTagIds = mutableSetOf<Long>()
    private lateinit var adapter: ShowBaseAdapter
    private val genreList = HashMap<String, String?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaggedListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        databaseHelper = MovieDatabaseHelper(this)
        
        loadGenres()

        val initialTagId = intent.getLongExtra("tag_id", -1)
        val initialTagName = intent.getStringExtra("tag_name")

        if (initialTagId != -1L && initialTagName != null) {
            supportActionBar?.title = initialTagName
            addTagFilter(initialTagId, initialTagName)
        }

        setupRecyclerView()

        binding.addFilterChip.setOnClickListener {
            showAddFilterDialog()
        }
        
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadMovies()
        }
        
        loadMovies()
    }
    
    private fun loadGenres() {
        val sharedPreferences = getSharedPreferences("GenreList", Context.MODE_PRIVATE)
        val allEntries = sharedPreferences.all
        for ((key, value) in allEntries) {
            if (key != "tvGenreJSONArrayList" && key != "movieGenreJSONArrayList") {
                genreList[key] = value.toString()
            }
        }
    }

    private fun setupRecyclerView() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isGrid = preferences.getBoolean(BaseFragment.SHOWS_LIST_PREFERENCE, true)
        
        if (isGrid) {
            val gridSize = preferences.getInt(BaseFragment.GRID_SIZE_PREFERENCE, 3)
            binding.recyclerView.layoutManager = GridLayoutManager(this, gridSize)
        } else {
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
        }
        
        adapter = ShowBaseAdapter(this, ArrayList(), genreList, isGrid)
        binding.recyclerView.adapter = adapter
    }
    
    private fun addTagFilter(tagId: Long, tagName: String) {
        if (selectedTagIds.contains(tagId)) return
        selectedTagIds.add(tagId)
        
        val chip = LayoutInflater.from(this).inflate(R.layout.chip_item, binding.filterChipGroup, false) as Chip
        chip.text = tagName
        chip.isCloseIconVisible = true
        chip.setOnCloseIconClickListener {
            binding.filterChipGroup.removeView(chip)
            selectedTagIds.remove(tagId)
            loadMovies()
            if (selectedTagIds.isEmpty()) {
                finish()
            }
        }
        binding.filterChipGroup.addView(chip, binding.filterChipGroup.childCount - 1)
    }

    private fun showAddFilterDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allTags = databaseHelper.getAllTags()
            val availableTags = allTags.filter { !selectedTagIds.contains(it.id) }
            
            withContext(Dispatchers.Main) {
                if (availableTags.isEmpty()) {
                     return@withContext
                }
                val tagNames = availableTags.map { it.name }.toTypedArray()
                MaterialAlertDialogBuilder(this@TaggedListActivity)
                    .setTitle(getString(R.string.add_tag))
                    .setItems(tagNames) { _, which ->
                        val selectedTag = availableTags[which]
                        addTagFilter(selectedTag.id, selectedTag.name)
                        loadMovies()
                    }
                    .show()
            }
        }
    }

    private fun loadMovies() {
        binding.shimmerFrameLayout.visibility = View.VISIBLE
        binding.shimmerFrameLayout.startShimmer()
        
        lifecycleScope.launch(Dispatchers.IO) {
            val movies = databaseHelper.getMoviesForTags(selectedTagIds.toList())
            withContext(Dispatchers.Main) {
                adapter.updateData(ArrayList(movies))
                binding.shimmerFrameLayout.stopShimmer()
                binding.shimmerFrameLayout.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
                
                if (movies.isEmpty()) {
                    // show empty state, will add later
                }
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
