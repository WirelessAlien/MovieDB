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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.Tag
import com.wirelessalien.android.moviedb.databinding.ItemTagFilterBinding

class FilterTagsAdapter(
    private val tags: List<Tag>,
    private val initialIncludedTagIds: Set<Long>,
    private val initialExcludedTagIds: Set<Long>
) : RecyclerView.Adapter<FilterTagsAdapter.ViewHolder>() {

    val selectedIncludedIds = initialIncludedTagIds.toMutableSet()
    val selectedExcludedIds = initialExcludedTagIds.toMutableSet()

    inner class ViewHolder(val binding: ItemTagFilterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTagFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tag = tags[position]
        holder.binding.tagName.text = tag.name

        holder.binding.filterRadioGroup.setOnCheckedChangeListener(null)
        if (selectedIncludedIds.contains(tag.id)) {
            holder.binding.radioInclude.isChecked = true
        } else if (selectedExcludedIds.contains(tag.id)) {
            holder.binding.radioExclude.isChecked = true
        } else {
            holder.binding.radioNone.isChecked = true
        }

        holder.binding.filterRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedIncludedIds.remove(tag.id)
            selectedExcludedIds.remove(tag.id)

            when (checkedId) {
                R.id.radio_include -> selectedIncludedIds.add(tag.id)
                R.id.radio_exclude -> selectedExcludedIds.add(tag.id)
            }
        }
    }

    override fun getItemCount() = tags.size
}
