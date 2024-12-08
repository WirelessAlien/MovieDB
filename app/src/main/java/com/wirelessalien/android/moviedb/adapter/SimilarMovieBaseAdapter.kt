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
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.adapter.SimilarMovieBaseAdapter.MovieItemViewHolder
import org.json.JSONException
import org.json.JSONObject

/*
* This class is quite similar to the MovieBaseAdapter.
* The main difference is the orientation, which needs the RecyclerView.
* This class is primarily used for the "Similar Movies" list in DetailActivity.
*/
// TODO: Currently, this class is almost the same as ShowBaseAdapter, it would be nice if they could be merged.
class SimilarMovieBaseAdapter // Create the adapter with the list of similar shows and the context.
    (
    private val similarMovieList: ArrayList<JSONObject>,
    private val context: Context
) : RecyclerView.Adapter<MovieItemViewHolder?>() {
    override fun getItemCount(): Int {
        // Return the amount of items in the list.
        return similarMovieList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieItemViewHolder {
        // Create a new CardItem when needed.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.movie_card, parent, false)
        return MovieItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieItemViewHolder, position: Int) {
        // Fill the views with the needed data.
        val movieData = similarMovieList[position]
        try {
            // Depending if it is a movie or a series, it needs to get the title or name.
            val title: String = if (movieData.has("title")) {
                "title"
            } else {
                "name"
            }
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                context
            )
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "w780" else "w500"
            holder.movieTitle.text = movieData.getString(title)

            // Either show the poster or an icon indicating that the poster is not available.
            if (movieData.getString("backdrop_path") == null) {
                holder.movieImage.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.md_theme_outline, null))
            } else {
                Picasso.get().load(
                    "https://image.tmdb.org/t/p/" + imageSize +
                            movieData.getString("backdrop_path")
                )
                    .into(holder.movieImage)
            }

            // Quickly fade in the poster when loaded.
            val animation = AnimationUtils.loadAnimation(
                context,
                R.anim.fade_in_fast
            )
            holder.movieImage.startAnimation(animation)
        } catch (je: JSONException) {
            je.printStackTrace()
        }
        holder.cardView.setBackgroundColor(Color.TRANSPARENT)

        // Send the movie data and the user to DetailActivity when clicking on a card.
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
        // The id is the same as the position,
        // therefore returning the position is enough.
        return position.toLong()
    }

    // Views that each CardItem will contain.
    class MovieItemViewHolder internal constructor(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val cardView: CardView
        val movieTitle: TextView
        val movieImage: ImageView

        init {
            cardView = itemView.findViewById(R.id.cardView)
            movieTitle = itemView.findViewById(R.id.movieTitle)
            movieImage = itemView.findViewById(R.id.movieImage)
        }
    }

    companion object {
        private const val HD_IMAGE_SIZE = "key_hq_images"
    }
}
