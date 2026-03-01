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
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.EpisodeGroup

class EpisodeGroupAdapter(
    private val groups: List<EpisodeGroup>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<EpisodeGroupAdapter.ViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(group: EpisodeGroup)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val groupName: TextView = view.findViewById(R.id.groupName)
        val groupDescription: TextView = view.findViewById(R.id.groupDescription)
        val groupDetails: TextView = view.findViewById(R.id.groupDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]
        holder.groupName.text = group.name
        holder.groupDescription.text = group.description
        if (group.description.isEmpty()) {
            holder.groupDescription.visibility = View.GONE
        } else {
            holder.groupDescription.visibility = View.VISIBLE
        }
        
        var detailsText = "${group.episodeCount} Episodes"
        if (group.groupCount > 0) {
            detailsText += " • ${group.groupCount} Groups"
        }
        if (!group.network.isNullOrEmpty()) {
            detailsText += " • ${group.network}"
        }
        holder.groupDetails.text = detailsText

        holder.itemView.setOnClickListener {
            listener.onItemClick(group)
        }
    }

    override fun getItemCount(): Int = groups.size
}
