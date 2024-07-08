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

package com.wirelessalien.android.moviedb.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.wirelessalien.android.moviedb.preference.NPPDialogFragmentCompat;
import com.wirelessalien.android.moviedb.preference.NumberPickerPreference;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.SettingsActivity;
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter;

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // Find the preference and set an OnPreferenceClickListener
        NumberPickerPreference numberPickerPreference = findPreference("key_grid_size_number");
        if (numberPickerPreference != null) {
            numberPickerPreference.setOnPreferenceClickListener(preference -> {
                // Create a new instance of NumberPickerPreferenceDialogFragmentCompat
                NPPDialogFragmentCompat dialogFragment = NPPDialogFragmentCompat.newInstance(preference.getKey());
                // Set the target fragment for use later when sending results
                dialogFragment.setTargetFragment(this, 0);
                // Show the dialog
                dialogFragment.show(getParentFragmentManager(), null);
                return true;
            });
        }

        Preference aboutPreference = findPreference("about_key");
        if (aboutPreference != null) {
            aboutPreference.setOnPreferenceClickListener(preference -> {
                // Show AboutFragment as a dialog
                new AboutFragment().show(getParentFragmentManager(), "about_dialog");
                return true;
            });
        }
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        // Check if the preference is a NumberPickerPreference
        if (preference instanceof NumberPickerPreference) {
            // Create a new instance of the dialog fragment
            NPPDialogFragmentCompat dialogFragment = NPPDialogFragmentCompat.newInstance(preference.getKey());
            // Set the target fragment for use later when sending results
            dialogFragment.setTargetFragment(this, 0);
            // Show the dialog
            dialogFragment.show(getParentFragmentManager(), null);
        } else {
            // Delegate the showing of the dialog to the super class
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(SectionsPagerAdapter.HIDE_MOVIES_PREFERENCE)
                || key.equals(SectionsPagerAdapter.HIDE_PERSON_PREFERENCE)
                || key.equals(SectionsPagerAdapter.HIDE_SAVED_PREFERENCE)
                || key.equals(SectionsPagerAdapter.HIDE_SERIES_PREFERENCE)) {
            ((SettingsActivity) requireActivity()).mTabsPreferenceChanged = true;
        }
    }
}
