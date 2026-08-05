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
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.databinding.ItemFixDateBinding
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper

class FixDatesAdapter(private val badDates: List<MovieDatabaseHelper.BadDateItem>) :
    RecyclerView.Adapter<FixDatesAdapter.ViewHolder>() {

    val checkedStates = BooleanArray(badDates.size) { true }

inner class ViewHolder(val binding: ItemFixDateBinding) : RecyclerView.ViewHolder(binding.root) {
    init {
        binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                checkedStates[pos] = isChecked
            }
        }
    }
}
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFixDateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = badDates[position]
        holder.binding.checkbox.isChecked = checkedStates[position]
        
        var titleText = item.title
        if (item.season != null && item.episode != null) {
            titleText += " - S${item.season} E${item.episode}"
        }
        holder.binding.titleTextView.text = titleText
        holder.binding.columnTextView.text = "Field: ${item.column}"
        holder.binding.originalDateTextView.text = item.originalDate
        holder.binding.suggestedDateTextView.text = item.suggestedDate
    }

    override fun getItemCount() = badDates.size

    fun getSelectedItems(): List<MovieDatabaseHelper.BadDateItem> {
        return badDates.filterIndexed { index, _ -> checkedStates[index] }
    }
}
