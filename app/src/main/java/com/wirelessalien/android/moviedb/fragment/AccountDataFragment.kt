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
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.databinding.ActivityMainBinding
import com.wirelessalien.android.moviedb.databinding.FragmentAccountDataBinding

class AccountDataFragment : BaseFragment() {
    private lateinit var sPreferences: SharedPreferences
    private var sessionId: String? = null
    private var accountId: String? = null
    private lateinit var binding: FragmentAccountDataBinding
    private lateinit var activityBinding: ActivityMainBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        binding = FragmentAccountDataBinding.inflate(inflater, container, false)
        val view = binding.root
        activityBinding = (activity as MainActivity).getBinding()
        sPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        activityBinding.fab2.visibility = View.GONE
        sessionId = sPreferences.getString("access_token", null)
        accountId = sPreferences.getString("account_id", null)
        activityBinding.fab.isEnabled = !(sessionId == null || accountId == null)

        setupTabs()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.account_swap_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.account_swap -> {
                        val accountDataFragmentTkt = AccountDataFragmentTkt()
                        requireActivity().supportFragmentManager.beginTransaction()
                            .replace(R.id.container, accountDataFragmentTkt).commit()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner)
    }

    override fun onResume() {
        super.onResume()
        activityBinding.fab.isEnabled = !(sessionId == null || accountId == null)
        activityBinding.fab2.visibility = View.GONE
        if (sPreferences.getBoolean("key_show_continue_watching", true) && sPreferences.getString("sync_provider", "local") == "local") {
            activityBinding.upnextChip.visibility = View.VISIBLE
        } else {
            activityBinding.upnextChip.visibility = View.GONE
        }
        requireActivity().invalidateOptionsMenu()
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.watchlist)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.favourite)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.rated)))
        binding.tabs.addTab(binding.tabs.newTab().setText(getString(R.string.lists)))
        binding.tabs.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val selectedFragment: Fragment? = when (tab.position) {
                    0 -> WatchlistFragment()
                    1 -> FavoriteFragment()
                    2 -> RatedListFragment()
                    3 -> ListFragmentTmdb()
                    else -> null
                }
                if (selectedFragment != null) {
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
        if (sessionId == null || accountId == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LoginFragment())
                .commit()
        } else {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, WatchlistFragment())
                .commit()
        }
    }

    companion object {
        fun newInstance(): AccountDataFragment {
            return AccountDataFragment()
        }
    }
}