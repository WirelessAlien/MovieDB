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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import org.json.JSONException
import org.json.JSONObject

class EpisodeSavedAdapter(
    private val episodes: List<Int>,
    private val watchedEpisodes: List<Int>,
    private val tvShowId: Int,
    private val seasonNumber: Int,
    private val context: Context,
) : RecyclerView.Adapter<EpisodeSavedAdapter.EpisodeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.episode_trakt_item, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val episodeNumber = episodes[position]
        holder.episodeTextView.text = context.getString(R.string.episode_p, episodeNumber)

        // Check if the episode is watched
        val isWatched = watchedEpisodes.contains(episodeNumber)

        val iconRes = if (isWatched) {
            R.drawable.ic_visibility
        } else {
            R.drawable.ic_visibility_off
        }
        holder.episodeStatusButton.setImageResource(iconRes)

        MovieDatabaseHelper(context).use { db ->
            holder.episodeStatusButton.setOnClickListener {
                val currentlyWatched = watchedEpisodes.contains(episodeNumber)

                if (currentlyWatched) {
                    db.removeEpisodeNumber(tvShowId, seasonNumber, listOf(episodeNumber))
                    holder.episodeStatusButton.setImageResource(R.drawable.ic_visibility_off)
                } else {
                    db.addEpisodeNumber(tvShowId, seasonNumber, listOf(episodeNumber))
                    holder.episodeStatusButton.setImageResource(R.drawable.ic_visibility)
                }
            }
        }
    }

    override fun getItemCount(): Int = episodes.size

    class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val episodeTextView: TextView = itemView.findViewById(R.id.episodeTextView)
        val episodeStatusButton: ImageButton = itemView.findViewById(R.id.episodeStatusButton)
    }
}