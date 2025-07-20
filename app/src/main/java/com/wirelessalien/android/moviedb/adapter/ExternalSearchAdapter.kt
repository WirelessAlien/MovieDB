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
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.databinding.ItemExternalSearchBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class ExternalSearchAdapter(
    results: JSONArray,
    private val onItemClick: (JSONObject) -> Unit
) : RecyclerView.Adapter<ExternalSearchAdapter.ViewHolder>() {

    private val filteredResults = JSONArray()

    init {
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            if (result.optString("media_type") != "person") {
                filteredResults.put(result)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExternalSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = filteredResults.getJSONObject(position)
        holder.bind(result)
        holder.itemView.setOnClickListener { onItemClick(result) }
    }

    override fun getItemCount(): Int = filteredResults.length()

    class ViewHolder(private val binding: ItemExternalSearchBinding) : RecyclerView.ViewHolder(binding.root) {
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
}
