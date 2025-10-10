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
import android.content.SharedPreferences
import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.databinding.BottomSheetSeasonEpisodeBinding
import com.wirelessalien.android.moviedb.databinding.ShowCardBinding
import com.wirelessalien.android.moviedb.databinding.ShowGridCardBinding
import com.wirelessalien.android.moviedb.fragment.ListFragment
import com.wirelessalien.android.moviedb.fragment.ListFragment.Companion.IS_MOVIE
import com.wirelessalien.android.moviedb.fragment.ListFragment.Companion.IS_UPCOMING
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShowBaseAdapter(
    val context: Context,
    showList: ArrayList<JSONObject>,
    genreList: HashMap<String, String?>, gridView: Boolean
) : RecyclerView.Adapter<ShowBaseAdapter.ShowItemViewHolder?>(),
    EpisodeSavedAdapter.EpisodeClickListener {
    private val mShowArrayList: ArrayList<JSONObject>
    private val mGenreHashMap: HashMap<String, String?>
    private var mGridView: Boolean
    private var genreType: String? = null
    private lateinit var preference: SharedPreferences
    private var apiKey: String? = null
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var bottomSheetBinding: BottomSheetSeasonEpisodeBinding? = null
    private var currentEpisodeAdapter: EpisodeSavedAdapter? = null

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
        preference = PreferenceManager.getDefaultSharedPreferences(context)
        apiKey = ConfigHelper.getConfigValue(context, "api_key")

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
                    val watchedEpisodes = getWatchedEpisodesCount(showData)
                    val episodesLeft = totalEpisodes - watchedEpisodes

                    holder.categoryColorView?.text = context.getString(
                        R.string.ep_progress_text,
                        watchedEpisodes,
                        totalEpisodes,
                        episodesLeft
                    )

                    holder.watchedProgressView?.visibility = View.VISIBLE
                    holder.showRating?.visibility = View.GONE
                    if (totalEpisodes > 0) {
                        holder.watchedProgressView?.progress = (watchedEpisodes.toFloat() / totalEpisodes.toFloat() * 100).toInt()
                    } else {
                        holder.watchedProgressView?.progress = 0
                    }
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
                    holder.watchedProgressView?.visibility = View.GONE
                    holder.showRating?.visibility = View.VISIBLE
                }
            } else {
                holder.categoryColorView?.visibility = View.GONE
                holder.watchedProgressView?.visibility = View.GONE
                holder.showRating?.visibility = View.VISIBLE
            }

            if (showData.has(IS_UPCOMING) && showData.optBoolean(IS_UPCOMING)) {
                val season = showData.optString("season")
                val episodeNumber = showData.optString("number")

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

        if (showData.has(IS_MOVIE) && showData.optInt(IS_MOVIE) != 1) {
            holder.itemView.setOnLongClickListener {

                bottomSheetDialog = BottomSheetDialog(context)
                bottomSheetBinding = BottomSheetSeasonEpisodeBinding.inflate(LayoutInflater.from(context))
                bottomSheetBinding!!.episodeTitle.visibility = View.VISIBLE
                bottomSheetBinding!!.episodeSliderLayout.visibility = View.VISIBLE
                val chipGroupSeasons = bottomSheetBinding!!.chipGroupSeasons
                val recyclerViewEpisodes = bottomSheetBinding!!.recyclerViewEpisodes
                val episodeSlider = bottomSheetBinding!!.episodeSlider
                val saveButton = bottomSheetBinding!!.saveBtn
                recyclerViewEpisodes.layoutManager = LinearLayoutManager(context)

                val currentDate = android.icu.text.SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                    .format(Calendar.getInstance().time)

                val tvShowId = showData.optInt(KEY_ID)
                val totalEpisodes = getTotalEpisodesFromTmdb(context, tvShowId).coerceAtLeast(0)
                val watchedEpisodesCount = getWatchedEpisodesCount(showData).coerceAtLeast(0)
                val nextEpisode = getNextEpisodeDetails(tvShowId)
                val seasons = getSeasonsFromTmdbDatabase(tvShowId)

                if (totalEpisodes == 0) {
                    Toast.makeText(context, context.getString(R.string.no_episodes_found), Toast.LENGTH_SHORT).show()
                    return@setOnLongClickListener true
                }

                episodeSlider.apply {
                    valueFrom = 0f
                    valueTo = totalEpisodes.toFloat()
                    value = watchedEpisodesCount.coerceIn(valueFrom.toInt(), valueTo.toInt()).toFloat()
                    stepSize = 1f
                }

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
                            view.visibility = if (isExpanded || index < maxVisibleChips) View.VISIBLE else View.GONE
                        }
                    }
                }

                // Add season chips
                seasons.forEach { seasonNumber ->
                    val chip = Chip(context).apply {
                        text = context.getString(R.string.season_p, seasonNumber)
                        isCheckable = true
                        setOnClickListener {
                            val episodes = getEpisodesForSeasonFromTmdbDatabase(showData.optInt(KEY_ID), seasonNumber)
                            val watchedEpisodes = getWatchedEpisodesFromDb(showData.optInt(KEY_ID), seasonNumber)
                            currentEpisodeAdapter = EpisodeSavedAdapter(
                                episodes,
                                watchedEpisodes.toMutableList(),
                                showData.optInt(KEY_ID),
                                seasonNumber,
                                context,
                                this@ShowBaseAdapter // Pass 'this' as the listener
                            )
                            recyclerViewEpisodes.adapter = currentEpisodeAdapter
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

                if (showData.has("number") && showData.has("season") ) {
                    val seasonNumber = showData.optInt("season", 1)
                    val episodeNumber = showData.optInt("number", 1)
                    showInitialEpisode(tvShowId, seasonNumber, episodeNumber)

                } else if (nextEpisode != null) {
                    val (seasonNumberN, episodeNumberN) = nextEpisode
                    showInitialEpisode(showData.optInt(KEY_ID), seasonNumberN ?: 1, episodeNumberN ?: 1)

                } else {
                    bottomSheetBinding?.episodeName?.visibility = View.GONE
                    bottomSheetBinding?.episodeOverview?.visibility = View.GONE
                    bottomSheetBinding?.episodeAirDate?.visibility = View.GONE
                    bottomSheetBinding?.imageView?.visibility = View.GONE
                    bottomSheetBinding?.chipEpS?.visibility = View.GONE
                    bottomSheetBinding?.addToWatched?.visibility = View.GONE
                }

                saveButton.isEnabled = false

                episodeSlider.addOnChangeListener { _, value, _ ->
                    saveButton.isEnabled = value.toInt() != getWatchedEpisodesCount(showData)
                }

                saveButton.setOnClickListener {
                    val episodesToMark = episodeSlider.value.toInt()
                    var episodesMarked = 0

                    val dbHelper = MovieDatabaseHelper(context)
                    val seasonsS = getSeasonsFromTmdbDatabase(tvShowId)
                    val markedEpisodes = mutableMapOf<Int, List<Int>>()

                    // First, get total marked episodes across all seasons
                    val totalMarkedEpisodes = seasonsS.sumOf { seasonNumber ->
                        getWatchedEpisodesFromDb(tvShowId, seasonNumber).size
                    }

                    if (episodesToMark < totalMarkedEpisodes) {
                        // Need to remove episodes
                        for (seasonNumber in seasonsS.reversed()) {
                            val watchedEpisodesR = getWatchedEpisodesFromDb(tvShowId, seasonNumber).sorted()
                            val episodesToRemove = mutableListOf<Int>()

                            for (episodeNumber in watchedEpisodesR.reversed()) {
                                if (totalMarkedEpisodes - episodesMarked > episodesToMark) {
                                    episodesToRemove.add(episodeNumber)
                                    episodesMarked++
                                } else {
                                    break
                                }
                            }

                            if (episodesToRemove.isNotEmpty()) {
                                dbHelper.removeEpisodeNumber(tvShowId, seasonNumber, episodesToRemove)
                                markedEpisodes[seasonNumber] = episodesToRemove
                            }
                        }

                        Toast.makeText(context,
                            context.getString(R.string.marked_episodes_as_unwatched, episodesMarked), Toast.LENGTH_SHORT).show()
                    } else {
                        // Add episodes
                        episodesMarked = totalMarkedEpisodes
                        for (seasonNumber in seasonsS) {
                            val episodesInSeason = getEpisodesForSeasonFromTmdbDatabase(tvShowId, seasonNumber)
                            val watchedEpisodesA = getWatchedEpisodesFromDb(tvShowId, seasonNumber)
                            val markedEpisodesInSeason = mutableListOf<Int>()

                            for (episodeNumber in episodesInSeason) {
                                if (episodesMarked < episodesToMark && !watchedEpisodesA.contains(episodeNumber)) {
                                    dbHelper.addEpisodeNumber(
                                        tvShowId,
                                        seasonNumber,
                                        listOf(episodeNumber),
                                        currentDate
                                    )
                                    markedEpisodesInSeason.add(episodeNumber)
                                    episodesMarked++
                                }
                            }

                            if (markedEpisodesInSeason.isNotEmpty()) {
                                markedEpisodes[seasonNumber] = markedEpisodesInSeason
                            }
                        }

                        // Show a toast message for added episodes
                        val episodesAdded = episodesMarked - totalMarkedEpisodes
                        if (episodesAdded > 0) {
                            Toast.makeText(context,
                                context.getString(
                                    R.string.marked_episodes_as_watched,
                                    episodesAdded
                                ), Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Disable the save button after saving
                    saveButton.isEnabled = false

                    // Refresh the current season's data
                    val currentChip = bottomSheetBinding?.chipGroupSeasons?.children
                        ?.filterIsInstance<Chip>()
                        ?.find { it.isChecked }

                    currentChip?.let { chip ->
                        val seasonText = chip.text.toString()
                        val currentSeason = seasonText.replace(Regex("[^0-9]"), "").toInt()

                        val allEpisodesInCurrentSeason = getEpisodesForSeasonFromTmdbDatabase(tvShowId, currentSeason)
                        val watchedEpisodesAfterUpdate = getWatchedEpisodesFromDb(tvShowId, currentSeason)

                        allEpisodesInCurrentSeason.forEach { episodeNumber ->
                            currentEpisodeAdapter?.updateEpisodeWatched(
                                episodeNumber,
                                watchedEpisodesAfterUpdate.contains(episodeNumber)
                            )
                        }
                    }

                    val nextEpisodeDetails = getNextEpisodeDetails(tvShowId)
                    val (seasonNumberN, episodeNumberN) = nextEpisodeDetails ?: Pair(1, 1)
                    showInitialEpisode(tvShowId, seasonNumberN ?: 1, episodeNumberN ?: 1)
                }

                bottomSheetDialog?.setContentView(bottomSheetBinding!!.root)
                bottomSheetDialog?.show()
                true
            }
        }

        holder.itemView.setOnClickListener { view: View ->
            val intent = Intent(view.context, DetailActivity::class.java)
            intent.putExtra("movieObject", showData.toString())
            if (showData.has(IS_UPCOMING) && showData.optBoolean(IS_UPCOMING)) {
                val upcomingType = showData.optString("upcoming_type")
                intent.putExtra("isMovie", upcomingType == "movie")
            } else if (showData.has(KEY_NAME)) {
                intent.putExtra("isMovie", false)
            }
            view.context.startActivity(intent)
        }
    }

    override fun onEpisodeClick(tvShowId: Int, seasonNumber: Int, episodeNumber: Int) {
        bottomSheetBinding?.let {
            showInitialEpisode(tvShowId, seasonNumber, episodeNumber)
        }
    }

    override fun onEpisodeWatchedStatusChanged(tvShowId: Int, seasonNumber: Int, episodeNumber: Int, isWatched: Boolean) {
        bottomSheetBinding?.let{
            if(it.chipEpS.text == "S${seasonNumber}:E${episodeNumber}"){
                it.addToWatched.icon = AppCompatResources.getDrawable(context,
                    if (isWatched) R.drawable.ic_visibility else R.drawable.ic_visibility_off
                )
            }
        }
    }

    private fun showInitialEpisode(tvShowId: Int, seasonNumber: Int, episodeNumber: Int) {
        bottomSheetBinding?.linearLayout?.visibility = View.VISIBLE
        bottomSheetBinding?.addToWatched?.visibility = View.VISIBLE
        bottomSheetBinding?.chipEpS?.visibility = View.VISIBLE
        bottomSheetBinding?.chipEpS?.text = "S${seasonNumber}:E${episodeNumber}"

        val currentDate = android.icu.text.SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            .format(Calendar.getInstance().time)

        MovieDatabaseHelper(context).use { db ->
            val isEpWatched = db.isEpisodeInDatabase(tvShowId, seasonNumber, listOf(episodeNumber))
            bottomSheetBinding?.addToWatched?.icon = AppCompatResources.getDrawable(context,
                if (isEpWatched) R.drawable.ic_visibility else R.drawable.ic_visibility_off
            )

            bottomSheetBinding?.addToWatched?.setOnClickListener {
                val isCurrentlyWatched = db.isEpisodeInDatabase(tvShowId, seasonNumber, listOf(episodeNumber))
                val newWatchedStatus = !isCurrentlyWatched

                if (isCurrentlyWatched) {
                    db.removeEpisodeNumber(tvShowId, seasonNumber, listOf(episodeNumber))
                } else {
                    db.addEpisodeNumber(tvShowId, seasonNumber, listOf(episodeNumber), currentDate)
                }
                bottomSheetBinding?.addToWatched?.icon = AppCompatResources.getDrawable(context,
                    if (newWatchedStatus) R.drawable.ic_visibility else R.drawable.ic_visibility_off
                )

                currentEpisodeAdapter?.updateEpisodeWatched(episodeNumber, newWatchedStatus)
            }
        }

        fetchAndDisplayEpisodeDetails(
            tvShowId,
            seasonNumber,
            episodeNumber,
            bottomSheetBinding!!.episodeName,
            bottomSheetBinding!!.episodeOverview,
            bottomSheetBinding!!.episodeAirDate,
            bottomSheetBinding!!.imageView,
            preference.getBoolean(HD_IMAGE_SIZE, false),
            apiKey ?: ""
        )
    }

    private fun getNextEpisodeDetails(showId: Int): Pair<Int?, Int?>? {

        // Get all seasons from TMDB database
        val seasons = getSeasonsFromTmdbDatabase(showId)

        // Iterate through seasons and episodes to find the next unwatched one
        for (seasonNumber in seasons) {
            val episodes = getEpisodesForSeasonFromTmdbDatabase(showId, seasonNumber)
            val watchedEpisodes = getWatchedEpisodesFromDb(showId, seasonNumber)

            for (episodeNumber in episodes) {
                if (episodeNumber !in watchedEpisodes) {
                    return Pair(seasonNumber, episodeNumber)
                }
            }
        }

        return null
    }

    private fun fetchAndDisplayEpisodeDetails(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeName: TextView,
        episodeOverview: TextView,
        episodeAirDate: TextView,
        episodeImageView: ImageView,
        loadHdImage : Boolean,
        apiKey: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.themoviedb.org/3/tv/$seriesId/season/$seasonNumber/episode/$episodeNumber?api_key=$apiKey")
                    .build()

                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()?.let { JSONObject(it) }

                Log.d("efdfdgf", jsonResponse.toString())
                if (jsonResponse != null) {
                    val name = jsonResponse.optString("name", "N/A")
                    val overview = jsonResponse.optString("overview", "No overview available.")
                    val stillPath = jsonResponse.optString("still_path")

                    val dateToFormat = jsonResponse.optString("air_date", "")

                    val formats = listOf(
                        "yyyy-MM-dd",
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        "dd-MM-yyyy"
                    )

                    var parsedDate: Date? = null
                    for (format in formats) {
                        try {
                            val formatter = SimpleDateFormat(format, Locale.getDefault())
                            parsedDate = formatter.parse(dateToFormat)
                            if (parsedDate != null) break
                        } catch (e: ParseException) {
                            continue
                        }
                    }

                    var formattedAirDate : String = dateToFormat
                    if (parsedDate != null) {
                        val localFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                        formattedAirDate = localFormat.format(parsedDate)
                    }

                    withContext(Dispatchers.Main) {
                        episodeName.text = name
                        episodeOverview.text = overview
                        episodeAirDate.text = formattedAirDate

                        if (stillPath.isNotEmpty() && stillPath != "null") {
                            val imageSize = if (loadHdImage) "w780" else "w500"
                            Picasso.get()
                                .load("https://image.tmdb.org/t/p/$imageSize$stillPath")
                                .into(episodeImageView)
                            episodeImageView.visibility = View.VISIBLE
                            episodeName.visibility = View.VISIBLE
                            episodeOverview.visibility = View.VISIBLE
                            episodeAirDate.visibility = View.VISIBLE
                        } else {
                            episodeImageView.visibility = View.VISIBLE
                            episodeImageView.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.md_theme_outlineVariant, null))
                            episodeName.visibility = View.GONE
                            episodeOverview.visibility = View.GONE
                            episodeAirDate.visibility = View.GONE
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        episodeImageView.visibility = View.GONE
                        episodeName.visibility = View.GONE
                        episodeOverview.visibility = View.GONE
                        episodeAirDate.visibility = View.GONE
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    episodeImageView.visibility = View.GONE
                    episodeName.visibility = View.GONE
                    episodeOverview.visibility = View.GONE
                    episodeAirDate.visibility = View.GONE
                }
                e.printStackTrace()
            }
        }
    }

    private fun getSeasonsFromTmdbDatabase(showId: Int): List<Int> {
        val dbHelper = TmdbDetailsDatabaseHelper(context)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
            arrayOf(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB),
            "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ?",
            arrayOf(showId.toString()),
            null, null, null
        )
        val seasons = if (cursor.moveToFirst()) {
            parseSeasonsTmdb(cursor.getString(cursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB)))
        } else {
            emptyList()
        }
        cursor.close()
        db.close()
        return seasons
    }

    private fun getEpisodesForSeasonFromTmdbDatabase(showId: Int, seasonNumber: Int): List<Int> {
        val dbHelper = TmdbDetailsDatabaseHelper(context)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
            arrayOf(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB),
            "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ?",
            arrayOf(showId.toString()),
            null, null, null
        )
        val episodes = if (cursor.moveToFirst()) {
            parseEpisodesForSeasonTmdb(cursor.getString(cursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB)), seasonNumber)
        } else {
            emptyList()
        }
        cursor.close()
        db.close()
        return episodes
    }

    private fun getWatchedEpisodesFromDb(showTraktId: Int, seasonNumber: Int): List<Int> {
        val dbHelper = MovieDatabaseHelper(context)
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                MovieDatabaseHelper.TABLE_EPISODES,
                arrayOf(MovieDatabaseHelper.COLUMN_EPISODE_NUMBER),
                "${MovieDatabaseHelper.COLUMN_MOVIES_ID} = ? AND ${MovieDatabaseHelper.COLUMN_SEASON_NUMBER} = ?",
                arrayOf(showTraktId.toString(), seasonNumber.toString()),
                null, null, null
            )

            val watchedEpisodes = mutableListOf<Int>()
            cursor.use { c ->
                if (c.moveToFirst()) {
                    do {
                        val episodeNumber = c.getInt(c.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_EPISODE_NUMBER))
                        watchedEpisodes.add(episodeNumber)
                    } while (c.moveToNext())
                }
            }
            watchedEpisodes
        } catch (e: Exception) {
            Log.e("ShowBaseAdapter", "Error getting watched episodes from db", e)
            emptyList()
        }
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

    fun updateData(newData: ArrayList<JSONObject>) {
        mShowArrayList.clear()
        mShowArrayList.addAll(newData)
        notifyDataSetChanged()
    }

    private fun getWatchedEpisodesCount(showData: JSONObject): Int {
        try {
            val tvShowId = showData.optInt(KEY_ID)
            val allSeasonsFromTmdb = getSeasonsFromTmdbDatabase(tvShowId)

            if (allSeasonsFromTmdb.isEmpty()) {
                if (showData.has("season") && showData.get("season") is JSONObject) {
                    val seasonsObject = showData.getJSONObject("season")
                    val watchedCount = 0
                    seasonsObject.keys().forEach { _ ->
                        val tvShowIdFromData = showData.optInt(KEY_ID)
                        val seasonsFromData = mutableListOf<Int>()
                        seasonsObject.keys().forEach { seasonKeyStr ->
                            seasonKeyStr.toIntOrNull()?.let { seasonsFromData.add(it) }
                        }
                        return seasonsFromData.sumOf { seasonNumber ->
                            getWatchedEpisodesFromDb(tvShowIdFromData, seasonNumber).size
                        }
                    }
                    return watchedCount
                }
                return 0
            }

            return allSeasonsFromTmdb.sumOf { seasonNumber ->
                getWatchedEpisodesFromDb(tvShowId, seasonNumber).size
            }
        } catch (e: Exception) {
            Log.e("ShowBaseAdapter", "Error counting watched episodes", e)
            return 0
        }
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
        const val KEY_HAS_PERSONAL_RATING = "has_personal_rating"
        const val KEY_DATE_MOVIE = "release_date"
        const val KEY_DATE_SERIES = "first_air_date"
        const val KEY_GENRES = "genre_ids"
        const val KEY_RELEASE_DATE = "release_date"
        const val KEY_IS_MOVIE = "is_movie"
        private const val HD_IMAGE_SIZE = "key_hq_images"
        private const val KEY_CATEGORIES = MovieDatabaseHelper.COLUMN_CATEGORIES
    }
}
