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
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.databinding.ShowCardBinding
import com.wirelessalien.android.moviedb.databinding.ShowGridCardBinding
import org.json.JSONObject

class CollectionAdapter(
    private val context: Context,
    private val movies: List<JSONObject>,
    private val genreList: HashMap<String, String?>,
    private val gridView: Boolean
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_LIST = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (gridView) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GRID) {
            val binding =
                ShowGridCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            GridViewHolder(binding)
        } else {
            val binding =
                ShowCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ListViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val movie = movies[position]
        if (holder is GridViewHolder) {
            holder.binding.title.text = movie.optString("title")
            Picasso.get()
                .load("https://image.tmdb.org/t/p/w500${movie.optString("poster_path")}")
                .into(holder.binding.image)
            holder.itemView.setOnClickListener {
                val intent = Intent(context, DetailActivity::class.java)
                intent.putExtra("movieObject", movie.toString())
                intent.putExtra("isMovie", true)
                context.startActivity(intent)
            }
        } else if (holder is ListViewHolder) {
            holder.binding.title.text = movie.optString("title")
            holder.binding.description.text = movie.optString("overview")
            var genreIds = movie.optString("genre_ids")
            genreIds = if (!genreIds.isNullOrEmpty() && genreIds.length > 2) {
                genreIds.substring(1, genreIds.length - 1)
            } else {
                ""
            }
            val genreArray = genreIds.split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
            val sharedPreferences = context.getSharedPreferences("GenreList", Context.MODE_PRIVATE)
            val genreNames = StringBuilder()
            for (aGenreArray in genreArray) {
                if (genreList[aGenreArray] != null) {
                    genreNames.append(", ").append(genreList[aGenreArray])
                } else {
                    genreNames.append(", ").append(sharedPreferences.getString(aGenreArray, ""))
                }
            }
            holder.binding.genre.text = if (genreNames.isNotEmpty()) genreNames.substring(2) else ""
            Picasso.get()
                .load("https://image.tmdb.org/t/p/w500${movie.optString("poster_path")}")
                .into(holder.binding.image)
            holder.itemView.setOnClickListener {
                val intent = Intent(context, DetailActivity::class.java)
                intent.putExtra("movieObject", movie.toString())
                intent.putExtra("isMovie", true)
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int {
        return movies.size
    }

    class GridViewHolder(val binding: ShowGridCardBinding) : RecyclerView.ViewHolder(binding.root)
    class ListViewHolder(val binding: ShowCardBinding) : RecyclerView.ViewHolder(binding.root)
}
