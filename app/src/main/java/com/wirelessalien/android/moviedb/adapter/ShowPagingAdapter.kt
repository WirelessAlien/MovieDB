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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.paging.PagingDataAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.account.DeleteFromList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.util.Locale

class ShowPagingAdapter(
    private val genreList: HashMap<String, String?>,
    private val gridView: Boolean,
    private val showDeleteButton: Boolean
) : PagingDataAdapter<JSONObject, ShowPagingAdapter.ShowItemViewHolder>(DIFF_CALLBACK) {

    private var genreType: String? = null

    init {
        genreType = when (genreType) {
            null, "2" -> null
            "1" -> SectionsPagerAdapter.MOVIE
            else -> SectionsPagerAdapter.TV
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShowItemViewHolder {
        val view = if (gridView) {
            LayoutInflater.from(parent.context).inflate(R.layout.show_grid_card, parent, false)
        } else {
            LayoutInflater.from(parent.context).inflate(R.layout.show_card, parent, false)
        }
        return ShowItemViewHolder(view, gridView)
    }

    override fun onBindViewHolder(holder: ShowItemViewHolder, position: Int) {
        val showData = getItem(position) ?: return
        val context = holder.showView.context

        try {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "w780" else "w500"
            if (showData.getString(KEY_POSTER) == "null") {
                holder.showImage.setBackgroundColor(
                    ResourcesCompat.getColor(
                        context.resources,
                        R.color.md_theme_outline,
                        null
                    )
                )
            } else {
                Picasso.get().load("https://image.tmdb.org/t/p/$imageSize" + showData.getString(
                    KEY_POSTER
                ))
                    .into(holder.showImage)
            }

            val name = if (showData.has(KEY_TITLE)) showData.getString(KEY_TITLE) else showData.getString(
                KEY_NAME
            )

            holder.showTitle.text = name

            if (showData.has(KEY_CATEGORIES)) {
                val categoryText = when (showData.getInt(KEY_CATEGORIES)) {
                    0 -> "Plan to watch"
                    1 -> "Watched"
                    2 -> "Watching"
                    3 -> "On hold"
                    4 -> "Dropped"
                    else -> "Unknown"
                }
                (holder.categoryColorView as TextView).text = categoryText
                holder.categoryColorView.visibility = View.VISIBLE
            } else {
                holder.categoryColorView.visibility = View.GONE
            }

            var dateString =
                if (showData.has(KEY_DATE_MOVIE)) showData.getString(KEY_DATE_MOVIE) else showData.getString(
                    KEY_DATE_SERIES
                )

            val originalFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            try {
                val date = originalFormat.parse(dateString)
                val localFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                dateString = localFormat.format(date)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            holder.showDate.text = dateString

            if (!gridView) {
                holder.showDescription?.text = showData.getString(KEY_DESCRIPTION)

                holder.showRating?.rating = showData.getString(KEY_RATING).toFloat() / 2

                var genreIds = showData.getString(KEY_GENRES)
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

                holder.showGenre?.text = if (genreNames.isNotEmpty()) genreNames.substring(2) else ""
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        holder.itemView.setOnClickListener { view: View ->
            val intent = Intent(view.context, DetailActivity::class.java)
            intent.putExtra("movieObject", showData.toString())
            if (showData.has(KEY_NAME)) {
                intent.putExtra("isMovie", false)
            }
            view.context.startActivity(intent)
        }

        if (showDeleteButton) {
            holder.deleteButton.visibility = View.VISIBLE
            holder.deleteButton.setOnClickListener {
                val mediaId: Int
                val type: String
                try {
                    mediaId = showData.getInt(KEY_ID)
                    type = if (showData.has(KEY_TITLE)) "movie" else "tv"
                } catch (e: JSONException) {
                    e.printStackTrace()
                    return@setOnClickListener
                }
                val dataList = getCurrentData()
                val listId = PreferenceManager.getDefaultSharedPreferences(context).getInt("listId", 0)
                val activity = context as Activity
                val deleteThread = DeleteFromList(
                    mediaId,
                    listId,
                    type,
                    activity,
                    position,
                    dataList,
                    this
                )
                CoroutineScope(Dispatchers.IO).launch {
                    deleteThread.deleteFromList()
                }
            }
        } else {
            holder.deleteButton.visibility = View.GONE
        }
    }

    private fun getCurrentData(): ArrayList<JSONObject> {
        val dataList = ArrayList<JSONObject>()
        for (i in 0 until itemCount) {
            getItem(i)?.let { dataList.add(it) }
        }
        return dataList
    }

    /**
     * The View of every item that is displayed in the grid/list.
     */
    class ShowItemViewHolder internal constructor(itemView: View, gridView: Boolean) :
        RecyclerView.ViewHolder(itemView) {
        val showView: CardView = itemView.findViewById(R.id.cardView)
        val showTitle: TextView = itemView.findViewById(R.id.title)
        val showImage: ImageView = itemView.findViewById(R.id.image)
        val categoryColorView: View = itemView.findViewById(R.id.categoryColor)
        val showDescription: TextView? = if (!gridView) itemView.findViewById(R.id.description) else null
        val showGenre: TextView? = if (!gridView) itemView.findViewById(R.id.genre) else null
        val showRating: RatingBar? = if (!gridView) itemView.findViewById(R.id.rating) else null
        val showDate: TextView = itemView.findViewById(R.id.date)
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
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
        private const val KEY_CATEGORIES = MovieDatabaseHelper.COLUMN_CATEGORIES

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<JSONObject>() {
            override fun areItemsTheSame(oldItem: JSONObject, newItem: JSONObject): Boolean {
                return oldItem.getInt(KEY_ID) == newItem.getInt(KEY_ID)
            }

            override fun areContentsTheSame(oldItem: JSONObject, newItem: JSONObject): Boolean {
                return oldItem.toString() == newItem.toString()
            }
        }
    }
}