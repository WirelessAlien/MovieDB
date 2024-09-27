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
import android.widget.Button
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountDetails
import com.wirelessalien.android.moviedb.tmdb.account.AccountLogout
import com.wirelessalien.android.moviedb.tmdb.account.TMDbAuthV4
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch

class LoginFragment : BottomSheetDialogFragment() {
    private lateinit var preferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
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
        val loginButton = view.findViewById<Button>(R.id.login)
        val logoutButton = view.findViewById<Button>(R.id.logout)
        val loginStatus = view.findViewById<TextView>(R.id.login_status)
        val signUpButton = view.findViewById<Button>(R.id.signup)
        val nameTextView = view.findViewById<TextView>(R.id.name)
        val avatar = view.findViewById<CircleImageView>(R.id.avatar)
        signUpButton.setOnClickListener {
            val url = "https://www.themoviedb.org/signup"
            val i = Intent(Intent.ACTION_VIEW)
            i.setData(Uri.parse(url))
            startActivity(i)
        }
        if (preferences.getString(
                "account_id",
                null
            ) != null && preferences.getString("access_token", null) != null
        ) {
            loginButton.visibility = View.GONE
            logoutButton.visibility = View.VISIBLE
            loginStatus.setText(R.string.logged_in)
        }
        logoutButton.setOnClickListener {
            lifecycleScope.launch {
                val logoutManager = AccountLogout(requireContext(), Handler(Looper.getMainLooper()))
                logoutManager.logout()
                loginButton.visibility = View.VISIBLE
                logoutButton.visibility = View.GONE
                loginStatus.setText(R.string.not_logged_in)
                nameTextView.visibility = View.GONE
            }
        }
        loginButton.setOnClickListener {
            lifecycleScope.launch {
                val authCoroutine = TMDbAuthV4(requireContext())
                val accessToken = authCoroutine.authenticate()
                if (accessToken != null) {
                    preferences.edit().putString("access_token", accessToken).apply()
                }
            }
        }

        if (preferences.getString("access_token", null) != null) {
            lifecycleScope.launch {
                val getAccountDetailsCoroutine = GetAccountDetails(requireContext(), object : GetAccountDetails.AccountDataCallback {
                    override fun onAccountDataReceived(accountId: Int, name: String?, username: String?, avatarPath: String?, gravatar: String?) {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                // If both username and name are available, display name. Otherwise, display whichever is available.
                                nameTextView.visibility = View.VISIBLE
                                if (!name.isNullOrEmpty()) {
                                    nameTextView.text = name
                                } else if (!username.isNullOrEmpty()) {
                                    nameTextView.text = username
                                }

                                // If both avatarPath and gravatar are available, display avatarPath. Otherwise, display whichever is available.
                                if (!avatarPath.isNullOrEmpty()) {
                                    Picasso.get().load("https://image.tmdb.org/t/p/w200$avatarPath").into(avatar)
                                } else if (!gravatar.isNullOrEmpty()) {
                                    Picasso.get().load("https://secure.gravatar.com/avatar/$gravatar.jpg?s=200").into(avatar)
                                }
                            }
                        }
                    }
                })
                getAccountDetailsCoroutine.fetchAccountDetails()
            }
        }
        return view
    }
}