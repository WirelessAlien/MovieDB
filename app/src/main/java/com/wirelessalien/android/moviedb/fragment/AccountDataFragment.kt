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
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.wirelessalien.android.moviedb.R

class AccountDataFragment : BaseFragment() {
    private lateinit var sPreferences: SharedPreferences
    private lateinit var tabLayout: TabLayout
    private var sessionId: String? = null
    private var accountId: String? = null
    private lateinit var fab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_account_data, container, false)
        sPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        tabLayout = view.findViewById(R.id.tabs)
        fab = requireActivity().findViewById(R.id.fab)
        val fab2 = requireActivity().findViewById<FloatingActionButton>(R.id.fab2)
        fab2.visibility = View.GONE
        sessionId = sPreferences.getString("access_token", null)
        accountId = sPreferences.getString("account_id", null)
        fab.isEnabled = !(sessionId == null || accountId == null)
        setupTabs()
        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        val item = menu.findItem(R.id.action_search)
        if (item != null) {
            item.setVisible(false)
            item.setEnabled(false)
        }
    }

    override fun onResume() {
        super.onResume()
        fab.isEnabled = !(sessionId == null || accountId == null)
        val fab2 = requireActivity().findViewById<FloatingActionButton>(R.id.fab2)
        fab2.visibility = View.GONE
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.watchlist)))
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.favourite)))
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.rated)))
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.lists)))
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val selectedFragment: Fragment? = when (tab.position) {
                    0 -> WatchlistFragment()
                    1 -> FavoriteFragment()
                    2 -> RatedListFragment()
                    3 -> MyListsFragment()
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