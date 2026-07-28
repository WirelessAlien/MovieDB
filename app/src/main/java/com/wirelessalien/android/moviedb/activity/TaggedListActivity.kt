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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.CustomizeTagsAdapter
import com.wirelessalien.android.moviedb.adapter.FilterTagsAdapter
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.data.Tag
import com.wirelessalien.android.moviedb.databinding.ActivityTaggedListBinding
import com.wirelessalien.android.moviedb.fragment.BaseFragment
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaggedListActivity : BaseActivity() {

    private lateinit var binding: ActivityTaggedListBinding
    private lateinit var databaseHelper: MovieDatabaseHelper
    private var selectedTagIds = mutableSetOf<Long>()
    private var hiddenTagIds = mutableSetOf<Long>()
    private lateinit var adapter: ShowBaseAdapter
    private val genreList = HashMap<String, String?>()
    
    private var allTags: List<Tag> = emptyList()
    private var orderedTagIds: List<Long> = emptyList()
    private var visibleTagIds: MutableSet<Long> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaggedListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.tags)

        databaseHelper = MovieDatabaseHelper(this)
        
        loadGenres()
        setupRecyclerView()

        val initialTagId = intent.getLongExtra("tag_id", -1)
        val hideTagIdsArray = intent.getLongArrayExtra("hide_tag_ids")

        savedInstanceState?.let { bundle ->
            val savedSelected = bundle.getLongArray("selected_tag_ids")
            val savedHidden = bundle.getLongArray("hidden_tag_ids")
            
            savedSelected?.forEach { selectedTagIds.add(it) }
            savedHidden?.forEach { hiddenTagIds.add(it) }
        } ?: run {
             if (initialTagId != -1L) {
                 selectedTagIds.add(initialTagId)
             }
             hideTagIdsArray?.forEach { hiddenTagIds.add(it) }
        }

        binding.customizeTagsButton.setOnClickListener {
            showCustomizeTagsDialog()
        }

        binding.filterFab.setOnClickListener {
            showFilterTagsDialog()
        }
        
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadMovies()
        }

        loadTagsAndChips()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLongArray("selected_tag_ids", selectedTagIds.toLongArray())
        outState.putLongArray("hidden_tag_ids", hiddenTagIds.toLongArray())
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
            val gridSize = preferences.getInt(BaseFragment.GRID_SIZE_PREFERENCE, 3).coerceAtLeast(1)
            binding.recyclerView.layoutManager = GridLayoutManager(this, gridSize)
        } else {
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
        }
        
        adapter = ShowBaseAdapter(this, ArrayList(), genreList, isGrid)
        binding.recyclerView.adapter = adapter
    }

    private fun loadTagsAndChips() {
        lifecycleScope.launch(Dispatchers.IO) {
            allTags = databaseHelper.getAllTags()
            
            val prefs = getSharedPreferences("TagPreferences", Context.MODE_PRIVATE)
            val savedOrderStr = prefs.getString("tag_order", null)
            val savedVisibleStr = prefs.getString("tag_visible", null)
            
            val existingIds = allTags.map { it.id }.toSet()
            
            val orderedIds = mutableListOf<Long>()
            if (savedOrderStr != null) {
                val ids = savedOrderStr.split(",").mapNotNull { it.toLongOrNull() }
                orderedIds.addAll(ids.filter { existingIds.contains(it) })
            }
            
            val missingIds = existingIds - orderedIds.toSet()
            orderedIds.addAll(missingIds)
            orderedTagIds = orderedIds

            val visibleIds = mutableSetOf<Long>()
            if (savedVisibleStr != null) {
                val ids = savedVisibleStr.split(",").mapNotNull { it.toLongOrNull() }
                visibleIds.addAll(ids.filter { existingIds.contains(it) })
            } else {
                visibleIds.addAll(existingIds)
            }
            
            visibleIds.addAll(missingIds)
            visibleTagIds = visibleIds

            if (selectedTagIds.isEmpty() && visibleTagIds.isNotEmpty()) {
                orderedTagIds.firstOrNull { visibleTagIds.contains(it) }?.let {
                    selectedTagIds.add(it)
                }
            }
            
            withContext(Dispatchers.Main) {
                buildTagChips()
                loadMovies()
            }
        }
    }
    
    private fun buildTagChips() {
        binding.tagsChipGroup.removeAllViews()
        
        for (tagId in orderedTagIds) {
            if (!visibleTagIds.contains(tagId)) continue
            
            val tag = allTags.find { it.id == tagId } ?: continue
            
            val chip = Chip(this)
            chip.text = tag.name
            chip.isCheckable = true
            
            updateChipAppearance(chip, tagId)
            
            chip.setOnClickListener {
                if (selectedTagIds.size == 1 && selectedTagIds.contains(tagId) && hiddenTagIds.isEmpty()) {
                    return@setOnClickListener
                }
                
                selectedTagIds.clear()
                hiddenTagIds.clear()
                selectedTagIds.add(tagId)
                
                refreshChipsAppearance()
                loadMovies()
            }
            
            chip.setOnLongClickListener {
                if (selectedTagIds.contains(tagId)) {
                    selectedTagIds.remove(tagId)
                    hiddenTagIds.add(tagId)
                } else if (hiddenTagIds.contains(tagId)) {
                    hiddenTagIds.remove(tagId)
                } else {
                    selectedTagIds.add(tagId)
                }
                
                updateChipAppearance(chip, tagId)
                loadMovies()
                true
            }
            
            binding.tagsChipGroup.addView(chip)
        }
    }
    
    private fun updateChipAppearance(chip: Chip, tagId: Long) {
        when {
            selectedTagIds.contains(tagId) -> {
                chip.isChecked = true
                chip.chipBackgroundColor = ContextCompat.getColorStateList(this, R.color.md_theme_primary)
                chip.setTextColor(ContextCompat.getColor(this, R.color.md_theme_onPrimary))
                chip.chipIcon = null
            }
            hiddenTagIds.contains(tagId) -> {
                chip.isChecked = false
                chip.chipBackgroundColor = ContextCompat.getColorStateList(this, R.color.md_theme_errorContainer)
                chip.setTextColor(ContextCompat.getColor(this, R.color.md_theme_onErrorContainer))
                chip.chipIcon = ContextCompat.getDrawable(this, R.drawable.ic_minus)
            }
            else -> {
                chip.isChecked = false
                chip.chipBackgroundColor = ContextCompat.getColorStateList(this, R.color.md_theme_surfaceVariant)
                chip.setTextColor(ContextCompat.getColor(this, R.color.md_theme_onSurfaceVariant))
                chip.chipIcon = null
            }
        }
    }
    
    private fun refreshChipsAppearance() {
        for (i in 0 until binding.tagsChipGroup.childCount) {
            val chip = binding.tagsChipGroup.getChildAt(i) as? Chip ?: continue
            val tag = allTags.find { it.name == chip.text.toString() } ?: continue
            updateChipAppearance(chip, tag.id)
        }
    }

    private fun showCustomizeTagsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_customize_tags, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.customize_tags_recycler_view)
        
        val mutableTags = orderedTagIds.mapNotNull { id -> allTags.find { it.id == id } }.toMutableList()
        val tempVisibleIds = visibleTagIds.toMutableSet()
        
        var touchHelper: ItemTouchHelper? = null
        
        val customizeAdapter = CustomizeTagsAdapter(mutableTags, tempVisibleIds) { viewHolder ->
            touchHelper?.startDrag(viewHolder)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = customizeAdapter
        
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                customizeAdapter.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }
        
        touchHelper = ItemTouchHelper(itemTouchHelperCallback)
        touchHelper.attachToRecyclerView(recyclerView)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.customize_tags))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                orderedTagIds = mutableTags.map { it.id }
                visibleTagIds = tempVisibleIds
                
                val prefs = getSharedPreferences("TagPreferences", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("tag_order", orderedTagIds.joinToString(","))
                    .putString("tag_visible", visibleTagIds.joinToString(","))
                    .apply()
                    
                buildTagChips()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showFilterTagsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filter_tags, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.filter_tags_recycler_view)
        
        val filterAdapter = FilterTagsAdapter(allTags, selectedTagIds, hiddenTagIds)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = filterAdapter
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.filter_tags))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                selectedTagIds = filterAdapter.selectedIncludedIds
                hiddenTagIds = filterAdapter.selectedExcludedIds
                
                refreshChipsAppearance()
                loadMovies()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun loadMovies() {
        binding.shimmerFrameLayout.visibility = View.VISIBLE
        binding.shimmerFrameLayout.startShimmer()
        
        lifecycleScope.launch(Dispatchers.IO) {
            val movies = databaseHelper.getMoviesForTags(selectedTagIds.toList(), hiddenTagIds.toList())
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
