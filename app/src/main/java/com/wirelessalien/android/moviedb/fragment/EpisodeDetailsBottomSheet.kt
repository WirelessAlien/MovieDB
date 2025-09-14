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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.BaseActivity
import com.wirelessalien.android.moviedb.activity.CastActivity
import com.wirelessalien.android.moviedb.adapter.EpisodeCastAdapter
import com.wirelessalien.android.moviedb.data.CastMember
import com.wirelessalien.android.moviedb.databinding.BottomSheetEpisodeDetailsBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class EpisodeDetailsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetEpisodeDetailsBinding
    private var tvShowId: Int = 0
    private var seasonNumber: Int = 0
    private var episodeNumber: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tvShowId = it.getInt(ARG_TV_SHOW_ID)
            seasonNumber = it.getInt(ARG_SEASON_NUMBER)
            episodeNumber = it.getInt(ARG_EPISODE_NUMBER)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetEpisodeDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showShimmer(true)
        fetchEpisodeDetails()
    }

    private fun showShimmer(show: Boolean) {
        if (show) {
            binding.shimmerFrameLayout.startShimmer()
            binding.shimmerFrameLayout.visibility = View.VISIBLE
            binding.contentView.visibility = View.GONE
        } else {
            binding.shimmerFrameLayout.stopShimmer()
            binding.shimmerFrameLayout.visibility = View.GONE
            binding.contentView.visibility = View.VISIBLE
        }
    }

    private fun fetchEpisodeDetails() {
        val apiKey = ConfigHelper.getConfigValue(requireContext(), "api_key")
        val language = BaseActivity.getLanguageParameter(requireContext()).replace("&language=", "")
        val url = "https://api.themoviedb.org/3/tv/$tvShowId/season/$seasonNumber/episode/$episodeNumber?api_key=$apiKey&append_to_response=credits&language=$language"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val jsonData = response.body?.string()
                val episodeObject = JSONObject(jsonData?: "{}")

                withContext(Dispatchers.Main) {
                    populateViews(episodeObject)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showShimmer(false)
                }
            }
        }
    }

    private fun populateViews(episodeObject: JSONObject) {
        showShimmer(false)
        binding.episodeTitle.text = episodeObject.getString("name")
        binding.episodeOverview.text = episodeObject.getString("overview")

        val airDate = episodeObject.getString("air_date")
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val parsedDate = inputFormat.parse(airDate)
            if (parsedDate != null) {
                val outputFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.DEFAULT, Locale.getDefault())
                val formattedDate = outputFormat.format(parsedDate)
                binding.episodeAirDate.text = formattedDate
            }
        } catch (e: Exception) {
            binding.episodeAirDate.text = airDate
        }


        binding.episodeRuntime.text = getString(R.string.runtime_minutes, episodeObject.getInt("runtime"))
        binding.episodeRating.text = getString(R.string.average_rating, episodeObject.getDouble("vote_average"), "10")

        val posterPath = episodeObject.getString("still_path")
        if (!posterPath.isNullOrEmpty() && posterPath != "null") {
            Picasso.get()
                .load("https://image.tmdb.org/t/p/w500$posterPath")
                .into(binding.episodePosterImage)
        }

        val credits = episodeObject.getJSONObject("credits")
        val cast = credits.getJSONArray("cast")
        val guestStars = credits.getJSONArray("guest_stars")

        val castList = mutableListOf<CastMember>()
        for (i in 0 until cast.length()) {
            val person = cast.getJSONObject(i)
            castList.add(
                CastMember(
                    person.getInt("id"),
                    person.getString("name"),
                    person.getString("character"),
                    person.optString("profile_path"),
                    person
                )
            )
        }

        val guestStarList = mutableListOf<CastMember>()
        for (i in 0 until guestStars.length()) {
            val person = guestStars.getJSONObject(i)
            guestStarList.add(
                CastMember(
                    person.getInt("id"),
                    person.getString("name"),
                    person.getString("character"),
                    person.optString("profile_path"),
                    person
                )
            )
        }

        setupRecyclerViews(castList, guestStarList)
    }

    private fun setupRecyclerViews(cast: List<CastMember>, guestStars: List<CastMember>) {
        if (guestStars.isNotEmpty()) {
            binding.guestStarsTitle.visibility = View.VISIBLE
            binding.guestStarsRecyclerView.visibility = View.VISIBLE
            binding.guestStarsRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            binding.guestStarsRecyclerView.adapter = EpisodeCastAdapter(requireContext(), guestStars) { castMember ->
                onPersonClicked(castMember)
            }
        } else {
            binding.guestStarsTitle.visibility = View.GONE
            binding.guestStarsRecyclerView.visibility = View.GONE
        }

        if (cast.isNotEmpty()) {
            binding.castTitle.visibility = View.VISIBLE
            binding.castRecyclerView.visibility = View.VISIBLE
            binding.castRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            binding.castRecyclerView.adapter = EpisodeCastAdapter(requireContext(), cast) { castMember ->
                onPersonClicked(castMember)
            }
        } else {
            binding.castTitle.visibility = View.GONE
            binding.castRecyclerView.visibility = View.GONE
        }
    }

    private fun onPersonClicked(castMember: CastMember) {
        val intent = Intent(activity, CastActivity::class.java)
        intent.putExtra("actorObject", castMember.actorObject.toString())
        startActivity(intent)
    }

    companion object {
        const val TAG = "EpisodeDetailsBottomSheet"
        private const val ARG_TV_SHOW_ID = "arg_tv_show_id"
        private const val ARG_SEASON_NUMBER = "arg_season_number"
        private const val ARG_EPISODE_NUMBER = "arg_episode_number"

        fun newInstance(tvShowId: Int, seasonNumber: Int, episodeNumber: Int): EpisodeDetailsBottomSheet {
            val args = Bundle()
            args.putInt(ARG_TV_SHOW_ID, tvShowId)
            args.putInt(ARG_SEASON_NUMBER, seasonNumber)
            args.putInt(ARG_EPISODE_NUMBER, episodeNumber)
            val fragment = EpisodeDetailsBottomSheet()
            fragment.arguments = args
            return fragment
        }
    }
}
