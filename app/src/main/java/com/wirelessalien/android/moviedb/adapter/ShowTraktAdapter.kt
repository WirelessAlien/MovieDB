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
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.databinding.ShowCardBinding
import com.wirelessalien.android.moviedb.databinding.ShowGridCardBinding
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShowTraktAdapter(
    showList: ArrayList<JSONObject>, gridView: Boolean
) : RecyclerView.Adapter<ShowTraktAdapter.ShowItemViewHolder>() {
    private var mShowArrayList: ArrayList<JSONObject>
    private val mGridView: Boolean

    init {
        mShowArrayList = showList
        mGridView = gridView
    }

    override fun getItemCount(): Int {
        return mShowArrayList.size
    }

    class ShowDiffCallback(
        private val oldList: List<JSONObject>,
        private val newList: List<JSONObject>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].getString("auto_id") == newList[newItemPosition].getString("auto_id")
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].toString() == newList[newItemPosition].toString()
        }
    }

    fun updateShowList(newShowList: ArrayList<JSONObject>) {
        val diffCallback = ShowDiffCallback(mShowArrayList, newShowList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        mShowArrayList.clear()
        mShowArrayList.addAll(newShowList)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShowItemViewHolder {
        return if (mGridView) {
            val gridBinding = ShowGridCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ShowItemViewHolder(null, gridBinding)
        } else {
            val binding = ShowCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ShowItemViewHolder(binding, null)
        }
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

            var dateString = if (showData.has("listed_at")) {
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

            val dateFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
                SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            )

            var parsedDate: Date? = null
            for (format in dateFormats) {
                try {
                    parsedDate = format.parse(dateString)
                    if (parsedDate != null) break
                } catch (e: ParseException) {
                    // Continue to the next format
                }
            }

            dateString = if (parsedDate != null) {
                val localFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                localFormat.format(parsedDate)
            } else {
                "Date"
            }
            holder.showDate.text = dateString

            when (showData.getString("type")) {
                "season", "episode" -> {
                    holder.showTitle.text = showData.getString("show_title")
                    holder.seasonEpisodeText.text = if (showData.getString("type") == "season") {
                        context.getString(R.string.season_p, showData.getInt("season"))
                    } else {
                        context.getString(R.string.episode_s, showData.getInt("number"), showData.getInt("season"))
                    }
                    holder.seasonEpisodeText.visibility = View.VISIBLE

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
                else -> {
                    holder.seasonEpisodeText.visibility = View.GONE
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

    class ShowItemViewHolder(
        val binding: ShowCardBinding?,
        val gridBinding: ShowGridCardBinding?
    ) : RecyclerView.ViewHolder(gridBinding?.root ?: binding!!.root) {

        val showView = gridBinding?.cardView ?: binding!!.cardView
        val showTitle = gridBinding?.title ?: binding!!.title
        val showImage = gridBinding?.image ?: binding!!.image
        val showDescription = binding?.description
        val showGenre = binding?.genre
        val showRating = binding?.rating
        val showDate = gridBinding?.date ?: binding!!.date
        val deleteButton = gridBinding?.deleteButton ?: binding!!.deleteButton
        val seasonEpisodeText = gridBinding?.seasonEpisodeText ?: binding!!.seasonEpisodeText
    }

    companion object {
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