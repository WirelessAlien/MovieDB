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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.icu.util.TimeZone
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.CrashHelper
import java.util.Locale

/**
 * This class contains some basic functionality that would
 * otherwise be duplicated in multiple activities.
 */
@SuppressLint("Registered")
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHelper.setDefaultUncaughtExceptionHandler(applicationContext)
    }

    /**
     * Creates the toolbar.
     */
    fun setNavigationDrawer() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    /**
     * Creates a home button in the toolbar.
     */
    fun setBackButtons() {
        // Add back button to the activity.
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeButtonEnabled(true)
        }
    }

    /**
     * Checks if a network is available.
     * If/Once a network connection is established, it calls doNetworkWork().
     */
    fun checkNetwork() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()
        connectivityManager.registerNetworkCallback(builder.build(),
            object : NetworkCallback() {
                override fun onAvailable(network: Network) {
                    doNetworkWork()
                }
            })

        // Check if there is an Internet connection, if not tell the user.
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        if (networkCapabilities == null || !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            Toast.makeText(
                applicationContext, applicationContext.resources
                    .getString(R.string.no_internet_connection), Toast.LENGTH_SHORT
            ).show()
        } else {
            doNetworkWork()
        }
    }

    open fun doNetworkWork() {}
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId

        // Back button
        if (id == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    public override fun onResume() {
        super.onResume()
    }

    public override fun onPause() {
        super.onPause()
    }

    inner class ConnectivityReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connectivityManager =
                context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            if (networkCapabilities != null && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                doNetworkWork()
            }
        }
    }

    companion object {
        private const val API_LANGUAGE_PREFERENCE = "key_api_language"
        private const val API_REGION_PREFERENCE = "key_api_region"
        private const val API_TIMEZONE_PREFERENCE = "key_api_timezone"

        fun getLanguageParameter(context: Context?): String {
            val languageParameter = "&language="
            val language = Locale.getDefault().language
            val preferences = PreferenceManager.getDefaultSharedPreferences(
                context!!
            )
            val userPickedLanguage = preferences.getString(API_LANGUAGE_PREFERENCE, null)
            return if (!userPickedLanguage.isNullOrEmpty()) {
                languageParameter + userPickedLanguage
            } else languageParameter + language
        }

        fun getLanguageParameter2(context: Context?): String {
            val languageParameter = "?language="
            val language = Locale.getDefault().language
            val preferences = PreferenceManager.getDefaultSharedPreferences(
                context!!
            )
            val userPickedLanguage = preferences.getString(API_LANGUAGE_PREFERENCE, null)
            return if (!userPickedLanguage.isNullOrEmpty()) {
                languageParameter + userPickedLanguage
            } else languageParameter + language
        }

        fun getRegionParameter(context: Context?): String {
            val regionParameter = "region="
            val region = Locale.getDefault().country
            val preferences = PreferenceManager.getDefaultSharedPreferences(
                context!!
            )
            val userPickedRegion = preferences.getString(API_REGION_PREFERENCE, null)
            return if (!userPickedRegion.isNullOrEmpty()) {
                regionParameter + userPickedRegion
            } else regionParameter + region
        }

        fun getRegionParameter2(context: Context?): String {
            val regionParameter = "watch_region="
            val region = Locale.getDefault().country
            val preferences = PreferenceManager.getDefaultSharedPreferences(
                context!!
            )
            val userPickedRegion = preferences.getString(API_REGION_PREFERENCE, null)
            return if (!userPickedRegion.isNullOrEmpty()) {
                regionParameter + userPickedRegion
            } else regionParameter + region
        }

        fun getTimeZoneParameter(context: Context?): String {
            val timeZoneParameter = "timezone="
            val timeZone = TimeZone.getDefault().id
            val preferences = PreferenceManager.getDefaultSharedPreferences(
                context!!
            )
            val userPickedTimeZone = preferences.getString(API_TIMEZONE_PREFERENCE, null)
            return if (!userPickedTimeZone.isNullOrEmpty()) {
                timeZoneParameter + userPickedTimeZone
            } else timeZoneParameter + timeZone
        }
    }
}
