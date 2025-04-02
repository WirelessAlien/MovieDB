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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.DateFormat
import android.os.Handler
import android.os.Looper
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
import androidx.recyclerview.widget.DiffUtil
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
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.trakt.TraktSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShowTraktAdapter(
    showList: ArrayList<JSONObject>, gridView: Boolean
) : RecyclerView.Adapter<ShowTraktAdapter.ShowItemViewHolder>(),
    EpisodeTraktAdapter.EpisodeClickListener {
    private var mShowArrayList: ArrayList<JSONObject>
    private val mGridView: Boolean
    private lateinit var context: Context
    private var clientId: String? = null
    private var traktAccessToken: String? = null
    private lateinit var preferences: SharedPreferences
    private var apiKey: String? = null
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var bottomSheetBinding: BottomSheetSeasonEpisodeBinding? = null
    private var currentEpisodeAdapter: EpisodeTraktAdapter? = null

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
        holder.showData = showData

        context = holder.showView.context
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        clientId = ConfigHelper.getConfigValue(context, "client_id")
        traktAccessToken = preferences.getString("trakt_access_token", "")
        apiKey = ConfigHelper.getConfigValue(context, "api_key")

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
            } else if (showData.has("air_date")) {
                showData.optString("air_date", "")
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

            when (showData.optString("type")) {
                "season", "episode" -> {
                    holder.showTitle.text = showData.optString("show_title")
                    holder.seasonEpisodeText.text = if (showData.optString("type") == "season") {
                        context.getString(R.string.season_p, showData.optInt("season"))
                    } else {
                        context.getString(R.string.episode_s, showData.optInt("number"), showData.optInt("season"))
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

            if (showData.has("type") && showData.optString("type") == "episode") {
                holder.itemView.setOnLongClickListener {
                    bottomSheetDialog = BottomSheetDialog(context)
                    bottomSheetBinding = BottomSheetSeasonEpisodeBinding.inflate(LayoutInflater.from(context))
                    val chipGroupSeasons = bottomSheetBinding!!.chipGroupSeasons
                    val recyclerViewEpisodes = bottomSheetBinding!!.recyclerViewEpisodes

                    bottomSheetBinding!!.seasonActionButton.visibility = View.VISIBLE
                    recyclerViewEpisodes.layoutManager = LinearLayoutManager(context)

                    val seasons = parseSeasonsTmdb(showData.optString("seasons_episode_show_tmdb", ""))

                    val nextEpisode = getNextEpisodeDetails(showData.optInt("trakt_id"), seasons, showData.optString("seasons_episode_show_tmdb"))

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
                            tag = seasonNumber
                            text = context.getString(R.string.season_p, seasonNumber)
                            isCheckable = true

                            setOnClickListener { chipView ->
                                val season = (chipView as Chip).tag as Int
                                val episodes = parseEpisodesForSeasonTmdb(
                                    showData.optString("seasons_episode_show_tmdb", ""),
                                    season
                                )
                                val watchedEpisodes = getWatchedEpisodesFromDb(
                                    showData.optInt("trakt_id"),
                                    season
                                )

                                // Update episode list
                                currentEpisodeAdapter = EpisodeTraktAdapter(episodes, watchedEpisodes, showData, season, context, traktAccessToken?: "", clientId?: "", this@ShowTraktAdapter)
                                recyclerViewEpisodes.adapter = currentEpisodeAdapter

                                // Update season action button
                                bottomSheetBinding?.seasonActionButton?.let { seasonButton ->
                                    seasonButton.isEnabled = true

                                    val isWholeSeasonWatched = episodes.all { it in watchedEpisodes }
                                    seasonButton.text = context.getString(
                                        if (isWholeSeasonWatched) R.string.mark_season_unwatched
                                        else R.string.mark_season_watched
                                    )
                                    seasonButton.icon = AppCompatResources.getDrawable(context,
                                        if (isWholeSeasonWatched) R.drawable.ic_close
                                        else R.drawable.ic_done_2
                                    )
                                }
                            }
                        }
                        chipGroupSeasons.addView(chip)
                    }

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

                    if (seasons.isNotEmpty()) {
                        val firstChip = chipGroupSeasons.getChildAt(0) as? Chip
                        firstChip?.performClick()
                    }

                    bottomSheetBinding?.seasonActionButton?.setOnClickListener {
                        // Get the currently selected season from the checked chip
                        val selectedChip = (0 until chipGroupSeasons.childCount)
                            .map { chipGroupSeasons.getChildAt(it) }
                            .filterIsInstance<Chip>()
                            .firstOrNull { it.isChecked }

                        (selectedChip?.tag as? Int)?.let { season ->
                            val currentDateTime = SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                Locale.getDefault()
                            ).format(Date())

                            val episodes = parseEpisodesForSeasonTmdb(
                                showData.optString("seasons_episode_show_tmdb", ""),
                                season
                            )
                            val watchedEpisodes = getWatchedEpisodesFromDb(
                                showData.optInt("trakt_id"),
                                season
                            )

                            val isWholeSeasonWatched = episodes.all { it in watchedEpisodes }
                            val endpoint = if (isWholeSeasonWatched)
                                ACTION_MARK_SEASON_UNWATCHED
                            else
                                ACTION_MARK_SEASON_WATCHED

                            val seasonsArray = JSONArray().apply {
                                put(JSONObject().apply {
                                    put("number", season)
                                    put("watched_at", currentDateTime as String)
                                })
                            }

                            val idsObject = JSONObject().apply {
                                put("trakt", showData.optInt("trakt_id"))
                                put("tmdb", showData.optInt("id"))
                            }

                            val showsObject = JSONObject().apply {
                                put("shows", JSONArray().put(JSONObject().apply {
                                    put("ids", idsObject)
                                    put("seasons", seasonsArray)
                                }))
                            }

                            // Make the API call
                            val traktApiService = TraktSync(traktAccessToken ?: "", context)
                            traktApiService.post(endpoint, showsObject, object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(context, context.getString(R.string.failed_to_sync, "season"), Toast.LENGTH_SHORT).show()
                                    }
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    Handler(Looper.getMainLooper()).post {
                                        if (response.isSuccessful) {
                                            val dbHelper = TraktDatabaseHelper(context)
                                            val title = showData.optString("show_title")
                                            val traktId = showData.optInt("trakt_id")
                                            val tmdbId = showData.optInt("id")

                                            if (endpoint == ACTION_MARK_SEASON_WATCHED) {
                                                episodes.forEach { episodeNumber ->
                                                    dbHelper.addEpisodeToHistory(title, traktId, tmdbId, "episode", season, episodeNumber, currentDateTime)
                                                    dbHelper.addEpisodeToWatched(traktId, tmdbId, season, episodeNumber, currentDateTime)
                                                }
                                                dbHelper.addEpisodeToWatchedTable(tmdbId, traktId, "show", title, currentDateTime)
                                                Toast.makeText(context, R.string.season_marked_watched, Toast.LENGTH_SHORT).show()
                                            } else {
                                                episodes.forEach { episodeNumber ->
                                                    dbHelper.removeEpisodeFromHistory(tmdbId, season, episodeNumber)
                                                    dbHelper.removeEpisodeFromWatched(tmdbId, season, episodeNumber) }
                                                Toast.makeText(context, R.string.season_marked_unwatched, Toast.LENGTH_SHORT).show()
                                            }

                                            // Update the UI
                                            bottomSheetBinding?.seasonActionButton?.let { seasonButton ->
                                                seasonButton.text = context.getString(
                                                    if (endpoint == ACTION_MARK_SEASON_WATCHED)
                                                        R.string.mark_season_unwatched
                                                    else
                                                        R.string.mark_season_watched
                                                )
                                                seasonButton.icon = AppCompatResources.getDrawable(
                                                    context,
                                                    if (endpoint == ACTION_MARK_SEASON_WATCHED)
                                                        R.drawable.ic_close
                                                    else
                                                        R.drawable.ic_done_2
                                                )
                                            }

                                            // Refresh the episode list
                                            currentEpisodeAdapter?.let { adapter ->
                                                val newWatchedStatus = endpoint == ACTION_MARK_SEASON_WATCHED
                                                episodes.forEach { episodeNumber ->
                                                    adapter.updateEpisodeWatched(episodeNumber, newWatchedStatus)
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            })
                        }
                    }

                    if (showData.has("number") && showData.has("season") ) {

                        val traktId = showData.optInt("trakt_id")
                        val tvShowId = showData.optInt("id")
                        val seasonNumber = showData.optInt("season", 1)
                        val episodeNumber = showData.optInt("number", 1)
                        val episodeTraktId = showData.optInt("episode_trakt_id")

                        bottomSheetBinding!!.chipEpS.text = "S$seasonNumber:E$episodeNumber"
                        val isWatched = isEpisodeWatched(traktId, seasonNumber, episodeNumber)

                        if (isWatched) {
                            bottomSheetBinding!!.addToWatched.icon = AppCompatResources.getDrawable(context, R.drawable.ic_done_2)
                        } else {
                            bottomSheetBinding!!.addToWatched.icon = AppCompatResources.getDrawable(context, R.drawable.ic_close)
                        }

                        bottomSheetBinding!!.addToWatched.setOnClickListener {
                            val currentDateTime = android.icu.text.SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                Locale.getDefault()
                            ).format(
                                Date()
                            )
                            CoroutineScope(Dispatchers.Main).launch {
                                val episodeObject = createTraktEpisodeObject(
                                    episodeSeason = seasonNumber,
                                    episodeNumber = episodeNumber,
                                    episodeTraktId = showData.optInt("episode_trakt_id"),

                                    )

                                val endpoint = if (isWatched) "sync/history/remove" else "sync/history"
                                traktSync(episodeObject, endpoint, bottomSheetBinding!!, showData.optInt("id"), traktId, showData.optString("show_title"), seasonNumber, episodeNumber, currentDateTime)
                            }
                        }

                        // Fetch and display episode details on initial load
                        showInitialEpisode(tvShowId, traktId, seasonNumber, episodeNumber, episodeTraktId, title = showData.optString("show_title"))
                    } else if (nextEpisode != null) {
                        val episodeTraktId = showData.optInt("episode_trakt_id")
                        val tvShowId = showData.optInt("id")
                        val (seasonNumberN, episodeNumberN) = nextEpisode
                        bottomSheetBinding!!.chipEpS.text = "S$seasonNumberN:E$episodeNumberN"
                        val traktId = showData.optInt("trakt_id")
                        val isWatched = isEpisodeWatched(traktId, seasonNumberN!!, episodeNumberN!!)

                        if (isWatched) {
                            bottomSheetBinding!!.addToWatched.icon = AppCompatResources.getDrawable(context, R.drawable.ic_done_2)
                        } else {
                            bottomSheetBinding!!.addToWatched.icon = AppCompatResources.getDrawable(context, R.drawable.ic_close)
                        }
                        bottomSheetBinding!!.addToWatched.setOnClickListener {
                            val currentDateTime = android.icu.text.SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                Locale.getDefault()
                            ).format(
                                Date()
                            )
                            CoroutineScope(Dispatchers.Main).launch {
                                val episodeObject = createTraktEpisodeObject(
                                    episodeSeason = seasonNumberN,
                                    episodeNumber = episodeNumberN,
                                    episodeTraktId = showData.optInt("episode_trakt_id"),

                                    )

                                val endpoint = if (isWatched) "sync/history/remove" else "sync/history"
                                traktSync(episodeObject, endpoint, bottomSheetBinding!!, showData.optInt("id"), traktId, showData.optString("show_title"), seasonNumberN, episodeNumberN, currentDateTime)
                            }
                        }

                        showInitialEpisode(tvShowId, traktId, seasonNumberN, episodeNumberN, episodeTraktId, title = showData.optString("show_title"))

                    } else {
                        bottomSheetBinding!!.episodeName.visibility = View.GONE
                        bottomSheetBinding!!.episodeOverview.visibility = View.GONE
                        bottomSheetBinding!!.episodeAirDate.visibility = View.GONE
                        bottomSheetBinding!!.imageView.visibility = View.GONE
                    }

                    bottomSheetDialog?.setContentView(bottomSheetBinding!!.root)
                    bottomSheetDialog?.show()
                    true
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        holder.itemView.setOnClickListener { view: View ->
            holder.showData?.let { safeShowData ->
                val intent = Intent(view.context, DetailActivity::class.java)
                intent.putExtra("movieObject", safeShowData.toString())
                val isMovie = safeShowData.optString("type") == "movie"
                intent.putExtra("isMovie", isMovie)
                view.context.startActivity(intent)
            }
        }
    }

    override fun onEpisodeClick(tvShowId: Int, traktId: Int, seasonNumber: Int, episodeNumber: Int, episodeTraktId: Int, title: String) {
        bottomSheetBinding?.let {
            showInitialEpisode(tvShowId, traktId, seasonNumber, episodeNumber, episodeTraktId, title)
        }
    }

    override fun onEpisodeWatchedStatusChanged(tvShowId: Int, seasonNumber: Int, episodeNumber: Int, isWatched: Boolean) {
        bottomSheetBinding?.let{
            if(it.chipEpS.text == "S${seasonNumber}:E${episodeNumber}"){
                it.addToWatched.icon = AppCompatResources.getDrawable(context,
                    if (isWatched) R.drawable.ic_close else R.drawable.ic_done_2
                )
            }
        }
    }

    private fun getNextEpisodeDetails(showId: Int, seasons: List<Int>, episodeSeason: String): Pair<Int?, Int?>? {

        // Iterate through seasons and episodes to find the next unwatched one
        for (seasonNumber in seasons) {
            val episodes = parseEpisodesForSeasonTmdb(episodeSeason, seasonNumber)
            val watchedEpisodes = getWatchedEpisodesFromDb(showId, seasonNumber)

            for (episodeNumber in episodes) {
                if (episodeNumber !in watchedEpisodes) {
                    return Pair(seasonNumber, episodeNumber)
                }
            }
        }

        return null
    }

    private fun isEpisodeWatched(showTraktId: Int, seasonNumber: Int, episodeNumber: Int): Boolean {
        val dbHelper = TraktDatabaseHelper(context)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TraktDatabaseHelper.TABLE_SEASON_EPISODE_WATCHED,
            arrayOf(TraktDatabaseHelper.COL_EPISODE_NUMBER),
            "${TraktDatabaseHelper.COL_SHOW_TRAKT_ID} = ? AND ${TraktDatabaseHelper.COL_SEASON_NUMBER} = ? AND ${TraktDatabaseHelper.COL_EPISODE_NUMBER} = ?",
            arrayOf(showTraktId.toString(), seasonNumber.toString(), episodeNumber.toString()),
            null, null, null
        )
        val isWatched = cursor.count > 0
        cursor.close()
        db.close()
        return isWatched
    }

    private fun showInitialEpisode(tvShowId: Int, traktId: Int, seasonNumber: Int, episodeNumber: Int, episodeTraktId: Int, title: String) {

        bottomSheetBinding?.chipEpS?.text = "S${seasonNumber}:E${episodeNumber}"

        val isWatched = isEpisodeWatched(traktId, seasonNumber, episodeNumber)
        bottomSheetBinding?.addToWatched?.icon = AppCompatResources.getDrawable(context,
            if (isWatched) R.drawable.ic_done_2 else R.drawable.ic_close
        )

        bottomSheetBinding?.addToWatched?.setOnClickListener {
            val isCurrentlyWatched = isEpisodeWatched(traktId, seasonNumber, episodeNumber)
            val newWatchedStatus = !isCurrentlyWatched

            val currentDateTime = android.icu.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.getDefault()
            ).format(
                Date()
            )
            CoroutineScope(Dispatchers.Main).launch {
                val episodeObject = createTraktEpisodeObject(
                    episodeSeason = seasonNumber,
                    episodeNumber = episodeNumber,
                    episodeTraktId = episodeTraktId,

                    )

                val endpoint = if (isCurrentlyWatched) "sync/history/remove" else "sync/history"
                traktSync(episodeObject, endpoint, bottomSheetBinding!!, tvShowId, traktId, title, seasonNumber, episodeNumber, currentDateTime)
            }

            bottomSheetBinding?.addToWatched?.icon = AppCompatResources.getDrawable(context,
                if (newWatchedStatus) R.drawable.ic_done_2 else R.drawable.ic_close
            )

            currentEpisodeAdapter?.updateEpisodeWatched(episodeNumber, newWatchedStatus)
        }

        fetchAndDisplayEpisodeDetails(
            tvShowId,
            seasonNumber,
            episodeNumber,
            bottomSheetBinding!!.episodeName,
            bottomSheetBinding!!.episodeOverview,
            bottomSheetBinding!!.episodeAirDate,
            bottomSheetBinding!!.imageView,
            preferences.getBoolean(HD_IMAGE_SIZE, false),
            apiKey ?: ""
        )
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
                    val airDate = jsonResponse.optString("air_date")

                    val formats = listOf(
                        "yyyy-MM-dd",
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        "dd-MM-yyyy"
                    )

                    var parsedDate: Date? = null
                    for (format in formats) {
                        try {
                            val formatter = SimpleDateFormat(format, Locale.getDefault())
                            parsedDate = formatter.parse(airDate)
                            if (parsedDate != null) break
                        } catch (e: ParseException) {
                            continue
                        }
                    }

                    var formattedAirDate : String = airDate
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
                        } else {
                            episodeImageView.visibility = View.GONE
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

    private fun createTraktEpisodeObject(
        episodeSeason: Int,
        episodeNumber: Int,
        episodeTraktId: Int,
    ): JSONObject {
        val episodeIds = JSONObject().apply {
            put("trakt", episodeTraktId)
        }

        val episodeObject = JSONObject().apply {
            put("season", episodeSeason)
            put("number", episodeNumber)
            put("ids", episodeIds)
        }

        return JSONObject().apply {
            put("episodes", JSONArray().put(episodeObject))
        }
    }

    private fun traktSync(episodeJSONObject: JSONObject, endpoint: String, binding: BottomSheetSeasonEpisodeBinding, tmdbId: Int, traktId:Int, title: String, seasonNumber: Int, episodeNumber: Int, currentTime: String) {
        val traktApiService = TraktSync(traktAccessToken?: "", context)
        traktApiService.post(endpoint, episodeJSONObject, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        context.getString(R.string.failed_to_sync, endpoint),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                Handler(Looper.getMainLooper()).post {
                    val message = if (response.isSuccessful) {
                        val dbHelper = TraktDatabaseHelper(context)
                        val db = dbHelper.writableDatabase

                        if (endpoint == "sync/history") {
                            val values = ContentValues().apply {
                                put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, traktId)
                                put(TraktDatabaseHelper.COL_SHOW_TMDB_ID, tmdbId)
                                put(TraktDatabaseHelper.COL_SEASON_NUMBER, seasonNumber)
                                put(TraktDatabaseHelper.COL_EPISODE_NUMBER, episodeNumber)
                                put(TraktDatabaseHelper.COL_LAST_WATCHED_AT, currentTime)
                            }
                            dbHelper.insertSeasonEpisodeWatchedData(values)
                            dbHelper.addEpisodeToHistory(
                                title,
                                traktId,
                                tmdbId,
                                "episode",
                                seasonNumber,
                                episodeNumber,
                                currentTime
                            )
                            binding.addToWatched.icon =
                                AppCompatResources.getDrawable(context, R.drawable.ic_done_2)
                        } else if (endpoint == "sync/history/remove") {
                            db.delete(
                                TraktDatabaseHelper.TABLE_SEASON_EPISODE_WATCHED,
                                "${TraktDatabaseHelper.COL_SHOW_TRAKT_ID} = ? AND ${TraktDatabaseHelper.COL_SEASON_NUMBER} = ? AND ${TraktDatabaseHelper.COL_EPISODE_NUMBER} = ?",
                                arrayOf(
                                    traktId.toString(),
                                    seasonNumber.toString(),
                                    episodeNumber.toString()
                                )
                            )
                            binding.addToWatched.icon =
                                AppCompatResources.getDrawable(context, R.drawable.ic_close)
                        }

                        db.close()
                        context.getString(R.string.success)
                    } else {
                        response.message
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
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

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    class ShowItemViewHolder(
        val binding: ShowCardBinding?,
        val gridBinding: ShowGridCardBinding?
    ) : RecyclerView.ViewHolder(gridBinding?.root ?: binding!!.root) {

        // Add a property to store the current show data
        var showData: JSONObject? = null

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
        private const val ACTION_MARK_SEASON_WATCHED = "sync/history"
        private const val ACTION_MARK_SEASON_UNWATCHED = "sync/history/remove"
    }
}