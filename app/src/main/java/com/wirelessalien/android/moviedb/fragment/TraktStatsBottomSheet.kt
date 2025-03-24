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

import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.BottomSheetTraktStatsBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class TraktStatsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetTraktStatsBinding
    private val client = OkHttpClient()
    private lateinit var preferences: SharedPreferences
    private var accessToken: String? = null
    private var clientId: String? = null



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetTraktStatsBinding.inflate(inflater, container, false)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        accessToken = preferences.getString("trakt_access_token", "") ?: ""
        clientId = ConfigHelper.getConfigValue(requireContext(), "client_id")
        fetchStats()
        return binding.root
    }

    private fun fetchStats() {
        binding.shimmerFrameLayout1.visibility = View.VISIBLE
        binding.shimmerFrameLayout1.startShimmer()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        val request = Request.Builder()
                .url("https://api.trakt.tv/users/me/stats")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("trakt-api-version", "2")
                .addHeader("trakt-api-key", clientId!!)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonObject = JSONObject(responseBody)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        displayStats(jsonObject)
                        binding.shimmerFrameLayout1.visibility = View.GONE
                        binding.shimmerFrameLayout1.stopShimmer()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        binding.shimmerFrameLayout1.visibility = View.GONE
                        binding.shimmerFrameLayout1.stopShimmer()
                    }
                }
            }
        }
    }

    private fun displayStats(jsonObject: JSONObject) {
        val movies = jsonObject.getJSONObject("movies")
        val shows = jsonObject.getJSONObject("shows")
        val episodes = jsonObject.getJSONObject("episodes")

        binding.moviesWatched.text = Editable.Factory.getInstance().newEditable(movies.getInt("watched").toString())
        binding.moviesCollected.text = Editable.Factory.getInstance().newEditable(movies.getInt("collected").toString())
        binding.moviesMinutes.text = Editable.Factory.getInstance().newEditable(convertMinutes(movies.getInt("minutes")))

        binding.showsWatched.text = Editable.Factory.getInstance().newEditable(shows.getInt("watched").toString())
        binding.showsCollected.text = Editable.Factory.getInstance().newEditable(shows.getInt("collected").toString())

        binding.episodesMinutes.text = Editable.Factory.getInstance().newEditable(convertMinutes(episodes.getInt("minutes")))
    }

    private fun convertMinutes(minutes: Int): String {
        val years = minutes / (60 * 24 * 365)
        val months = (minutes % (60 * 24 * 365)) / (60 * 24 * 30)
        val days = (minutes % (60 * 24 * 30)) / (60 * 24)
        val hours = (minutes % (60 * 24)) / 60
        val mins = minutes % 60

        return buildString {
            if (years > 0) append(requireContext().resources.getQuantityString(R.plurals.years, years, years) + " ")
            if (months > 0) append(requireContext().resources.getQuantityString(R.plurals.months, months, months) + " ")
            if (days > 0) append(requireContext().resources.getQuantityString(R.plurals.days, days, days) + " ")
            if (hours > 0) append(requireContext().resources.getQuantityString(R.plurals.hours, hours, hours) + " ")
            if (mins > 0) append(requireContext().resources.getQuantityString(R.plurals.minutes, mins, mins))
        }.trim()
    }
}