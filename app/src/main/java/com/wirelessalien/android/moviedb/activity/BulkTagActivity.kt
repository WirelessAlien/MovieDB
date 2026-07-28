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

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.BulkTagMediaAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityBulkTagBinding
import com.wirelessalien.android.moviedb.databinding.DialogEditTagBinding
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BulkTagActivity : BaseActivity() {

    private lateinit var binding: ActivityBulkTagBinding
    private lateinit var databaseHelper: MovieDatabaseHelper
    private lateinit var adapter: BulkTagMediaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBulkTagBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.bulk_tag_management)

        databaseHelper = MovieDatabaseHelper(this)

        adapter = BulkTagMediaAdapter { count ->
            updateSelectionState(count)
        }
        
        binding.mediaRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.mediaRecyclerView.adapter = adapter

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s.toString())
            }
        })

        binding.addToTagButton.setOnClickListener { showTagPickerDialog(isAdd = true) }
        binding.removeFromTagButton.setOnClickListener { showTagPickerDialog(isAdd = false) }

        updateSelectionState(0)
        loadMedia()
    }

    private fun loadMedia() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mediaItems = databaseHelper.getAllMediaSimple()
            withContext(Dispatchers.Main) {
                adapter.setItems(mediaItems)
            }
        }
    }

    private fun updateSelectionState(count: Int) {
        binding.selectionCounter.text = getString(R.string.items_selected, count)
        val hasSelection = count > 0
        binding.addToTagButton.isEnabled = hasSelection
        binding.removeFromTagButton.isEnabled = hasSelection
    }

    private fun showTagPickerDialog(isAdd: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val allTags = databaseHelper.getAllTags()
            
            withContext(Dispatchers.Main) {
                if (allTags.isEmpty()) {
                    return@withContext
                }

                val tagNames = allTags.map { it.name }.toTypedArray()
                val selectedIndices = BooleanArray(allTags.size)

                MaterialAlertDialogBuilder(this@BulkTagActivity)
                    .setTitle(getString(R.string.select_tags))
                    .setMultiChoiceItems(tagNames, selectedIndices) { _, which, isChecked ->
                        selectedIndices[which] = isChecked
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val selectedTags = allTags.filterIndexed { index, _ -> selectedIndices[index] }
                        if (selectedTags.isNotEmpty()) {
                            performBulkOperation(selectedTags, isAdd)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun performBulkOperation(selectedTags: List<com.wirelessalien.android.moviedb.data.Tag>, isAdd: Boolean) {
        val selectedMedia = adapter.getSelectedItems()
        
        lifecycleScope.launch(Dispatchers.IO) {
            for (tag in selectedTags) {
                if (isAdd) {
                    databaseHelper.bulkAddTag(selectedMedia, tag.id)
                } else {
                    databaseHelper.bulkRemoveTag(selectedMedia, tag.id)
                }
            }
            
            withContext(Dispatchers.Main) {
                val messageId = if (isAdd) R.string.bulk_tag_success_add else R.string.bulk_tag_success_remove
                val message = getString(messageId, selectedMedia.size, selectedTags.size)
                
                Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
                
                adapter.deselectAll()
                loadMedia()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, getString(R.string.select_all)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, 2, 0, getString(R.string.deselect_all)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, 3, 0, getString(R.string.add_new_tag)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, 4, 0, getString(R.string.delete)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            1 -> {
                adapter.selectAll()
                true
            }
            2 -> {
                adapter.deselectAll()
                true
            }
            3 -> {
                val binding = DialogEditTagBinding.inflate(layoutInflater)

                binding.renameInputLayout.hint = getString(R.string.add_new_tag)

                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.add_new_tag))
                    .setView(binding.root)
                    .setPositiveButton(getString(R.string.save)) { _, _ ->
                        val newName = binding.renameInput.text.toString().trim()
                        if (newName.isNotEmpty()) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                databaseHelper.addTag(newName)
                            }
                        }
                    }
                    .show()

                true
            }
            4 -> {
                showDeleteTagsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteTagsDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allTags = databaseHelper.getAllTags()
            
            withContext(Dispatchers.Main) {
                if (allTags.isEmpty()) {
                    Snackbar.make(findViewById(android.R.id.content), "No tags to delete", Snackbar.LENGTH_SHORT).show()
                    return@withContext
                }

                val tagNames = allTags.map { it.name }.toTypedArray()
                val selectedIndices = BooleanArray(allTags.size)

                MaterialAlertDialogBuilder(this@BulkTagActivity)
                    .setTitle(getString(R.string.delete))
                    .setMultiChoiceItems(tagNames, selectedIndices) { _, which, isChecked ->
                        selectedIndices[which] = isChecked
                    }
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        val tagsToDelete = allTags.filterIndexed { index, _ -> selectedIndices[index] }
                        if (tagsToDelete.isNotEmpty()) {
                            MaterialAlertDialogBuilder(this@BulkTagActivity)
                                .setTitle(getString(R.string.delete))
                                .setMessage("Are you sure you want to delete the selected tags? This action is irreversible and deleted tags cannot be restored.")
                                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        tagsToDelete.forEach { tag ->
                                            databaseHelper.deleteTag(tag.id)
                                        }
                                        withContext(Dispatchers.Main) {
                                            Snackbar.make(findViewById(android.R.id.content), "Deleted ${tagsToDelete.size} tag(s)", Snackbar.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }
}
