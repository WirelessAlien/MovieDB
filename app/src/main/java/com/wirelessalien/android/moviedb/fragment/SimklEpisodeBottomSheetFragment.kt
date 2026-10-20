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

package com.wirelessalien.android.moviedb.fragment

import android.icu.text.DateFormat
import android.icu.util.TimeZone
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.NetworkClient
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.EpisodeSimklAdapter
import com.wirelessalien.android.moviedb.databinding.BottomSheetSeasonEpisodeBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.SimklDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
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
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SimklEpisodeBottomSheetFragment :
    BottomSheetDialogFragment(),
    EpisodeSimklAdapter.EpisodeClickListener {

    private var _binding: BottomSheetSeasonEpisodeBinding? = null
    private val binding get() = _binding!!

    private var showId: Int = 0
    private var initialSeasonNumber: Int = 0
    private var initialEpisodeNumber: Int = 0
    private var showTitle: String = ""
    private var isMovie: Boolean = false
    private var movieDataObject: JSONObject? = null

    private var simklAccessToken: String? = null
    private var apiKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSeasonEpisodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.shimmerFrameLayout.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE

        arguments?.let {
            showId = it.getInt("id")
            initialSeasonNumber = it.getInt("seasonNumber")
            initialEpisodeNumber = it.getInt("episodeNumber")
            showTitle = it.getString("show_title", "")
            isMovie = it.getBoolean("isMovie", false)
            movieDataObject = it.getString("showObject")?.let { objStr -> JSONObject(objStr) }
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        simklAccessToken = preferences.getString("simkl_access_token", null)
        apiKey = ConfigHelper.getConfigValue(requireContext(), "api_key")

        setupViews()
    }

    private fun setupViews() {
        binding.recyclerViewEpisodes.layoutManager = LinearLayoutManager(context)

        lifecycleScope.launch {
            val seasons = TmdbDetailsDatabaseHelper(requireContext()).use {
                it.getSeasonsForShow(showId)
            }
            val showData = JSONObject().apply {
                put("id", showId)
                put("tmdb", showId)
                put("show_title", showTitle)
            }

            seasons.forEach { sNum ->
                val chip = Chip(requireContext()).apply {
                    text = getString(R.string.season_p, sNum)
                    isCheckable = true
                    setOnClickListener {
                        val episodes = TmdbDetailsDatabaseHelper(requireContext()).use {
                            it.getEpisodesForSeason(showId, sNum)
                        }
                        val watchedEpisodes = SimklDatabaseHelper(requireContext()).use {
                            it.getWatchedEpisodesForSeason(showId, sNum).toMutableList()
                        }

                        val episodeAdapter = EpisodeSimklAdapter(
                            episodes,
                            watchedEpisodes,
                            showData,
                            sNum,
                            requireContext(),
                            simklAccessToken ?: "",
                            this@SimklEpisodeBottomSheetFragment
                        )
                        binding.recyclerViewEpisodes.adapter = episodeAdapter
                    }
                }
                binding.chipGroupSeasons.addView(chip)
            }

            binding.chipGroupSeasons.children.filterIsInstance<Chip>().find {
                it.text == getString(R.string.season_p, initialSeasonNumber)
            }?.performClick()

            onEpisodeClick(showId, 0, initialSeasonNumber, initialEpisodeNumber, showTitle)
        }
    }

    override fun onEpisodeClick(
        tvShowId: Int,
        simklId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String
    ) {
        binding.chipEpS.text = "S${seasonNumber}:E${episodeNumber}"

        val isWatched = SimklDatabaseHelper(requireContext()).use {
            it.isEpisodeWatched(tvShowId, seasonNumber, episodeNumber)
        }
        updateWatchedIcon(isWatched)

        binding.addToWatched.setOnClickListener {
            val isCurrentlyWatched = SimklDatabaseHelper(requireContext()).use {
                it.isEpisodeWatched(tvShowId, seasonNumber, episodeNumber)
            }
            val currentDateTime = android.icu.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.ENGLISH
            ).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val endpoint = if (isCurrentlyWatched) "sync/history/remove" else "sync/history"
            val epObj = JSONObject().apply {
                put("number", episodeNumber)
                if (!isCurrentlyWatched) {
                    put("watched_at", currentDateTime)
                }
            }
            val seasonObj = JSONObject().apply {
                put("number", seasonNumber)
                put("episodes", JSONArray().put(epObj))
            }
            val showObj = JSONObject().apply {
                if (!isCurrentlyWatched && showTitle.isNotEmpty()) {
                    put("title", showTitle)
                }
                put("ids", JSONObject().apply { put("tmdb", tvShowId) })
                put("seasons", JSONArray().put(seasonObj))
            }
            val payload = JSONObject().apply {
                put("shows", JSONArray().put(showObj))
            }

            val simklSync = SimklSync(simklAccessToken ?: "", requireContext())
            simklSync.post(endpoint, payload, object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    activity?.runOnUiThread {
                        Toast.makeText(
                            context,
                            context?.getString(R.string.failed_to_sync, endpoint),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val isSuccess = response.isSuccessful
                    response.close()
                    if (isSuccess) {
                        CoroutineScope(Dispatchers.IO).launch {
                            SimklDatabaseHelper(requireContext()).use { db ->
                                if (endpoint == "sync/history") {
                                    db.addEpisodeToWatched(
                                        simklId,
                                        tvShowId,
                                        seasonNumber,
                                        episodeNumber,
                                        currentDateTime
                                    )
                                } else {
                                    db.removeEpisodeFromWatched(tvShowId, seasonNumber, episodeNumber)
                                }
                            }
                            addItemtoTmdb()
                        }
                    }
                    activity?.runOnUiThread {
                        if (isSuccess) {
                            val newStatus = endpoint == "sync/history"
                            updateWatchedIcon(newStatus)
                            (binding.recyclerViewEpisodes.adapter as? EpisodeSimklAdapter)
                                ?.updateEpisodeWatched(episodeNumber, newStatus)
                        }
                    }
                }
            })
        }

        fetchAndDisplayEpisodeDetails(tvShowId, seasonNumber, episodeNumber)
    }

    private fun addItemtoTmdb() {
        val dbHelper = TmdbDetailsDatabaseHelper(requireContext())
        val tmdbId = movieDataObject?.optInt("id") ?: showId
        val name = if (isMovie) movieDataObject?.optString("title") else movieDataObject?.optString("name")
        val backdropPath = movieDataObject?.optString("backdrop_path")
        val posterPath = movieDataObject?.optString("poster_path")
        val summary = movieDataObject?.optString("overview")
        val voteAverage = movieDataObject?.optDouble("vote_average")
        val type = if (isMovie) "movie" else "show"
        val releaseDate = if (isMovie) movieDataObject?.optString("release_date") else movieDataObject?.optString("first_air_date")
        val genreIds = movieDataObject?.optJSONArray("genres")?.let { genresArray ->
            val ids = (0 until genresArray.length()).joinToString(",") { i ->
                genresArray.getJSONObject(i).getInt("id").toString()
            }
            "[$ids]"
        }
        val seasonEpisodeCount = movieDataObject?.optJSONArray("seasons")
        val seasonsEpisodes = StringBuilder()

        for (i in 0 until (seasonEpisodeCount?.length() ?: 0)) {
            val season = seasonEpisodeCount?.getJSONObject(i)
            val seasonNum = season?.getInt("season_number")
            if (seasonNum == 0) continue

            val episodeCount = season?.getInt("episode_count") ?: 0
            val episodesList = (1..episodeCount).toList()

            seasonsEpisodes.append("$seasonNum{${episodesList.joinToString(",")}}")
            if (i < (seasonEpisodeCount?.length() ?: 0) - 1) {
                seasonsEpisodes.append(",")
            }
        }

        dbHelper.addItem(
            tmdbId,
            name ?: showTitle,
            backdropPath ?: "",
            posterPath ?: "",
            summary ?: "",
            voteAverage ?: 0.0,
            releaseDate ?: "",
            genreIds ?: "",
            seasonsEpisodes.toString(),
            type
        )
    }

    override fun onEpisodeWatchedStatusChanged(
        tvShowId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        isWatched: Boolean
    ) {
        if (_binding != null && binding.chipEpS.text == "S${seasonNumber}:E${episodeNumber}") {
            updateWatchedIcon(!isWatched)
        }
    }

    private fun updateWatchedIcon(isWatched: Boolean) {
        val iconRes = if (isWatched) R.drawable.ic_done_2 else R.drawable.ic_close
        binding.addToWatched.setIconResource(iconRes)
    }

    private fun fetchAndDisplayEpisodeDetails(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = NetworkClient.client
                val request = Request.Builder()
                    .url("https://api.themoviedb.org/3/tv/$seriesId/season/$seasonNumber/episode/$episodeNumber?api_key=$apiKey")
                    .build()
                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()?.let { JSONObject(it) }

                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.contentLayout.visibility = View.VISIBLE
                    if (jsonResponse != null) {
                        binding.episodeName.text = jsonResponse.optString("name", "N/A")
                        binding.episodeOverview.text =
                            jsonResponse.optString("overview", "No overview available.")

                        val airDate = jsonResponse.optString("air_date")
                        if (airDate.isNotEmpty()) {
                            try {
                                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val date = inputFormat.parse(airDate)
                                val formattedDate =
                                    DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                                        .format(date)
                                binding.episodeAirDate.text = formattedDate
                            } catch (e: Exception) {
                                binding.episodeAirDate.text = "N/A"
                            }
                        } else {
                            binding.episodeAirDate.text = "N/A"
                        }

                        val stillPath = jsonResponse.optString("still_path")
                        if (stillPath.isNotEmpty() && stillPath != "null") {
                            Picasso.get().load("https://image.tmdb.org/t/p/w500$stillPath")
                                .into(binding.imageView)
                            binding.imageView.visibility = View.VISIBLE
                        } else {
                            binding.imageView.visibility = View.GONE
                        }
                    } else {
                        binding.imageView.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.contentLayout.visibility = View.VISIBLE
                    Toast.makeText(
                        context,
                        getString(R.string.error_loading_data) + ":" + e.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SimklEpisodeBottomSheetFragment"

        fun newInstance(
            showId: Int,
            seasonNumber: Int,
            episodeNumber: Int,
            showTitle: String,
            isMovie: Boolean,
            tmdbJSONObject: JSONObject?
        ): SimklEpisodeBottomSheetFragment {
            val args = Bundle().apply {
                putInt("id", showId)
                putInt("seasonNumber", seasonNumber)
                putInt("episodeNumber", episodeNumber)
                putString("show_title", showTitle)
                putBoolean("isMovie", isMovie)
                putString("showObject", tmdbJSONObject?.toString())
            }
            return SimklEpisodeBottomSheetFragment().apply {
                arguments = args
            }
        }
    }
}
