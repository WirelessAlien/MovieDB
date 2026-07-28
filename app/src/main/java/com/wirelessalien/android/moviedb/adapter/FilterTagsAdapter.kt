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

class FilterTagsAdapter(
    private val tags: List<Tag>,
    private val initialIncludedTagIds: Set<Long>,
    private val initialExcludedTagIds: Set<Long>
) : RecyclerView.Adapter<FilterTagsAdapter.ViewHolder>() {

    val selectedIncludedIds = initialIncludedTagIds.toMutableSet()
    val selectedExcludedIds = initialExcludedTagIds.toMutableSet()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tagName: TextView = view.findViewById(R.id.tag_name)
        val radioGroup: RadioGroup = view.findViewById(R.id.filter_radio_group)
        val radioNone: RadioButton = view.findViewById(R.id.radio_none)
        val radioInclude: RadioButton = view.findViewById(R.id.radio_include)
        val radioExclude: RadioButton = view.findViewById(R.id.radio_exclude)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tag_filter, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tag = tags[position]
        holder.tagName.text = tag.name

        holder.radioGroup.setOnCheckedChangeListener(null)
        if (selectedIncludedIds.contains(tag.id)) {
            holder.radioInclude.isChecked = true
        } else if (selectedExcludedIds.contains(tag.id)) {
            holder.radioExclude.isChecked = true
        } else {
            holder.radioNone.isChecked = true
        }

        holder.radioGroup.setOnCheckedChangeListener { _, checkedId ->
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
