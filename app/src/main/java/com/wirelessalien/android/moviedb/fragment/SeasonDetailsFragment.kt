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

import android.content.ContentValues
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.TVSeasonDetailsActivity
import com.wirelessalien.android.moviedb.adapter.EpisodeAdapter
import com.wirelessalien.android.moviedb.data.Episode
import com.wirelessalien.android.moviedb.databinding.ActivityTvSeasonDetailsBinding
import com.wirelessalien.android.moviedb.databinding.FragmentTvSeasonDetailsBinding
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.TVSeasonDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.ParseException
import java.util.Collections
import java.util.Date
import java.util.Locale

class SeasonDetailsFragment : Fragment() {
    private var tvShowId = 0
    private var tvShowName: String = ""
    private var traktId = 0
    private var seasonNumber = 0
    private var currentTabNumber = 1
    private lateinit var tmdbObject: JSONObject
    private lateinit var pageChangeCallback: ViewPager2.OnPageChangeCallback
    private lateinit var dbHelper: EpisodeReminderDatabaseHelper
    private lateinit var binding: FragmentTvSeasonDetailsBinding
    private lateinit var activityBinding: ActivityTvSeasonDetailsBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTvSeasonDetailsBinding.inflate(inflater, container, false)
        activityBinding = (activity as TVSeasonDetailsActivity).getBinding()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvShowId = requireArguments().getInt(ARG_TV_SHOW_ID)
        seasonNumber = requireArguments().getInt(ARG_SEASON_NUMBER)
        traktId = requireArguments().getInt(ARG_TRAKT_ID)
        tvShowName = requireArguments().getString(ARG_TV_SHOW_NAME) ?: ""
        val tmdbObjectString = requireArguments().getString(ARG_TMDB_OBJECT)
        tmdbObject = JSONObject(tmdbObjectString?: "{}")

        activityBinding.toolbar.title = getString(R.string.seasons)
        pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentTabNumber = position
                seasonNumber = if (currentTabNumber == 0) 0 else currentTabNumber
                loadSeasonDetails()
                requireActivity().invalidateOptionsMenu()
            }
        }
        activityBinding.viewPager.registerOnPageChangeCallback(pageChangeCallback)
        loadSeasonDetails()

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.notification_menu, menu)
                dbHelper = EpisodeReminderDatabaseHelper(requireContext())
                val notificationItem = menu.findItem(R.id.action_notification)
                val tvShowId = requireArguments().getInt(ARG_TV_SHOW_ID)
                if (isShowInDatabase(tvShowId)) {
                    notificationItem.setIcon(R.drawable.ic_notifications_active)
                } else {
                    notificationItem.setIcon(R.drawable.ic_notification_add)
                }
                val adapter = binding.episodeRecyclerView.adapter as EpisodeAdapter?
                if (adapter != null) {
                    val episodes = adapter.episodes
                    if (episodes.isNotEmpty()) {
                        val latestEpisode = Collections.max(
                            episodes,
                            Comparator.comparingInt { obj: Episode -> obj.episodeNumber })

                        // Parse the air date of the latest episode
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        try {
                            val latestEpisodeDate = sdf.parse(latestEpisode.airDate)
                            val currentDate = Date()

                            // If the air date of the latest episode is older than the current date, disable the notification action
                            if (latestEpisodeDate != null && latestEpisodeDate.before(currentDate)) {
                                notificationItem.isEnabled = false
                            }
                        } catch (e: ParseException) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            override fun onPrepareMenu(menu: Menu) {
                val watchedItem = menu.findItem(R.id.action_watched)
                val adapter = binding.episodeRecyclerView.adapter as EpisodeAdapter?
                if (adapter != null) {
                    val episodes = adapter.episodes
                    val db = MovieDatabaseHelper(requireContext())
                    var allEpisodesInDatabase = true
                    for (episode in episodes) {
                        val seasonEpisodeNumbers: MutableMap<Int, List<Int>> = HashMap()
                        seasonEpisodeNumbers[currentTabNumber] = listOf(episode.episodeNumber)
                        if (!db.isEpisodeInDatabase(tvShowId, currentTabNumber, listOf(episode.episodeNumber))) {
                            allEpisodesInDatabase = false
                            break
                        }
                    }
                    if (allEpisodesInDatabase) {
                        watchedItem.setIcon(R.drawable.ic_visibility)
                    } else {
                        watchedItem.setIcon(R.drawable.ic_visibility_off)
                    }
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_notification -> {
                        dbHelper = EpisodeReminderDatabaseHelper(requireContext())
                        if (isShowInDatabase(requireArguments().getInt(ARG_TV_SHOW_ID))) {
                            val tvShowId = requireArguments().getInt(ARG_TV_SHOW_ID)
                            val showName = dbHelper.getShowNameById(tvShowId)
                            dbHelper.deleteData(tvShowId)
                            val message = getString(R.string.removed_from_reminder, showName)
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                            menuItem.setIcon(R.drawable.ic_notification_add)
                            true
                        } else {
                            val db = dbHelper.writableDatabase
                            val adapter = binding.episodeRecyclerView.adapter as EpisodeAdapter?
                            if (adapter != null) {
                                for (episode in adapter.episodes) {
                                    val values = ContentValues()
                                    val tvShowId = requireArguments().getInt(ARG_TV_SHOW_ID)
                                    values.put(EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID, tvShowId)
                                    val tvShowName = requireArguments().getString(ARG_TV_SHOW_NAME)
                                    values.put(EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME, tvShowName)
                                    values.put(EpisodeReminderDatabaseHelper.COLUMN_NAME, episode.name)
                                    values.put(EpisodeReminderDatabaseHelper.COLUMN_DATE, episode.airDate)
                                    values.put(EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER, episode.episodeNumber)
                                    val newRowId = db.insert(
                                        EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS, null, values)
                                    if (newRowId == -1L) {
                                        val message = getString(R.string.error_reminder_episode, tvShowName)
                                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                                        return true
                                    }
                                }
                                val message = getString(
                                    R.string.get_notified_for_episode, requireArguments().getString(
                                        ARG_TV_SHOW_NAME
                                    )
                                )
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                                menuItem.setIcon(R.drawable.ic_notifications_active)
                                true
                            } else {
                                false
                            }
                        }
                    }
                    R.id.action_watched -> {
                        val adapter = binding.episodeRecyclerView.adapter as EpisodeAdapter?
                        if (adapter != null) {
                            val episodes = adapter.episodes
                            val db = MovieDatabaseHelper(requireContext())
                            var allEpisodesInDatabase = true
                            for (episode in episodes) {
                                if (!db.isEpisodeInDatabase(tvShowId, currentTabNumber, listOf(episode.episodeNumber))) {
                                    allEpisodesInDatabase = false
                                    break
                                }
                            }
                            if (!allEpisodesInDatabase) {
                                for (episode in episodes) {
                                    if (!db.isEpisodeInDatabase(tvShowId, currentTabNumber, listOf(episode.episodeNumber))) {
                                        db.addEpisodeNumber(tvShowId, currentTabNumber, listOf(episode.episodeNumber))
                                    }
                                }
                                menuItem.setIcon(R.drawable.ic_visibility)
                                Toast.makeText(requireContext(), R.string.episodes_removed, Toast.LENGTH_SHORT).show()
                            } else {
                                for (episode in episodes) {
                                    db.removeEpisodeNumber(tvShowId, currentTabNumber, listOf(episode.episodeNumber))
                                }
                                menuItem.setIcon(R.drawable.ic_visibility_off)
                                Toast.makeText(requireContext(), R.string.episodes_added, Toast.LENGTH_SHORT).show()
                            }
                            adapter.updateEpisodes(episodes)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun loadSeasonDetails() {
        CoroutineScope(Dispatchers.Main).launch {
            binding.shimmerFrameLayout1.visibility = View.VISIBLE
            binding.shimmerFrameLayout1.startShimmer()
            try {
                val tvSeasonDetails = TVSeasonDetails(tvShowId, seasonNumber, requireContext())
                tvSeasonDetails.fetchSeasonDetails(object : TVSeasonDetails.SeasonDetailsCallback {
                    override fun onSeasonDetailsFetched(episodes: List<Episode>) {
                        val adapter = EpisodeAdapter(requireContext(), episodes, seasonNumber, tvShowName, tvShowId, traktId, tmdbObject)
                        binding.episodeRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                        binding.episodeRecyclerView.adapter = adapter
                        binding.episodeRecyclerView.visibility = View.VISIBLE
                        binding.defaultMessage.visibility = View.GONE
                        binding.shimmerFrameLayout1.visibility = View.GONE
                        binding.shimmerFrameLayout1.stopShimmer()
                        requireActivity().invalidateOptionsMenu()
                    }

                    override fun onSeasonDetailsNotAvailable() {
                        if (seasonNumber == 0) {
                            binding.defaultMessage.visibility = View.VISIBLE
                            binding.episodeRecyclerView.visibility = View.GONE
                        }
                        binding.shimmerFrameLayout1.visibility = View.GONE
                        binding.shimmerFrameLayout1.stopShimmer()
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
                binding.shimmerFrameLayout1.visibility = View.GONE
                binding.shimmerFrameLayout1.stopShimmer()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityBinding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
    }

    private fun isShowInDatabase(tvShowId: Int): Boolean {
        val db = dbHelper.readableDatabase
        val selection = EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID + " = ?"
        val selectionArgs = arrayOf(tvShowId.toString())
        val cursor = db.query(
            EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS,
            null, selection, selectionArgs, null, null, null
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    companion object {
        private const val ARG_TV_SHOW_ID = "tvShowId"
        private const val ARG_SEASON_NUMBER = "seasonNumber"
        private const val ARG_TV_SHOW_NAME = "tvShowName"
        private const val ARG_TRAKT_ID = "traktId"
        private const val ARG_TMDB_OBJECT = "tmdbObject"

        fun newInstance(
            tvShowId: Int,
            seasonNumber: Int,
            tvShowName: String?,
            traktId: Int,
            tmdbObject: JSONObject
        ): SeasonDetailsFragment {
            val fragment = SeasonDetailsFragment()
            val args = Bundle()
            args.putInt(ARG_TV_SHOW_ID, tvShowId)
            args.putInt(ARG_SEASON_NUMBER, seasonNumber)
            args.putString(ARG_TV_SHOW_NAME, tvShowName)
            args.putInt(ARG_TRAKT_ID, traktId)
            args.putString(ARG_TMDB_OBJECT, tmdbObject.toString())
            fragment.arguments = args
            return fragment
        }
    }
}