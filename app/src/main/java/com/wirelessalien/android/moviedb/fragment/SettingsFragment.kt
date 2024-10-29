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

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.SettingsActivity
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter
import com.wirelessalien.android.moviedb.preference.NPPDialogFragmentCompat
import com.wirelessalien.android.moviedb.preference.NumberPickerPreference

class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey)


        // Find the preference and set an OnPreferenceClickListener
        val numberPickerPreference = findPreference<NumberPickerPreference>("key_grid_size_number")
        if (numberPickerPreference != null) {
            numberPickerPreference.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { preference: Preference ->
                    // Create a new instance of NumberPickerPreferenceDialogFragmentCompat
                    val dialogFragment = NPPDialogFragmentCompat.newInstance(preference.key)
                    // Set the target fragment for use later when sending results
                    dialogFragment.setTargetFragment(this, 0)
                    // Show the dialog
                    dialogFragment.show(parentFragmentManager, null)
                    true
                }
        }
        val aboutPreference = findPreference<Preference>("about_key")
        if (aboutPreference != null) {
            aboutPreference.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    // Show AboutFragment as a dialog
                    AboutFragment().show(parentFragmentManager, "about_dialog")
                    true
                }
        }

        val privacyKey = findPreference<Preference>("privacy_key")
        if (privacyKey != null) {
            privacyKey.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val url = "https://sites.google.com/view/privacy-policy-showcase"
                    try {
                        val builder = CustomTabsIntent.Builder()
                        val customTabsIntent = builder.build()
                        customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
                    } catch (e: Exception) {
                        // Fallback to default browser if Chrome Custom Tabs is not available
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(browserIntent)
                    }
                    true
                }
        }

        val searchEngineKey = findPreference<Preference>("key_search_engine") as EditTextPreference
        searchEngineKey.setOnBindEditTextListener {
            it.hint = "https://www.google.com/search?q="
        }

    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        // Check if the preference is a NumberPickerPreference
        if (preference is NumberPickerPreference) {
            // Create a new instance of the dialog fragment
            val dialogFragment = NPPDialogFragmentCompat.newInstance(preference.getKey())
            // Set the target fragment for use later when sending results
            dialogFragment.setTargetFragment(this, 0)
            // Show the dialog
            dialogFragment.show(parentFragmentManager, null)
        } else {
            // Delegate the showing of the dialog to the super class
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == SectionsPagerAdapter.HIDE_MOVIES_PREFERENCE || key == SectionsPagerAdapter.HIDE_ACCOUNT_PREFERENCE || key == SectionsPagerAdapter.HIDE_SAVED_PREFERENCE || key == SectionsPagerAdapter.HIDE_SERIES_PREFERENCE) {
            (requireActivity() as SettingsActivity).mTabsPreferenceChanged = true
        }
    }
}
