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
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.chip.Chip
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.adapter.ShowPagingAdapter
import com.wirelessalien.android.moviedb.data.ListDataTmdb
import com.wirelessalien.android.moviedb.data.TktTokenResponse
import com.wirelessalien.android.moviedb.databinding.ActivityMainBinding
import com.wirelessalien.android.moviedb.databinding.DialogProgressIndicatorBinding
import com.wirelessalien.android.moviedb.databinding.DialogRefreshOptionsBinding
import com.wirelessalien.android.moviedb.fragment.AboutBottomSheet
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
import com.wirelessalien.android.moviedb.fragment.SyncProviderBottomSheet
import com.wirelessalien.android.moviedb.fragment.UpNextFragment
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.pagingSource.MultiSearchPagingSource
import com.wirelessalien.android.moviedb.pagingSource.SearchPagingSource
import com.wirelessalien.android.moviedb.service.TraktSyncService
import com.wirelessalien.android.moviedb.tmdb.GetTmdbDetails
import com.wirelessalien.android.moviedb.tmdb.account.FetchList
import com.wirelessalien.android.moviedb.tmdb.account.GetAccessToken
import com.wirelessalien.android.moviedb.tmdb.account.GetAllListData
import com.wirelessalien.android.moviedb.trakt.GetTraktSyncData
import com.wirelessalien.android.moviedb.work.DailyWorkerTkt
import com.wirelessalien.android.moviedb.work.GetTmdbTvDetailsWorker
import com.wirelessalien.android.moviedb.work.TktTokenRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity() {

    private lateinit var preferences: SharedPreferences
    private lateinit var apiReadAccessToken: String
    private lateinit var tmdbApiKey: String
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
    private lateinit var epDbHelper: EpisodeReminderDatabaseHelper
    private var traktReceiver: BroadcastReceiver? = null


    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        epDbHelper = EpisodeReminderDatabaseHelper(applicationContext)
        mDatabaseHelper = MovieDatabaseHelper(applicationContext)

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

        binding.toolbar.setNavigationOnClickListener {
            val aboutBottomSheet = AboutBottomSheet()
            aboutBottomSheet.show(supportFragmentManager, "aboutBottomSheet")
        }

        binding.appBar.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(this)

        apiReadAccessToken = ConfigHelper.getConfigValue(this, "api_read_access_token")?: ""
        tmdbApiKey = ConfigHelper.getConfigValue(this, "api_key")?: ""
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
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                val id = menuItem.itemId

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

            val paramsChip = binding.upnextChip.layoutParams as CoordinatorLayout.LayoutParams
            paramsChip.bottomMargin = bottomNavHeight + 16
            binding.upnextChip.layoutParams = paramsChip

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

        if (preferences.getBoolean("key_show_continue_watching", true)) {
            binding.upnextChip.visibility = View.VISIBLE
        } else {
            binding.upnextChip.visibility = View.GONE
        }

        binding.upnextChip.setOnClickListener {
            val upNextFragment = UpNextFragment()
            upNextFragment.show(supportFragmentManager, "UpNextFragment")
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

        if (preferences.getBoolean("key_auto_update_episode_data", true)) {
            scheduleMonthlyTvShowUpdate()
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

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val dailyWorkRequest = PeriodicWorkRequest.Builder(DailyWorkerTkt::class.java, 1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_work_tkt",
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyWorkRequest
        )

        checkTokenExpiration()

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
                            override fun onListFetch(listDatumTmdbs: List<ListDataTmdb>?) {
                                if (listDatumTmdbs != null) {
                                    for (data in listDatumTmdbs) {
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

    private fun scheduleMonthlyTvShowUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val monthlyWorkRequest =
            PeriodicWorkRequestBuilder<GetTmdbTvDetailsWorker>(30, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "monthlyTvShowUpdateWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            monthlyWorkRequest
        )
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
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
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
                    binding.shimmerFrameLayout.visibility = View.GONE
                    val errorMessage = (loadState.source.refresh as LoadState.Error).error.message
                    Toast.makeText(this, getString(R.string.error_loading_data) + ": " + errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSyncProviderDialog() {
        val bottomSheet = SyncProviderBottomSheet()
        bottomSheet.show(supportFragmentManager, bottomSheet.tag)
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
                    binding.shimmerFrameLayout.visibility = View.GONE
                    val errorMessage = (loadState.source.refresh as LoadState.Error).error.message
                    Toast.makeText(this, getString(R.string.error_loading_data) + ": " + errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun databaseSearch(query: String) {
        if (query.isNotEmpty()) {
            val currentFragment = getCurrentFragment()
            if (currentFragment is ListFragment) {
                val showsFromDatabase = currentFragment.getShowsFromDatabase(query, MovieDatabaseHelper.COLUMN_ID + " DESC", null)
                mDatabaseSearchAdapter = ShowBaseAdapter(context, showsFromDatabase, mShowGenreList, true)
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
                                                override fun onListFetch(listDatumTmdbs: List<ListDataTmdb>?) {
                                                    for (data in listDatumTmdbs!!) {
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
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.peekBody(Long.MAX_VALUE).string()
                    val jsonObject = JSONObject(responseBody)
                    val tokenResponse = TktTokenResponse(
                        accessToken = jsonObject.getString("access_token"),
                        refreshToken = jsonObject.getString("refresh_token"),
                        expiresIn = jsonObject.getLong("expires_in"),
                        createdAt = jsonObject.getLong("created_at")
                    )

                    // Save token data
                    preferences.edit().apply {
                        putString("trakt_access_token", tokenResponse.accessToken)
                        putString("trakt_refresh_token", tokenResponse.refreshToken)
                        putLong("token_expires_in", tokenResponse.expiresIn)
                        putLong("token_created_at", tokenResponse.createdAt)
                        apply()
                    }

                    scheduleTokenRefresh(tokenResponse.expiresIn)

                    runOnUiThread {
                        Toast.makeText(this@MainActivity, getString(R.string.login_successful), Toast.LENGTH_SHORT).show()
                        showRefreshDialog(tokenResponse.accessToken)
                    }
                } else {
                    Log.e("MainActivity", "Error: ${response.message}")
                }
            }
        })
    }

    private fun scheduleTokenRefresh(expiresIn: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Schedule refresh 1 hour before token expires
        val refreshDelay = expiresIn - 3600 // Refresh 1 hour before expiration

        val tokenRefreshWorkRequest = OneTimeWorkRequestBuilder<TktTokenRefreshWorker>()
            .setInitialDelay(refreshDelay, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).apply {
            // Cancel any existing refresh work
            cancelAllWorkByTag("token_refresh")
            // Enqueue new refresh work
            enqueueUniqueWork(
                "token_refresh",
                ExistingWorkPolicy.REPLACE,
                tokenRefreshWorkRequest
            )
        }
    }

    private fun checkTokenExpiration() {
        val createdAt = preferences.getLong("token_created_at", 0)
        val expiresIn = preferences.getLong("token_expires_in", 0)
        val refreshToken = preferences.getString("trakt_refresh_token", null)

        if (createdAt > 0 && expiresIn > 0 && refreshToken != null) {
            val currentTime = System.currentTimeMillis() / 1000
            val expirationTime = createdAt + expiresIn

            // Refresh token if it's expired or will expire in next hour
            if (currentTime >= expirationTime - 3600) {
                refreshAccessToken(refreshToken)
            }
        }
    }

    private fun refreshAccessToken(refreshToken: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val requestBody = FormBody.Builder()
                    .add("refresh_token", refreshToken)
                    .add("client_id", clientId ?: "")
                    .add("client_secret", clientSecret ?: "")
                    .add("redirect_uri", "trakt.wirelessalien.showcase://callback")
                    .add("grant_type", "refresh_token")
                    .build()

                val request = Request.Builder()
                    .url("https://api.trakt.tv/oauth/token")
                    .post(requestBody)
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val jsonObject = JSONObject(responseBody)

                    val tokenResponse = TktTokenResponse(
                        accessToken = jsonObject.getString("access_token"),
                        refreshToken = jsonObject.getString("refresh_token"),
                        expiresIn = jsonObject.getLong("expires_in"),
                        createdAt = System.currentTimeMillis() / 1000
                    )

                    // Save new token data
                    preferences.edit().apply {
                        putString("trakt_access_token", tokenResponse.accessToken)
                        putString("trakt_refresh_token", tokenResponse.refreshToken)
                        putLong("token_expires_in", tokenResponse.expiresIn)
                        putLong("token_created_at", tokenResponse.createdAt)
                        apply()
                    }

                    scheduleTokenRefresh(tokenResponse.expiresIn)
                } else {
                    Log.e("TokenRefresh", "Failed to refresh token: ${response.code}")
                    preferences.edit().remove("trakt_access_token").apply()
                }
            } catch (e: Exception) {
                Log.e("TokenRefresh", "Error refreshing token", e)
                preferences.edit().remove("trakt_access_token").apply()
            }
        }
    }

    @SuppressLint("InlinedApi", "UnspecifiedRegisterReceiverFlag")
    private fun showRefreshDialog(accessToken: String) {
        val options = listOf(
            getString(R.string.movie_collection),
            getString(R.string.show_collection),
            getString(R.string.movie_watched),
            getString(R.string.show_watched),
            getString(R.string.history),
            getString(R.string.rating1),
            getString(R.string.watchlist),
            getString(R.string.favourite),
            getString(R.string.lists),
            getString(R.string.list_items),
            getString(R.string.upcoming)
        )

        val selectedOptions = preferences.getStringSet("selected_options", options.toMutableSet())?.toMutableSet() ?: options.toMutableSet()

        val dialogBinding = DialogRefreshOptionsBinding.inflate(LayoutInflater.from(context))
        val chipGroup = dialogBinding.chipGroup
        val allSwitch = dialogBinding.allSwitch.apply {
            isChecked = true
        }

        chipGroup.visibility = View.GONE

        allSwitch.setOnCheckedChangeListener { _, isChecked ->
            chipGroup.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        options.forEach { option ->
            val chip = Chip(context).apply {
                text = option
                isCheckable = true
                isChecked = selectedOptions.contains(option)
                setChipIconResource(if (isChecked) R.drawable.ic_done_all else R.drawable.ic_close)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedOptions.add(option)
                        setChipIconResource(R.drawable.ic_done_all)
                    } else {
                        selectedOptions.remove(option)
                        setChipIconResource(R.drawable.ic_close)
                    }
                }
            }
            chipGroup.addView(chip)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.refresh))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                if (allSwitch.isChecked) {
                    val intent = Intent(this, TraktSyncService::class.java).apply {
                        action = TraktSyncService.ACTION_START_SERVICE
                        putExtra(TraktSyncService.EXTRA_ACCESS_TOKEN, accessToken)
                        putExtra(TraktSyncService.EXTRA_CLIENT_ID, clientId)
                        putExtra(TraktSyncService.EXTRA_TMDB_API_KEY, tmdbApiKey)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }

                    traktReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            try {
                                context?.unregisterReceiver(this)
                            } catch (e: IllegalArgumentException) {
                                e.printStackTrace()
                            }

                            Toast.makeText(context, getString(R.string.sync_completed), Toast.LENGTH_SHORT).show()
                        }
                    }

                    val filter = IntentFilter(TraktSyncService.ACTION_SERVICE_COMPLETED)

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            registerReceiver(traktReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                        } else {
                            registerReceiver(traktReceiver, filter)
                        }
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()
                    }

                } else {
                    preferences.edit().putStringSet("selected_options", selectedOptions).apply()
                    refreshData(selectedOptions, accessToken)
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
            }
            .create()

        dialog.setOnDismissListener {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dialog.show()
    }

    private fun refreshData(selectedOptions: Set<String>, accessToken: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_progress_indicator, null)
        var job: Job? = null

        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.fetching_data))
            .setView(dialogView)
            .setCancelable(false)
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                job?.cancel()
                dialog.dismiss()
            }
            .show()

        val progressTextView = dialogView.findViewById<TextView>(R.id.progressText)

        fun updateProgressMessage(message: String) {
            lifecycleScope.launch(Dispatchers.Main) {
                progressTextView.text = message
            }
        }

        job = lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val getTraktSyncData = GetTraktSyncData(this@MainActivity, accessToken, clientId)
                selectedOptions.forEach { option ->
                    when (option) {
                        getString(R.string.movie_collection) -> {
                            updateProgressMessage(getString(R.string.fetching_movie_collection))
                            getTraktSyncData.fetchCollectionData()
                        }
                        getString(R.string.show_collection) -> {
                            updateProgressMessage(getString(R.string.fetching_show_collection))
                            getTraktSyncData.fetchCollectionShowData()
                        }
                        getString(R.string.movie_watched) -> {
                            updateProgressMessage(getString(R.string.fetching_movie_watched))
                            getTraktSyncData.fetchWatchedDataMovie()
                        }
                        getString(R.string.show_watched) -> {
                            updateProgressMessage(getString(R.string.fetching_show_watched))
                            getTraktSyncData.fetchWatchedDataShow()
                        }
                        getString(R.string.history) -> {
                            updateProgressMessage(getString(R.string.fetching_history))
                            getTraktSyncData.fetchHistoryData()
                        }
                        getString(R.string.rating1) -> {
                            updateProgressMessage(getString(R.string.fetching_rating))
                            getTraktSyncData.fetchRatingData()
                        }
                        getString(R.string.watchlist) -> {
                            updateProgressMessage(getString(R.string.fetching_watchlist))
                            getTraktSyncData.fetchWatchlistData()
                        }
                        getString(R.string.favourite) -> {
                            updateProgressMessage(getString(R.string.fetching_favorite))
                            getTraktSyncData.fetchFavoriteData()
                        }
                        getString(R.string.lists) -> {
                            updateProgressMessage(getString(R.string.fetching_lists))
                            getTraktSyncData.fetchUserLists()
                        }
                        getString(R.string.list_items) -> {
                            updateProgressMessage(getString(R.string.fetching_list_items))
                            getTraktSyncData.fetchAllListItems()
                        }
                        getString(R.string.upcoming) -> {
                            updateProgressMessage(getString(R.string.fetching_upcoming))
                            getTraktSyncData.fetchCalendarData()
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    showTmdbDetailsDialog()
                }
            }
        }
    }

    private fun showTmdbDetailsDialog() {
        val binding = DialogProgressIndicatorBinding.inflate(LayoutInflater.from(context))
        var job: Job? = null

        val tmdbDialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle(getString(R.string.fetching_tmdb_data))
            .setView(binding.root)
            .setCancelable(false)
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                job?.cancel()
                dialog.dismiss()
            }
            .show()

        job = lifecycleScope.launch {
            val getTmdbDetails = GetTmdbDetails(this@MainActivity, tmdbApiKey)
            getTmdbDetails.fetchAndSaveTmdbDetails { showTitle, progress ->
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.progressText.text = showTitle
                    binding.progressIndicator.progress = progress
                }
            }
            tmdbDialog.dismiss()
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

    override fun onSupportNavigateUp(): Boolean {
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the listener
        preferences.unregisterOnSharedPreferenceChangeListener(prefListener)
        mDatabaseHelper.close()
        epDbHelper.close()
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
