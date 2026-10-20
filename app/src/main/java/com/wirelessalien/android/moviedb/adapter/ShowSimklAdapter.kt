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
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.NetworkClient
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.databinding.BottomSheetSeasonEpisodeBinding
import com.wirelessalien.android.moviedb.databinding.ShowCardTraktBinding
import com.wirelessalien.android.moviedb.databinding.ShowGridCardTraktBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.SimklDatabaseHelper
import com.wirelessalien.android.moviedb.simkl.SimklSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
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
import java.util.TimeZone

class ShowSimklAdapter(
    showList: ArrayList<JSONObject>,
    gridView: Boolean,
    private val simklAccessToken: String
) : RecyclerView.Adapter<ShowSimklAdapter.ShowItemViewHolder>(),
    EpisodeSimklAdapter.EpisodeClickListener {

    private var mShowArrayList: ArrayList<JSONObject> = showList
    private val mGridView: Boolean = gridView
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private var apiKey: String? = null
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var bottomSheetBinding: BottomSheetSeasonEpisodeBinding? = null
    private var currentEpisodeAdapter: EpisodeSimklAdapter? = null

    override fun getItemCount(): Int = mShowArrayList.size

    class ShowDiffCallback(
        private val oldList: List<JSONObject>,
        private val newList: List<JSONObject>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].optString("auto_id") ==
                newList[newItemPosition].optString("auto_id")
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
            val gridBinding = ShowGridCardTraktBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            ShowItemViewHolder(null, gridBinding)
        } else {
            val binding = ShowCardTraktBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            ShowItemViewHolder(binding, null)
        }
    }

    override fun onBindViewHolder(holder: ShowItemViewHolder, position: Int) {
        val showData = mShowArrayList[position]
        context = holder.showView.context
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        apiKey = ConfigHelper.getConfigValue(context, "api_key")

        try {
            val loadHDImage = preferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "w780" else "w500"
            val posterPath = showData.optString(KEY_POSTER, "null")

            Picasso.get().cancelRequest(holder.showImage)
            holder.showImage.setImageDrawable(null)

            if (posterPath == "null" || posterPath.isEmpty()) {
                holder.showImage.setBackgroundColor(
                    ResourcesCompat.getColor(context.resources, R.color.md_theme_outline, null)
                )
            } else {
                Picasso.get().load("https://image.tmdb.org/t/p/$imageSize$posterPath")
                    .into(holder.showImage)
            }

            val name = showData.optString(KEY_NAME, showData.optString(KEY_TITLE, ""))
            holder.showTitle.text = name

            var dateString = when {
                showData.optString("last_watched_at", "").isNotEmpty() ->
                    showData.optString("last_watched_at", "")
                showData.optString("rated_at", "").isNotEmpty() ->
                    showData.optString("rated_at", "")
                showData.optString("added_at", "").isNotEmpty() ->
                    showData.optString("added_at", "")
                else -> showData.optString("release_date", "")
            }

            val dateFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            )
            var parsedDate: Date? = null
            for (fmt in dateFormats) {
                try {
                    parsedDate = fmt.parse(dateString)
                    if (parsedDate != null) break
                } catch (_: ParseException) {
                }
            }
            if (parsedDate != null) {
                dateString = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                    .format(parsedDate)
            }
            holder.showDate.text = dateString

            val itemType = showData.optString("type", "movie")
            val tmdbId = showData.optInt("id", showData.optInt("tmdb", 0))

            if (itemType == "movie") {
                holder.watchedProgressBar?.visibility = View.GONE
                holder.seasonEpisodeText.visibility = View.GONE
            } else {
                val watchedEpisodes = getWatchedEpisodesShow(tmdbId)
                val watchedCount = if (watchedEpisodes.isNotEmpty()) {
                    watchedEpisodes.size
                } else {
                    showData.optInt("watched_episodes_count", 0)
                }
                val totalFromTmdb = parseEpisodesTmdb(showData.optString("seasons_episode_show_tmdb", ""))
                val totalEpisodes = if (totalFromTmdb > 0) {
                    totalFromTmdb
                } else {
                    showData.optInt("total_episodes_count", 0)
                }

                if (totalEpisodes > 0) {
                    val episodesLeft = (totalEpisodes - watchedCount).coerceAtLeast(0)
                    val progress = ((watchedCount.toFloat() / totalEpisodes.toFloat()) * 100).toInt().coerceIn(0, 100)
                    holder.watchedProgressBar?.progress = progress
                    holder.watchedProgressBar?.visibility = View.VISIBLE
                    holder.seasonEpisodeText.visibility = View.VISIBLE
                    if (watchedCount >= totalEpisodes) {
                        holder.seasonEpisodeText.text = context.getString(R.string.watched)
                    } else {
                        holder.seasonEpisodeText.text = context.getString(
                            R.string.ep_progress_text,
                            watchedCount,
                            totalEpisodes,
                            episodesLeft
                        )
                    }
                } else {
                    holder.watchedProgressBar?.visibility = View.GONE
                    holder.seasonEpisodeText.visibility = View.GONE
                }
            }

            if (!mGridView) {
                holder.showDescription?.text = showData.optString(KEY_DESCRIPTION, "")
                val ratingVal = if (showData.optInt("user_rating", 0) > 0) {
                    showData.optInt("user_rating", 0).toFloat() / 2f
                } else {
                    showData.optString(KEY_RATING, "0").toFloatOrNull()?.div(2f) ?: 0f
                }
                holder.showRating?.rating = ratingVal

                val genreIdsString = showData.optString(KEY_GENRES, "[]")
                val genreIds = if (genreIdsString.startsWith("[")) JSONArray(genreIdsString) else JSONArray()
                val genrePrefs = context.getSharedPreferences("GenreList", Context.MODE_PRIVATE)
                val genreNames = StringBuilder()

                for (i in 0 until genreIds.length()) {
                    val genreId = genreIds.optInt(i).toString()
                    val genreName = genrePrefs.getString(genreId, "")
                    if (!genreName.isNullOrEmpty()) {
                        genreNames.append(", ").append(genreName)
                    }
                }
                holder.showGenre?.text = if (genreNames.isNotEmpty()) genreNames.substring(2) else ""
            }

            if (itemType != "movie") {
                holder.itemView.setOnLongClickListener {
                    openSeasonEpisodeBottomSheet(showData)
                    true
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        holder.itemView.setOnClickListener { view: View ->
            val intent = Intent(view.context, DetailActivity::class.java)
            intent.putExtra("movieObject", showData.toString())
            val isMovie = showData.optString("type") == "movie"
            intent.putExtra("isMovie", isMovie)
            view.context.startActivity(intent)
        }
    }

    private fun openSeasonEpisodeBottomSheet(showData: JSONObject) {
        val tmdbId = showData.optInt("id", showData.optInt("tmdb", 0))
        val simklId = showData.optInt("simkl_id", 0)
        val showTitle = showData.optString("show_title", showData.optString("name", showData.optString("title", "")))
        val seasonsStr = showData.optString("seasons_episode_show_tmdb", "")

        bottomSheetDialog = BottomSheetDialog(context)
        bottomSheetBinding = BottomSheetSeasonEpisodeBinding.inflate(LayoutInflater.from(context))
        val chipGroupSeasons = bottomSheetBinding!!.chipGroupSeasons
        val recyclerViewEpisodes = bottomSheetBinding!!.recyclerViewEpisodes
        bottomSheetBinding!!.seasonActionButton.visibility = View.VISIBLE
        recyclerViewEpisodes.layoutManager = LinearLayoutManager(context)

        val seasons = parseSeasonsTmdb(seasonsStr)
        val nextEpisode = getNextEpisodeDetails(tmdbId, seasons, seasonsStr)

        val maxVisibleChips = 5
        var isExpanded = false
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

        seasons.forEach { seasonNumber ->
            val chip = Chip(context).apply {
                tag = seasonNumber
                text = context.getString(R.string.season_p, seasonNumber)
                isCheckable = true
                setOnClickListener { chipView ->
                    val season = (chipView as Chip).tag as Int
                    val episodes = parseEpisodesForSeasonTmdb(seasonsStr, season)
                    val watchedEpisodes = getWatchedEpisodesFromDb(tmdbId, season)

                    currentEpisodeAdapter = EpisodeSimklAdapter(
                        episodes,
                        watchedEpisodes,
                        showData,
                        season,
                        context,
                        simklAccessToken,
                        this@ShowSimklAdapter
                    )
                    recyclerViewEpisodes.adapter = currentEpisodeAdapter

                    bottomSheetBinding?.seasonActionButton?.let { seasonButton ->
                        seasonButton.isEnabled = true
                        val isWholeSeasonWatched = episodes.isNotEmpty() && episodes.all { it in watchedEpisodes }
                        seasonButton.text = context.getString(
                            if (isWholeSeasonWatched) R.string.mark_season_unwatched
                            else R.string.mark_season_watched
                        )
                        seasonButton.icon = AppCompatResources.getDrawable(
                            context,
                            if (isWholeSeasonWatched) R.drawable.ic_close else R.drawable.ic_done_2
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
            val selectedChip = (0 until chipGroupSeasons.childCount)
                .map { chipGroupSeasons.getChildAt(it) }
                .filterIsInstance<Chip>()
                .firstOrNull { it.isChecked }

            (selectedChip?.tag as? Int)?.let { season ->
                val currentDateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                val episodes = parseEpisodesForSeasonTmdb(seasonsStr, season)
                val watchedEpisodes = getWatchedEpisodesFromDb(tmdbId, season)
                val isWholeSeasonWatched = episodes.isNotEmpty() && episodes.all { it in watchedEpisodes }
                val endpoint = if (isWholeSeasonWatched) ACTION_MARK_SEASON_UNWATCHED else ACTION_MARK_SEASON_WATCHED

                val episodesJsonArray = JSONArray()
                episodes.forEach { epNum ->
                    episodesJsonArray.put(JSONObject().apply {
                        put("number", epNum)
                        if (!isWholeSeasonWatched) {
                            put("watched_at", currentDateTime)
                        }
                    })
                }

                val seasonObj = JSONObject().apply {
                    put("number", season)
                    put("episodes", episodesJsonArray)
                }

                val idsObject = JSONObject().apply {
                    put("tmdb", tmdbId)
                    if (simklId > 0) put("simkl", simklId)
                }

                val showsObject = JSONObject().apply {
                    put("shows", JSONArray().put(JSONObject().apply {
                        if (!isWholeSeasonWatched && showTitle.isNotEmpty()) {
                            put("title", showTitle)
                        }
                        put("ids", idsObject)
                        put("seasons", JSONArray().put(seasonObj))
                    }))
                }

                val simklSync = SimklSync(simklAccessToken, context)
                simklSync.post(endpoint, showsObject, object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                context.getString(R.string.failed_to_sync, "season"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val isSuccess = response.isSuccessful
                        val msg = response.message
                        response.close()
                        if (isSuccess) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val db = SimklDatabaseHelper(context)
                                if (endpoint == ACTION_MARK_SEASON_WATCHED) {
                                    episodes.forEach { epNum ->
                                        db.addEpisodeToWatched(simklId, tmdbId, season, epNum, currentDateTime)
                                    }
                                } else {
                                    db.removeSeasonFromWatched(tmdbId, season)
                                }
                            }
                        }
                        Handler(Looper.getMainLooper()).post {
                            if (isSuccess) {
                                Toast.makeText(
                                    context,
                                    if (endpoint == ACTION_MARK_SEASON_WATCHED) R.string.season_marked_watched
                                    else R.string.season_marked_unwatched,
                                    Toast.LENGTH_SHORT
                                ).show()

                                bottomSheetBinding?.seasonActionButton?.let { seasonButton ->
                                    seasonButton.text = context.getString(
                                        if (endpoint == ACTION_MARK_SEASON_WATCHED) R.string.mark_season_unwatched
                                        else R.string.mark_season_watched
                                    )
                                    seasonButton.icon = AppCompatResources.getDrawable(
                                        context,
                                        if (endpoint == ACTION_MARK_SEASON_WATCHED) R.drawable.ic_close
                                        else R.drawable.ic_done_2
                                    )
                                }

                                currentEpisodeAdapter?.let { adapter ->
                                    val newWatchedStatus = endpoint == ACTION_MARK_SEASON_WATCHED
                                    episodes.forEach { epNum ->
                                        adapter.updateEpisodeWatched(epNum, newWatchedStatus)
                                    }
                                }
                            } else {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                })
            }
        }

        if (nextEpisode != null) {
            val (seasonNumberN, episodeNumberN) = nextEpisode
            showInitialEpisode(tmdbId, simklId, seasonNumberN!!, episodeNumberN!!, showTitle)
        } else {
            bottomSheetBinding!!.episodeName.visibility = View.GONE
            bottomSheetBinding!!.episodeOverview.visibility = View.GONE
            bottomSheetBinding!!.episodeAirDate.visibility = View.GONE
            bottomSheetBinding!!.imageView.visibility = View.GONE
            bottomSheetBinding!!.chipEpS.visibility = View.GONE
            bottomSheetBinding!!.addToWatched.visibility = View.GONE
        }

        bottomSheetDialog?.setContentView(bottomSheetBinding!!.root)
        bottomSheetDialog?.show()
    }

    private fun showInitialEpisode(
        tvShowId: Int,
        simklId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String
    ) {
        bottomSheetBinding?.chipEpS?.visibility = View.VISIBLE
        bottomSheetBinding?.linearLayout?.visibility = View.VISIBLE
        bottomSheetBinding?.addToWatched?.visibility = View.VISIBLE
        bottomSheetBinding?.chipEpS?.text = "S${seasonNumber}:E${episodeNumber}"

        val isWatched = isEpisodeWatched(tvShowId, seasonNumber, episodeNumber)
        bottomSheetBinding?.addToWatched?.icon = AppCompatResources.getDrawable(
            context,
            if (isWatched) R.drawable.ic_done_2 else R.drawable.ic_close
        )

        bottomSheetBinding?.addToWatched?.setOnClickListener {
            val isCurrentlyWatched = isEpisodeWatched(tvShowId, seasonNumber, episodeNumber)
            val newWatchedStatus = !isCurrentlyWatched
            val currentDateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val endpoint = if (isCurrentlyWatched) "sync/history/remove" else "sync/history"
            val epObj = JSONObject().apply {
                put("number", episodeNumber)
                if (!isCurrentlyWatched) put("watched_at", currentDateTime)
            }
            val seasonObj = JSONObject().apply {
                put("number", seasonNumber)
                put("episodes", JSONArray().put(epObj))
            }
            val showObj = JSONObject().apply {
                if (!isCurrentlyWatched && title.isNotEmpty()) put("title", title)
                put("ids", JSONObject().apply {
                    put("tmdb", tvShowId)
                    if (simklId > 0) put("simkl", simklId)
                })
                put("seasons", JSONArray().put(seasonObj))
            }
            val payload = JSONObject().apply {
                put("shows", JSONArray().put(showObj))
            }

            val simklSync = SimklSync(simklAccessToken, context)
            simklSync.post(endpoint, payload, object : Callback {
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
                    val isSuccess = response.isSuccessful
                    response.close()
                    if (isSuccess) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = SimklDatabaseHelper(context)
                            if (newWatchedStatus) {
                                db.addEpisodeToWatched(simklId, tvShowId, seasonNumber, episodeNumber, currentDateTime)
                            } else {
                                db.removeEpisodeFromWatched(tvShowId, seasonNumber, episodeNumber)
                            }
                        }
                    }
                    Handler(Looper.getMainLooper()).post {
                        if (isSuccess) {
                            bottomSheetBinding?.addToWatched?.icon = AppCompatResources.getDrawable(
                                context,
                                if (newWatchedStatus) R.drawable.ic_done_2 else R.drawable.ic_close
                            )
                            currentEpisodeAdapter?.updateEpisodeWatched(episodeNumber, newWatchedStatus)
                        }
                    }
                }
            })
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

    override fun onEpisodeClick(
        tvShowId: Int,
        simklId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String
    ) {
        showInitialEpisode(tvShowId, simklId, seasonNumber, episodeNumber, title)
    }

    override fun onEpisodeWatchedStatusChanged(
        tvShowId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        isWatched: Boolean
    ) {
        bottomSheetBinding?.let {
            if (it.chipEpS.text == "S${seasonNumber}:E${episodeNumber}") {
                it.addToWatched.icon = AppCompatResources.getDrawable(
                    context,
                    if (isWatched) R.drawable.ic_close else R.drawable.ic_done_2
                )
            }
        }
    }

    private fun getNextEpisodeDetails(
        showTmdbId: Int,
        seasons: List<Int>,
        episodeSeason: String
    ): Pair<Int?, Int?>? {
        for (seasonNumber in seasons) {
            val episodes = parseEpisodesForSeasonTmdb(episodeSeason, seasonNumber)
            val watchedEpisodes = getWatchedEpisodesFromDb(showTmdbId, seasonNumber)
            for (episodeNumber in episodes) {
                if (episodeNumber !in watchedEpisodes) {
                    return Pair(seasonNumber, episodeNumber)
                }
            }
        }
        return null
    }

    private fun isEpisodeWatched(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int): Boolean {
        return SimklDatabaseHelper(context).isEpisodeWatched(showTmdbId, seasonNumber, episodeNumber)
    }

    private fun getWatchedEpisodesFromDb(showTmdbId: Int, seasonNumber: Int): MutableList<Int> {
        return SimklDatabaseHelper(context).getWatchedEpisodesForSeason(showTmdbId, seasonNumber).toMutableList()
    }

    private fun getWatchedEpisodesShow(showTmdbId: Int): List<Pair<Int, Int>> {
        val dbHelper = SimklDatabaseHelper(context)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            SimklDatabaseHelper.TABLE_SEASON_EPISODE_WATCHED,
            arrayOf(SimklDatabaseHelper.COL_SEASON_NUMBER, SimklDatabaseHelper.COL_EPISODE_NUMBER),
            "${SimklDatabaseHelper.COL_SHOW_TMDB_ID} = ?",
            arrayOf(showTmdbId.toString()),
            null, null, null
        )
        val watchedEpisodes = mutableListOf<Pair<Int, Int>>()
        while (cursor.moveToNext()) {
            val s = cursor.getInt(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_SEASON_NUMBER))
            val e = cursor.getInt(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_EPISODE_NUMBER))
            watchedEpisodes.add(Pair(s, e))
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

    private fun fetchAndDisplayEpisodeDetails(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeName: TextView,
        episodeOverview: TextView,
        episodeAirDate: TextView,
        episodeImageView: ImageView,
        loadHdImage: Boolean,
        apiKey: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = NetworkClient.client
                val request = Request.Builder()
                    .url("https://api.themoviedb.org/3/tv/$seriesId/season/$seasonNumber/episode/$episodeNumber?api_key=$apiKey")
                    .build()
                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()?.let { JSONObject(it) }

                if (jsonResponse != null) {
                    val name = jsonResponse.optString("name", "N/A")
                    val overview = jsonResponse.optString("overview", "No overview available.")
                    val stillPath = jsonResponse.optString("still_path")
                    val airDate = jsonResponse.optString("air_date", "")

                    var formattedAirDate = airDate
                    try {
                        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val parsedDate = formatter.parse(airDate)
                        if (parsedDate != null) {
                            formattedAirDate = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                                .format(parsedDate)
                        }
                    } catch (_: ParseException) {
                    }

                    withContext(Dispatchers.Main) {
                        episodeName.text = name
                        episodeOverview.text = overview
                        episodeAirDate.text = formattedAirDate
                        episodeName.visibility = View.VISIBLE
                        episodeOverview.visibility = View.VISIBLE
                        episodeAirDate.visibility = View.VISIBLE

                        Picasso.get().cancelRequest(episodeImageView)
                        episodeImageView.setImageDrawable(null)

                        if (stillPath.isNotEmpty() && stillPath != "null") {
                            val imageSize = if (loadHdImage) "w780" else "w500"
                            Picasso.get()
                                .load("https://image.tmdb.org/t/p/$imageSize$stillPath")
                                .into(episodeImageView)
                            episodeImageView.visibility = View.VISIBLE
                        } else {
                            episodeImageView.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    class ShowItemViewHolder(
        val cardBinding: ShowCardTraktBinding?,
        val gridBinding: ShowGridCardTraktBinding?
    ) : RecyclerView.ViewHolder(cardBinding?.root ?: gridBinding!!.root) {
        val showView: View = cardBinding?.root ?: gridBinding!!.root
        val showTitle: TextView = cardBinding?.title ?: gridBinding!!.title
        val showImage: ImageView = cardBinding?.image ?: gridBinding!!.image
        val showDescription: TextView? = cardBinding?.description
        val showGenre: TextView? = cardBinding?.genre
        val showRating: RatingBar? = cardBinding?.rating
        val showDate: TextView = cardBinding?.date ?: gridBinding!!.date
        val seasonEpisodeText: TextView = cardBinding?.seasonEpisodeText ?: gridBinding!!.seasonEpisodeText
        val watchedProgressBar: ProgressBar? = cardBinding?.watchedProgress ?: gridBinding?.watchedProgress
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_NAME = "name"
        const val KEY_POSTER = "poster_path"
        const val KEY_DESCRIPTION = "overview"
        const val KEY_GENRES = "genre_ids"
        const val KEY_RATING = "vote_average"
        private const val HD_IMAGE_SIZE = "key_hq_images"
        private const val ACTION_MARK_SEASON_WATCHED = "sync/history"
        private const val ACTION_MARK_SEASON_UNWATCHED = "sync/history/remove"
    }
}
