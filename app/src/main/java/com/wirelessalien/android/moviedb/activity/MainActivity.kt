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


import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationBarView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter
import com.wirelessalien.android.moviedb.data.ListData
import com.wirelessalien.android.moviedb.fragment.AccountDataFragment
import com.wirelessalien.android.moviedb.fragment.BaseFragment
import com.wirelessalien.android.moviedb.fragment.HomeFragment
import com.wirelessalien.android.moviedb.fragment.ListFragment
import com.wirelessalien.android.moviedb.fragment.ListFragment.Companion.newSavedInstance
import com.wirelessalien.android.moviedb.fragment.LoginFragment
import com.wirelessalien.android.moviedb.fragment.PersonFragment
import com.wirelessalien.android.moviedb.fragment.ShowFragment
import com.wirelessalien.android.moviedb.fragment.ShowFragment.Companion.newInstance
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.account.FetchList
import com.wirelessalien.android.moviedb.tmdb.account.GetAccessToken
import com.wirelessalien.android.moviedb.tmdb.account.GetAllListData
import com.wirelessalien.android.moviedb.work.ReleaseReminderWorker
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class MainActivity : BaseActivity() {
    private lateinit var bottomNavigationView: BottomNavigationView

    // Variables used for searching
    private var mSearchAction: MenuItem? = null
    private var isSearchOpened = false
    private lateinit var preferences: SharedPreferences
    private lateinit var apiReadAccessToken: String
    private lateinit var context: Context
    private lateinit var prefListener: OnSharedPreferenceChangeListener
    private lateinit var settingsActivityResultLauncher: ActivityResultLauncher<Intent>

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        context = this

        val fileName = "Crash_Log.txt"
        val crashLogFile = File(filesDir, fileName)
        if (crashLogFile.exists()) {
            val crashLog = StringBuilder()
            try {
                val reader = BufferedReader(FileReader(crashLogFile))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    crashLog.append(line)
                    crashLog.append('\n')
                }
                reader.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_crash_log, null)
            val textView = dialogView.findViewById<TextView>(R.id.crash_log_text)
            textView.text = crashLog.toString()

            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.crash_log))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.copy_text)) { _: DialogInterface?, _: Int ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("ShowCase Crash Log", crashLog.toString())
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@MainActivity, R.string.crash_log_copied, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.close), null)
                .show()
            crashLogFile.delete()
        }

        // Set the default preference values.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        apiReadAccessToken = ConfigHelper.getConfigValue(this, "api_read_access_token")!!
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setOnItemSelectedListener(NavigationBarView.OnItemSelectedListener setOnItemSelectedListener@{ item: MenuItem ->
            val itemId = item.itemId
            var selectedFragment: Fragment? = null
            when (itemId) {
                R.id.nav_home -> {
                    selectedFragment = HomeFragment()
                }
                R.id.nav_movie -> {
                    selectedFragment = newInstance(MOVIE)
                }
                R.id.nav_series -> {
                    selectedFragment = newInstance(TV)
                }
                R.id.nav_saved -> {
                    selectedFragment = newSavedInstance()
                }
                R.id.nav_account -> {
                    selectedFragment = AccountDataFragment()
                }
            }
            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction().replace(R.id.container, selectedFragment)
                    .commit()
                return@setOnItemSelectedListener true
            }
            false
        })
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.container, HomeFragment())
                .commit()
        }
        val menu = bottomNavigationView.menu
        menu.findItem(R.id.nav_movie)
            .setVisible(!preferences.getBoolean(HIDE_MOVIES_PREFERENCE, false))
        menu.findItem(R.id.nav_series)
            .setVisible(!preferences.getBoolean(HIDE_SERIES_PREFERENCE, false))
        menu.findItem(R.id.nav_saved)
            .setVisible(!preferences.getBoolean(HIDE_SAVED_PREFERENCE, false))
        menu.findItem(R.id.nav_account)
            .setVisible(!preferences.getBoolean(HIDE_ACCOUNT_PREFERENCE, false))
        prefListener = OnSharedPreferenceChangeListener { _: SharedPreferences?, key: String? ->
            if (key == HIDE_MOVIES_PREFERENCE || key == HIDE_SERIES_PREFERENCE || key == HIDE_SAVED_PREFERENCE || key == HIDE_ACCOUNT_PREFERENCE) {
                val menu1 = bottomNavigationView.menu
                menu1.findItem(R.id.nav_movie)
                    .setVisible(!preferences.getBoolean(HIDE_MOVIES_PREFERENCE, false))
                menu1.findItem(R.id.nav_series).setVisible(
                    !preferences.getBoolean(
                        HIDE_SERIES_PREFERENCE, false
                    )
                )
                menu1.findItem(R.id.nav_saved)
                    .setVisible(!preferences.getBoolean(HIDE_SAVED_PREFERENCE, false))
                menu1.findItem(R.id.nav_account).setVisible(
                    !preferences.getBoolean(
                        HIDE_ACCOUNT_PREFERENCE, false
                    )
                )
            }
        }

        preferences.registerOnSharedPreferenceChangeListener(prefListener)
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        val fab2 = findViewById<FloatingActionButton>(R.id.fab2)

        bottomNavigationView.viewTreeObserver.addOnGlobalLayoutListener {
            val bottomNavHeight = bottomNavigationView.height

            // Adjust the position of the first FAB
            val paramsFab = fab.layoutParams as CoordinatorLayout.LayoutParams
            paramsFab.bottomMargin = bottomNavHeight + 16
            fab.layoutParams = paramsFab

            // Adjust the position of the second FAB above the first FAB with a 16dp margin
            val paramsFab2 = fab2.layoutParams as CoordinatorLayout.LayoutParams
            paramsFab2.bottomMargin = bottomNavHeight + fab.height + 32 // 16dp margin + fab height + 16dp margin
            fab2.layoutParams = paramsFab2
        }

        val fragmentContainerView = findViewById<FragmentContainerView>(R.id.container)
        fragmentContainerView.viewTreeObserver.addOnGlobalLayoutListener {
            val bottomNavHeight = bottomNavigationView.height
            val params = fragmentContainerView.layoutParams as CoordinatorLayout.LayoutParams
            params.bottomMargin = bottomNavHeight
            fragmentContainerView.layoutParams = params
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                super.onFragmentResumed(fm, f)
                updateToolbarTitle(f)
            }
        }, true)


        OnBackPressedDispatcher().addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearchOpened) {
                    handleMenuSearch()
                } else {
                    finish()
                }
            }
        })

        settingsActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val pagerChanged = data.getBooleanExtra("pager_changed", false)
                    if (pagerChanged) {
                        setResult(RESULT_SETTINGS_PAGER_CHANGED)
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_required)
                    .setMessage(R.string.permission_required_description)
                    .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                        ActivityCompat.requestPermissions(
                            this@MainActivity, arrayOf(
                                Manifest.permission.POST_NOTIFICATIONS
                            ), REQUEST_CODE
                        )
                    }
                    .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                    .create().show()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.released_movies)
            val description = getString(R.string.notification_for_movie_released)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("released_movies", name, importance)
            channel.description = description
            val notificationManager = getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.aired_episodes)
            val description = getString(R.string.notification_for_episode_air)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("episode_reminders", name, importance)
            channel.description = description
            val notificationManager = getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }

        ReleaseReminderWorker.scheduleWork(this)

        val accessToken = preferences.getString("access_token", null)
        val hasRunOnce = preferences.getBoolean("hasRunOnce", false)
        if (!hasRunOnce && accessToken != null && accessToken != "") {
            val listDatabaseHelper = ListDatabaseHelper(this@MainActivity)
            val db = listDatabaseHelper.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM " + ListDatabaseHelper.TABLE_LISTS, null)
            if (cursor.count > 0) {
                Handler(Looper.getMainLooper())
                val progressDialog = MaterialAlertDialogBuilder(this@MainActivity)
                    .setView(R.layout.dialog_progress)
                    .setCancelable(false)
                    .create()
                progressDialog.show()

                lifecycleScope.launch {
                    try {
                        val fetchListCoroutineTMDb = FetchList(this@MainActivity, object : FetchList.OnListFetchListener {
                            override fun onListFetch(listData: List<ListData>?) {
                                if (listData != null) {
                                    for (data in listData) {
                                        listDatabaseHelper.addList(data.id, data.name)
                                        val listDetailsCoroutineTMDb = GetAllListData(
                                            data.id,
                                            this@MainActivity,
                                            object : GetAllListData.OnFetchListDetailsListener {
                                                override fun onFetchListDetails(listDetailsData: ArrayList<JSONObject>?) {
                                                    if (listDetailsData != null) {
                                                        for (item in listDetailsData) {
                                                            try {
                                                                val movieId = item.getInt("id")
                                                                val mediaType = item.getString("media_type")
                                                                listDatabaseHelper.addListDetails(
                                                                    data.id,
                                                                    data.name,
                                                                    movieId,
                                                                    mediaType
                                                                )
                                                            } catch (e: JSONException) {
                                                                e.printStackTrace()
                                                                Toast.makeText(
                                                                    this@MainActivity,
                                                                    R.string.error_occurred_in_list_data,
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                        lifecycleScope.launch {
                                            listDetailsCoroutineTMDb.fetchAllListData()
                                        }
                                    }
                                }
                            }
                        })
                        fetchListCoroutineTMDb.fetchLists()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        progressDialog.dismiss()
                    }
                }
            }
            cursor.close()
            preferences.edit().putBoolean("hasRunOnce", true).apply()
        }
    }

    private fun updateToolbarTitle(fragment: Fragment) {
        val title = when (fragment) {
            is HomeFragment -> getString(R.string.app_name)
            is ShowFragment -> {
                val listType = fragment.arguments?.getString(ShowFragment.ARG_LIST_TYPE)
                if (listType == SectionsPagerAdapter.MOVIE) getString(R.string.title_movies) else getString(R.string.title_series)
            }
            is ListFragment -> getString(R.string.title_saved)
            is AccountDataFragment -> getString(R.string.title_account)
            else -> getString(R.string.app_name)
        }
        supportActionBar?.title = title
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data
        if (uri != null) {
            if (uri.toString().startsWith("com.wirelessalien.android.moviedb://callback")) {
                val requestToken = preferences.getString("request_token", null)
                if (requestToken != null) {
                    val progressDialog = MaterialAlertDialogBuilder(this)
                        .setView(R.layout.dialog_progress)
                        .setCancelable(false)
                        .create()
                    progressDialog.show()

                    lifecycleScope.launch {
                        val getAccessToken = GetAccessToken(
                            apiReadAccessToken,
                            requestToken,
                            this@MainActivity,
                            null,
                            object : GetAccessToken.OnTokenReceivedListener {
                                override fun onTokenReceived(accessToken: String?) {
                                    lifecycleScope.launch {
                                        val listDatabaseHelper = ListDatabaseHelper(this@MainActivity)
                                        listDatabaseHelper.deleteAllData()

                                        val fetchListCoroutineTMDb = FetchList(
                                            this@MainActivity,
                                            object : FetchList.OnListFetchListener {
                                                override fun onListFetch(listData: List<ListData>?) {
                                                    for (data in listData!!) {
                                                        listDatabaseHelper.addList(data.id, data.name)
                                                        val listDetailsCoroutineTMDb = GetAllListData(
                                                            data.id,
                                                            this@MainActivity,
                                                            object : GetAllListData.OnFetchListDetailsListener {
                                                                override fun onFetchListDetails(listDetailsData: ArrayList<JSONObject>?) {
                                                                    for (item in listDetailsData!!) {
                                                                        try {
                                                                            val movieId = item.getInt("id")
                                                                            val mediaType = item.getString("media_type")
                                                                            listDatabaseHelper.addListDetails(
                                                                                data.id,
                                                                                data.name,
                                                                                movieId,
                                                                                mediaType
                                                                            )
                                                                        } catch (e: JSONException) {
                                                                            e.printStackTrace()
                                                                            progressDialog.dismiss()
                                                                            Toast.makeText(
                                                                                this@MainActivity,
                                                                                R.string.error_occurred_in_list_data,
                                                                                Toast.LENGTH_SHORT
                                                                            ).show()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        )
                                                        lifecycleScope.launch {
                                                            listDetailsCoroutineTMDb.fetchAllListData()
                                                        }
                                                    }
                                                    progressDialog.dismiss()
                                                }
                                            }
                                        )
                                        fetchListCoroutineTMDb.fetchLists()
                                    }
                                }
                            }
                        )
                        getAccessToken.fetchAccessToken()
                    }
                }
            }
        }
    }

    override fun doNetworkWork() {
        // Pass the call to all fragments.
        val fragmentManager = supportFragmentManager
        val fragmentList = fragmentManager.fragments
        for (fragment in fragmentList) {
            val baseFragment = fragment as BaseFragment
            baseFragment.doNetworkWork()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the listener
        preferences.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        inflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId

        // Search action
        if (id == R.id.action_search) {
            handleMenuSearch()
            return true
        }

        if (id == R.id.action_settings) {
            val intent = Intent(applicationContext, SettingsActivity::class.java)
            settingsActivityResultLauncher.launch(intent)
            return true
        }
        if (id == R.id.action_login) {
            val loginFragment = LoginFragment()
            loginFragment.show(supportFragmentManager, "login")
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        mSearchAction = menu.findItem(R.id.action_search)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_ASK_PERMISSIONS_EXPORT -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    val intent = Intent(applicationContext, ExportActivity::class.java)
                    startActivity(intent)
                } // else: permission denied
            }

            REQUEST_CODE_ASK_PERMISSIONS_IMPORT -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    val intent = Intent(applicationContext, ImportActivity::class.java)
                    startActivity(intent)
                } // else: permission denied
            }

            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    /**
     * Handles input from the search bar and icon.
     */
    private fun handleMenuSearch() {
        val liveSearch = preferences.getBoolean(LIVE_SEARCH_PREFERENCE, true)
        val searchView = mSearchAction!!.actionView as SearchView?
        if (isSearchOpened) {
            if (searchView != null && searchView.query.toString() == "") {
                searchView.isIconified = true
                mSearchAction!!.collapseActionView()
                isSearchOpened = false
                cancelSearchInFragment()
            }
        } else {
            mSearchAction!!.expandActionView()
            isSearchOpened = true
            searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    searchInFragment(query)
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    if (liveSearch) {
                        searchInFragment(newText)
                    }
                    return true
                }
            })
        }
    }

    private fun searchInFragment(query: String) {
        // This is a hack
        val fragmentManager = supportFragmentManager
        val mCurrentFragment = fragmentManager.findFragmentById(R.id.container)
        if (mCurrentFragment != null) {
            if (mCurrentFragment is ShowFragment) {
                mCurrentFragment.search(query)
            }
            if (mCurrentFragment is ListFragment) {
                mCurrentFragment.search(query)
            }
            if (mCurrentFragment is PersonFragment) {
                mCurrentFragment.search(query)
            }
        } else {
            Log.d("MainActivity", "Current fragment is null")
        }
    }

    /**
     * Cancel the searching process in the fragment.
     */
    private fun cancelSearchInFragment() {
        // This is a hack
        val fragmentManager = supportFragmentManager
        val mCurrentFragment = fragmentManager.findFragmentById(R.id.container)
        if (mCurrentFragment != null) {
            if (mCurrentFragment is ShowFragment) {
                mCurrentFragment.cancelSearch()
            }
            if (mCurrentFragment is ListFragment) {
                mCurrentFragment.cancelSearch()
            }
            if (mCurrentFragment is PersonFragment) {
                mCurrentFragment.cancelSearch()
            }
        }
    }

    companion object {
        const val RESULT_SETTINGS_PAGER_CHANGED = 1001
        private const val REQUEST_CODE_ASK_PERMISSIONS_EXPORT = 123
        private const val REQUEST_CODE_ASK_PERMISSIONS_IMPORT = 124
        private const val LIVE_SEARCH_PREFERENCE = "key_live_search"
        const val HIDE_MOVIES_PREFERENCE = "key_hide_movies_tab"
        const val HIDE_SERIES_PREFERENCE = "key_hide_series_tab"
        const val HIDE_SAVED_PREFERENCE = "key_hide_saved_tab"
        const val HIDE_ACCOUNT_PREFERENCE = "key_hide_account_tab"
        const val MOVIE = "movie"
        const val TV = "tv"
        private const val REQUEST_CODE = 101
    }
}
