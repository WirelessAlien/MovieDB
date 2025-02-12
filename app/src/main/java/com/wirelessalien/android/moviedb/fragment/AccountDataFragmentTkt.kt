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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.databinding.ActivityMainBinding
import com.wirelessalien.android.moviedb.databinding.FragmentAccountDataTktBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.tmdb.GetTmdbDetails
import com.wirelessalien.android.moviedb.trakt.GetTraktSyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountDataFragmentTkt : BaseFragment() {

    private lateinit var sharedPreferences: SharedPreferences
    private var accessToken: String? = null
    private var tmdbApiKey: String? = null
    private var clientId: String? = null
    private lateinit var binding: FragmentAccountDataTktBinding
    private lateinit var activityBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        accessToken = sharedPreferences.getString("trakt_access_token", null)
        clientId = ConfigHelper.getConfigValue(requireContext(), "client_id")
        tmdbApiKey = ConfigHelper.getConfigValue(requireContext(), "api_key")

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAccountDataTktBinding.inflate(inflater, container, false)
        val view = binding.root
        activityBinding = (activity as MainActivity).getBinding()

        setupTabs()

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.trakt_refresh_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_refresh -> {
                        showRefreshDialog()
                        true
                    }
                    R.id.action_stats -> {
                        val traktStatsBottomSheet = TraktStatsBottomSheet()
                        traktStatsBottomSheet.show(parentFragmentManager, traktStatsBottomSheet.tag)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner)

        return view
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.watchlist)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.progress)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.collection)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.history)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.favourite)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.rated)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.lists)))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val selectedFragment: Fragment? = when (tab.position) {
                    0 -> WatchlistFragmentTkt()
                    1 -> ProgressFragmentTkt()
                    2 -> CollectionFragmentTkt()
                    3 -> HistoryFragmentTkt()
                    4 -> FavoriteFragmentTkt()
                    5 -> RatingFragmentTkt()
                    6 -> ListFragmentTkt()
                    else -> null
                }
                if (selectedFragment != null && isAdded && activity != null) {
                    childFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                // Not used
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                // Not used
            }
        })
        if (accessToken == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LoginFragmentTkt())
                .commit()
        } else {
            if (isAdded && activity != null) {
                childFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, WatchlistFragmentTkt())
                    .commit()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityBinding.fab.visibility = View.GONE
        activityBinding.fab2.visibility = View.GONE
    }

    private fun showRefreshDialog() {
        val options = listOf(
            getString(R.string.movie_collection),
            getString(R.string.show_collection),
            getString(R.string.movie_watched),
            getString(R.string.show_watched),
            getString(R.string.history),
            getString(R.string.rating1),
            getString(R.string.watchlist),
            getString(R.string.favourite),
            getString(R.string.lists),
            getString(R.string.list_items)
        )

        val selectedOptions = options.toMutableSet()

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_refresh_options, null)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroup)

        options.forEach { option ->
            val chip = Chip(context).apply {
                text = option
                isCheckable = true
                isChecked = true
                setChipIconResource(R.drawable.ic_done_all)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedOptions.add(option)
                        setChipIconResource(R.drawable.ic_done_all)
                    } else {
                        selectedOptions.remove(option)
                        setChipIconResource(R.drawable.ic_close)
                    }
                }
            }
            chipGroup.addView(chip)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.select_options))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                refreshData(selectedOptions)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun refreshData(selectedOptions: Set<String>) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_progress_indicator, null)
        var job: Job? = null

        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.fetching_data))
            .setView(dialogView)
            .setCancelable(false)
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                job?.cancel()
                dialog.dismiss()
            }
            .show()

        val progressTextView = dialogView.findViewById<TextView>(R.id.progressText)

        fun updateProgressMessage(message: String) {
            lifecycleScope.launch(Dispatchers.Main) {
                progressTextView.text = message
            }
        }

        job = lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val getTraktSyncData = GetTraktSyncData(requireContext(), accessToken, clientId)
                selectedOptions.forEach { option ->
                    when (option) {
                        getString(R.string.movie_collection) -> {
                            updateProgressMessage(getString(R.string.fetching_movie_collection))
                            getTraktSyncData.fetchCollectionData()
                        }
                        getString(R.string.show_collection) -> {
                            updateProgressMessage(getString(R.string.fetching_show_collection))
                            getTraktSyncData.fetchCollectionShowData()
                        }
                        getString(R.string.movie_watched) -> {
                            updateProgressMessage(getString(R.string.fetching_movie_watched))
                            getTraktSyncData.fetchWatchedDataMovie()
                        }
                        getString(R.string.show_watched) -> {
                            updateProgressMessage(getString(R.string.fetching_show_watched))
                            getTraktSyncData.fetchWatchedDataShow()
                        }
                        getString(R.string.history) -> {
                            updateProgressMessage(getString(R.string.fetching_history))
                            getTraktSyncData.fetchHistoryData()
                        }
                        getString(R.string.rating1) -> {
                            updateProgressMessage(getString(R.string.fetching_rating))
                            getTraktSyncData.fetchRatingData()
                        }
                        getString(R.string.watchlist) -> {
                            updateProgressMessage(getString(R.string.fetching_watchlist))
                            getTraktSyncData.fetchWatchlistData()
                        }
                        getString(R.string.favourite) -> {
                            updateProgressMessage(getString(R.string.fetching_favorite))
                            getTraktSyncData.fetchFavoriteData()
                        }
                        getString(R.string.lists) -> {
                            updateProgressMessage(getString(R.string.fetching_lists))
                            getTraktSyncData.fetchUserLists()
                        }
                        getString(R.string.list_items) -> {
                            updateProgressMessage(getString(R.string.fetching_list_items))
                            getTraktSyncData.fetchAllListItems()
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    showTmdbDetailsDialog()
                }
            }
        }
    }

    private fun showTmdbDetailsDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_progress_indicator, null)
        var job: Job? = null

        val tmdbDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.fetching_tmdb_data))
            .setView(dialogView)
            .setCancelable(false)
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                job?.cancel()
                dialog.dismiss()
            }
            .show()

        job = lifecycleScope.launch {
            val getTmdbDetails = GetTmdbDetails(requireContext(), tmdbApiKey ?: "")
            getTmdbDetails.fetchAndSaveTmdbDetails()
            tmdbDialog.dismiss()
            reloadFragment()
        }
    }


    private fun reloadFragment() {
        parentFragmentManager.beginTransaction()
            .replace(this.id, AccountDataFragmentTkt())
            .commit()
    }
}