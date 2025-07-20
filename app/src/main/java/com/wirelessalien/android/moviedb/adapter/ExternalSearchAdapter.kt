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

import android.icu.text.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.databinding.ItemExternalSearchBinding
import com.wirelessalien.android.moviedb.databinding.ShowGridCardBinding
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class ExternalSearchAdapter(
    private var gridView: Boolean,
    private val onItemClick: (JSONObject) -> Unit
) : PagingDataAdapter<JSONObject, RecyclerView.ViewHolder>(diffCallback) {

    companion object {
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_LIST = 2

        private val diffCallback = object : DiffUtil.ItemCallback<JSONObject>() {
            override fun areItemsTheSame(oldItem: JSONObject, newItem: JSONObject): Boolean {
                return oldItem.optInt("id") == newItem.optInt("id")
            }

            override fun areContentsTheSame(oldItem: JSONObject, newItem: JSONObject): Boolean {
                return oldItem.toString() == newItem.toString()
            }
        }
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
                ItemExternalSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ListViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val result = getItem(position)
        if (result != null) {
            when (holder) {
                is GridViewHolder -> {
                    holder.bind(result)
                    holder.itemView.setOnClickListener { onItemClick(result) }
                }
                is ListViewHolder -> {
                    holder.bind(result)
                    holder.itemView.setOnClickListener { onItemClick(result) }
                }
            }
        }
    }

    class ListViewHolder(private val binding: ItemExternalSearchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(result: JSONObject) {
            val mediaType = result.optString("media_type")
            val title: String
            val posterPath: String

            when (mediaType) {
                "movie" -> {
                    title = result.optString("title")
                    posterPath = result.optString("poster_path")
                }
                "tv" -> {
                    title = result.optString("name")
                    posterPath = result.optString("poster_path")
                }
                else -> {
                    title = "Unknown"
                    posterPath = ""
                }
            }

            binding.title.text = title
            binding.description.text = result.optString("overview")
            binding.rating.rating = result.optDouble("vote_average").toFloat() / 2

            val releaseDate = when (mediaType) {
                "movie" -> result.optString("release_date")
                "tv" -> result.optString("first_air_date")
                else -> ""
            }

            if (releaseDate.isNotEmpty()) {
                val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val defaultDateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                binding.date.text = try {
                    val parsedDate = apiDateFormat.parse(releaseDate)
                    defaultDateFormat.format(parsedDate)
                } catch (e: Exception) {
                    releaseDate
                }
            }

            if (posterPath.isNotEmpty()) {
                Picasso.get()
                    .load("https://image.tmdb.org/t/p/w500$posterPath")
                    .into(binding.image)
            }
        }
    }

    class GridViewHolder(private val binding: ShowGridCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(result: JSONObject) {
            val mediaType = result.optString("media_type")
            val title: String
            val posterPath: String

            when (mediaType) {
                "movie" -> {
                    title = result.optString("title")
                    posterPath = result.optString("poster_path")
                }
                "tv" -> {
                    title = result.optString("name")
                    posterPath = result.optString("poster_path")
                }
                else -> {
                    title = "Unknown"
                    posterPath = ""
                }
            }

            binding.title.text = title

            val releaseDate = when (mediaType) {
                "movie" -> result.optString("release_date")
                "tv" -> result.optString("first_air_date")
                else -> ""
            }

            if (releaseDate.isNotEmpty()) {
                val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val defaultDateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                binding.date.text = try {
                    val parsedDate = apiDateFormat.parse(releaseDate)
                    defaultDateFormat.format(parsedDate)
                } catch (e: Exception) {
                    releaseDate
                }
            }

            if (posterPath.isNotEmpty()) {
                Picasso.get()
                    .load("https://image.tmdb.org/t/p/w500$posterPath")
                    .into(binding.image)
            }
        }
    }
}
