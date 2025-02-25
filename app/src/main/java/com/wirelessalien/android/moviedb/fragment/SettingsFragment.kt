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
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.SettingsActivity
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter
import com.wirelessalien.android.moviedb.databinding.DialogSyncProviderBinding

class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {

    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val aboutPreference = findPreference<Preference>("about_key")
        aboutPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            AboutFragment().show(parentFragmentManager, "about_dialog")
            true
        }

        val privacyKey = findPreference<Preference>("privacy_key")
        privacyKey?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
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

        val searchEngineKey = findPreference<EditTextPreference>("key_search_engine")
        searchEngineKey?.setOnBindEditTextListener {
            it.hint = "Example: https://www.google.com/search?q="
        }

        val apiLanguageKey = findPreference<EditTextPreference>("key_api_language")
        apiLanguageKey?.setOnBindEditTextListener {
            it.hint = "Example: en-US or en"
        }

        val apiRegionKey = findPreference<EditTextPreference>("key_api_region")
        apiRegionKey?.setOnBindEditTextListener {
            it.hint = "Example: US (the iso3166-1 tag)"
        }

        val apiTimezoneKey = findPreference<EditTextPreference>("key_api_timezone")
        apiTimezoneKey?.setOnBindEditTextListener {
            it.hint = "Example: America/New_York"
        }

        val hideAccountTab = findPreference<CheckBoxPreference>("key_hide_account_tab")
        val hideAccountTktTab = findPreference<CheckBoxPreference>("key_hide_account_tkt_tab")

        val preferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val newState = newValue as Boolean

            when (preference.key) {
                "key_hide_account_tab" -> {
                    if (!newState && !hideAccountTktTab?.isChecked!!) {
                        return@OnPreferenceChangeListener false
                    }
                }
                "key_hide_account_tkt_tab" -> {
                    if (!newState && !hideAccountTab?.isChecked!!) {
                        return@OnPreferenceChangeListener false
                    }
                }
            }
            true
        }

        hideAccountTab?.onPreferenceChangeListener = preferenceChangeListener
        hideAccountTktTab?.onPreferenceChangeListener = preferenceChangeListener

        val syncProvider = findPreference<Preference>("sync_provider")

        syncProvider?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showSyncProviderDialog()
            true
        }
    }

    private fun showSyncProviderDialog() {
        val dialogBinding = DialogSyncProviderBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        // Retrieve stored values from SharedPreferences
        val syncProvider = preferences.getString("sync_provider", "local")
        val forceLocalSync = preferences.getBoolean("force_local_sync", false)

        // Set initial state of radio buttons based on stored value
        when (syncProvider) {
            "local" -> dialogBinding.radioLocal.isChecked = true
            "trakt" -> dialogBinding.radioTrakt.isChecked = true
            "tmdb" -> dialogBinding.radioTmdb.isChecked = true
        }

        // Set initial state of forceLocalSync checkbox
        dialogBinding.forceLocalSync.isChecked = forceLocalSync
        dialogBinding.forceLocalSync.isEnabled = syncProvider != "local"

        dialogBinding.radioLocal.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.forceLocalSync.isEnabled = !isChecked
        }

        dialogBinding.forceLocalSync.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("force_local_sync", isChecked).apply()
        }

        dialogBinding.buttonOk.setOnClickListener {
            val selectedProvider = when {
                dialogBinding.radioLocal.isChecked -> "local"
                dialogBinding.radioTrakt.isChecked -> "trakt"
                dialogBinding.radioTmdb.isChecked -> "tmdb"
                else -> "local"
            }

            preferences.edit().putString("sync_provider", selectedProvider).apply()
            preferences.edit().putBoolean("sync_provider_dialog_shown", true).apply()
            dialog.dismiss()
        }

        dialog.show()
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
