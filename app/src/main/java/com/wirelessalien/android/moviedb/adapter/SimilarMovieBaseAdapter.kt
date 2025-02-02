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
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.databinding.MovieCardBinding
import org.json.JSONException
import org.json.JSONObject

class SimilarMovieBaseAdapter(
    private val similarMovieList: ArrayList<JSONObject>,
    private val context: Context
) : RecyclerView.Adapter<SimilarMovieBaseAdapter.MovieItemViewHolder>() {

    override fun getItemCount(): Int {
        return similarMovieList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieItemViewHolder {
        val binding = MovieCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MovieItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MovieItemViewHolder, position: Int) {
        val movieData = similarMovieList[position]
        try {
            val title: String = if (movieData.has("title")) {
                "title"
            } else {
                "name"
            }
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "w780" else "w500"
            holder.binding.movieTitle.text = movieData.getString(title)

            Picasso.get().load(
                "https://image.tmdb.org/t/p/" + imageSize + movieData.getString("backdrop_path")
            ).into(holder.binding.movieImage)

            val animation = AnimationUtils.loadAnimation(context, R.anim.fade_in_fast)
            holder.binding.movieImage.startAnimation(animation)
        } catch (je: JSONException) {
            je.printStackTrace()
        }
        holder.binding.cardView.setBackgroundColor(Color.TRANSPARENT)

        holder.itemView.setOnClickListener { view: View ->
            val intent = Intent(view.context, DetailActivity::class.java)
            intent.putExtra("movieObject", movieData.toString())
            if (movieData.has("name")) {
                intent.putExtra("isMovie", false)
            }
            view.context.startActivity(intent)
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    class MovieItemViewHolder(val binding: MovieCardBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val HD_IMAGE_SIZE = "key_hq_images"
    }
}