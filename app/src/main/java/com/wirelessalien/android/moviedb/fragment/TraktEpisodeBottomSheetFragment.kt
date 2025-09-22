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
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.EpisodeTraktAdapter
import com.wirelessalien.android.moviedb.databinding.BottomSheetSeasonEpisodeBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.trakt.TraktSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class TraktEpisodeBottomSheetFragment : BottomSheetDialogFragment(), EpisodeTraktAdapter.EpisodeClickListener {

    private var _binding: BottomSheetSeasonEpisodeBinding? = null
    private val binding get() = _binding!!

    private var showId: Int = 0
    private var initialSeasonNumber: Int = 0
    private var initialEpisodeNumber: Int = 0
    private var traktId: Int = 0
    private var showTitle: String? = null
    private var isMovie: Boolean = false
    private var movieDataObject: JSONObject? = null

    private var tktaccessToken: String? = null
    private var tktApiKey: String? = null
    private var apiKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
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
            traktId = it.getInt("trakt_id")
            showTitle = it.getString("show_title")
            isMovie = it.getBoolean("isMovie", false)
            movieDataObject = it.getString("showObject")?.let { objStr -> JSONObject(objStr) }

        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        tktaccessToken = preferences.getString("trakt_access_token", null)
        tktApiKey = ConfigHelper.getConfigValue(requireContext(), "client_id")
        apiKey = ConfigHelper.getConfigValue(requireContext(), "api_key")

        setupViews()
    }

    private fun setupViews() {
        binding.recyclerViewEpisodes.layoutManager = LinearLayoutManager(context)

        lifecycleScope.launch {
            if (traktId == 0) {
                Toast.makeText(context, getString(R.string.could_not_get_trakt_id_for_this_show), Toast.LENGTH_SHORT).show()
                dismiss()
                return@launch
            }

            val seasons = TmdbDetailsDatabaseHelper(requireContext()).use { it.getSeasonsForShow(showId) }
            val showData = JSONObject().apply {
                put("id", showId)
                put("trakt_id", traktId)
                put("show_title", showTitle)
            }

            seasons.forEach { sNum ->
                val chip = Chip(requireContext()).apply {
                    text = getString(R.string.season_p, sNum)
                    isCheckable = true
                    setOnClickListener {
                        val episodes = TmdbDetailsDatabaseHelper(requireContext()).use { it.getEpisodesForSeason(showId, sNum) }
                        val watchedEpisodes = getWatchedEpisodesFromDb(traktId, sNum)

                        val episodeAdapter = EpisodeTraktAdapter(episodes, watchedEpisodes, showData, sNum, requireContext(), tktaccessToken ?: "", tktApiKey ?: "", this@TraktEpisodeBottomSheetFragment)
                        binding.recyclerViewEpisodes.adapter = episodeAdapter
                    }
                }
                binding.chipGroupSeasons.addView(chip)
            }

            binding.chipGroupSeasons.children.filterIsInstance<Chip>().find {
                it.text == getString(R.string.season_p, initialSeasonNumber)
            }?.performClick()

            onEpisodeClick(showId, traktId, initialSeasonNumber, initialEpisodeNumber, -1, showTitle ?: "")
        }
    }

    override fun onEpisodeClick(tvShowId: Int, traktId: Int, seasonNumber: Int, episodeNumber: Int, episodeTraktId: Int, title: String) {
        binding.chipEpS.text = "S${seasonNumber}:E${episodeNumber}"

        fun updateViewForSelectedEpisode() {
            lifecycleScope.launch {
                val isWatched = getWatchedEpisodesFromDb(traktId, seasonNumber).contains(episodeNumber)
                updateWatchedIcon(isWatched)
            }
        }

        updateViewForSelectedEpisode()

        binding.addToWatched.setOnClickListener {
            val isCurrentlyWatched = getWatchedEpisodesFromDb(traktId, seasonNumber).contains(episodeNumber)

            val currentDateTime = android.icu.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.ENGLISH
            ).format(java.util.Date())

            lifecycleScope.launch {
                val episodeData = traktFetchepisodedata(traktId, seasonNumber, episodeNumber)
                if (episodeData != null) {
                    val episodeObject = traktCreatetraktepisodeobject(episodeData)
                    val endpoint = if (isCurrentlyWatched) "sync/history/remove" else "sync/history"
                    traktSync(episodeObject, endpoint, tvShowId, traktId, title, seasonNumber, episodeNumber, currentDateTime)
                }
            }
        }

        traktFetchanddisplayepisodedetails(tvShowId, seasonNumber, episodeNumber)
    }

    private fun addItemtoTmdb() {
        val dbHelper = TmdbDetailsDatabaseHelper(requireContext())
        val tmdbId = movieDataObject?.optInt("id")
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
            val seasonNumber = season?.getInt("season_number")

            // Skip specials (season_number == 0)
            if (seasonNumber == 0) continue

            val episodeCount = season?.getInt("episode_count")?: 0
            val episodesList = (1..episodeCount).toList()

            seasonsEpisodes.append("$seasonNumber{${episodesList.joinToString(",")}}")
            if (i < (seasonEpisodeCount?.length() ?: 0) - 1) {
                seasonsEpisodes.append(",")
            }
        }

        dbHelper.addItem(tmdbId ?: 0, name ?: "", backdropPath ?: "", posterPath ?: "", summary ?: "", voteAverage ?: 0.0, releaseDate ?: "", genreIds?: "", seasonsEpisodes.toString(), type)
    }

    override fun onEpisodeWatchedStatusChanged(tvShowId: Int, seasonNumber: Int, episodeNumber: Int, isWatched: Boolean) {
        // This is called when an item in the adapter is toggled.
        // We need to update the main button if the selected episode is the one that was changed.
        if (binding.chipEpS.text == "S${seasonNumber}:E${episodeNumber}") {
            updateWatchedIcon(!isWatched) // !isWatched is the new state
        }
    }

    private fun updateWatchedIcon(isWatched: Boolean) {
        val iconRes = if (isWatched) R.drawable.ic_done_2 else R.drawable.ic_close
        binding.addToWatched.setIconResource(iconRes)
    }

    private fun getWatchedEpisodesFromDb(showTraktId: Int, seasonNumber: Int): MutableList<Int> {
        val dbHelper = TraktDatabaseHelper(requireContext())
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
                val epNum = cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_EPISODE_NUMBER))
                watchedEpisodes.add(epNum)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return watchedEpisodes
    }

    private fun traktFetchanddisplayepisodedetails(seriesId: Int, seasonNumber: Int, episodeNumber: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.themoviedb.org/3/tv/$seriesId/season/$seasonNumber/episode/$episodeNumber?api_key=$apiKey")
                    .build()
                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()?.let { JSONObject(it) }

                withContext(Dispatchers.Main) {
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.contentLayout.visibility = View.VISIBLE
                    if (jsonResponse != null) {
                        binding.episodeName.text = jsonResponse.optString("name", "N/A")
                        binding.episodeOverview.text = jsonResponse.optString("overview", "No overview available.")

                        val airDate = jsonResponse.optString("air_date")
                        if (airDate.isNotEmpty()) {
                            try {
                                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val date = inputFormat.parse(airDate)
                                val formattedDate = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault()).format(date)
                                binding.episodeAirDate.text = formattedDate
                            } catch (e: Exception) {
                                binding.episodeAirDate.text = "N/A"
                            }
                        } else {
                            binding.episodeAirDate.text = "N/A"
                        }

                        val stillPath = jsonResponse.optString("still_path")
                        if (stillPath.isNotEmpty() && stillPath != "null") {
                            Picasso.get().load("https://image.tmdb.org/t/p/w500$stillPath").into(binding.imageView)
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
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.contentLayout.visibility = View.VISIBLE
                    Toast.makeText(context, getString(R.string.error_loading_data) + ":" + e.message, Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
    }

    private suspend fun traktFetchepisodedata(traktId: Int, seasonNumber: Int, episodeNumber: Int): JSONObject? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.trakt.tv/shows/$traktId/seasons/$seasonNumber/episodes/$episodeNumber")
                .get()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer $tktaccessToken")
                .addHeader("trakt-api-version", "2")
                .addHeader("trakt-api-key", tktApiKey ?: "")
                .build()
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                response.body?.string()?.let { JSONObject(it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun traktCreatetraktepisodeobject(episodeData: JSONObject): JSONObject {
        val episodeIds = episodeData.getJSONObject("ids")
        val episodeObject = JSONObject().apply {
            put("season", episodeData.getInt("season"))
            put("number", episodeData.getInt("number"))
            put("title", episodeData.getString("title"))
            put("ids", episodeIds)
        }
        return JSONObject().apply {
            put("episodes", JSONArray().put(episodeObject))
        }
    }

    private fun traktSync(episodeJSONObject: JSONObject, endpoint: String, tmdbId: Int, traktId:Int, title: String, seasonNumber: Int, episodeNumber: Int, currentTime: String) {
        try {
            val traktApiService = TraktSync(tktaccessToken ?: "", requireContext())
            traktApiService.post(endpoint, episodeJSONObject, object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, context?.getString(R.string.failed_to_sync, endpoint), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    activity?.runOnUiThread {
                        if (response.isSuccessful) {
                            val dbHelper = TraktDatabaseHelper(requireContext())
                            val newStatus = endpoint == "sync/history"
                            if (newStatus) {
                                dbHelper.addEpisodeToWatched(traktId, tmdbId, seasonNumber, episodeNumber, currentTime)
                                dbHelper.addEpisodeToHistory(title, traktId, tmdbId, "episode", seasonNumber, episodeNumber, currentTime)
                            } else {
                                dbHelper.removeEpisodeFromWatched(tmdbId, seasonNumber, episodeNumber)
                                dbHelper.removeEpisodeFromHistory(tmdbId, seasonNumber, episodeNumber)
                            }
                            addItemtoTmdb()
                            updateWatchedIcon(newStatus)
                            (binding.recyclerViewEpisodes.adapter as? EpisodeTraktAdapter)?.updateEpisodeWatched(episodeNumber, newStatus)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            activity?.runOnUiThread {
                Toast.makeText(context, context?.getString(R.string.failed_to_sync, endpoint), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TraktEpisodeBottomSheetFragment"

        fun newInstance(showId: Int, traktId: Int, seasonNumber: Int, episodeNumber: Int, showTitle: String?, isMovie: Boolean, tmdbJSONObject: JSONObject?): TraktEpisodeBottomSheetFragment {
            val args = Bundle()
            args.putInt("id", showId)
            args.putInt("trakt_id", traktId)
            args.putInt("seasonNumber", seasonNumber)
            args.putInt("episodeNumber", episodeNumber)
            args.putString("show_title", showTitle)
            args.putBoolean("isMovie", isMovie)
            args.putString("showObject", tmdbJSONObject.toString())
            val fragment = TraktEpisodeBottomSheetFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
