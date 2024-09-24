/*
 *     This file is part of Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     Movie DB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movie DB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movie DB.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.wirelessalien.android.moviedb.fragment

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountDetails
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccountDataFragment : BaseFragment() {
    private lateinit var sPreferences: SharedPreferences
    private lateinit var nameTextView: TextView
    private lateinit var avatar: CircleImageView
    private lateinit var tabLayout: TabLayout
    private var sessionId: String? = null
    private var accountId: String? = null
    private lateinit var loginBtn: ImageView
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
        nameTextView = view.findViewById(R.id.userName)
        avatar = view.findViewById(R.id.profileImage)
        tabLayout = view.findViewById(R.id.tabs)
        loginBtn = view.findViewById(R.id.loginLogoutBtn)
        fab = requireActivity().findViewById(R.id.fab)
        sessionId = sPreferences.getString("access_token", null)
        accountId = sPreferences.getString("account_id", null)
        if (sessionId == null || accountId == null) {
            loginBtn.isEnabled = false
            fab.isEnabled = false
        } else {
            loginBtn.setOnClickListener {
                val url = "https://www.themoviedb.org/settings/profile"
                openLinkInBrowser(url)
            }
            loginBtn.isEnabled = true
            fab.isEnabled = true
        }
        setupTabs()
        loadAccountDetails()
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
        if (sessionId == null || accountId == null) {
            loginBtn.isEnabled = false
            fab.isEnabled = false
        } else {
            loginBtn.setOnClickListener {
                val url = "https://www.themoviedb.org/settings/profile"
                openLinkInBrowser(url)
            }
            loginBtn.isEnabled = true
            fab.isEnabled = true
        }
    }

    private fun openLinkInBrowser(url: String) {
        requireActivity().runOnUiThread {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        }
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

    private fun loadAccountDetails() {
        if (sPreferences.getString("access_token", null) != null) {
            val getAccountDetails = GetAccountDetails(context, object : GetAccountDetails.AccountDataCallback {
                override fun onAccountDataReceived(accountId: Int, name: String?, username: String?, avatarPath: String?, gravatar: String?) {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            if (!name.isNullOrEmpty()) {
                                nameTextView.text = name
                            } else if (!username.isNullOrEmpty()) {
                                nameTextView.text = username
                            }
                            if (!avatarPath.isNullOrEmpty()) {
                                Picasso.get().load("https://image.tmdb.org/t/p/w200$avatarPath").into(avatar)
                            } else if (!gravatar.isNullOrEmpty()) {
                                Picasso.get().load("https://secure.gravatar.com/avatar/$gravatar.jpg?s=200").into(avatar)
                            }
                        }
                    }
                }
            })

            CoroutineScope(Dispatchers.Main).launch {
                getAccountDetails.fetchAccountDetails()
            }
        }
    }

    companion object {
        fun newInstance(): AccountDataFragment {
            return AccountDataFragment()
        }
    }
}