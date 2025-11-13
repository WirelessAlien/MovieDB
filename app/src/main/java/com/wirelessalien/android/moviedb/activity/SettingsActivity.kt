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
package com.wirelessalien.android.moviedb.activity


import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ActivitySettingsBinding
import com.wirelessalien.android.moviedb.fragment.SettingsFragment
import com.wirelessalien.android.moviedb.helper.ThemeHelper

class SettingsActivity : AppCompatActivity() {
    var mTabsPreferenceChanged = false
    private var mThemeChanged = false
    private lateinit var binding: ActivitySettingsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyAmoledTheme(this)

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        mThemeChanged = preferences.getBoolean(SettingsFragment.PREF_THEME_CHANGED, false)
        if (mThemeChanged) {
            preferences.edit().putBoolean(SettingsFragment.PREF_THEME_CHANGED, false).apply()
        }

        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = getString(R.string.action_settings)
        setSupportActionBar(binding.toolbar)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentCntainer, SettingsFragment())
            .commit()

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeButtonEnabled(true)
        }

        OnBackPressedDispatcher().addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mTabsPreferenceChanged) {
                    setResult(MainActivity.RESULT_SETTINGS_PAGER_CHANGED)
                } else if (mThemeChanged) {
                    setResult(SettingsFragment.RESULT_THEME_CHANGED)
                }
                finish()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
