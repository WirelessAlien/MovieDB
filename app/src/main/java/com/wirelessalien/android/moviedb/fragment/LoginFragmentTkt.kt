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

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.FragmentLoginTktBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.trakt.AccountLogoutTkt
import com.wirelessalien.android.moviedb.trakt.GetAccountDetailsTkt
import kotlinx.coroutines.launch

class LoginFragmentTkt : BottomSheetDialogFragment() {
    private lateinit var preferences: SharedPreferences
    private var clientId: String? = null
    private lateinit var binding: FragmentLoginTktBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginTktBinding.inflate(inflater, container, false)
        val view = binding.root
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        clientId = ConfigHelper.getConfigValue(requireContext().applicationContext, "client_id")

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.setPackage("com.android.chrome")
        CustomTabsClient.bindCustomTabsService(
            requireContext(),
            "com.android.chrome",
            object : CustomTabsServiceConnection() {
                override fun onCustomTabsServiceConnected(
                    componentName: ComponentName,
                    customTabsClient: CustomTabsClient
                ) {
                    customTabsClient.warmup(0L)
                }

                override fun onServiceDisconnected(name: ComponentName) {}
            })

        binding.signup.setOnClickListener {
            val url = "https://trakt.tv/auth/join"
            val i = Intent(Intent.ACTION_VIEW)
            i.setData(Uri.parse(url))
            startActivity(i)
        }

        if (preferences.getString("trakt_access_token", null) != null) {
            binding.login.visibility = View.GONE
            binding.logout.visibility = View.VISIBLE
            binding.loginStatus.setText(R.string.logged_in)
        }

        binding.logout.setOnClickListener {
            lifecycleScope.launch {
                val logoutManager = AccountLogoutTkt(requireContext(), Handler(Looper.getMainLooper()))
                logoutManager.logout()
                binding.login.visibility = View.VISIBLE
                binding.logout.visibility = View.GONE
                binding.loginStatus.setText(R.string.not_logged_in)
                binding.name.visibility = View.GONE
                binding.avatar.setImageResource(R.drawable.ic_account)
            }
        }

        binding.login.setOnClickListener {
            redirectToTraktAuthorization()
        }

        binding.changeProvider.setOnClickListener {
            dismiss()
            val bottomSheetFragment = LoginFragment()
            bottomSheetFragment.show(parentFragmentManager, bottomSheetFragment.tag)
        }

        if (preferences.getString("trakt_access_token", null) != null) {
            lifecycleScope.launch {
                val getAccountDetailsTkt = GetAccountDetailsTkt(requireContext(), clientId!!, object : GetAccountDetailsTkt.AccountDataCallback {
                    override fun onAccountDataReceived(username: String?, name: String?, avatarUrl: String?, location: String?, joinedAt: String?) {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                binding.name.visibility = View.VISIBLE
                                if (!name.isNullOrEmpty()) {
                                    binding.name.text = name
                                } else if (!username.isNullOrEmpty()) {
                                    binding.name.text = username
                                }

                                if (!avatarUrl.isNullOrEmpty()) {
                                    Picasso.get().load(avatarUrl).into(binding.avatar)
                                }
                            }
                        }
                    }
                })
                getAccountDetailsTkt.fetchAccountDetails()
            }
        }
        return view
    }

    private fun redirectToTraktAuthorization() {
        dismiss()
        val redirectUri = "trakt.wirelessalien.showcase://callback"
        val authUrl = "https://trakt.tv/oauth/authorize?response_type=code&client_id=$clientId&redirect_uri=$redirectUri"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        startActivity(intent)
    }
}