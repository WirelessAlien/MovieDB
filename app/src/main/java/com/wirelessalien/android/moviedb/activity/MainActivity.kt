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
import android.annotation.SuppressLint
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
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.adapter.ShowPagingAdapter
import com.wirelessalien.android.moviedb.data.ListData
import com.wirelessalien.android.moviedb.databinding.ActivityMainBinding
import com.wirelessalien.android.moviedb.databinding.DialogSyncProviderBinding
import com.wirelessalien.android.moviedb.fragment.AccountDataFragment
import com.wirelessalien.android.moviedb.fragment.AccountDataFragmentTkt
import com.wirelessalien.android.moviedb.fragment.BaseFragment
import com.wirelessalien.android.moviedb.fragment.HomeFragment
import com.wirelessalien.android.moviedb.fragment.ListFragment
import com.wirelessalien.android.moviedb.fragment.ListFragment.Companion.newSavedInstance
import com.wirelessalien.android.moviedb.fragment.LoginFragment
import com.wirelessalien.android.moviedb.fragment.LoginFragmentTkt
import com.wirelessalien.android.moviedb.fragment.ShowFragment
import com.wirelessalien.android.moviedb.fragment.ShowFragment.Companion.newInstance
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.pagingSource.MultiSearchPagingSource
import com.wirelessalien.android.moviedb.pagingSource.SearchPagingSource
import com.wirelessalien.android.moviedb.tmdb.account.FetchList
import com.wirelessalien.android.moviedb.tmdb.account.GetAccessToken
import com.wirelessalien.android.moviedb.tmdb.account.GetAllListData
import com.wirelessalien.android.moviedb.work.ReleaseReminderWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class MainActivity : BaseActivity() {

    private lateinit var preferences: SharedPreferences
    private lateinit var apiReadAccessToken: String
    private var clientId: String? = null
    private var clientSecret: String? = null
    private lateinit var context: Context
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefListener: OnSharedPreferenceChangeListener
    private lateinit var settingsActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var mHomeSearchShowAdapter: ShowPagingAdapter
    private lateinit var mDatabaseSearchAdapter: ShowBaseAdapter
    private lateinit var mShowLinearLayoutManager: LinearLayoutManager
    private lateinit var mShowGenreList: HashMap<String, String?>
    private lateinit var mDatabaseHelper: MovieDatabaseHelper


    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        mDatabaseHelper = MovieDatabaseHelper(this)

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
        setSupportActionBar(binding.toolbar)

//        binding.toolbar.setNavigationOnClickListener {
//
//        }

        binding.appBar.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(this)

        apiReadAccessToken = ConfigHelper.getConfigValue(this, "api_read_access_token")!!
        clientId = ConfigHelper.getConfigValue(this, "client_id")
        clientSecret = ConfigHelper.getConfigValue(this, "client_secret")
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        binding.bottomNavigation.setOnItemSelectedListener { item: MenuItem ->
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
                R.id.nav_account_tkt -> {
                    selectedFragment = AccountDataFragmentTkt()
                }
            }
            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction().replace(R.id.container, selectedFragment).commit()
                updateSearchBarTitle(selectedFragment)
                updateSearchViewHint(selectedFragment)
                return@setOnItemSelectedListener true
            }
            false
        }

        if (savedInstanceState == null) {
            val initialFragment = HomeFragment()
            supportFragmentManager.beginTransaction().replace(R.id.container, initialFragment).commit()
            updateSearchBarTitle(initialFragment)
            updateSearchViewHint(initialFragment)
        }

        val menu = binding.bottomNavigation.menu
        menu.findItem(R.id.nav_movie)
            .setVisible(!preferences.getBoolean(HIDE_MOVIES_PREFERENCE, false))
        menu.findItem(R.id.nav_series)
            .setVisible(!preferences.getBoolean(HIDE_SERIES_PREFERENCE, false))
        menu.findItem(R.id.nav_saved)
            .setVisible(!preferences.getBoolean(HIDE_SAVED_PREFERENCE, false))
        menu.findItem(R.id.nav_account)
            .setVisible(!preferences.getBoolean(HIDE_ACCOUNT_PREFERENCE, false))
        menu.findItem(R.id.nav_account_tkt)
            .setVisible(!preferences.getBoolean(HIDE_ACCOUNT_TKT_PREFERENCE, false))
        prefListener = OnSharedPreferenceChangeListener { _: SharedPreferences?, key: String? ->
            if (key == HIDE_MOVIES_PREFERENCE || key == HIDE_SERIES_PREFERENCE || key == HIDE_SAVED_PREFERENCE || key == HIDE_ACCOUNT_PREFERENCE || key == HIDE_ACCOUNT_TKT_PREFERENCE) {
                val menu1 = binding.bottomNavigation.menu
                menu1.findItem(R.id.nav_movie)
                    .setVisible(!preferences.getBoolean(HIDE_MOVIES_PREFERENCE, false))
                menu1.findItem(R.id.nav_series)
                    .setVisible(!preferences.getBoolean(HIDE_SERIES_PREFERENCE, false))
                menu1.findItem(R.id.nav_saved)
                    .setVisible(!preferences.getBoolean(HIDE_SAVED_PREFERENCE, false))
                menu1.findItem(R.id.nav_account)
                    .setVisible(!preferences.getBoolean(HIDE_ACCOUNT_PREFERENCE, false))
                menu1.findItem(R.id.nav_account_tkt)
                    .setVisible(!preferences.getBoolean(HIDE_ACCOUNT_TKT_PREFERENCE, false))
            }
        }

        preferences.registerOnSharedPreferenceChangeListener(prefListener)

        val menuHost: MenuHost = this
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
                val currentFragment = supportFragmentManager.findFragmentById(R.id.container)
                val searchMenuItem = menu.findItem(R.id.action_search)
                searchMenuItem.isVisible =
                    !(currentFragment is HomeFragment || currentFragment is AccountDataFragment || currentFragment is AccountDataFragmentTkt)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                val id = menuItem.itemId

                if (id == R.id.action_search) {
                    binding.searchView.show()
                    return true
                }

                if (id == R.id.action_settings) {
                    val intent = Intent(applicationContext, SettingsActivity::class.java)
                    settingsActivityResultLauncher.launch(intent)
                    return true
                }

                if (id == R.id.action_login) {
                    if (preferences.getString("sync_provider", "local") == "trakt") {
                        val loginFragmentTkt = LoginFragmentTkt()
                        loginFragmentTkt.show(supportFragmentManager, "login")
                    } else {
                        val loginFragment = LoginFragment()
                        loginFragment.show(supportFragmentManager, "login")
                    }
                    return true
                }
                return false
            }
        }, this, Lifecycle.State.RESUMED)

        binding.bottomNavigation.viewTreeObserver.addOnGlobalLayoutListener {
            val bottomNavHeight = binding.bottomNavigation.height

            // Adjust the position of the first FAB
            val paramsFab = binding.fab.layoutParams as CoordinatorLayout.LayoutParams
            paramsFab.bottomMargin = bottomNavHeight + 16
            binding.fab.layoutParams = paramsFab

            // Adjust the position of the second FAB above the first FAB with a 16dp margin
            val paramsFab2 = binding.fab2.layoutParams as CoordinatorLayout.LayoutParams
            paramsFab2.bottomMargin = bottomNavHeight + binding.fab.height + 32 // 16dp margin + fab height + 16dp margin
            binding.fab2.layoutParams = paramsFab2
        }

        binding.container.viewTreeObserver.addOnGlobalLayoutListener {
            val bottomNavHeight = binding.bottomNavigation.height
            val params = binding.container.layoutParams as CoordinatorLayout.LayoutParams
            params.bottomMargin = bottomNavHeight
            binding.container.layoutParams = params
        }

        mShowGenreList = HashMap()
        mHomeSearchShowAdapter = ShowPagingAdapter(mShowGenreList, gridView = true, showDeleteButton = false)

        val mShowGridView = GridLayoutManager(this, preferences.getInt(BaseFragment.GRID_SIZE_PREFERENCE, 3))
        binding.searchResultsRecyclerView.layoutManager = mShowGridView
        mShowLinearLayoutManager = mShowGridView

        binding.searchResultsRecyclerView.adapter = mHomeSearchShowAdapter

        setupSearchView()

        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                binding.searchView.hide()
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)

        binding.searchView.addTransitionListener { _, _, newState ->
            if (newState === com.google.android.material.search.SearchView.TransitionState.SHOWING) {
                callback.isEnabled = true
            } else if (newState === com.google.android.material.search.SearchView.TransitionState.HIDING) {
                callback.isEnabled = false
                clearSearchData()
            }
        }

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

        val dialogShown = preferences.getBoolean("sync_provider_dialog_shown", false)
        if (!dialogShown) {
            showSyncProviderDialog()
        }
    }

    private fun clearSearchData() {
        // Clear the search results
        binding.searchResultsRecyclerView.adapter = null
    }

    private fun getCurrentFragment(): Fragment? {
        return supportFragmentManager.findFragmentById(R.id.container)
    }

    private fun updateSearchBarTitle(fragment: Fragment) {
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
        binding.toolbar.title = title
    }

    private fun setupSearchView() {
        val liveSearch = preferences.getBoolean(LIVE_SEARCH_PREFERENCE, false)

        if (liveSearch) {
            binding.searchView.editText.addTextChangedListener(object : TextWatcher {
                private val handler = Handler(Looper.getMainLooper())
                private var workRunnable: Runnable? = null
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (workRunnable != null) {
                        handler.removeCallbacks(workRunnable!!)
                    }
                    workRunnable = Runnable {
                        val currentFragment = getCurrentFragment()
                        if (currentFragment is HomeFragment) {
                            multiSearch(s.toString())
                        } else if (currentFragment is ShowFragment) {
                            val listType = currentFragment.arguments?.getString(ShowFragment.ARG_LIST_TYPE)
                            if (listType == "movie") {
                                showSearch(listType, s.toString())
                            } else if (listType == "tv") {
                                showSearch(listType, s.toString())
                            }
                        } else if (currentFragment is ListFragment) {
                            databaseSearch(s.toString())
                        } else if (currentFragment is AccountDataFragment) {
                            multiSearch(s.toString())
                        } else if (currentFragment is AccountDataFragmentTkt) {
                            multiSearch(s.toString())
                        }
                    }
                    handler.postDelayed(workRunnable!!, 500)
                }

                override fun afterTextChanged(s: Editable) {}
            })
        } else {
            binding.searchView.editText.setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    val query = v.text.toString()
                    val currentFragment = getCurrentFragment()
                    if (currentFragment is HomeFragment) {
                        multiSearch(query)
                    } else if (currentFragment is ShowFragment) {
                        val listType = currentFragment.arguments?.getString(ShowFragment.ARG_LIST_TYPE)
                        if (listType == "movie") {
                            showSearch(listType, query)
                        } else if (listType == "tv") {
                            showSearch(listType, query)
                        }
                    } else if (currentFragment is ListFragment) {
                        databaseSearch(query)
                    } else if (currentFragment is AccountDataFragment) {
                        multiSearch(query)
                    } else if (currentFragment is AccountDataFragmentTkt) {
                        multiSearch(query)
                    }
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun updateSearchViewHint(fragment: Fragment) {
        val hint = when (fragment) {
            is HomeFragment -> getString(R.string.search_movie_show)
            is ShowFragment -> {
                val listType = fragment.arguments?.getString(ShowFragment.ARG_LIST_TYPE)
                if (listType == "movie") getString(R.string.search_movies) else getString(R.string.search_series)
            }
            is ListFragment -> getString(R.string.search_saved)
//            is AccountDataFragment -> getString(R.string.search_account)
//            is AccountDataFragmentTkt -> getString(R.string.search_account_tkt)
            else -> getString(R.string.hint_search)
        }
        binding.searchView.editText.hint = hint
    }

    fun multiSearch(query: String?) {
        if (query.isNullOrEmpty()) {
            return
        }

        binding.searchResultsRecyclerView.adapter =  mHomeSearchShowAdapter

        lifecycleScope.launch {
            Pager(PagingConfig(pageSize = 20)) {
                MultiSearchPagingSource(apiReadAccessToken, query, context)
            }.flow.collectLatest { pagingData ->
                mHomeSearchShowAdapter.submitData(pagingData)
            }
        }

        mHomeSearchShowAdapter.addLoadStateListener { loadState ->
            when (loadState.source.refresh) {
                is LoadState.Loading -> {
                    binding.searchResultsRecyclerView.visibility = View.GONE
                    binding.shimmerFrameLayout.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.startShimmer()
                }

                is LoadState.NotLoading -> {
                    binding.searchResultsRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                }

                is LoadState.Error -> {
                    binding.searchResultsRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                    Toast.makeText(
                        this,
                        getString(R.string.error_loading_data),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showSyncProviderDialog() {
        val dialogBinding = DialogSyncProviderBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.buttonOk.setOnClickListener {
            val selectedProvider = when {
                dialogBinding.radioTrakt.isChecked -> "trakt"
                dialogBinding.radioTmdb.isChecked -> "tmdb"
                else -> "local"
            }

            if (selectedProvider != "local") {
                preferences.edit().putString("sync_provider", selectedProvider).apply()
            }

            preferences.edit().putBoolean("sync_provider_dialog_shown", true).apply()
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showSearch(listType: String, query: String?) {
        if (query.isNullOrEmpty()) {
            return
        }

        binding.searchResultsRecyclerView.adapter =  mHomeSearchShowAdapter

        lifecycleScope.launch {
            Pager(PagingConfig(pageSize = 20)) {
                SearchPagingSource(listType, query, apiReadAccessToken, context)
            }.flow.collectLatest { pagingData ->
                mHomeSearchShowAdapter.submitData(pagingData)
            }
        }

        mHomeSearchShowAdapter.addLoadStateListener { loadState ->
            when (loadState.source.refresh) {
                is LoadState.Loading -> {
                    binding.searchResultsRecyclerView.visibility = View.GONE
                    binding.shimmerFrameLayout.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.startShimmer()
                }

                is LoadState.NotLoading -> {
                    binding.searchResultsRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                }

                is LoadState.Error -> {
                    binding.searchResultsRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                    Toast.makeText(
                        this,
                        getString(R.string.error_loading_data),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun databaseSearch(query: String) {
        if (query.isNotEmpty()) {
            val currentFragment = getCurrentFragment()
            if (currentFragment is ListFragment) {
                val showsFromDatabase = currentFragment.getShowsFromDatabase(query, MovieDatabaseHelper.COLUMN_ID + " DESC")
                mDatabaseSearchAdapter = ShowBaseAdapter(
                    showsFromDatabase, mShowGenreList,
                    preferences.getBoolean(BaseFragment.SHOWS_LIST_PREFERENCE, true)
                )
                binding.searchResultsRecyclerView.adapter = mDatabaseSearchAdapter
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data
        Log.d("MainActivity", "URI received: $uri")
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
            } else if (uri.toString().startsWith("trakt.wirelessalien.showcase://callback")) {
                val code = uri.getQueryParameter("code")
                if (code != null) {
                    Log.d("MainActivity", "Authorization code received: $code")
                    exchangeCodeForAccessToken(code)
                } else {
                    Log.e("MainActivity", "Authorization code not found in the redirect URI")
                }
            }
        }
    }

    private fun exchangeCodeForAccessToken(code: String) {
        val redirectUri = "trakt.wirelessalien.showcase://callback"
        val grantType = "authorization_code"

        val requestBody = FormBody.Builder()
            .add("code", code)
            .add("client_id", clientId ?: "")
            .add("client_secret", clientSecret ?: "")
            .add("redirect_uri", redirectUri)
            .add("grant_type", grantType)
            .build()

        val request = Request.Builder()
            .url("https://api.trakt.tv/oauth/token")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e("MainActivity", "Token exchange failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.peekBody(Long.MAX_VALUE).string()
                    val jsonObject = JSONObject(responseBody)
                    val accessToken = jsonObject.getString("access_token")
                    val refreshToken = jsonObject.getString("refresh_token")
                    // Save the tokens for future use
                    preferences.edit().putString("trakt_access_token", accessToken).apply()
                    preferences.edit().putString("trakt_refresh_token", refreshToken).apply()
                    Log.d("MainActivity", "Access token and refresh token saved to preferences $response")
                } else {
                    // Handle error
                    Log.e("MainActivity", "Error: ${response.message}")
                }
            }
        })
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

    fun getBinding(): ActivityMainBinding {
        return binding
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
        const val HIDE_ACCOUNT_TKT_PREFERENCE = "key_hide_account_tkt_tab"
        const val MOVIE = "movie"
        const val TV = "tv"
        private const val REQUEST_CODE = 101
    }
}
