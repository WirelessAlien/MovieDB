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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wirelessalien.android.moviedb.BuildConfig
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.FragmentAboutBinding


class AboutFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = FragmentAboutBinding.inflate(layoutInflater)
        val dialogView = binding.root

        binding.versionNumberText.text = BuildConfig.VERSION_NAME

        binding.githubIcon.setOnClickListener {
            openUrl("https://github.com/WirelessAlien/MovieDB")
        }

        binding.githubIssueButton.setOnClickListener {
            openUrl("https://github.com/WirelessAlien/MovieDB/issues")
        }

        binding.translateText.setOnClickListener {
            openUrl("https://hosted.weblate.org/engage/showcase/")
        }

        binding.donate.setOnClickListener {
            val donateFragment = DonationFragment()
            donateFragment.show(requireActivity().supportFragmentManager, "donationFragment")
        }



        binding.shareIcon.setOnClickListener {
            val githubUrl = "https://github.com/WirelessAlien/MovieDB"
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, githubUrl)
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}