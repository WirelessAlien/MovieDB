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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.databinding.ShowCardBinding
import com.wirelessalien.android.moviedb.databinding.ShowGridCardBinding
import com.wirelessalien.android.moviedb.fragment.ListFragment
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShowBaseAdapter(
    showList: ArrayList<JSONObject>,
    genreList: HashMap<String, String?>, gridView: Boolean
) : RecyclerView.Adapter<ShowBaseAdapter.ShowItemViewHolder?>() {
    private val mShowArrayList: ArrayList<JSONObject>
    private val mGenreHashMap: HashMap<String, String?>
    private var mGridView: Boolean
    private var genreType: String? = null

    init {
        genreType = when (genreType) {
            null, "2" -> {
                null
            }
            "1" -> {
                SectionsPagerAdapter.MOVIE
            }
            else -> {
                SectionsPagerAdapter.TV
            }
        }
        mShowArrayList = showList
        mGenreHashMap = genreList
        mGridView = gridView
    }

    override fun getItemCount(): Int {
        return mShowArrayList.size
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
            if (showData.getString(KEY_POSTER) == "null") {
                holder.showImage.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.md_theme_outline, null))
            } else {
                Picasso.get().load("https://image.tmdb.org/t/p/$imageSize" + showData.getString(KEY_POSTER)).into(holder.showImage)
            }

            val name = if (showData.has(KEY_TITLE)) showData.getString(KEY_TITLE) else showData.getString(KEY_NAME)
            holder.showTitle.text = name

            if (showData.has(KEY_CATEGORIES)) {
                val category = showData.getInt(KEY_CATEGORIES)
                holder.categoryColorView?.visibility = View.VISIBLE

                if (category == 2 && showData.optInt(KEY_IS_MOVIE) == 0) { // Watching and it's a TV show
                    val movieId = showData.optInt(KEY_ID)
                    val totalEpisodes = getTotalEpisodesFromTmdb(context, movieId)
                    Log.d("ShowBaseAdapter", "Total episodes: $totalEpisodes")
                    val watchedEpisodes = getWatchedEpisodesCount(showData)
                    Log.d("ShowBaseAdapter", "Watched episodes: $watchedEpisodes")
                    val episodesLeft = totalEpisodes - watchedEpisodes

                    holder.categoryColorView?.text = context.getString(
                        R.string.ep_progress_text,
                        watchedEpisodes,
                        totalEpisodes,
                        episodesLeft
                    )

                    holder.watchedProgressView?.visibility = View.VISIBLE
                    holder.watchedProgressView?.progress = (watchedEpisodes.toFloat() / totalEpisodes.toFloat() * 100).toInt()
                } else {
                    val categoryText = when (category) {
                        0 -> "Plan to watch"
                        1 -> "Watched"
                        2 -> "Watching"
                        3 -> "On hold"
                        4 -> "Dropped"
                        else -> "Unknown"
                    }
                    holder.categoryColorView?.text = categoryText
                }
            } else {
                holder.categoryColorView?.visibility = View.GONE
                holder.watchedProgressView?.visibility = View.GONE
            }


            if (showData.has(ListFragment.IS_UPCOMING) && showData.getBoolean(ListFragment.IS_UPCOMING)) {
                val season = showData.optString("seasons")
                val episodeNumber = showData.optString("upcoming_episode_number")

                if (!season.isNullOrEmpty() && !episodeNumber.isNullOrEmpty()) {
                    holder.categoryColorView?.visibility = View.VISIBLE
                    holder.categoryColorView?.text = context.getString(
                        R.string.episode_s,
                        episodeNumber.toInt(), season.toInt()
                    )
                } else {
                    holder.categoryColorView?.visibility = View.GONE
                }
            }

            var dateString = when {
                showData.has(ListFragment.UPCOMING_DATE) -> showData.optString(ListFragment.UPCOMING_DATE)
                showData.has(KEY_DATE_MOVIE) -> showData.optString(KEY_DATE_MOVIE)
                else -> showData.optString(KEY_DATE_SERIES)
            }

            val formats = listOf(
                "yyyy-MM-dd",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "dd-MM-yyyy"
            )

            var parsedDate: Date? = null
            for (format in formats) {
                try {
                    val formatter = SimpleDateFormat(format, Locale.getDefault())
                    parsedDate = formatter.parse(dateString)
                    if (parsedDate != null) break
                } catch (e: ParseException) {
                    continue
                }
            }

            if (parsedDate != null) {
                val localFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                dateString = localFormat.format(parsedDate)
            }
            holder.showDate.text = dateString

            if (!mGridView) {
                holder.showDescription?.text = showData.optString(KEY_DESCRIPTION)
                holder.showRating?.rating = showData.optString(KEY_RATING).toFloat() / 2

                var genreIds = showData.optString(KEY_GENRES)
                genreIds = if (!genreIds.isNullOrEmpty() && genreIds.length > 2) {
                    genreIds.substring(1, genreIds.length - 1)
                } else {
                    ""
                }
                val genreArray = genreIds.split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
                val sharedPreferences = context.getSharedPreferences("GenreList", Context.MODE_PRIVATE)
                val genreNames = StringBuilder()
                for (aGenreArray in genreArray) {
                    if (mGenreHashMap[aGenreArray] != null) {
                        genreNames.append(", ").append(mGenreHashMap[aGenreArray])
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
            if (showData.has(ListFragment.IS_UPCOMING) && showData.getBoolean(ListFragment.IS_UPCOMING)) {
                val upcomingType = showData.optString("upcoming_type")
                intent.putExtra("isMovie", upcomingType == "movie")
            } else if (showData.has(KEY_NAME)) {
                intent.putExtra("isMovie", false)
            }
            view.context.startActivity(intent)
        }
    }

    private fun getWatchedEpisodesCount(showData: JSONObject): Int {
        if (!showData.has("seasons")) return 0

        val seasons = showData.getJSONObject("seasons")
        var watchedCount = 0

        seasons.keys().forEach { seasonNumber ->
            val episodes = seasons.getJSONArray(seasonNumber)
            watchedCount += episodes.length()
        }

        return watchedCount
    }

    private fun getTotalEpisodesFromTmdb(context: Context, movieId: Int): Int {
        val tmdbDbHelper = TmdbDetailsDatabaseHelper(context)
        val tmdbDb = tmdbDbHelper.readableDatabase

        val cursor = tmdbDb.query(
            TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
            arrayOf(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB),
            "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ?",
            arrayOf(movieId.toString()),
            null, null, null
        )

        var totalEpisodes = 0
        if (cursor.moveToFirst()) {
            val seasonsEpisodeString = cursor.getString(
                cursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB)
            )

            if (!seasonsEpisodeString.isNullOrEmpty()) {
                val regex = Regex("""\d+\{(\d+(,\d+)*)\}""")
                totalEpisodes = regex.findAll(seasonsEpisodeString).sumOf { matchResult ->
                    matchResult.groupValues[1].split(",").size
                }
            }
        }

        cursor.close()
        tmdbDb.close()
        return totalEpisodes
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
        val categoryColorView = gridBinding?.categoryColor ?: binding?.categoryColor
        val watchedProgressView = gridBinding?.watchedProgress ?: binding?.watchedProgress
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
        const val KEY_IS_MOVIE = "is_movie"
        private const val HD_IMAGE_SIZE = "key_hq_images"
        private const val KEY_CATEGORIES = MovieDatabaseHelper.COLUMN_CATEGORIES
    }
}
