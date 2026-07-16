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

package com.wirelessalien.android.moviedb.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.MediaTagItem
import com.wirelessalien.android.moviedb.databinding.ItemBulkMediaBinding
import java.util.Locale

class BulkTagMediaAdapter(private val onSelectionChanged: (Int) -> Unit) :
    RecyclerView.Adapter<BulkTagMediaAdapter.ViewHolder>() {

    private var allItems = listOf<MediaTagItem>()
    private var displayedItems = listOf<MediaTagItem>()
    private val selectedIds = mutableSetOf<Pair<Int, Boolean>>()

    fun setItems(items: List<MediaTagItem>) {
        allItems = items
        displayedItems = items
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val lowerCaseQuery = query.lowercase(Locale.getDefault())
        displayedItems = if (query.isEmpty()) {
            allItems
        } else {
            allItems.filter { it.title.lowercase(Locale.getDefault()).contains(lowerCaseQuery) }
        }
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<Pair<Int, Boolean>> {
        return selectedIds.toList()
    }

    fun getSelectedCount(): Int {
        return selectedIds.size
    }

    fun selectAll() {
        displayedItems.forEach { selectedIds.add(Pair(it.id, it.isMovie)) }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun deselectAll() {
        displayedItems.forEach { selectedIds.remove(Pair(it.id, it.isMovie)) }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBulkMediaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = displayedItems[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = displayedItems.size

    inner class ViewHolder(private val binding: ItemBulkMediaBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaTagItem) {
            binding.titleTextView.text = item.title
            binding.yearTextView.text = if (item.releaseYear.length >= 4) item.releaseYear.substring(0, 4) else item.releaseYear
            
            val context = binding.root.context
            if (item.isMovie) {
                binding.typeBadge.text = context.getString(R.string.movie)
            } else {
                binding.typeBadge.text = context.getString(R.string.tv_shows)
            }

            binding.itemCheckbox.setOnCheckedChangeListener(null)
            binding.itemCheckbox.isChecked = selectedIds.contains(Pair(item.id, item.isMovie))

            val clickListener = View.OnClickListener {
                val pair = Pair(item.id, item.isMovie)
                val newCheckedState = !selectedIds.contains(pair)
                
                if (newCheckedState) {
                    selectedIds.add(pair)
                } else {
                    selectedIds.remove(pair)
                }
                
                binding.itemCheckbox.isChecked = newCheckedState
                onSelectionChanged(selectedIds.size)
            }
            
            binding.root.setOnClickListener(clickListener)
            binding.itemCheckbox.setOnClickListener(clickListener)
        }
    }
}
