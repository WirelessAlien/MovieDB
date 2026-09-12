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
import com.wirelessalien.android.moviedb.databinding.ItemWrappedPosterBinding
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.tmdb.GetMovieImage

class WrappedPosterAdapter(
    private val titleIds: List<Pair<Int, Boolean>>,
    private val isGridMode: Boolean = false
) : RecyclerView.Adapter<WrappedPosterAdapter.PosterViewHolder>() {

    inner class PosterViewHolder(val binding: ItemWrappedPosterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterViewHolder {
        val binding = ItemWrappedPosterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PosterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PosterViewHolder, position: Int) {
        val item = titleIds[position]
        val context = holder.itemView.context
        
        val type = if (item.second) "movie" else "tv"
        val apiKey = ConfigHelper.getConfigValue(context, "tmdb_api_key")
        
        GetMovieImage(item.first, type, apiKey).fetchMovieImages("posters") { images ->
            if (images.isNotEmpty()) {
                val posterPath = images[0].getFilePath()
                val url = "https://image.tmdb.org/t/p/w342$posterPath"
                
                Picasso.get()
                    .load(url)
                    .into(holder.binding.ivPoster)
            }
        }
    }

    override fun getItemCount(): Int = titleIds.size
}
