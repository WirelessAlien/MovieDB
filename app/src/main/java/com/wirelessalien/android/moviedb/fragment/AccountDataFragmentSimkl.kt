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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.tabs.TabLayout
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.databinding.ActivityMainBinding
import com.wirelessalien.android.moviedb.databinding.FragmentAccountDataTktBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.SimklDatabaseHelper
import com.wirelessalien.android.moviedb.service.SimklSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountDataFragmentSimkl : BaseFragment() {

    private lateinit var sharedPreferences: SharedPreferences
    private var accessToken: String? = null
    private var tmdbApiKey: String? = null
    private var clientId: String? = null
    private lateinit var binding: FragmentAccountDataTktBinding
    private lateinit var activityBinding: ActivityMainBinding
    private var simklReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        accessToken = sharedPreferences.getString("simkl_access_token", null)
        clientId = ConfigHelper.getConfigValue(requireContext(), "simkl_client_id")
        tmdbApiKey = ConfigHelper.getConfigValue(requireContext(), "api_key")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAccountDataTktBinding.inflate(inflater, container, false)
        val view = binding.root
        activityBinding = (activity as MainActivity).getBinding()
        binding.currentlyWatchingContainer.visibility = View.GONE

        setupTabs()

        binding.swipeRefreshLayout.setOnRefreshListener {
            startSimklIncrementalSync()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Simkl API Rule 7: Trigger incremental sync on user-visible open, throttled to once per 15 min
        viewLifecycleOwner.lifecycleScope.launch {
            val token = sharedPreferences.getString("simkl_access_token", null)
            if (!token.isNullOrEmpty()) {
                val shouldSync = withContext(Dispatchers.IO) {
                    SimklDatabaseHelper(requireContext()).use { db ->
                        val lastChecked = db.getSyncActivities()?.lastCheckedMs ?: 0L
                        System.currentTimeMillis() - lastChecked >= SYNC_THROTTLE_MS
                    }
                }
                if (shouldSync && isAdded) {
                    startSimklIncrementalSync()
                }
            }
        }

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.account_swap_menu, menu)
                menuInflater.inflate(R.menu.trakt_refresh_menu, menu)
                menuInflater.inflate(R.menu.tkt_sort_menu, menu)

                menu.findItem(R.id.action_stats)?.isVisible = false
                menu.findItem(R.id.action_export_tkt_data)?.isVisible = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.account_swap -> {
                        val accountDataFragment = AccountDataFragment()
                        requireActivity().supportFragmentManager.beginTransaction()
                            .replace(R.id.container, accountDataFragment)
                            .commit()
                        true
                    }

                    R.id.action_refresh -> {
                        binding.swipeRefreshLayout.isRefreshing = true
                        startSimklIncrementalSync()
                        true
                    }

                    R.id.sort_name_asc -> {
                        saveSortPreference("name", "asc")
                        true
                    }

                    R.id.sort_name_desc -> {
                        saveSortPreference("name", "desc")
                        true
                    }

                    R.id.sort_date_asc -> {
                        saveSortPreference("date", "asc")
                        true
                    }

                    R.id.sort_date_desc -> {
                        saveSortPreference("date", "desc")
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun saveSortPreference(criteria: String, order: String) {
        with(sharedPreferences.edit()) {
            putString("simkl_sort_criteria", criteria)
            putString("simkl_sort_order", order)
            apply()
        }
        reloadFragment()
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.watching)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.plan_to_watch)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.completed)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.on_hold)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.dropped)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.rated)))

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val selectedFragment = getFragmentForTab(tab.position)
                if (selectedFragment != null && isAdded && activity != null) {
                    childFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        accessToken = sharedPreferences.getString("simkl_access_token", null)
        if (accessToken == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LoginFragmentSimkl())
                .commit()
        } else {
            if (isAdded && activity != null) {
                childFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SimklListFragment.newInstance("watching"))
                    .commit()
            }
        }
    }

    private fun getFragmentForTab(position: Int): Fragment? {
        return when (position) {
            0 -> SimklListFragment.newInstance("watching")
            1 -> SimklListFragment.newInstance("plantowatch")
            2 -> SimklListFragment.newInstance("completed")
            3 -> SimklListFragment.newInstance("hold")
            4 -> SimklListFragment.newInstance("dropped")
            5 -> SimklListFragment.newInstance("rated")
            else -> null
        }
    }

    @SuppressLint("InlinedApi", "UnspecifiedRegisterReceiverFlag")
    private fun startSimklIncrementalSync() {
        accessToken = sharedPreferences.getString("simkl_access_token", null)
        if (accessToken.isNullOrEmpty()) {
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        val intent = Intent(requireContext(), SimklSyncService::class.java).apply {
            action = SimklSyncService.ACTION_START_SERVICE
            putExtra(SimklSyncService.EXTRA_ACCESS_TOKEN, accessToken)
            putExtra(SimklSyncService.EXTRA_CLIENT_ID, clientId)
            putExtra(SimklSyncService.EXTRA_TMDB_API_KEY, tmdbApiKey)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }

        simklReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }

        simklReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    context?.unregisterReceiver(this)
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                }

                if (!isAdded || !isResumed) return
                binding.swipeRefreshLayout.isRefreshing = false
                reloadFragment()
            }
        }

        val filter = IntentFilter(SimklSyncService.ACTION_SERVICE_COMPLETED)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().registerReceiver(simklReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                requireContext().registerReceiver(simklReceiver, filter)
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        simklReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        simklReceiver = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        reloadFragment()
        activityBinding.fab.visibility = View.GONE
        activityBinding.fab2.visibility = View.GONE
        activityBinding.upnextChip.visibility = View.GONE
        activityBinding.toggleButtonGroup.root.visibility = View.GONE
        requireActivity().invalidateOptionsMenu()
    }

    private fun reloadFragment() {
        accessToken = sharedPreferences.getString("simkl_access_token", null)
        if (accessToken == null) {
            if (isAdded && activity != null) {
                childFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, LoginFragmentSimkl())
                    .commit()
            }
            return
        }
        val selectedFragment = getFragmentForTab(binding.tabs.selectedTabPosition)
        if (selectedFragment != null && isAdded && activity != null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, selectedFragment)
                .commit()
        }
    }

    companion object {
        private const val SYNC_THROTTLE_MS = 15 * 60 * 1000L
    }
}
