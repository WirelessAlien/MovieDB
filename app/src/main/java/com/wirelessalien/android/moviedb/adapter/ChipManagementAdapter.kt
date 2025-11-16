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
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.ChipInfo
import java.util.Collections

class ChipManagementAdapter(
    private val chips: MutableList<ChipInfo>,
    private val onStartDragListener: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<ChipManagementAdapter.ChipViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chip_management, parent, false)
        return ChipViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChipViewHolder, position: Int) {
        holder.bind(chips[position])
    }

    override fun getItemCount(): Int = chips.size

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(chips, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(chips, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    inner class ChipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
        private val chipName: TextView = itemView.findViewById(R.id.chip_name)
        private val chipVisibility: CheckBox = itemView.findViewById(R.id.chip_visibility)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(chipInfo: ChipInfo) {
            chipName.text = chipInfo.name
            chipVisibility.isChecked = chipInfo.isVisible

            // "All" chip cannot be hidden
            if (chipInfo.tag == "all") {
                chipVisibility.isEnabled = false
            } else {
                chipVisibility.isEnabled = true
                chipVisibility.setOnCheckedChangeListener { _, isChecked ->
                    chipInfo.isVisible = isChecked
                }
            }

            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDragListener(this)
                }
                false
            }
        }
    }
}
