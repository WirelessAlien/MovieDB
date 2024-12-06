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

class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val aboutPreference = findPreference<Preference>("about_key")
        if (aboutPreference != null) {
            aboutPreference.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    AboutFragment().show(parentFragmentManager, "about_dialog")
                    true
                }
        }

        val privacyKey = findPreference<Preference>("privacy_key")
        if (privacyKey != null) {
            privacyKey.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val url = "https://showcase-app.blogspot.com/2024/11/privacy-policy.html"
                    try {
                        val builder = CustomTabsIntent.Builder()
                        val customTabsIntent = builder.build()
                        customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
                    } catch (e: Exception) {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(browserIntent)
                    }
                    true
                }
        }

        val searchEngineKey = findPreference<Preference>("key_search_engine") as EditTextPreference
        searchEngineKey.setOnBindEditTextListener {
            it.hint = "Example: https://www.google.com/search?q="
        }

        val apiLanguageKey = findPreference<Preference>("key_api_language") as EditTextPreference
        apiLanguageKey.setOnBindEditTextListener {
            it.hint = "Example: en-US or en"
        }

        val apiRegionKey = findPreference<Preference>("key_api_region") as EditTextPreference
        apiRegionKey.setOnBindEditTextListener {
            it.hint = "Example: US (the iso3166-1 tag)"
        }

        val apiTimezoneKey = findPreference<Preference>("key_api_timezone") as EditTextPreference
        apiTimezoneKey.setOnBindEditTextListener {
            it.hint = "Example: America/New_York"
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
