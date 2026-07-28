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

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.Tag
import java.util.Collections

class CustomizeTagsAdapter(
    private val tags: MutableList<Tag>,
    private val visibleTagIds: MutableSet<Long>,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<CustomizeTagsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
        val tagName: TextView = view.findViewById(R.id.tag_name)
        val visibilitySwitch: SwitchMaterial = view.findViewById(R.id.tag_visibility_switch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tag_customize, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tag = tags[position]
        holder.tagName.text = tag.name

        holder.visibilitySwitch.setOnCheckedChangeListener(null)
        holder.visibilitySwitch.isChecked = visibleTagIds.contains(tag.id)
        holder.visibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                visibleTagIds.add(tag.id)
            } else {
                visibleTagIds.remove(tag.id)
            }
        }

        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount() = tags.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        Collections.swap(tags, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }
}
