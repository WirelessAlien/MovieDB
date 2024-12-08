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

import android.content.Intent
import android.icu.text.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class NowPlayingMovieAdapter (private val mShowArrayList: ArrayList<JSONObject>?) :
    RecyclerView.Adapter<NowPlayingMovieAdapter.ShowItemViewHolder?>() {
    override fun getItemCount(): Int {
        // Return the amount of items in the list.
        return mShowArrayList?.size ?: 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShowItemViewHolder {
        // Create a new CardItem when needed.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.home_card_one, parent, false)
        return ShowItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShowItemViewHolder, position: Int) {
        // Ensure the item at the position is not null
        val showData = mShowArrayList?.get(position) ?: return

        val context = holder.showView.context
        // Fills the views with show details.
        try {
            // Load the thumbnail with Picasso.
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "w780" else "w500"
            if (showData.getString(KEY_POSTER) == "null") {
                holder.showImage.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.md_theme_outline, null))
            } else {
                Picasso.get().load(
                    "https://image.tmdb.org/t/p/$imageSize" + showData.getString(
                        KEY_POSTER
                    )
                ).into(holder.showImage)
            }

            // Check if the object has "title" if not,
            // it is a series and "name" is used.
            val name =
                if (showData.has(KEY_TITLE)) showData.getString(KEY_TITLE) else showData.getString(
                    KEY_NAME
                )

            // Set the title and description.
            holder.showTitle.text = name

            // Check if the object has "title" if not,
            // it is a series and "name" is used.
            var dateString =
                if (showData.has(KEY_DATE_MOVIE)) showData.getString(KEY_DATE_MOVIE) else showData.getString(
                    KEY_DATE_SERIES
                )

            // Convert date to locale.
            val originalFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            try {
                val date = originalFormat.parse(dateString)
                val localFormat =
                    DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                dateString = localFormat.format(date)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            holder.showDate.text = dateString
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        // Send the movie data and the user to DetailActivity when clicking on a card.
        holder.itemView.setOnClickListener { view: View ->
            val intent = Intent(view.context, DetailActivity::class.java)
            intent.putExtra("movieObject", showData.toString())
            if (showData.has(KEY_NAME)) {
                intent.putExtra("isMovie", false)
            }
            view.context.startActivity(intent)
        }
    }

    override fun getItemId(position: Int): Long {
        // The id is the same as the position,
        // therefore returning the position is enough.
        return position.toLong()
    }

    /**
     * The View of every item that is displayed in the list.
     */
    class ShowItemViewHolder internal constructor(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val showView: View
        val showTitle: TextView
        val showImage: ImageView
        val showDate: TextView
        val deleteButton: Button

        init {
            showView = itemView.findViewById(R.id.cardView)
            showTitle = itemView.findViewById(R.id.title)
            showImage = itemView.findViewById(R.id.image)

            // Only used if presented in a list.
            showDate = itemView.findViewById(R.id.date)
            deleteButton = itemView.findViewById(R.id.deleteButton)
        }
    }

    companion object {
        // API key names
        const val KEY_ID = "id"
        const val KEY_IMAGE = "backdrop_path"
        const val KEY_POSTER = "poster_path"
        const val KEY_TITLE = "title"
        const val KEY_NAME = "name"
        const val KEY_DESCRIPTION = "overview"
        const val KEY_RATING = "vote_average"
        const val KEY_DATE_MOVIE = "release_date"
        const val KEY_DATE_SERIES = "first_air_date"
        private const val HD_IMAGE_SIZE = "key_hq_images"
    }
}
