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

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.CsvImportActivity
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.activity.ExportActivity
import com.wirelessalien.android.moviedb.activity.FilterActivity
import com.wirelessalien.android.moviedb.activity.ImportActivity
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.data.CategoryDTO
import com.wirelessalien.android.moviedb.data.ChipInfo
import com.wirelessalien.android.moviedb.databinding.ActivityMainBinding
import com.wirelessalien.android.moviedb.databinding.DialogEpisodeDataExpBinding
import com.wirelessalien.android.moviedb.databinding.DialogProgressIndicatorBinding
import com.wirelessalien.android.moviedb.databinding.DialogSwitchBinding
import com.wirelessalien.android.moviedb.databinding.DialogTraktSyncMinimalBinding
import com.wirelessalien.android.moviedb.databinding.FragmentSavedBinding
import com.wirelessalien.android.moviedb.databinding.FragmentSavedSetupBinding
import com.wirelessalien.android.moviedb.databinding.WatchSummaryBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener
import com.wirelessalien.android.moviedb.tmdb.GetTmdbDetailsSaved
import com.wirelessalien.android.moviedb.trakt.TraktAutoSyncManager
import com.wirelessalien.android.moviedb.work.GetTmdbTvDetailsWorker
import com.wirelessalien.android.moviedb.work.TktAutoSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 *
 * Shows the shows that are stored in the local database.
 */
class ListFragment : BaseFragment(), AdapterDataChangedListener {
    private val REQUEST_CODE_ASK_PERMISSIONS_EXPORT = 123
    private val REQUEST_CODE_ASK_PERMISSIONS_IMPORT = 124
    private var usedFilter = false
    private lateinit var mDatabase: SQLiteDatabase
    private val client = OkHttpClient()
    private lateinit var mDatabaseHelper: MovieDatabaseHelper
    private lateinit var epDbHelper: EpisodeReminderDatabaseHelper
    private var mScrollPosition: Int? = null
    private var tmdbApiKey: String? = null
    private var accessToken: String? = null
    private var clientId: String? = null
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private lateinit var filterActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var binding: FragmentSavedBinding
    private lateinit var activityBinding: ActivityMainBinding
    private lateinit var chipInfos: MutableList<ChipInfo>
    private val gson = Gson()

    override fun onAdapterDataChangedListener() {
        updateShowViewAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        epDbHelper = EpisodeReminderDatabaseHelper(requireContext().applicationContext)
        mDatabaseHelper = MovieDatabaseHelper(requireContext().applicationContext)
        tmdbApiKey = ConfigHelper.getConfigValue(requireContext(), "api_key")
        accessToken = preferences.getString("trakt_access_token", null)
        clientId = ConfigHelper.getConfigValue(requireContext(), "client_id")
        filterActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                usedFilter = true
                filterAdapter()
                updateShowViewAdapter()
            }
        }

        mShowArrayList = ArrayList()
        mUpcomingArrayList = ArrayList()
        mShowGenreList = HashMap()
        mShowView = RecyclerView(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireActivity())

        lifecycleScope.launch {
            loadInitialData()
            if (preferences.getBoolean(PERSISTENT_FILTERING_PREFERENCE, false)) {
                filterAdapter()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSavedBinding.inflate(inflater, container, false)
        val fragmentView = binding.root
        activityBinding = (activity as MainActivity).getBinding()
        activityBinding.fab.setImageResource(R.drawable.ic_filter_list)
        activityBinding.fab.isEnabled = true
        activityBinding.fab.setOnClickListener {
            val intent = Intent(requireContext().applicationContext, FilterActivity::class.java)
            intent.putExtra("categories", true)
            intent.putExtra("most_popular", false)
            intent.putExtra("dates", false)
            intent.putExtra("keywords", false)
            intent.putExtra("startDate", true)
            intent.putExtra("finishDate", true)
            intent.putExtra("account", false)
            intent.putExtra("account", false)
            filterActivityResultLauncher.launch(intent)
        }

        activityBinding.fab2.setImageResource(R.drawable.ic_chart)
        activityBinding.fab2.visibility = View.VISIBLE
        activityBinding.fab2.isEnabled = true
        activityBinding.fab2.setOnClickListener {
            showWatchSummaryDialog()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
        showShowList(fragmentView)

        setupMediaTypeChips()
        binding.settingsButton.setOnClickListener {
            val dialog = ChipManagementDialogFragment(chipInfos) { updatedChips ->
                saveChipOrder(updatedChips)
                applyChipOrder()
            }
            dialog.show(parentFragmentManager, "ChipManagementDialog")
        }

        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_search, menu)
                menuInflater.inflate(R.menu.database_menu, menu)
                menuInflater.inflate(R.menu.tkt_auto_sync_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {

                    R.id.action_search -> {
                        activityBinding.searchView.show()
                        true
                    }

                    R.id.action_export -> {
                        // Handle export action
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            if (ActivityCompat.checkSelfPermission(
                                    requireContext(),
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                ActivityCompat.requestPermissions(
                                    requireActivity(),
                                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                    REQUEST_CODE_ASK_PERMISSIONS_EXPORT
                                )
                            } else {
                                val intent = Intent(requireContext().applicationContext, ExportActivity::class.java)
                                startActivity(intent)
                            }
                        } else {
                            val intent = Intent(requireContext().applicationContext, ExportActivity::class.java)
                            startActivity(intent)
                        }
                        true
                    }

                    R.id.action_import -> {
                        // Handle import action
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            if (ActivityCompat.checkSelfPermission(
                                    requireContext(),
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                ActivityCompat.requestPermissions(
                                    requireActivity(),
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                    REQUEST_CODE_ASK_PERMISSIONS_IMPORT
                                )
                            } else {
                                val intent = Intent(requireContext().applicationContext, ImportActivity::class.java)
                                startActivity(intent)
                            }
                        } else {
                            val intent = Intent(requireContext().applicationContext, ImportActivity::class.java)
                            startActivity(intent)
                        }
                        true
                    }

                    R.id.action_external_import -> {
                        val intent = Intent(requireContext().applicationContext, CsvImportActivity::class.java)
                        startActivity(intent)
                        true
                    }

                    R.id.action_auto_sync -> {
                        showTraktSyncDialog()
                        true
                    }

                    R.id.action_fetch_tmdb_data -> {
                        showTmdbDetailsExplanationDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val dialogShown = preferences.getBoolean("quick_access_dialog_shown", false)
        if (!dialogShown) {
            showQuickAccessBottomSheet()
        }
    }

    private fun setChipsEnabled(enabled: Boolean) {
        for (i in 0 until binding.chipGroup.childCount) {
            val chip = binding.chipGroup.getChildAt(i) as Chip
            chip.isEnabled = enabled
        }
        binding.settingsButton.isEnabled = enabled
    }

    private suspend fun loadInitialData() {
        withContext(Dispatchers.Main) {
            setChipsEnabled(false)
            binding.shimmerFrameLayout1.visibility = View.VISIBLE
            binding.shimmerFrameLayout1.startShimmer()
            mShowAdapter.updateData(ArrayList())
        }

        val shows = withContext(Dispatchers.IO) {
            getShowsFromDatabase(null, MovieDatabaseHelper.COLUMN_ID + " DESC", null)
        }
        val upcomingContent = withContext(Dispatchers.IO) {
            loadUpcomingContent()
        }

        withContext(Dispatchers.Main) {
            mShowArrayList = shows
            mUpcomingArrayList = upcomingContent
            mShowAdapter.updateData(mShowArrayList)

            if (!mSearchView) {
                mShowView.adapter = mShowAdapter
                if (usedFilter) {
                    filterAdapter()
                }
            }
            binding.shimmerFrameLayout1.stopShimmer()
            binding.shimmerFrameLayout1.visibility = View.GONE
            setChipsEnabled(true)
        }
    }

    private fun loadUpcomingContent(): ArrayList<JSONObject> {
        val upcomingContent = ArrayList<JSONObject>()
        val epDb = epDbHelper.readableDatabase
        val movieDb = mDatabaseHelper.readableDatabase
        val projection = arrayOf(
            EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID,
            EpisodeReminderDatabaseHelper.COLUMN_DATE,
            EpisodeReminderDatabaseHelper.COL_TYPE,
            EpisodeReminderDatabaseHelper.COL_SEASON,
            EpisodeReminderDatabaseHelper.COLUMN_NAME,
            EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER
        )
        epDb.query(
            EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS,
            projection,
            null,
            null,
            null,
            null,
            "${EpisodeReminderDatabaseHelper.COLUMN_DATE} ASC"
        ).use { epCursor ->
            while (epCursor.moveToNext()) {
                val movieId = epCursor.getInt(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID))
                val movieProjection = arrayOf(
                    MovieDatabaseHelper.COLUMN_MOVIES_ID,
                    MovieDatabaseHelper.COLUMN_PERSONAL_RATING,
                    MovieDatabaseHelper.COLUMN_RATING,
                    MovieDatabaseHelper.COLUMN_IMAGE,
                    MovieDatabaseHelper.COLUMN_ICON,
                    MovieDatabaseHelper.COLUMN_TITLE,
                    MovieDatabaseHelper.COLUMN_SUMMARY,
                    MovieDatabaseHelper.COLUMN_GENRES_IDS,
                    MovieDatabaseHelper.COLUMN_MOVIE
                )
                movieDb.query(
                    MovieDatabaseHelper.TABLE_MOVIES,
                    movieProjection,
                    "${MovieDatabaseHelper.COLUMN_MOVIES_ID} = ?",
                    arrayOf(movieId.toString()),
                    null,
                    null,
                    null
                ).use { movieCursor ->
                    if (movieCursor.moveToFirst()) {
                        val isMovie = movieCursor.getInt(movieCursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE)) == 1
                        val upcomingType = epCursor.getString(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COL_TYPE))

                        if (isMovie && upcomingType == EPISODE) {
                            // Skip this entry
                        } else {
                            createMovieDetails(movieCursor, epCursor)?.let {
                                upcomingContent.add(it)
                            }
                        }
                    }
                }
            }
        }
        return upcomingContent
    }

    private fun showTmdbDetailsExplanationDialog() {
        val binding = DialogEpisodeDataExpBinding.inflate(LayoutInflater.from(context))

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.get_tmdb_progress_data))
            .setView(binding.root)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                showTmdbDetailsDialog()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton(getString(R.string.schedule_update)) { dialog, _ ->
                showScheduleUpdateDialog()
                dialog.dismiss()
            }
            .show()
    }

    private fun showScheduleUpdateDialog() {
        val binding = DialogSwitchBinding.inflate(LayoutInflater.from(context))
        val workManager = WorkManager.getInstance(requireContext())
        val switchState = MutableLiveData(false)

        // Check if the worker is running
        workManager.getWorkInfosForUniqueWorkLiveData("monthlyTvShowUpdateWorker")
            .observe(viewLifecycleOwner) { workInfoList ->
                val isRunning = workInfoList?.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING } == true
                switchState.value = isRunning
            }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.schedule_monthly_update))
            .setView(binding.root)
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                if (switchState.value == true) {
                    scheduleMonthlyTvShowUpdate()
                } else {
                    workManager.cancelUniqueWork("monthlyTvShowUpdateWorker")
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        binding.switchEnableUpdate.apply {
            switchState.observe(viewLifecycleOwner) { isChecked = it }
            setOnCheckedChangeListener { _, isChecked ->
                switchState.value = isChecked
            }
        }

        dialog.show()
    }

    private fun scheduleMonthlyTvShowUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val monthlyWorkRequest =
            PeriodicWorkRequestBuilder<GetTmdbTvDetailsWorker>(30, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "monthlyTvShowUpdateWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            monthlyWorkRequest
        )
    }

    private fun showTmdbDetailsDialog() {
        val binding = DialogProgressIndicatorBinding.inflate(LayoutInflater.from(context))
        var job: Job? = null

        val tmdbDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.fetching_episode_data))
            .setView(binding.root)
            .setCancelable(false)
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                job?.cancel()
                dialog.dismiss()
            }
            .show()

        job = lifecycleScope.launch {
            val getTmdbDetails = GetTmdbDetailsSaved(requireContext(), tmdbApiKey ?: "")
            getTmdbDetails.fetchAndSaveTmdbDetails { showTitle, progress ->
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.progressText.text = getString(R.string.fetching_show_data, showTitle)
                    binding.progressIndicator.progress = progress
                }
            }
            tmdbDialog.dismiss()
            updateShowViewAdapter()
        }
    }

    private fun showQuickAccessBottomSheet() {
        preferences.edit { putBoolean("quick_access_dialog_shown", true) }
        val binding = FragmentSavedSetupBinding.inflate(LayoutInflater.from(requireContext()))
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(binding.root)
        dialog.show()
    }

    private fun refreshData() {
        CoroutineScope(Dispatchers.Main).launch {
            // Refresh data based on the selected chip
            when (binding.chipGroup.checkedChipId) {
                R.id.chipAll -> {
                    updateShowViewAdapter()
                }
                R.id.chipUpcoming -> {
                    setChipsEnabled(false)
                    mShowArrayList.clear()
                    mShowAdapter.notifyDataSetChanged()
                    binding.shimmerFrameLayout1.apply {
                        startShimmer()
                        visibility = View.VISIBLE
                    }

                    lifecycleScope.launch(Dispatchers.IO) {
                        fetchCalendarData {
                            val upcomingContent = loadUpcomingContent()

                            lifecycleScope.launch(Dispatchers.Main) {
                                mUpcomingArrayList = upcomingContent
                                mShowAdapter.updateData(mUpcomingArrayList)
                                binding.shimmerFrameLayout1.apply {
                                    stopShimmer()
                                    visibility = View.GONE
                                }
                                setChipsEnabled(true)
                            }
                        }
                    }
                }

                R.id.chipWatching -> {
                    setChipsEnabled(false)
                    // Refresh Watching shows
                    var watchingShows = withContext(Dispatchers.IO) {
                        getShowsFromDatabase(null, MovieDatabaseHelper.COLUMN_ID + " DESC", MovieDatabaseHelper.CATEGORY_WATCHING)
                    }
                    watchingShows = sortShowsByDate(watchingShows, "key_sort_watching_by_date")
                    mShowAdapter.updateData(watchingShows)
                    setChipsEnabled(true)
                }
                R.id.chipWatched -> {
                    setChipsEnabled(false)
                    var watchedShows = withContext(Dispatchers.IO) {
                        getShowsFromDatabase(null, MovieDatabaseHelper.COLUMN_ID + " DESC", MovieDatabaseHelper.CATEGORY_WATCHED)
                    }
                    watchedShows = sortShowsByDate(watchedShows, "key_sort_watched_by_date")
                    mShowAdapter.updateData(watchedShows)
                    setChipsEnabled(true)
                }
                R.id.chipPlanToWatch -> {
                    setChipsEnabled(false)
                    var planToWatchShows = withContext(Dispatchers.IO) {
                        getShowsFromDatabase(null, MovieDatabaseHelper.COLUMN_ID + " DESC", MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH)
                    }
                    planToWatchShows = sortShowsByDate(planToWatchShows, "key_sort_plan_to_watch_by_date")
                    mShowAdapter.updateData(planToWatchShows)
                    setChipsEnabled(true)
                }
            }

            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun sortShowsByDate(shows: ArrayList<JSONObject>, preferenceKey: String): ArrayList<JSONObject> {
        val sortEnabled = preferences.getBoolean(preferenceKey, true)
        if (sortEnabled) {
            shows.sortWith { o1, o2 ->
                val date1 = getSortDate(o1)
                val date2 = getSortDate(o2)
                when {
                    date1 == null && date2 == null -> 0
                    date1 == null -> 1
                    date2 == null -> -1
                    else -> date2.compareTo(date1)
                }
            }
        }
        return shows
    }

    override fun onResume() {
        super.onResume()

        requireActivity().invalidateOptionsMenu()

        if (mDatabaseUpdate) {
            // The database is updated, load the changes into the array list.
            updateShowViewAdapter()
            mDatabaseUpdate = false
        }

        if (preferences.getBoolean(PERSISTENT_FILTERING_PREFERENCE, false)) {
            filterAdapter()
        }

        if (preferences.getBoolean("key_show_continue_watching", true)) {
            activityBinding.upnextChip.visibility = View.VISIBLE
        } else {
            activityBinding.upnextChip.visibility = View.GONE
        }

        activityBinding.toggleButtonGroup.root.visibility = View.GONE

        when (binding.chipGroup.checkedChipId) {
            R.id.chipUpcoming -> {
                // Hide the FAB if the "Upcoming" chip is checked
                activityBinding.fab.visibility = View.GONE
                activityBinding.fab2.visibility = View.GONE
            }
            R.id.chipWatching -> {
                if (preferences.getBoolean("key_show_continue_watching", true)) {
                    activityBinding.fab.visibility = View.GONE
                } else {
                    activityBinding.fab.visibility = View.VISIBLE
                    activityBinding.fab.setImageResource(R.drawable.ic_next_plan)
                }
                activityBinding.fab.setOnClickListener {
                    val upNextFragment = UpNextFragment()
                    upNextFragment.show(parentFragmentManager, "UpNextFragment")
                }
            }
            else -> {
                // Otherwise, show and configure the FAB (your original logic)
                activityBinding.fab.setImageResource(R.drawable.ic_filter_list)
                activityBinding.fab.visibility = View.VISIBLE
                activityBinding.fab.isEnabled = true
                activityBinding.fab.setOnClickListener {
                    val intent = Intent(requireContext().applicationContext, FilterActivity::class.java)
                    intent.putExtra("categories", true)
                    intent.putExtra("most_popular", false)
                    intent.putExtra("dates", false)
                    intent.putExtra("keywords", false)
                    intent.putExtra("startDate", true)
                    intent.putExtra("finishDate", true)
                    intent.putExtra("account", false)
                    filterActivityResultLauncher.launch(intent)
                }
            }
        }
    }

    override fun onPause() {
        mScrollPosition = mShowLinearLayoutManager.findFirstVisibleItemPosition()
        super.onPause()
    }

    private suspend fun getTotalItem(category: Int): Int = withContext(Dispatchers.IO) {
        open()
        mDatabaseHelper.onCreate(mDatabase)
        val cursor = mDatabase.rawQuery(
            "SELECT * FROM ${MovieDatabaseHelper.TABLE_MOVIES} WHERE ${MovieDatabaseHelper.COLUMN_CATEGORIES} = $category",
            null
        )
        val totalItem = cursor.count
        cursor.close()
        totalItem
    }

    private suspend fun getTotalMoviesInWatchedCategory(): Int = withContext(Dispatchers.IO) {
        open()
        mDatabaseHelper.onCreate(mDatabase)
        val cursor = mDatabase.rawQuery(
            "SELECT * FROM ${MovieDatabaseHelper.TABLE_MOVIES} WHERE ${MovieDatabaseHelper.COLUMN_CATEGORIES} = ${MovieDatabaseHelper.CATEGORY_WATCHED} AND ${MovieDatabaseHelper.COLUMN_MOVIE} = 1",
            null
        )
        val totalMovies = cursor.count
        cursor.close()
        totalMovies
    }

    private suspend fun getTotalTVShowsInWatchedCategory(): Int = withContext(Dispatchers.IO) {
        open()
        mDatabaseHelper.onCreate(mDatabase)
        val cursor = mDatabase.rawQuery(
            "SELECT * FROM ${MovieDatabaseHelper.TABLE_MOVIES} WHERE ${MovieDatabaseHelper.COLUMN_CATEGORIES} = ${MovieDatabaseHelper.CATEGORY_WATCHED} AND ${MovieDatabaseHelper.COLUMN_MOVIE} = 0",
            null
        )
        val totalTVShows = cursor.count
        cursor.close()
        totalTVShows
    }

    private suspend fun getTotalItemCountForGenre(genreId: Int): Int = withContext(Dispatchers.IO) {
        open()
        mDatabaseHelper.onCreate(mDatabase)
        val cursor: Cursor = mDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${MovieDatabaseHelper.TABLE_MOVIES} WHERE ${MovieDatabaseHelper.COLUMN_GENRES_IDS} LIKE '%$genreId%'",
            null
        )
        val totalItemCount = if (cursor.moveToFirst()) {
            cursor.getInt(0)
        } else {
            0
        }
        cursor.close()
        totalItemCount
    }

    private suspend fun getGenreIdsFromDatabase(): List<Int> = withContext(Dispatchers.IO) {
        val genreIds = mutableListOf<Int>()
        open()
        mDatabaseHelper.onCreate(mDatabase)
        val cursor: Cursor = mDatabase.rawQuery(
            "SELECT ${MovieDatabaseHelper.COLUMN_GENRES_IDS} FROM ${MovieDatabaseHelper.TABLE_MOVIES}",
            null
        )
        if (cursor.moveToFirst()) {
            do {
                val genreIdString = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_GENRES_IDS))
                val ids = genreIdString
                    .removeSurrounding("[", "]")
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                genreIds.addAll(ids)
            } while (cursor.moveToNext())
        }
        cursor.close()
        genreIds.distinct()
    }

    private fun getGenreNamesFromSharedPreferences(context: Context): Map<Int, String> {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("GenreList", Context.MODE_PRIVATE)
        val genreNames = mutableMapOf<Int, String>()
        val allEntries = sharedPreferences.all

        // First, add individual genre entries
        for ((key, value) in allEntries) {
            if (key != "tvGenreJSONArrayList" && key != "movieGenreJSONArrayList") {
                try {
                    genreNames[key.toInt()] = value as String
                } catch (e: NumberFormatException) {
                    // Log or handle the exception if needed
                    e.printStackTrace()
                }
            }
        }

        // Then, add genres from tvGenreJSONArrayList
        val tvGenreJSONArrayList = sharedPreferences.getString("tvGenreJSONArrayList", null)
        if (tvGenreJSONArrayList != null) {
            val jsonArray = JSONArray(tvGenreJSONArrayList)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                genreNames[jsonObject.getInt("id")] = jsonObject.getString("name")
            }
        }

        return genreNames
    }

    private fun displayGenresInChipGroup(context: Context, chipGroup: ChipGroup, genreIds: List<Int>, genreNames: Map<Int, String>) {
        val inflater = LayoutInflater.from(context)
        CoroutineScope(Dispatchers.Main).launch {
            for (genreId in genreIds) {
                val genreName = genreNames[genreId]
                if (genreName != null) {
                    val chip = inflater.inflate(R.layout.chip_item, chipGroup, false) as Chip
                    val totalItemCount = getTotalItemCountForGenre(genreId)
                    chip.text = String.format("%s: %d", genreName, totalItemCount)
                    chipGroup.addView(chip)
                }
            }
        }
    }

    private suspend fun setupGenreChips(context: Context, chipGroup: ChipGroup) {
        val genreIds = getGenreIdsFromDatabase()
        val genreNames = getGenreNamesFromSharedPreferences(context)
        displayGenresInChipGroup(context, chipGroup, genreIds, genreNames)
    }

    private fun showTraktSyncDialog() {
        val binding = DialogTraktSyncMinimalBinding.inflate(LayoutInflater.from(requireContext()))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()

        // Check if worker is running
        val workManager = WorkManager.getInstance(requireContext())
        workManager.getWorkInfosForUniqueWorkLiveData(TktAutoSyncWorker.WORK_NAME)
            .observe(viewLifecycleOwner) { workInfoList ->
                val workInfo = workInfoList?.firstOrNull()
                val isWorkRunning = workInfo?.state == WorkInfo.State.RUNNING

                binding.btnCancelWorker.visibility = if (isWorkRunning) View.VISIBLE else View.GONE
            }


        if (accessToken == null) {
            binding.btnOk.isEnabled = false
            binding.btnLogin.visibility = View.VISIBLE
        } else {
            binding.btnOk.isEnabled = true
            binding.btnLogin.visibility = View.GONE
        }

        binding.btnLogin.setOnClickListener {
            LoginFragmentTkt().show(childFragmentManager, "LoginFragmentTkt")
            dialog.dismiss()
        }

        binding.btnCancelWorker.setOnClickListener {
            workManager.cancelUniqueWork(TktAutoSyncWorker.WORK_NAME)
            binding.btnCancelWorker.visibility = View.GONE
        }

        binding.btnOk.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                binding.progressBar.visibility = View.VISIBLE
                binding.btnOk.isEnabled = false
                binding.btnCancelWorker.isEnabled = false

                val syncManager = TraktAutoSyncManager(requireContext())
                syncManager.syncMediaToTrakt()

                binding.progressBar.visibility = View.GONE
                binding.btnOk.isEnabled = true
                binding.btnCancelWorker.isEnabled = true
                setupTraktAutoSync()
                dialog.dismiss()
            }
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupTraktAutoSync() {
        TktAutoSyncWorker.setupPeriodicSync(requireContext())
    }

    private fun showWatchSummaryDialog() {
        if (!isAdded) {
            Log.w("ListFragment", "Fragment not attached to activity")
            return
        }

        val binding = WatchSummaryBinding.inflate(LayoutInflater.from(requireContext()))
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(binding.root)
        bottomSheetDialog.edgeToEdgeEnabled
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheetDialog.show()

        CoroutineScope(Dispatchers.Main).launch {
            binding.shimmerFrameLayout.visibility = View.VISIBLE
            binding.shimmerFrameLayout.startShimmer()

            val watching = getTotalItem(MovieDatabaseHelper.CATEGORY_WATCHING)
            val watched = getTotalItem(MovieDatabaseHelper.CATEGORY_WATCHED)
            val planToWatch = getTotalItem(MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH)
            val onHold = getTotalItem(MovieDatabaseHelper.CATEGORY_ON_HOLD)
            val dropped = getTotalItem(MovieDatabaseHelper.CATEGORY_DROPPED)
            val watchedMovies = getTotalMoviesInWatchedCategory()
            val watchedTVShows = getTotalTVShowsInWatchedCategory()

            binding.chipWatching.text = getString(R.string.watching1, watching)
            binding.chipWatched.text = getString(R.string.watched1, watched)
            binding.chipPlanToWatch.text = getString(R.string.plan_to_watch1, planToWatch)
            binding.chipOnHold.text = getString(R.string.on_hold1, onHold)
            binding.chipDropped.text = getString(R.string.dropped1, dropped)
            binding.chipWatchedMovies.text = getString(R.string.watched1, watchedMovies)
            binding.chipWatchedTVShows.text = getString(R.string.watched1, watchedTVShows)

            // Setup genre chips
            setupGenreChips(requireContext(), binding.chipGroupGenres)

            binding.shimmerFrameLayout.visibility = View.GONE
            binding.shimmerFrameLayout.stopShimmer()
        }
    }
    /**
     * Create and set the new adapter to update the show view.
     */
    private fun updateShowViewAdapter() {
        lifecycleScope.launch(Dispatchers.Main) {
            setChipsEnabled(false)
            binding.shimmerFrameLayout1.visibility = View.VISIBLE
            binding.shimmerFrameLayout1.startShimmer()

            try {
                // Perform the database operation in the IO dispatcher
                val showsDeferred = async(Dispatchers.IO) {
                    getShowsFromDatabase(null, MovieDatabaseHelper.COLUMN_ID + " DESC", null)
                }
                val showArrayList = showsDeferred.await()

                mShowArrayList = showArrayList

                mShowAdapter.updateData(mShowArrayList)

                if (!mSearchView) {
                    mShowView.adapter = mShowAdapter
                    if (usedFilter || preferences.getBoolean(PERSISTENT_FILTERING_PREFERENCE, false)) {
                        filterAdapter()
                    }
                    mScrollPosition?.let { mShowView.scrollToPosition(it) }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                binding.shimmerFrameLayout1.visibility = View.GONE
                binding.shimmerFrameLayout1.stopShimmer()
                setChipsEnabled(true)
            }
        }
    }

    private fun getSortDate(show: JSONObject): Date? {
        val isMovie = show.optInt(IS_MOVIE, 0) == 1
        return try {
            if (isMovie) {
                val dateStr = show.optString(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)
                if (dateStr.isNotEmpty()) simpleDateFormat.parse(dateStr) else null
            } else {
                var maxDate: Date? = null
                val seasons = show.optJSONObject(SEASONS)
                if (seasons != null) {
                    for (seasonKey in seasons.keys()) {
                        val episodes = seasons.optJSONArray(seasonKey)
                        if (episodes != null) {
                            for (i in 0 until episodes.length()) {
                                val episode = episodes.getJSONObject(i)
                                val dateStr = episode.optString(EPISODE_WATCH_DATE)
                                if (dateStr.isNotEmpty()) {
                                    val date = simpleDateFormat.parse(dateStr)
                                    if (maxDate == null || date?.after(maxDate) == true) {
                                        maxDate = date
                                    }
                                }
                            }
                        }
                    }
                }
                maxDate
            }
        } catch (e: ParseException) {
            null
        }
    }

    private fun setupMediaTypeChips() {
        loadChipOrder()
        applyChipOrder()
        setChipListeners()
    }

    private fun setChipListeners() {
        binding.chipGroup.findViewById<Chip>(R.id.chipAll)?.setOnCheckedChangeListener { _, isChecked ->
            handleChipChange(isChecked, getOtherChips(R.id.chipAll)) {
                updateFab(true)
                mShowAdapter.updateData(mShowArrayList)

                if (!mSearchView) {
                    mShowView.adapter = mShowAdapter
                    if (usedFilter || preferences.getBoolean(PERSISTENT_FILTERING_PREFERENCE, false)) {
                        filterAdapter()
                    }
                }
            }
        }

        binding.chipGroup.findViewById<Chip>(R.id.chipUpcoming)?.setOnCheckedChangeListener { _, isChecked ->
            handleChipChange(isChecked, getOtherChips(R.id.chipUpcoming)) {
                activityBinding.fab.visibility = View.GONE
                activityBinding.fab2.visibility = View.GONE
                mShowAdapter.updateData(mUpcomingArrayList)
            }
        }

        binding.chipGroup.findViewById<Chip>(R.id.chipWatching)?.setOnCheckedChangeListener { _, isChecked ->
            handleChipChange(isChecked, getOtherChips(R.id.chipWatching)) {
                if (preferences.getBoolean("key_show_continue_watching", true)) {
                    activityBinding.fab.visibility = View.GONE
                } else {
                    activityBinding.fab.visibility = View.VISIBLE
                    activityBinding.fab.setImageResource(R.drawable.ic_next_plan)
                }
                activityBinding.fab2.visibility = View.GONE
                activityBinding.fab.setOnClickListener {
                    val upNextFragment = UpNextFragment()
                    upNextFragment.show(parentFragmentManager, "UpNextFragment")
                }

                var watchingShows = ArrayList<JSONObject>()
                for (show in mShowArrayList) {
                    if (show.optInt(MovieDatabaseHelper.COLUMN_CATEGORIES) == MovieDatabaseHelper.CATEGORY_WATCHING) {
                        watchingShows.add(show)
                    }
                }
                watchingShows = sortShowsByDate(watchingShows, "key_sort_watching_by_date")
                mShowAdapter.updateData(watchingShows)
            }
        }

        binding.chipGroup.findViewById<Chip>(R.id.chipWatched)?.setOnCheckedChangeListener { _, isChecked ->
            handleChipChange(isChecked, getOtherChips(R.id.chipWatched)) {
                updateFab(false)
                var watchedShows = ArrayList<JSONObject>()
                for (show in mShowArrayList) {
                    if (show.optInt(MovieDatabaseHelper.COLUMN_CATEGORIES) == MovieDatabaseHelper.CATEGORY_WATCHED) {
                        watchedShows.add(show)
                    }
                }
                watchedShows = sortShowsByDate(watchedShows, "key_sort_watched_by_date")
                mShowAdapter.updateData(watchedShows)
            }
        }

        binding.chipGroup.findViewById<Chip>(R.id.chipPlanToWatch)?.setOnCheckedChangeListener { _, isChecked ->
            handleChipChange(isChecked, getOtherChips(R.id.chipPlanToWatch)) {
                updateFab(false)
                var planToWatchShows = ArrayList<JSONObject>()
                for (show in mShowArrayList) {
                    if (show.optInt(MovieDatabaseHelper.COLUMN_CATEGORIES) == MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH) {
                        planToWatchShows.add(show)
                    }
                }
                planToWatchShows = sortShowsByDate(planToWatchShows, "key_sort_plan_to_watch_by_date")
                mShowAdapter.updateData(planToWatchShows)
            }
        }
    }

    private fun updateFab(show: Boolean) {
        if (show) {
            activityBinding.fab.visibility = View.VISIBLE
            activityBinding.fab2.visibility = View.VISIBLE
            activityBinding.fab.setImageResource(R.drawable.ic_filter_list)
            activityBinding.fab.setOnClickListener {
                val intent = Intent(requireContext().applicationContext, FilterActivity::class.java)
                intent.putExtra("categories", true)
                intent.putExtra("most_popular", false)
                intent.putExtra("dates", false)
                intent.putExtra("keywords", false)
                intent.putExtra("startDate", true)
                intent.putExtra("finishDate", true)
                intent.putExtra("account", false)
                filterActivityResultLauncher.launch(intent)
            }
        } else {
            activityBinding.fab.visibility = View.GONE
            activityBinding.fab2.visibility = View.GONE
        }
    }

    private fun handleChipChange(isChecked: Boolean, otherChips: List<Chip>, action: () -> Unit) {
        if (isChecked) {
            otherChips.forEach { it.isChecked = false }
            action()
        } else if (otherChips.none { it.isChecked }) {
            otherChips.firstOrNull()?.isChecked = true
        }
    }

    private fun createMovieDetails(movieCursor: Cursor, epCursor: Cursor): JSONObject? {
        return try {
            JSONObject().apply {
                val movieId = movieCursor.getInt(movieCursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_MOVIES_ID))

                put(ShowBaseAdapter.KEY_ID, movieId)
                put(ShowBaseAdapter.KEY_RATING, getMovieRating(movieCursor))
                put(ShowBaseAdapter.KEY_IMAGE, movieCursor.getString(movieCursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_IMAGE)))
                put(ShowBaseAdapter.KEY_POSTER, movieCursor.getString(movieCursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_ICON)))
                put(ShowBaseAdapter.KEY_TITLE, movieCursor.getString(movieCursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_TITLE)))
                put(ShowBaseAdapter.KEY_DESCRIPTION, movieCursor.getString(movieCursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_SUMMARY)))
                put(ShowBaseAdapter.KEY_GENRES, movieCursor.getString(movieCursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_GENRES_IDS)))

                val upcomingType = epCursor.getString(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COL_TYPE))
                if (upcomingType == EPISODE) {
                    put(IS_MOVIE, 0)
                } else {
                    put(IS_MOVIE, 1)
                }

                // Add upcoming specific data
                put(IS_UPCOMING, true)
                put(UPCOMING_DATE, epCursor.getString(epCursor.getColumnIndexOrThrow(
                    EpisodeReminderDatabaseHelper.COLUMN_DATE)))
                put(UPCOMING_TYPE, epCursor.getString(epCursor.getColumnIndexOrThrow(
                    EpisodeReminderDatabaseHelper.COL_TYPE)))

                if (epCursor.getString(epCursor.getColumnIndexOrThrow(
                        EpisodeReminderDatabaseHelper.COL_TYPE)) == EPISODE) {
                    addEpisodeDetails(this, epCursor)
                }
            }
        } catch (e: Exception) {
            Log.e("ListFragment", "Error creating movie details", e)
            null
        }
    }

    private fun getMovieRating(cursor: Cursor): Double {
        val personalRatingColIdx = cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING)
        return if (!cursor.isNull(personalRatingColIdx)) {
            cursor.getDouble(personalRatingColIdx)
        } else {
            cursor.getDouble(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RATING))
        }
    }

    private fun addEpisodeDetails(jsonObject: JSONObject, cursor: Cursor) {
        jsonObject.apply {
            put(SEASONS, cursor.getString(cursor.getColumnIndexOrThrow(
                EpisodeReminderDatabaseHelper.COL_SEASON)))
            put(UPCOMING_EPISODE_NAME, cursor.getString(cursor.getColumnIndexOrThrow(
                EpisodeReminderDatabaseHelper.COLUMN_NAME)))
            put(UPCOMING_EPISODE_NUMBER, cursor.getString(cursor.getColumnIndexOrThrow(
                EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER)))
        }
    }

    /**
     * Filters the shows based on the settings in the FilterActivity.
     */
    private fun filterAdapter() {
        // Get the parameters from the filter activity and reload the adapter
        val sharedPreferences =
            requireActivity().getSharedPreferences(FilterActivity.FILTER_PREFERENCES, Context.MODE_PRIVATE)

        val listToFilter = if (mSearchView) mSearchShowArrayList else mShowArrayList

        // Filter by show type (movie or TV)
        val showMovie = FilterActivity.convertStringToArrayList(
            sharedPreferences.getString(FilterActivity.FILTER_SHOW_MOVIE, null), ", "
        )
        if (showMovie != null && (!showMovie.contains("movie") || !showMovie.contains("tv"))) {
            listToFilter.removeIf { showObject: JSONObject ->
                val isTV = showObject.optString(ShowBaseAdapter.KEY_NAME) == "0"
                (showMovie.contains("movie") && isTV) || (showMovie.contains("tv") && !isTV)
            }
        }

        // --- Sorting Logic ---
        val sortPreference = sharedPreferences.getString(FilterActivity.FILTER_SORT, null)
        val nestedSortEnabled = sharedPreferences.getBoolean(FilterActivity.FILTER_NESTED_SORT, false)

        // Create category comparator
        val categoryOrderJson = sharedPreferences.getString(FilterActivity.CATEGORY_ORDER, null)
        val categoryComparator: java.util.Comparator<JSONObject>? = if (categoryOrderJson != null) {
            try {
                val type = object : TypeToken<List<CategoryDTO>>() {}.type
                val categoryOrder: List<CategoryDTO> = Gson().fromJson(categoryOrderJson, type)
                val categoryMap = categoryOrder.mapIndexed { index, categoryDTO -> categoryDTO.tag to index }.toMap()
                compareBy {
                    val category = it.optInt(MovieDatabaseHelper.COLUMN_CATEGORIES)
                    val categoryTag = DetailActivity.getCategoryName(category)
                    categoryMap[categoryTag] ?: Int.MAX_VALUE
                }
            } catch (e: Exception) {
                null // Handle potential Gson parsing errors
            }
        } else {
            null
        }

        // Create primary sort comparator based on preference
        val primaryComparator: java.util.Comparator<JSONObject>? = sortPreference?.let {
            when (it) {
                "best_rated" -> compareByDescending<JSONObject> { obj ->
                    obj.optBoolean(ShowBaseAdapter.KEY_HAS_PERSONAL_RATING)
                }.thenByDescending { obj ->
                    if (obj.optBoolean(ShowBaseAdapter.KEY_HAS_PERSONAL_RATING)) {
                        obj.optDouble(ShowBaseAdapter.KEY_RATING, -1.0)
                    } else {
                        Double.NEGATIVE_INFINITY
                    }
                }.thenBy { obj ->
                    obj.optString(ShowBaseAdapter.KEY_TITLE)
                }

                "release_date" -> compareByDescending { obj ->
                    getDateFromString(obj.optString(ShowBaseAdapter.KEY_RELEASE_DATE))
                }

                "alphabetic_order" -> compareBy(String.CASE_INSENSITIVE_ORDER) { obj ->
                    obj.optString(ShowBaseAdapter.KEY_TITLE)
                }

                "start_date_order", "finish_date_order" -> java.util.Comparator { o1, o2 ->
                    val dateField = if (it == "start_date_order") {
                        MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE
                    } else {
                        MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE
                    }
                    val simpleDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                    fun parseDate(obj: JSONObject): Date {
                        val dateString = obj.optString(dateField)
                        return if (dateString.isNotEmpty()) {
                            try {
                                simpleDateFormat.parse(dateString) ?: Date(Long.MIN_VALUE)
                            } catch (e: ParseException) {
                                Date(Long.MIN_VALUE)
                            }
                        } else {
                            Date(Long.MIN_VALUE)
                        }
                    }

                    val date1 = parseDate(o1)
                    val date2 = parseDate(o2)
                    date2.compareTo(date1) // Descending order
                }

                "watch_category" -> categoryComparator
                else -> null
            }
        }

        // Combine comparators if nested sorting is enabled
        var finalComparator: java.util.Comparator<JSONObject>? = primaryComparator
        if (nestedSortEnabled && categoryComparator != null && sortPreference != "watch_category") {
            finalComparator = if (primaryComparator != null) {
                categoryComparator.then(primaryComparator)
            } else {
                categoryComparator
            }
        }

        // Apply the final sort
        if (finalComparator != null) {
            listToFilter.sortWith(finalComparator)
        }

        // Filter by selected categories
        val selectedCategories = FilterActivity.convertStringToArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_CATEGORIES,
                null
            ), ", "
        )
        if (selectedCategories != null) {
            listToFilter.removeIf {
                val columnWatched = it.optInt(MovieDatabaseHelper.COLUMN_CATEGORIES)
                selectedCategories.none { category ->
                    columnWatched == DetailActivity.getCategoryNumber(category)
                }
            }
        }


        // Filter by genres to include
        val withGenres = FilterActivity.convertStringToIntegerArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_WITH_GENRES,
                null
            ), ", "
        )
        if (withGenres != null && withGenres.isNotEmpty()) {
            listToFilter.removeIf {
                val idList = FilterActivity.convertStringToIntegerArrayList(
                    it.optString(ShowBaseAdapter.KEY_GENRES), ","
                )
                !idList.containsAll(withGenres)
            }
        }

        // Filter by genres to exclude
        val withoutGenres = FilterActivity.convertStringToIntegerArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_WITHOUT_GENRES,
                null
            ), ", "
        )
        if (withoutGenres != null && withoutGenres.isNotEmpty()) {
            listToFilter.removeIf {
                val idList = FilterActivity.convertStringToIntegerArrayList(
                    it.optString(ShowBaseAdapter.KEY_GENRES), ","
                )
                idList.any { id -> withoutGenres.contains(id) }
            }
        }

        // Set a new adapter with the filtered ArrayList.
        if (mSearchView) {
            mShowAdapter.updateData(mSearchShowArrayList)
        } else {
            mShowAdapter.updateData(mShowArrayList)
        }
        mShowView.adapter = mShowAdapter
    }

    private fun getDateFromString(dateString: String): Date? {
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd",
            "dd-MM-yyyy"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.isLenient = false // Important:  Make parsing strict
                return sdf.parse(dateString)
            } catch (e: ParseException) {
                // Try the next format
            }
        }
        return null
    }

    /**
     * Retrieves the shows from the database.
     *
     * @param searchQuery the text (if any) that the title should contain.
     * @param order       the order of the shows.
     * @param categoryFilter an optional category ID to filter by.
     * @return an ArrayList filled with the shows from the database
     * (optionally filtered and sorted on the given query, order, and category).
     */
    fun getShowsFromDatabase(searchQuery: String?, order: String?, categoryFilter: Int? = null): ArrayList<JSONObject> {
        // Add the order that the output should be sorted on.
        // When no order is specified leave the string empty.
        val dbOrder: String = if (order != null) {
            " ORDER BY $order"
        } else {
            ""
        }
        open()

        // Base query with LEFT JOIN to include episode data
        var baseQuery = """
        SELECT m.*, e.${MovieDatabaseHelper.COLUMN_SEASON_NUMBER}, 
               e.${MovieDatabaseHelper.COLUMN_EPISODE_NUMBER},
               e.${MovieDatabaseHelper.COLUMN_EPISODE_RATING},
               e.${MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE},
               e.${MovieDatabaseHelper.COLUMN_EPISODE_REVIEW}
        FROM ${MovieDatabaseHelper.TABLE_MOVIES} m
        LEFT JOIN ${MovieDatabaseHelper.TABLE_EPISODES} e 
        ON m.${MovieDatabaseHelper.COLUMN_MOVIES_ID} = e.${MovieDatabaseHelper.COLUMN_MOVIES_ID}
    """

        val selectionArgs = mutableListOf<String>()
        val conditions = mutableListOf<String>()

        if (!searchQuery.isNullOrEmpty()) {
            conditions.add("m.${MovieDatabaseHelper.COLUMN_TITLE} LIKE ?")
            selectionArgs.add("%$searchQuery%")
        }

        if (categoryFilter != null) {
            conditions.add("m.${MovieDatabaseHelper.COLUMN_CATEGORIES} = ?")
            selectionArgs.add(categoryFilter.toString())
        }

        if (conditions.isNotEmpty()) {
            baseQuery += " WHERE " + conditions.joinToString(separator = " AND ")
        }

        // Search for shows that fulfill the searchQuery and fit in the list
        val cursor: Cursor = mDatabase.rawQuery("$baseQuery $dbOrder", selectionArgs.toTypedArray())

        return convertDatabaseListToArrayList(cursor)
    }

    /**
     * Goes through all the shows with the given cursor and adds them to the ArrayList.
     *
     * @param cursor the cursor containing the shows.
     * @return an ArrayList with all the shows from the cursor.
     */
    private fun convertDatabaseListToArrayList(cursor: Cursor): ArrayList<JSONObject> {
        val dbShowsArrayList = ArrayList<JSONObject>()
        val showEpisodesMap = mutableMapOf<Int, MutableMap<Int, JSONArray>>()

        if (cursor.isClosed) {
            Log.e("ListFragment", "Cursor is closed, cannot convert database list to array list.")
            return dbShowsArrayList
        }

        if (!cursor.moveToFirst()) {
            Log.d("ListFragment", "Cursor is empty, no data to convert.")
            cursor.close()
            return dbShowsArrayList
        }

        // Proceed with processing the cursor only if it's not empty
        do {
            val movieId = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIES_ID))

            // Create show object if it doesn't exist for this movieId
            if (!showEpisodesMap.containsKey(movieId)) {
                val showObject = JSONObject().apply {
                    try {
                        put(ShowBaseAdapter.KEY_ID, movieId)
                        val personalRatingColumnIndex = cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING)
                        val generalRatingColumnIndex = cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RATING)

                        if (!cursor.isNull(personalRatingColumnIndex)) {
                            put(ShowBaseAdapter.KEY_RATING,
                                cursor.getDouble(personalRatingColumnIndex))
                            put(ShowBaseAdapter.KEY_HAS_PERSONAL_RATING, true)
                        } else {
                            // Even if no personal rating, store the general rating for other sort types or display
                            put(ShowBaseAdapter.KEY_RATING,
                                cursor.getDouble(generalRatingColumnIndex))
                            put(ShowBaseAdapter.KEY_HAS_PERSONAL_RATING, false)
                        }
                        put(ShowBaseAdapter.KEY_IMAGE,
                            cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_IMAGE)))
                        put(ShowBaseAdapter.KEY_POSTER,
                            cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_ICON)))
                        put(ShowBaseAdapter.KEY_TITLE,
                            cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE)))
                        put(ShowBaseAdapter.KEY_DESCRIPTION,
                            cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_SUMMARY)))
                        put(ShowBaseAdapter.KEY_GENRES,
                            cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_GENRES_IDS)))
                        put(MovieDatabaseHelper.COLUMN_CATEGORIES,
                            cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_CATEGORIES)))
                        put(IS_MOVIE,
                            cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE)))
                        put(ShowBaseAdapter.KEY_DATE_MOVIE,
                            cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RELEASE_DATE)))
                        put(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE,
                            cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)))
                        put(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE,
                            cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)))

                        if (cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE)) != 1) {
                            put(ShowBaseAdapter.KEY_NAME,
                                cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE)))
                            put(ShowBaseAdapter.KEY_DATE_SERIES,
                                cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RELEASE_DATE)))
                        }

                        // Initialize seasons object
                        put(SEASONS, JSONObject())
                    } catch (je: JSONException) {
                        je.printStackTrace()
                    }
                }
                dbShowsArrayList.add(showObject)
                showEpisodesMap[movieId] = mutableMapOf()
            }

            // Process episode data if it exists
            val seasonNumber = cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_SEASON_NUMBER)
            if (!cursor.isNull(seasonNumber)) {
                val season = cursor.getInt(seasonNumber)
                val episodeObject = JSONObject().apply {
                    put(EPISODE_NUMBER,
                        cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_EPISODE_NUMBER)))
                    put(EPISODE_RATING,
                        cursor.getFloat(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_EPISODE_RATING)))
                    put(EPISODE_WATCH_DATE,
                        cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE)))
                    put(EPISODE_REVIEW,
                        cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_EPISODE_REVIEW)))
                }

                // Add episode to season array
                val seasonEpisodes = showEpisodesMap[movieId]?.getOrPut(season) { JSONArray() }
                seasonEpisodes?.put(episodeObject)
            }

        } while (cursor.moveToNext())

        // Add organized episodes to their respective shows
        dbShowsArrayList.forEach { showObject ->
            try {
                val movieId = showObject.getInt(ShowBaseAdapter.KEY_ID)
                val seasonMap = showEpisodesMap[movieId]
                val seasonsObject = showObject.getJSONObject(SEASONS)

                seasonMap?.forEach { (season, episodes) ->
                    seasonsObject.put(season.toString(), episodes)
                }
            } catch (je: JSONException) {
                je.printStackTrace()
            }
        }

        cursor.close()
        Log.d("ShowBaseAdapter", "dbShowsArrayList: $dbShowsArrayList")
        return dbShowsArrayList
    }

    private fun fetchCalendarData(callback: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val sdf = android.icu.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = sdf.format(Date())

            epDbHelper.writableDatabase.delete(
                EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS,
                null,
                null
            )

            // Fetch shows calendar
            val showsUrl = "https://api.trakt.tv/calendars/all/shows/$today/7"
            val showsRequest = createRequest(showsUrl)
            executeRequest(showsRequest) { showResponse ->
                val showsArray = JSONArray(showResponse)
                for (i in 0 until showsArray.length()) {
                    val item = showsArray.getJSONObject(i)
                    val firstAired = item.optString("first_aired", "NULL")
                    val episode = item.getJSONObject("episode")
                    val show = item.getJSONObject("show")

                    val values = ContentValues().apply {
                        put(EpisodeReminderDatabaseHelper.COL_TYPE, "episode")
                        put(EpisodeReminderDatabaseHelper.COLUMN_DATE, firstAired)
                        // Required fields with default values
                        put(
                            EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID,
                            show.getJSONObject("ids").optInt("tmdb", 0)
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME,
                            show.optString("title", "NULL")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COLUMN_NAME,
                            episode.optString("title", "NULL")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER,
                            episode.optString("number", "NULL")
                        )

                        // Episode details
                        put(
                            EpisodeReminderDatabaseHelper.COL_SEASON,
                            episode.optInt("season")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_EPISODE_TRAKT_ID,
                            episode.getJSONObject("ids").optInt("trakt")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_EPISODE_TVDB,
                            episode.getJSONObject("ids").optInt("tvdb")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_EPISODE_IMDB,
                            episode.getJSONObject("ids").optString("imdb")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_EPISODE_TMDB,
                            episode.getJSONObject("ids").optInt("tmdb")
                        )

                        // Show details
                        put(
                            EpisodeReminderDatabaseHelper.COL_SHOW_YEAR,
                            show.optInt("year")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_SHOW_TRAKT_ID,
                            show.getJSONObject("ids").optInt("trakt")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_SHOW_SLUG,
                            show.getJSONObject("ids").optString("slug")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_SHOW_TVDB,
                            show.getJSONObject("ids").optInt("tvdb")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_SHOW_IMDB,
                            show.getJSONObject("ids").optString("imdb")
                        )
                    }
                    epDbHelper.writableDatabase.insert(
                        EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS,
                        null,
                        values
                    )
                }
            }

            // Fetch movies calendar
            val moviesUrl = "https://api.trakt.tv/calendars/all/movies/$today/7"
            val moviesRequest = createRequest(moviesUrl)
            executeRequest(moviesRequest) { movieResponse ->
                val moviesArray = JSONArray(movieResponse)
                for (i in 0 until moviesArray.length()) {
                    val item = moviesArray.getJSONObject(i)
                    val releaseDate = item.optString("released", "NULL")
                    val movie = item.getJSONObject("movie")

                    val values = ContentValues().apply {
                        put(EpisodeReminderDatabaseHelper.COL_TYPE, "movie")
                        put(EpisodeReminderDatabaseHelper.COLUMN_DATE, releaseDate)
                        put(
                            EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID,
                            movie.getJSONObject("ids").optInt("tmdb", 0)
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME,
                            movie.optString("title", "NULL")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_YEAR,
                            movie.optInt("year")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_SHOW_TRAKT_ID,
                            movie.getJSONObject("ids").optInt("trakt")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_SLUG,
                            movie.getJSONObject("ids").optString("slug")
                        )
                        put(
                            EpisodeReminderDatabaseHelper.COL_IMDB,
                            movie.getJSONObject("ids").optString("imdb")
                        )
                        put(EpisodeReminderDatabaseHelper.COLUMN_NAME, "")
                        put(EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER, "")
                    }
                    epDbHelper.writableDatabase.insert(
                        EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS,
                        null,
                        values
                    )
                }
            }

            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }

    private fun createRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .get()
            .addHeader("Content-Type", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", clientId ?: "")
            .build()
    }

    private fun executeRequest(request: Request, onResponse: (String) -> Unit) {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body.string().let { onResponse(it) }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Gets (or creates) a writable database.
     *
     * @throws SQLException if the database cannot be opened for writing.
     */
    @Throws(SQLException::class)
    private fun open() {
        mDatabase = mDatabaseHelper.writableDatabase
    }

    /**
     * Shows a dialog with the given message and a positive (triggering the given listener) and
     * negative (does nothing) button.
     *
     * @param message    the message to be displayed
     * @param okListener the listener that should be triggered
     * when the user presses the positive button.
     */
    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setPositiveButton(
                requireContext().applicationContext.resources.getString(R.string.no_permission_dialog_ok), okListener)
            .setNegativeButton(
                requireContext().applicationContext.resources.getString(R.string.no_permission_dialog_cancel), null)
            .create()
            .show()
    }

    /**
     * Sets a new adapter only containing the shows that fit the search query.
     * If a filter is being used, the retrieved shows will be filtered
     * and the adapter will be replaced again.
     *
     * @param query the text that the show title should contain.
     */
    fun search(query: String) {
        if (query != "") {
            mSearchView = true
            CoroutineScope(Dispatchers.Main).launch {
                // Database operations on IO dispatcher
                val shows = withContext(Dispatchers.IO) {
                    getShowsFromDatabase(query, MovieDatabaseHelper.COLUMN_ID + " DESC", null)
                }

                // UI updates on Main dispatcher
                mSearchShowArrayList = shows

                mSearchShowAdapter.updateData(mSearchShowArrayList)

                mShowView.adapter = mSearchShowAdapter
            }
            // Only use the filter if the user has gone to the FilterActivity in this session.
            if (usedFilter) {
                filterAdapter()
            }
        }
    }

    companion object {
        private var mDatabaseUpdate = false
        const val EPISODE_NUMBER = "episode_number"
        const val EPISODE_RATING = "episode_rating"
        const val EPISODE_WATCH_DATE = "episode_watch_date"
        const val EPISODE_REVIEW = "episode_review"
        const val SEASONS = "season"
        const val IS_MOVIE = "is_movie"
        const val IS_UPCOMING = "is_upcoming"
        const val UPCOMING_DATE = "upcoming_date"
        const val UPCOMING_TIME = "upcoming_time"
        const val UPCOMING_TYPE = "upcoming_type"
        const val UPCOMING_EPISODE_NAME = "upcoming_episode_name"
        const val UPCOMING_EPISODE_NUMBER = "number"
        const val EPISODE = "episode"

        fun newSavedInstance(): ListFragment {
            return ListFragment()
        }

        fun databaseUpdate() {
            mDatabaseUpdate = true
        }
    }

    private fun loadChipOrder() {
        val json = preferences.getString("chip_order", null)
        if (json != null) {
            val type = object : TypeToken<MutableList<ChipInfo>>() {}.type
            chipInfos = gson.fromJson(json, type)
        } else {
            chipInfos = mutableListOf(
                ChipInfo(R.id.chipAll, getString(R.string.all), "all", true),
                ChipInfo(R.id.chipWatching, getString(R.string.watching), "watching", true),
                ChipInfo(R.id.chipWatched, getString(R.string.watched), "watched", true),
                ChipInfo(
                    R.id.chipPlanToWatch,
                    getString(R.string.plan_to_watch),
                    "plan_to_watch",
                    true
                ),
                ChipInfo(R.id.chipUpcoming, getString(R.string.upcoming), "upcoming", true)
            )
        }
    }

    private fun saveChipOrder(chips: List<ChipInfo>) {
        val json = gson.toJson(chips)
        preferences.edit {
            putString("chip_order", json)
        }
    }

    private fun applyChipOrder() {
        binding.chipGroup.removeAllViews()
        for (chipInfo in chipInfos) {
            val chip = Chip(requireContext()).apply {
                id = chipInfo.id
                text = chipInfo.name
                tag = chipInfo.tag
                isCheckable = true
                visibility = if (chipInfo.isVisible) View.VISIBLE else View.GONE

            }
            binding.chipGroup.addView(chip)
        }
        setChipListeners()

        if (binding.chipGroup.checkedChipId == -1) {
            val allChip = binding.chipGroup.findViewById<Chip>(R.id.chipAll)
            if (allChip != null && allChip.isVisible) {
                allChip.isChecked = true
            } else {
                for (chipInfo in chipInfos) {
                    if (chipInfo.isVisible) {
                        binding.chipGroup.findViewById<Chip>(chipInfo.id)?.isChecked = true
                        break
                    }
                }
            }
        }
    }

    private fun getOtherChips(chipId: Int): List<Chip> {
        val otherChips = mutableListOf<Chip>()
        for (i in 0 until binding.chipGroup.childCount) {
            val chip = binding.chipGroup.getChildAt(i) as Chip
            if (chip.id != chipId) {
                otherChips.add(chip)
            }
        }
        return otherChips
    }
}