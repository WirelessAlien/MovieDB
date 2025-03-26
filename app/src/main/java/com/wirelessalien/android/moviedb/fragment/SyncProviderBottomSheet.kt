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

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.databinding.DialogSyncProviderBinding

class SyncProviderBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: DialogSyncProviderBinding
    private lateinit var preferences: SharedPreferences

    override fun onAttach(context: Context) {
        super.onAttach(context)
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogSyncProviderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve stored values from SharedPreferences
        val syncProvider = preferences.getString("sync_provider", "local")
        val forceLocalSync = preferences.getBoolean("force_local_sync", false)

        // Set initial state of radio buttons based on stored value
        when (syncProvider) {
            "local" -> binding.radioLocal.isChecked = true
            "trakt" -> binding.radioTrakt.isChecked = true
            "tmdb" -> binding.radioTmdb.isChecked = true
        }

        // Set initial state of forceLocalSync checkbox
        binding.forceLocalSync.isChecked = forceLocalSync
        binding.forceLocalSync.isEnabled = syncProvider != "local"

        binding.radioLocal.setOnCheckedChangeListener { _, isChecked ->
            binding.forceLocalSync.isEnabled = !isChecked
        }

        binding.forceLocalSync.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("force_local_sync", isChecked).apply()
            // Apply HIDE_SAVED_PREFERENCE based on forceLocalSync state
            if (isChecked) {
                preferences.edit().putBoolean(MainActivity.HIDE_SAVED_PREFERENCE, false).apply()
            }
        }

        binding.buttonOk.setOnClickListener {
            val selectedProvider = when {
                binding.radioLocal.isChecked -> "local"
                binding.radioTrakt.isChecked -> "trakt"
                binding.radioTmdb.isChecked -> "tmdb"
                else -> "local"
            }

            // Apply preferences based on forceLocalSync *before* applying provider-specific settings
            if (forceLocalSync) {
                preferences.edit().putBoolean(MainActivity.HIDE_SAVED_PREFERENCE, false).apply()
            } else { // Only apply based on selected provider if forceLocalSync is NOT checked.
                when (selectedProvider) {
                    "local" -> {
                        preferences.edit().putBoolean(MainActivity.HIDE_ACCOUNT_PREFERENCE, true).apply()
                        preferences.edit().putBoolean(MainActivity.HIDE_ACCOUNT_TKT_PREFERENCE, true).apply()
                        preferences.edit().putBoolean(MainActivity.HIDE_SAVED_PREFERENCE, false).apply()
                    }
                    "trakt" -> {
                        preferences.edit().putBoolean(MainActivity.HIDE_ACCOUNT_PREFERENCE, true).apply()
                        preferences.edit().putBoolean(MainActivity.HIDE_ACCOUNT_TKT_PREFERENCE, false).apply()
                        preferences.edit().putBoolean(MainActivity.HIDE_SAVED_PREFERENCE, true).apply()
                    }
                    "tmdb" -> {
                        preferences.edit().putBoolean(MainActivity.HIDE_ACCOUNT_PREFERENCE, false).apply()
                        preferences.edit().putBoolean(MainActivity.HIDE_ACCOUNT_TKT_PREFERENCE, true).apply()
                        preferences.edit().putBoolean(MainActivity.HIDE_SAVED_PREFERENCE, true).apply()
                    }
                }
            }

            preferences.edit().putString("sync_provider", selectedProvider).apply()
            preferences.edit().putBoolean("sync_provider_dialog_shown", true).apply()
            dismiss()

            val omdbSetupFragment = OmdbSetupFragment()
            omdbSetupFragment.show(parentFragmentManager, "OmdbSetupFragment")
        }
    }
}