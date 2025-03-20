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
import androidx.core.view.children
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.databinding.BottomSheetSeasonEpisodeBinding
import com.wirelessalien.android.moviedb.databinding.ShowCardTraktBinding
import com.wirelessalien.android.moviedb.databinding.ShowGridCardTraktBinding
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ShowProgressTraktAdapter(
    showList: ArrayList<JSONObject>,
    gridView: Boolean,
    private val traktAccessToken: String,
    private val clientId: String
) : RecyclerView.Adapter<ShowProgressTraktAdapter.ShowItemViewHolder?>() {
    private var mShowArrayList: ArrayList<JSONObject>
    private val mGridView: Boolean
    private lateinit var context: Context

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
            val gridBinding = ShowGridCardTraktBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ShowItemViewHolder(null, gridBinding)
        } else {
            val binding = ShowCardTraktBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ShowItemViewHolder(binding, null)
        }
    }

    override fun onBindViewHolder(holder: ShowItemViewHolder, position: Int) {
        val showData = mShowArrayList[position]
        context = holder.showView.context
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

            var dateString = showData.optString("last_watched_at", "")
            val originalFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            try {
                val date = originalFormat.parse(dateString)
                val localFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                dateString = localFormat.format(date)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            holder.showDate.text = dateString

            if (showData.getString("type") == "movie") {
                holder.watchedProgressBar?.visibility = View.GONE
                holder.seasonEpisodeText.visibility = View.GONE
            }

            if (showData.getString("type") == "show") {
                val watchedEpisodes = getWatchedEpisodesShow(showData.optInt("trakt_id", -1))
                val totalEpisodes = parseEpisodesTmdb(showData.optString("seasons_episode_show_tmdb", ""))
                val episodesLeft = totalEpisodes - watchedEpisodes.size
                val progress = if (totalEpisodes > 0) (watchedEpisodes.size.toFloat() / totalEpisodes.toFloat() * 100).toInt() else 0
                holder.watchedProgressBar?.progress = progress
                holder.watchedProgressBar?.visibility = View.VISIBLE
                holder.seasonEpisodeText.visibility = View.VISIBLE
                if (watchedEpisodes.size == totalEpisodes) {
                    holder.seasonEpisodeText.text = context.getString(R.string.watched)
                } else {
                    holder.seasonEpisodeText.text = context.getString(
                        R.string.ep_progress_text,
                        watchedEpisodes.size,
                        totalEpisodes,
                        episodesLeft
                    )
                }
            }

            if (!mGridView) {
                holder.showDescription?.text = showData.optString(KEY_DESCRIPTION, "")
                holder.showRating?.rating = showData.optString(KEY_RATING, "0").toFloat() / 2

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

           if (showData.has("type") && showData.getString("type") == "show") {
               holder.itemView.setOnLongClickListener {
                   val bottomSheetDialog = BottomSheetDialog(context)
                   val bottomSheetBinding =
                       BottomSheetSeasonEpisodeBinding.inflate(LayoutInflater.from(context))
                   val chipGroupSeasons = bottomSheetBinding.chipGroupSeasons
                   val recyclerViewEpisodes = bottomSheetBinding.recyclerViewEpisodes

                   recyclerViewEpisodes.layoutManager = LinearLayoutManager(context)

                   val seasons =
                       parseSeasonsTmdb(showData.optString("seasons_episode_show_tmdb", ""))
                   val maxVisibleChips = 5
                   var isExpanded = false

                   // Create show more chip
                   val showMoreChip = Chip(context).apply {
                       text = context.getString(R.string.show_more)
                       isCheckable = false
                       visibility = if (seasons.size > maxVisibleChips) View.VISIBLE else View.GONE
                   }

                   fun updateChipsVisibility() {
                       chipGroupSeasons.children.forEachIndexed { index, view ->
                           if (view != showMoreChip) {
                               view.visibility =
                                   if (isExpanded || index < maxVisibleChips) View.VISIBLE else View.GONE
                           }
                       }
                   }

                   // Add season chips
                   seasons.forEach { seasonNumber ->
                       val chip = Chip(context).apply {
                           text = context.getString(R.string.season_p, seasonNumber)
                           isCheckable = true
                           setOnClickListener {
                               val episodes = parseEpisodesForSeasonTmdb(
                                   showData.optString(
                                       "seasons_episode_show_tmdb",
                                       ""
                                   ), seasonNumber
                               )
                               val watchedEpisodesD = getWatchedEpisodesFromDb(
                                   showData.getInt("trakt_id"),
                                   seasonNumber
                               )
                               recyclerViewEpisodes.adapter = EpisodeTraktAdapter(
                                   episodes,
                                   watchedEpisodesD,
                                   showData,
                                   seasonNumber,
                                   context,
                                   traktAccessToken,
                                   clientId
                               )
                           }
                       }
                       chipGroupSeasons.addView(chip)
                   }

                   // Add show more chip and set its click listener
                   if (seasons.size > maxVisibleChips) {
                       chipGroupSeasons.addView(showMoreChip)
                       showMoreChip.setOnClickListener {
                           isExpanded = !isExpanded
                           showMoreChip.text = context.getString(
                               if (isExpanded) R.string.show_less else R.string.show_more
                           )
                           updateChipsVisibility()
                       }
                       updateChipsVisibility()
                   }

                   bottomSheetDialog.setContentView(bottomSheetBinding.root)
                   bottomSheetDialog.show()
                   true
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

    private fun getWatchedEpisodesFromDb(showTraktId: Int, seasonNumber: Int): MutableList<Int> {
        val dbHelper = TraktDatabaseHelper(context)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TraktDatabaseHelper.TABLE_SEASON_EPISODE_WATCHED,
            arrayOf(TraktDatabaseHelper.COL_EPISODE_NUMBER),
            "${TraktDatabaseHelper.COL_SHOW_TRAKT_ID} = ? AND ${TraktDatabaseHelper.COL_SEASON_NUMBER} = ?",
            arrayOf(showTraktId.toString(), seasonNumber.toString()),
            null, null, null
        )
        val watchedEpisodes = mutableListOf<Int>()
        if (cursor.moveToFirst()) {
            do {
                val episodeNumber = cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_EPISODE_NUMBER))
                watchedEpisodes.add(episodeNumber)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return watchedEpisodes
    }

    private fun parseSeasonsTmdb(seasonsString: String): List<Int> {
        val regex = Regex("""(\d+)\{.*?\}""")
        return regex.findAll(seasonsString).map { it.groupValues[1].toInt() }.toList()
    }

    private fun parseEpisodesForSeasonTmdb(seasonsString: String, seasonNumber: Int): List<Int> {
        val regex = Regex("""$seasonNumber\{(\d+(,\d+)*)\}""")
        val matchResult = regex.find(seasonsString) ?: return emptyList()
        return matchResult.groupValues[1].split(",").map { it.toInt() }
    }

    private fun parseEpisodesTmdb(episodesString: String): Int {
        val regex = Regex("""\d+\{(\d+(,\d+)*)\}""")
        return regex.findAll(episodesString).sumOf { matchResult ->
            matchResult.groupValues[1].split(",").size
        }
    }

    private fun getWatchedEpisodesShow(showTraktId: Int): List<Pair<Int, Int>> {
        val dbHelper = TraktDatabaseHelper(context)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TraktDatabaseHelper.TABLE_SEASON_EPISODE_WATCHED,
            arrayOf(
                TraktDatabaseHelper.COL_SEASON_NUMBER,
                TraktDatabaseHelper.COL_EPISODE_NUMBER
            ),
            "${TraktDatabaseHelper.COL_SHOW_TRAKT_ID} = ?",
            arrayOf(showTraktId.toString()),
            null, null, null
        )
        val watchedEpisodes = mutableListOf<Pair<Int, Int>>()
        if (cursor.moveToFirst()) {
            do {
                val seasonNumber = cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SEASON_NUMBER))
                val episodeNumber = cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_EPISODE_NUMBER))
                watchedEpisodes.add(Pair(seasonNumber, episodeNumber))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return watchedEpisodes
    }

    override fun getItemId(position: Int): Long {

        return position.toLong()
    }

    class ShowItemViewHolder(
        val binding: ShowCardTraktBinding?,
        val gridBinding: ShowGridCardTraktBinding?
    ) : RecyclerView.ViewHolder(gridBinding?.root ?: binding!!.root) {

        val showView = gridBinding?.cardView ?: binding!!.cardView
        val showTitle = gridBinding?.title ?: binding!!.title
        val showImage = gridBinding?.image ?: binding!!.image
        val showDescription = binding?.description
        val showGenre = binding?.genre
        val showRating = binding?.rating
        val showDate = gridBinding?.date ?: binding!!.date
        val deleteButton = gridBinding?.deleteButton ?: binding!!.deleteButton
        val watchedProgressBar = gridBinding?.watchedProgress ?: binding?.watchedProgress
        val seasonEpisodeText = gridBinding?.seasonEpisodeText ?: binding!!.seasonEpisodeText
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
        const val TYPE_MOVIE = 0
        const val TYPE_SHOW = 1
    }
}
