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
import android.icu.text.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ShowTraktAdapter(
    showList: ArrayList<JSONObject>, gridView: Boolean
) : RecyclerView.Adapter<ShowTraktAdapter.ShowItemViewHolder?>() {
    private var mShowArrayList: ArrayList<JSONObject>
    private val mGridView: Boolean

    init {

        mShowArrayList = showList
        mGridView = gridView
    }

    override fun getItemCount(): Int {
        // Return the amount of items in the list.
        return mShowArrayList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShowItemViewHolder {
        val view: View = if (mGridView) {
            LayoutInflater.from(parent.context).inflate(R.layout.show_grid_card, parent, false)
        } else {
            LayoutInflater.from(parent.context).inflate(R.layout.show_card, parent, false)
        }
        return ShowItemViewHolder(view, mGridView)
    }

    override fun onBindViewHolder(holder: ShowItemViewHolder, position: Int) {
        val showData = mShowArrayList[position]
        val context = holder.showView.context
        try {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "w780" else "w500"
            val posterPath = showData.optString(KEY_POSTER, "null")
            if (posterPath == "null") {
                holder.showImage.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.md_theme_outline, null))
            } else {
                Picasso.get().load("https://image.tmdb.org/t/p/$imageSize$posterPath").into(holder.showImage)
            }
            val name = showData.optString(KEY_TITLE, showData.optString(KEY_NAME, ""))
            holder.showTitle.text = name

            var dateString = if (showData.has("listed_at")){
                showData.optString("listed_at", "")
            } else if (showData.has("rated_at")) {
                showData.optString("rated_at", "")
            } else if (showData.has("watched_at")) {
                showData.optString("watched_at", "")
            } else if (showData.has("collected_at")) {
                showData.optString("collected_at", "")
            } else if (showData.has("last_watched_at")) {
                showData.optString("last_watched_at", "")
            } else {
                showData.optString("release_date", "")
            }

            val originalFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            try {
                val date = originalFormat.parse(dateString)
                val localFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                dateString = localFormat.format(date)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            holder.showDate.text = dateString

            when (showData.getString("type")) {
                "season", "episode" -> {
                    holder.showTitle.text = showData.getString("show_title")
                    holder.seasonEpisodeText?.text = if (showData.getString("type") == "season") {
                        "Season ${showData.getInt("season")}"
                    } else {
                        "Episode ${showData.getInt("number")}(S: ${showData.getInt("season")})"
                    }
                    holder.seasonEpisodeText?.visibility = View.VISIBLE

                    if (!mGridView) {
                        holder.showDescription?.text = showData.optString(KEY_DESCRIPTION, "")
                        holder.showRating?.rating = if (showData.has("rating")) {
                            showData.optString("rating", "0").toFloat() / 2
                        } else {
                            showData.optString(KEY_RATING, "0").toFloat() / 2
                        }

                        val genreIdsString = showData.optString(KEY_GENRES, "[]")
                        val genreIds = JSONArray(genreIdsString)
                        val sharedPreferences = context.getSharedPreferences("GenreList", Context.MODE_PRIVATE)
                        val genreNames = StringBuilder()

                        for (i in 0 until genreIds.length()) {
                            val genreId = genreIds.getInt(i).toString()
                            val genreName = sharedPreferences.getString(genreId, "")
                            if (!genreName.isNullOrEmpty()) {
                                genreNames.append(", ").append(genreName)
                            }
                        }

                        holder.showGenre?.text = if (genreNames.isNotEmpty()) genreNames.substring(2) else ""
                    }
                } else -> {
                    if (!mGridView) {
                        holder.showDescription?.text = showData.optString(KEY_DESCRIPTION, "")
                        holder.showRating?.rating = if (showData.has("rating")) {
                            showData.optString("rating", "0").toFloat() / 2
                        } else {
                            showData.optString(KEY_RATING, "0").toFloat() / 2
                        }

                        val genreIdsString = showData.optString(KEY_GENRES, "[]")
                        val genreIds = JSONArray(genreIdsString)
                        val sharedPreferences = context.getSharedPreferences("GenreList", Context.MODE_PRIVATE)
                        val genreNames = StringBuilder()

                        for (i in 0 until genreIds.length()) {
                            val genreId = genreIds.getInt(i).toString()
                            val genreName = sharedPreferences.getString(genreId, "")
                            if (!genreName.isNullOrEmpty()) {
                                genreNames.append(", ").append(genreName)
                            }
                        }

                        holder.showGenre?.text = if (genreNames.isNotEmpty()) genreNames.substring(2) else ""
                    }
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        holder.itemView.setOnClickListener { view: View ->
            val intent = Intent(view.context, DetailActivity::class.java)
            intent.putExtra("movieObject", showData.toString())
            val isMovie = showData.getString("type") == "movie"
            intent.putExtra("isMovie", isMovie)
            view.context.startActivity(intent)
        }
    }

    override fun getItemId(position: Int): Long {

        return position.toLong()
    }


    fun addItems(newItems: ArrayList<JSONObject>) {
        val startPosition = mShowArrayList.size
        mShowArrayList.addAll(newItems)
        notifyItemRangeInserted(startPosition, newItems.size)
    }

    fun updateData(newShowList: ArrayList<JSONObject>) {
        mShowArrayList = newShowList
        notifyDataSetChanged()
    }

    /**
     * The View of every item that is displayed in the grid/list.
     */
    class ShowItemViewHolder internal constructor(itemView: View, gridView: Boolean) :
        RecyclerView.ViewHolder(itemView) {
        val showView: CardView = itemView.findViewById(R.id.cardView)
        val showTitle: TextView = itemView.findViewById(R.id.title)
        val showImage: ImageView = itemView.findViewById(R.id.image)
        val showDescription: TextView? = if (!gridView) itemView.findViewById(R.id.description) else null
        val showGenre: TextView? = if (!gridView) itemView.findViewById(R.id.genre) else null
        val showRating: RatingBar? = if (!gridView) itemView.findViewById(R.id.rating) else null
        val showDate: TextView = itemView.findViewById(R.id.date)
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
        val seasonEpisodeText: TextView? = itemView.findViewById(R.id.seasonEpisodeText)

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
        const val KEY_GENRES = "genre_ids"
        const val KEY_RELEASE_DATE = "release_date"
        private const val HD_IMAGE_SIZE = "key_hq_images"
    }
}
