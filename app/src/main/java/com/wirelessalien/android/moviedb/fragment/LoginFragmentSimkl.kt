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
import android.util.Base64
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
import com.wirelessalien.android.moviedb.databinding.FragmentLoginSimklBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.simkl.AccountLogoutSimkl
import com.wirelessalien.android.moviedb.simkl.GetAccountDetailsSimkl
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

class LoginFragmentSimkl : BottomSheetDialogFragment() {
    private lateinit var preferences: SharedPreferences
    private var clientId: String? = null
    private lateinit var binding: FragmentLoginSimklBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginSimklBinding.inflate(inflater, container, false)
        val view = binding.root
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        clientId = ConfigHelper.getConfigValue(requireContext().applicationContext, "simkl_client_id")

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
            val url = "https://simkl.com/signup/"
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(i)
        }

        if (preferences.getString("simkl_access_token", null) != null) {
            binding.login.visibility = View.GONE
            binding.logout.visibility = View.VISIBLE
            binding.loginStatus.setText(R.string.logged_in)
        }

        binding.logout.setOnClickListener {
            lifecycleScope.launch {
                val logoutManager = AccountLogoutSimkl(requireContext(), Handler(Looper.getMainLooper()))
                logoutManager.logout()
                binding.login.visibility = View.VISIBLE
                binding.logout.visibility = View.GONE
                binding.loginStatus.setText(R.string.not_logged_in)
                binding.name.visibility = View.GONE
                binding.avatar.setImageResource(R.drawable.ic_account)
            }
        }

        binding.login.setOnClickListener {
            redirectToSimklAuthorization()
        }

        binding.changeProvider.setOnClickListener {
            dismiss()
            val bottomSheetFragment = LoginFragment()
            bottomSheetFragment.show(parentFragmentManager, bottomSheetFragment.tag)
        }

        if (preferences.getString("simkl_access_token", null) != null && !clientId.isNullOrEmpty()) {
            lifecycleScope.launch {
                val getAccountDetailsSimkl = GetAccountDetailsSimkl(
                    requireContext(),
                    clientId!!,
                    object : GetAccountDetailsSimkl.AccountDataCallback {
                        override fun onAccountDataReceived(userId: Int, name: String?, avatarUrl: String?) {
                            if (isAdded) {
                                requireActivity().runOnUiThread {
                                    if (!name.isNullOrEmpty()) {
                                        binding.name.visibility = View.VISIBLE
                                        binding.name.text = name
                                    }
                                    if (!avatarUrl.isNullOrEmpty()) {
                                        Picasso.get().load(avatarUrl).into(binding.avatar)
                                    }
                                }
                            }
                        }
                    }
                )
                getAccountDetailsSimkl.fetchAccountDetails()
            }
        }
        return view
    }

    private fun redirectToSimklAuthorization() {
        dismiss()
        val codeVerifier = generateRandomUrlSafeString(64)
        val codeChallenge = computeCodeChallengeS256(codeVerifier)
        val state = generateRandomUrlSafeString(32)

        preferences.edit()
            .putString("simkl_code_verifier", codeVerifier)
            .putString("simkl_oauth_state", state)
            .apply()

        val redirectUri = "simkl.wirelessalien.showcase://callback"
        val authUrl = "https://simkl.com/oauth2/authorize?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=media:read%20media:write&state=$state&code_challenge=$codeChallenge&code_challenge_method=S256"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        startActivity(intent)
    }

    private fun generateRandomUrlSafeString(length: Int): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val secureRandom = SecureRandom()
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(allowedChars[secureRandom.nextInt(allowedChars.length)])
        }
        return sb.toString()
    }

    private fun computeCodeChallengeS256(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
