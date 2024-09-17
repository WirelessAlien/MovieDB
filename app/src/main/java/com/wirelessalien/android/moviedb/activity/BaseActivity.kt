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
package com.wirelessalien.android.moviedb.activity

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.wirelessalien.android.moviedb.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale

/**
 * This class contains some basic functionality that would
 * otherwise be duplicated in multiple activities.
 */
@SuppressLint("Registered")
open class BaseActivity : AppCompatActivity() {
    private val connectivityReceiver: ConnectivityReceiver? = null
    private val intentFilter: IntentFilter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler { thread: Thread?, throwable: Throwable ->
            val crashLog = StringWriter()
            val printWriter = PrintWriter(crashLog)
            throwable.printStackTrace(printWriter)
            val osVersion = Build.VERSION.RELEASE
            var appVersion = ""
            try {
                appVersion = applicationContext.packageManager.getPackageInfo(
                    applicationContext.packageName,
                    0
                ).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            printWriter.write("\nDevice OS Version: $osVersion")
            printWriter.write("\nApp Version: $appVersion")
            printWriter.close()
            try {
                val fileName = "Crash_Log.txt"
                val targetFile = File(applicationContext.filesDir, fileName)
                val fileOutputStream = FileOutputStream(targetFile, true)
                fileOutputStream.write((crashLog.toString() + "\n").toByteArray())
                fileOutputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            Process.killProcess(Process.myPid())
        }
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
        val networkInfo = connectivityManager.activeNetworkInfo
        if (networkInfo == null || !networkInfo.isConnectedOrConnecting) {
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
            val activeNetInfo = connectivityManager.activeNetworkInfo
            if (activeNetInfo != null && activeNetInfo.isConnectedOrConnecting) {
                doNetworkWork()
            }
        }
    }

    companion object {
        private const val API_LANGUAGE_PREFERENCE = "key_api_language"

        /**
         * Returns the language that is used by the phone.
         * Usage: this is only meant to be used at the end of the API url.
         * Otherwise an ampersand needs to be added manually at the end
         * and the possibility that an empty string can be returned
         * (which will interfere with the manual ampersand) must be
         * taken into account.
         */
        @JvmStatic
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
    }
}
