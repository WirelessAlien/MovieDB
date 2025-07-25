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

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AnimationUtils
import androidx.fragment.app.DialogFragment
import com.wirelessalien.android.moviedb.BuildConfig
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.UpdateActivity
import com.wirelessalien.android.moviedb.databinding.FragmentAboutBinding


class AboutFragment : DialogFragment() {

    private lateinit var binding: FragmentAboutBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.versionNumberText.text = BuildConfig.VERSION_NAME

        val prefs = requireActivity().getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val latestVersion = prefs.getString("release_version", "") ?: ""
        val installedVersion = com.wirelessalien.android.moviedb.helper.UpdateUtils.getInstalledVersionName(requireContext())

        if (com.wirelessalien.android.moviedb.helper.UpdateUtils.isNewVersionAvailable(installedVersion, latestVersion)) {
            binding.updateAvailableText.visibility = View.VISIBLE
            val blinkAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.blink)
            binding.updateAvailableText.startAnimation(blinkAnimation)
            binding.updateAvailableText.setOnClickListener {
                val intent = Intent(requireContext(), UpdateActivity::class.java).apply {
                    putExtra("release", latestVersion)
                    putExtra("downloadUrl", prefs.getString("download_url", ""))
                    putExtra("plusDownloadUrl", prefs.getString("plus_download_url", ""))
                    putExtra("changelog", prefs.getString("changelog", ""))
                }
                startActivity(intent)
            }
        }

        binding.sourceCode.setOnClickListener {
            openUrl("https://github.com/WirelessAlien/MovieDB")
        }

        binding.reportIssue.setOnClickListener {
            openUrl("https://github.com/WirelessAlien/MovieDB/issues")
        }

        binding.licenseText.setOnClickListener {
            openUrl("https://www.gnu.org/licenses/gpl-3.0.txt")
        }

        binding.donate.setOnClickListener {
            val donateFragment = DonationFragment()
            donateFragment.show(requireActivity().supportFragmentManager, "donationFragment")
        }

        binding.privacyPolicyLink.setOnClickListener {
            openUrl("https://showcase-app.blogspot.com/2024/11/privacy-policy.html")
        }

        binding.shareIcon.setOnClickListener {
            val githubUrl = "https://github.com/WirelessAlien/MovieDB"
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, githubUrl)
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}