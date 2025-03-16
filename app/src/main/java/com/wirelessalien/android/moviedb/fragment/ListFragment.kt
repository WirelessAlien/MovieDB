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
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.activity.ExportActivity
import com.wirelessalien.android.moviedb.activity.FilterActivity
import com.wirelessalien.android.moviedb.activity.ImportActivity
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityMainBinding
import com.wirelessalien.android.moviedb.databinding.DialogTraktSyncMinimalBinding
import com.wirelessalien.android.moviedb.databinding.FragmentSavedBinding
import com.wirelessalien.android.moviedb.databinding.WatchSummaryBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener
import com.wirelessalien.android.moviedb.trakt.TraktAutoSyncManager
import com.wirelessalien.android.moviedb.work.TktAutoSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/**
 *
 * Shows the shows that are stored in the local database.
 */
class ListFragment : BaseFragment(), AdapterDataChangedListener {
    private val REQUEST_CODE_ASK_PERMISSIONS_EXPORT = 123
    private val REQUEST_CODE_ASK_PERMISSIONS_IMPORT = 124
    private lateinit var mShowBackupArrayList: ArrayList<JSONObject>
    private lateinit var mSearchShowBackupArrayList: ArrayList<JSONObject>
    private var usedFilter = false
    private lateinit var mDatabase: SQLiteDatabase
    private val client = OkHttpClient()
    private lateinit var mDatabaseHelper: MovieDatabaseHelper
    private lateinit var epDbHelper: EpisodeReminderDatabaseHelper
    private var selectedMediaTypes = mutableSetOf<String>()
    private var mScrollPosition: Int? = null
    private var accessToken: String? = null
    private var clientId: String? = null
    private lateinit var filterActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var binding: FragmentSavedBinding
    private lateinit var activityBinding: ActivityMainBinding

    override fun onAdapterDataChangedListener() {
        updateShowViewAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        mDatabaseHelper = MovieDatabaseHelper(requireContext().applicationContext)
        epDbHelper = EpisodeReminderDatabaseHelper(requireContext().applicationContext)
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
        mShowView = RecyclerView(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        createShowList()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSavedBinding.inflate(inflater, container, false)
        val fragmentView = binding.root
        activityBinding = (activity as MainActivity).getBinding()
        showShowList(fragmentView)
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
            filterActivityResultLauncher.launch(intent)
        }

        activityBinding.fab2.setImageResource(R.drawable.ic_chart)
        activityBinding.fab2.visibility = View.VISIBLE
        activityBinding.fab2.isEnabled = true
        activityBinding.fab2.setOnClickListener {
            showWatchSummaryDialog()
        }

        selectedMediaTypes.addAll(listOf("movie", "tv"))

        setupMediaTypeChips()

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }

        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.database_menu, menu)
                menuInflater.inflate(R.menu.tkt_auto_sync_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
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
                    R.id.action_auto_sync -> {
                        showTraktSyncDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun refreshData() {
        CoroutineScope(Dispatchers.Main).launch {
            // Refresh data based on the selected chip
            when {
                binding.chipAll.isChecked -> {
                    selectedMediaTypes.clear()
                    selectedMediaTypes.addAll(listOf("movie", "tv"))
                    updateShowViewAdapter()
                }
                binding.chipMovie.isChecked -> {
                    selectedMediaTypes.clear()
                    selectedMediaTypes.add("movie")
                    filterByMediaType()
                }
                binding.chipShow.isChecked -> {
                    selectedMediaTypes.clear()
                    selectedMediaTypes.add("tv")
                    filterByMediaType()
                }
                binding.chipUpcoming.isChecked -> {
                    mShowArrayList.clear()
                    mShowAdapter.notifyDataSetChanged()
                    binding.shimmerFrameLayout1.apply {
                        startShimmer()
                        visibility = View.VISIBLE
                    }
                    fetchCalendarData {
                        showUpcomingContent()
                    }
                }
            }
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    override fun onResume() {

        if (mDatabaseUpdate) {
            // The database is updated, load the changes into the array list.
            updateShowViewAdapter()
            mDatabaseUpdate = false
        }

        if (preferences.getBoolean(PERSISTENT_FILTERING_PREFERENCE, false)) {
            filterAdapter()
        }

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
        super.onResume()
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
        close()
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
        close()
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
        close()
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
        close()
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
        close()
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
        CoroutineScope(Dispatchers.Main).launch {
            binding.shimmerFrameLayout1.visibility = View.VISIBLE
            binding.shimmerFrameLayout1.startShimmer()
            // Database operations on IO dispatcher
            val shows = withContext(Dispatchers.IO) {
                getShowsFromDatabase(null, MovieDatabaseHelper.COLUMN_ID + " DESC")
            }

            // UI updates on Main dispatcher
            mShowArrayList = shows
            mShowAdapter = ShowBaseAdapter(
                mShowArrayList,
                mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
            )
            mShowView.adapter = mShowAdapter
            binding.shimmerFrameLayout1.stopShimmer()
            binding.shimmerFrameLayout1.visibility = View.GONE
        }

        if (!mSearchView) {
            if (usedFilter) {
                // Also change the backup.
                mShowBackupArrayList = mShowArrayList.clone() as ArrayList<JSONObject>
                filterAdapter()
            } else {
                mShowView.adapter = mShowAdapter
            }
            if (mScrollPosition != null) {
                mShowView.scrollToPosition(mScrollPosition!!)
            }
        }
    }

    private fun setupMediaTypeChips() {
        // Initially check the "All" chip and uncheck others
        binding.chipAll.isChecked = true
        binding.chipMovie.isChecked = false
        binding.chipShow.isChecked = false
        binding.chipUpcoming.isChecked = false
        selectedMediaTypes.clear()
        selectedMediaTypes.addAll(listOf("movie", "tv"))

        binding.chipAll.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                // When "All" is selected, uncheck others
                binding.noUpcomingText.visibility = View.GONE
                binding.chipMovie.isChecked = false
                binding.chipShow.isChecked = false
                binding.chipUpcoming.isChecked = false
                selectedMediaTypes.clear()
                selectedMediaTypes.addAll(listOf("movie", "tv"))
                filterByMediaType()
            } else if (!binding.chipMovie.isChecked && !binding.chipShow.isChecked && !binding.chipUpcoming.isChecked) {
                // If no other chips are checked, recheck "All"
                binding.noUpcomingText.visibility = View.GONE
                buttonView.isChecked = true
                selectedMediaTypes.clear()
                selectedMediaTypes.addAll(listOf("movie", "tv"))
                filterByMediaType()
            }
        }

        binding.chipMovie.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Uncheck others when Movie is selected
                binding.noUpcomingText.visibility = View.GONE
                binding.chipAll.isChecked = false
                binding.chipShow.isChecked = false
                binding.chipUpcoming.isChecked = false
                selectedMediaTypes.clear()
                selectedMediaTypes.add("movie")
            } else {
                selectedMediaTypes.remove("movie")
                // If no other filter is checked, check "All"
                if (!binding.chipShow.isChecked && !binding.chipUpcoming.isChecked) {
                    binding.chipAll.isChecked = true
                    binding.noUpcomingText.visibility = View.GONE
                    selectedMediaTypes.clear()
                    selectedMediaTypes.addAll(listOf("movie", "tv"))
                }
            }
            filterByMediaType()
        }

        binding.chipShow.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Uncheck others when Show is selected
                binding.noUpcomingText.visibility = View.GONE
                binding.chipAll.isChecked = false
                binding.chipMovie.isChecked = false
                binding.chipUpcoming.isChecked = false
                selectedMediaTypes.clear()
                selectedMediaTypes.add("tv")
            } else {
                selectedMediaTypes.remove("tv")
                // If no other filter is checked, check "All"
                if (!binding.chipMovie.isChecked && !binding.chipUpcoming.isChecked) {
                    binding.chipAll.isChecked = true
                    binding.noUpcomingText.visibility = View.GONE
                    selectedMediaTypes.clear()
                    selectedMediaTypes.addAll(listOf("movie", "tv"))
                }
            }
            filterByMediaType()
        }

        binding.chipUpcoming.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Uncheck others when Upcoming is selected
                binding.chipAll.isChecked = false
                binding.chipMovie.isChecked = false
                binding.chipShow.isChecked = false
                selectedMediaTypes.clear()

                mShowArrayList.clear()
                mShowAdapter.notifyDataSetChanged()
                showUpcomingContent()
            } else {
                // If no other filter is checked, check "All"
                if (!binding.chipMovie.isChecked && !binding.chipShow.isChecked) {
                    binding.chipAll.isChecked = true
                    selectedMediaTypes.clear()
                    selectedMediaTypes.addAll(listOf("movie", "tv"))
                    updateShowViewAdapter()
                }
            }
        }
    }

    private fun showUpcomingContent() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                binding.shimmerFrameLayout1.apply {
                    visibility = View.VISIBLE
                    startShimmer()
                }

                val upcomingContent = withContext(Dispatchers.IO) {
                    val upcomingShows = ArrayList<JSONObject>()

                    // Use try-with-resources for database helpers
                    EpisodeReminderDatabaseHelper(requireContext()).use { epDbHelper ->
                        MovieDatabaseHelper(requireContext()).use { movieDbHelper ->
                            val epDb = epDbHelper.readableDatabase
                            val movieDb = movieDbHelper.readableDatabase

                            // Query episode reminders with specific columns needed
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
                                    val movieId = epCursor.getInt(epCursor.getColumnIndexOrThrow(
                                        EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID))

                                    // Query movie details with specific columns
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
                                            createMovieDetails(movieCursor, epCursor)?.let {
                                                upcomingShows.add(it)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    upcomingShows
                }

                updateUI(upcomingContent)

            } catch (e: Exception) {
                binding.shimmerFrameLayout1.apply {
                    stopShimmer()
                    visibility = View.GONE
                }
                Log.e("ListFragment", "Error loading upcoming content", e)
                binding.noUpcomingText.visibility = View.VISIBLE
            } finally {
                binding.shimmerFrameLayout1.apply {
                    stopShimmer()
                    visibility = View.GONE
                }
            }
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
                put(IS_MOVIE, movieCursor.getString(movieCursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_MOVIE)))

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

    private fun getMovieRating(cursor: Cursor): Int {
        return if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING))) {
            cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING))
        } else {
            cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RATING))
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

    private fun updateUI(upcomingContent: ArrayList<JSONObject>) {
        if (upcomingContent.isNotEmpty()) {
            Log.d("ListFragment", "Upcoming content: $upcomingContent")
            mShowArrayList = upcomingContent
            mShowAdapter = ShowBaseAdapter(
                mShowArrayList,
                mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
            )
            mShowView.adapter = mShowAdapter
            binding.noUpcomingText.visibility = View.GONE
        } else {
            mShowArrayList.clear()
            mShowAdapter.notifyDataSetChanged()
            binding.noUpcomingText.visibility = View.VISIBLE
        }
    }

    private fun filterByMediaType() {
        if (!mSearchView) {
            // Clone the backup if it's the first filter
            if (!::mShowBackupArrayList.isInitialized) {
                mShowBackupArrayList = mShowArrayList.clone() as ArrayList<JSONObject>
            }

            // Reset to backup before filtering
            mShowArrayList = mShowBackupArrayList.clone() as ArrayList<JSONObject>

            // Apply media type filter
            if (selectedMediaTypes.size < 2) { // Only filter if not all types are selected
                mShowArrayList.removeIf { showObject ->
                    val isTV = showObject.optString(IS_MOVIE) == "0"
                    when {
                        selectedMediaTypes.contains("movie") -> isTV
                        selectedMediaTypes.contains("tv") -> !isTV
                        else -> true // Remove all if nothing selected
                    }
                }
            }

            // Apply other filters if needed
            if (usedFilter) {
                filterAdapter()
            } else {
                // Update adapter with filtered list
                mShowAdapter = ShowBaseAdapter(
                    mShowArrayList,
                    mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
                )
                mShowView.adapter = mShowAdapter
            }
        } else {
            // Handle search view filtering
            if (!::mSearchShowBackupArrayList.isInitialized) {
                mSearchShowBackupArrayList = mSearchShowArrayList.clone() as ArrayList<JSONObject>
            }

            mSearchShowArrayList = mSearchShowBackupArrayList.clone() as ArrayList<JSONObject>

            if (selectedMediaTypes.size < 2) {
                mSearchShowArrayList.removeIf { showObject ->
                    val isTV = showObject.optString(IS_MOVIE) == "0"
                    when {
                        selectedMediaTypes.contains("movie") -> isTV
                        selectedMediaTypes.contains("tv") -> !isTV
                        else -> true
                    }
                }
            }

            if (usedFilter) {
                filterAdapter()
            } else {
                mSearchShowAdapter = ShowBaseAdapter(
                    mSearchShowArrayList,
                    mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
                )
                mShowView.adapter = mSearchShowAdapter
            }
        }
    }

    /**
     * Filters the shows based on the settings in the FilterActivity.
     */
    private fun filterAdapter() {
        // Get the parameters from the filter activity and reload the adapter
        val sharedPreferences =
            requireActivity().getSharedPreferences(FilterActivity.FILTER_PREFERENCES, Context.MODE_PRIVATE)

        // Clone the ArrayList as the original needs to be kept
        // in case the filter settings are changed (and removed shows might need to be shown again).
        if (!mSearchView) {
            mShowBackupArrayList = mShowArrayList.clone() as ArrayList<JSONObject>
            mShowArrayList = mShowBackupArrayList.clone() as ArrayList<JSONObject>
        } else {
            mSearchShowBackupArrayList = mSearchShowArrayList.clone() as ArrayList<JSONObject>
            mSearchShowArrayList = mSearchShowBackupArrayList.clone() as ArrayList<JSONObject>
        }

        // First apply media type filter
        if (selectedMediaTypes.size < 2) {
            if (!mSearchView) {
                mShowArrayList.removeIf { showObject ->
                    val isTV = showObject.optString(IS_MOVIE) == "0"
                    when {
                        selectedMediaTypes.contains("movie") -> isTV
                        selectedMediaTypes.contains("tv") -> !isTV
                        else -> true
                    }
                }
            } else {
                mSearchShowArrayList.removeIf { showObject ->
                    val isTV = showObject.optString(IS_MOVIE) == "0"
                    when {
                        selectedMediaTypes.contains("movie") -> isTV
                        selectedMediaTypes.contains("tv") -> !isTV
                        else -> true
                    }
                }
            }
        }

        // Sort the ArrayList based on the chosen order.
        var sortPreference: String?
        if (sharedPreferences.getString(FilterActivity.FILTER_SORT, null)
                .also { sortPreference = it } != null
        ) {
            when (sortPreference) {
                "best_rated" -> {
                    if (mSearchView) {
                        mSearchShowArrayList.sortWith { firstObject: JSONObject, secondObject: JSONObject ->
                            firstObject
                                .optInt(ShowBaseAdapter.KEY_RATING).compareTo(
                                    secondObject
                                        .optInt(ShowBaseAdapter.KEY_RATING)
                                ) * -1
                        }
                    } else {
                        mShowArrayList.sortWith { firstObject: JSONObject, secondObject: JSONObject ->
                            firstObject
                                .optInt(ShowBaseAdapter.KEY_RATING).compareTo(
                                    secondObject
                                        .optInt(ShowBaseAdapter.KEY_RATING)
                                ) * -1
                        }
                    }
                }

                "release_date" -> {
                    if (mSearchView) {
                        mSearchShowArrayList.sortWith(java.util.Comparator<JSONObject> { firstObject: JSONObject, secondObject: JSONObject ->
                            val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val firstDate: Date?
                            val secondDate: Date?
                            try {
                                firstDate =
                                    simpleDateFormat.parse(firstObject.optString(ShowBaseAdapter.KEY_RELEASE_DATE))
                                secondDate =
                                    simpleDateFormat.parse(secondObject.optString(ShowBaseAdapter.KEY_RELEASE_DATE))
                            } catch (e: ParseException) {
                                e.printStackTrace()
                                return@Comparator 0
                            }
                            if (firstDate.time > secondDate.time) -1 else 1
                        })
                    } else {
                        mShowArrayList.sortWith(java.util.Comparator<JSONObject> { firstObject: JSONObject, secondObject: JSONObject ->
                            val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val firstDate: Date?
                            val secondDate: Date?
                            try {
                                firstDate =
                                    simpleDateFormat.parse(firstObject.optString(ShowBaseAdapter.KEY_RELEASE_DATE))
                                secondDate =
                                    simpleDateFormat.parse(secondObject.optString(ShowBaseAdapter.KEY_RELEASE_DATE))
                            } catch (e: ParseException) {
                                e.printStackTrace()
                                return@Comparator 0
                            }
                            if (firstDate.time > secondDate.time) -1 else 1
                        })
                    }
                }

                "alphabetic_order" -> {
                    if (mSearchView) {
                        mSearchShowArrayList.sortWith { firstObject: JSONObject, secondObject: JSONObject ->
                            firstObject.optString(ShowBaseAdapter.KEY_TITLE)
                                .compareTo(
                                    secondObject.optString(ShowBaseAdapter.KEY_TITLE),
                                    ignoreCase = true
                                )
                        }
                    } else {
                        mShowArrayList.sortWith { firstObject: JSONObject, secondObject: JSONObject ->
                            firstObject.optString(ShowBaseAdapter.KEY_TITLE)
                                .compareTo(
                                    secondObject.optString(ShowBaseAdapter.KEY_TITLE),
                                    ignoreCase = true
                                )
                        }
                    }
                }

                "start_date_order" -> {
                    val simpleDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                    if (mSearchView) {
                        mSearchShowArrayList.sortWith(java.util.Comparator<JSONObject> { firstObject: JSONObject, secondObject: JSONObject ->
                            val firstDate: Date?
                            val secondDate: Date?
                            try {
                                val firstDateString =
                                    firstObject.optString(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)
                                val secondDateString =
                                    secondObject.optString(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)
                                firstDate = if (firstDateString.isNotEmpty()) {
                                    simpleDateFormat.parse(firstDateString)
                                } else {
                                    Date(Long.MIN_VALUE)
                                }
                                secondDate = if (secondDateString.isNotEmpty()) {
                                    simpleDateFormat.parse(secondDateString)
                                } else {
                                    Date(Long.MIN_VALUE)
                                }
                            } catch (e: ParseException) {
                                e.printStackTrace()
                                return@Comparator 0
                            }
                            // * -1 is to make the order descending instead of ascending.
                            if (firstDate != null) {
                                return@Comparator firstDate.compareTo(secondDate) * -1
                            } else if (secondDate != null) {
                                return@Comparator -1
                            }
                            0
                        })
                    } else {
                        mShowArrayList.sortWith(java.util.Comparator<JSONObject> { firstObject: JSONObject, secondObject: JSONObject ->
                            val firstDate: Date?
                            val secondDate: Date?
                            try {
                                val firstDateString =
                                    firstObject.optString(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)
                                val secondDateString =
                                    secondObject.optString(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)
                                firstDate = if (firstDateString.isNotEmpty()) {
                                    simpleDateFormat.parse(firstDateString)
                                } else {
                                    Date(Long.MIN_VALUE)
                                }
                                secondDate = if (secondDateString.isNotEmpty()) {
                                    simpleDateFormat.parse(secondDateString)
                                } else {
                                    Date(Long.MIN_VALUE)
                                }
                            } catch (e: ParseException) {
                                e.printStackTrace()
                                return@Comparator 0
                            }
                            if (firstDate == null) return@Comparator if (secondDate == null) 0 else 1
                            if (secondDate == null) return@Comparator -1
                            firstDate.compareTo(secondDate) * -1
                        })
                    }
                }

                "finish_date_order" -> {
                    val simpleDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                    if (mSearchView) {
                        mSearchShowArrayList.sortWith(java.util.Comparator<JSONObject> { firstObject: JSONObject, secondObject: JSONObject ->
                            val firstDate: Date?
                            val secondDate: Date?
                            try {
                                val firstDateString =
                                    firstObject.optString(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)
                                val secondDateString =
                                    secondObject.optString(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)
                                firstDate = if (firstDateString.isNotEmpty()) {
                                    simpleDateFormat.parse(firstDateString)
                                } else {
                                    Date(Long.MIN_VALUE)
                                }
                                secondDate = if (secondDateString.isNotEmpty()) {
                                    simpleDateFormat.parse(secondDateString)
                                } else {
                                    Date(Long.MIN_VALUE)
                                }
                            } catch (e: ParseException) {
                                e.printStackTrace()
                                return@Comparator 0
                            }
                            if (firstDate == null) return@Comparator if (secondDate == null) 0 else 1
                            if (secondDate == null) return@Comparator -1
                            firstDate.compareTo(secondDate) * -1
                        })
                    } else {
                        mShowArrayList.sortWith(java.util.Comparator<JSONObject> { firstObject: JSONObject, secondObject: JSONObject ->
                            val firstDate: Date?
                            val secondDate: Date?
                            try {
                                val firstDateString =
                                    firstObject.optString(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)
                                val secondDateString =
                                    secondObject.optString(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)
                                firstDate = if (firstDateString.isNotEmpty()) {
                                    simpleDateFormat.parse(firstDateString)
                                } else {
                                    Date(Long.MIN_VALUE)
                                }
                                secondDate = if (secondDateString.isNotEmpty()) {
                                    simpleDateFormat.parse(secondDateString)
                                } else {
                                    Date(Long.MIN_VALUE)
                                }
                            } catch (e: ParseException) {
                                e.printStackTrace()
                                return@Comparator 0
                            }
                            if (firstDate == null) return@Comparator if (secondDate == null) 0 else 1
                            if (secondDate == null) return@Comparator -1
                            firstDate.compareTo(secondDate) * -1
                        })
                    }
                }
            }
        }


        // Remove the movies that should not be displayed from the list.
        val selectedCategories = FilterActivity.convertStringToArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_CATEGORIES,
                null
            ), ", "
        )
        // Filter the search list if the user was searching, otherwise filter the normal list.
        if (mSearchView && selectedCategories != null) {
            val iterator = mSearchShowArrayList.iterator()
            while (iterator.hasNext()) {
                val columnWatched = iterator.next()
                    .optInt(MovieDatabaseHelper.COLUMN_CATEGORIES)
                var shouldKeep = false
                for (i in selectedCategories.indices) {
                    if (columnWatched == DetailActivity.getCategoryNumber(selectedCategories[i])) {
                        shouldKeep = true
                        break
                    }
                }
                if (!shouldKeep) {
                    iterator.remove()
                }
            }
        } else if (selectedCategories != null) {
            val iterator = mShowArrayList.iterator()
            while (iterator.hasNext()) {
                val columnWatched = iterator.next()
                    .optInt(MovieDatabaseHelper.COLUMN_CATEGORIES)
                var shouldKeep = false
                for (i in selectedCategories.indices) {
                    if (columnWatched == DetailActivity.getCategoryNumber(selectedCategories[i])) {
                        shouldKeep = true
                        break
                    }
                }
                if (!shouldKeep) {
                    iterator.remove()
                }
            }
        }

        // Remove shows that do not contain certain genres from the list.
        val withGenres = FilterActivity.convertStringToIntegerArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_WITH_GENRES,
                null
            ), ", "
        )
        if (withGenres != null && withGenres.isNotEmpty()) {
            if (mSearchView) {
                val iterator = mSearchShowArrayList.iterator()
                while (iterator.hasNext()) {
                    val showObject = iterator.next()
                    val idList = FilterActivity.convertStringToIntegerArrayList(
                        showObject.optString(ShowBaseAdapter.KEY_GENRES), ","
                    )
                    if (!idList.containsAll(withGenres)) {
                        iterator.remove()
                    }
                }
            } else {
                val iterator = mShowArrayList.iterator()
                while (iterator.hasNext()) {
                    val showObject = iterator.next()
                    val idList = FilterActivity.convertStringToIntegerArrayList(
                        showObject.optString(ShowBaseAdapter.KEY_GENRES), ","
                    )
                    if (!idList.containsAll(withGenres)) {
                        iterator.remove()
                    }
                }
            }
        }

// Remove shows that contain certain genres from the list.
        val withoutGenres = FilterActivity.convertStringToIntegerArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_WITHOUT_GENRES,
                null
            ), ", "
        )
        if (withoutGenres != null && withoutGenres.isNotEmpty()) {
            if (mSearchView) {
                val iterator = mSearchShowArrayList.iterator()
                while (iterator.hasNext()) {
                    val showObject = iterator.next()
                    val idList = FilterActivity.convertStringToIntegerArrayList(
                        showObject.optString(ShowBaseAdapter.KEY_GENRES), ","
                    )
                    if (idList.any { withoutGenres.contains(it) }) {
                        iterator.remove()
                    }
                }
            } else {
                val iterator = mShowArrayList.iterator()
                while (iterator.hasNext()) {
                    val showObject = iterator.next()
                    val idList = FilterActivity.convertStringToIntegerArrayList(
                        showObject.optString(ShowBaseAdapter.KEY_GENRES), ","
                    )
                    if (idList.any { withoutGenres.contains(it) }) {
                        iterator.remove()
                    }
                }
            }
        }

        // Set a new adapter with the cloned (and filtered) ArrayList.
        if (mSearchView) {
            mShowView.adapter = ShowBaseAdapter(
                mSearchShowArrayList, mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
            )
        } else {
            mShowView.adapter = ShowBaseAdapter(
                mShowArrayList, mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
            )
        }
    }

    /**
     * Creates the ShowBaseAdapter with the ArrayList containing shows from the database and
     * genres loaded from the API.
     */
    private fun createShowList() {
        mShowGenreList = HashMap()
        CoroutineScope(Dispatchers.Main).launch {
            val shows = withContext(Dispatchers.IO) {
                getShowsFromDatabase(null, MovieDatabaseHelper.COLUMN_ID + " DESC")
            }

            mShowArrayList = shows
            mShowAdapter = ShowBaseAdapter(
                mShowArrayList,
                mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
            )
            mShowView.adapter = mShowAdapter
        }
    }

    /**
     * Retrieves the shows from the database.
     *
     * @param searchQuery the text (if any) that the title should contain.
     * @param order       the order of the shows.
     * @return an ArrayList filled with the shows from the database
     * (optionally filtered and sorted on the given query and order).
     */
    fun getShowsFromDatabase(searchQuery: String?, order: String?): ArrayList<JSONObject> {
        // Add the order that the output should be sorted on.
        // When no order is specified leave the string empty.
        val dbOrder: String = if (order != null) {
            " ORDER BY $order"
        } else {
            ""
        }
        open()
        mDatabaseHelper.onCreate(mDatabase)

        // Base query with LEFT JOIN to include episode data
        val baseQuery = """
        SELECT m.*, e.${MovieDatabaseHelper.COLUMN_SEASON_NUMBER}, 
               e.${MovieDatabaseHelper.COLUMN_EPISODE_NUMBER},
               e.${MovieDatabaseHelper.COLUMN_EPISODE_RATING},
               e.${MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE},
               e.${MovieDatabaseHelper.COLUMN_EPISODE_REVIEW}
        FROM ${MovieDatabaseHelper.TABLE_MOVIES} m
        LEFT JOIN ${MovieDatabaseHelper.TABLE_EPISODES} e 
        ON m.${MovieDatabaseHelper.COLUMN_MOVIES_ID} = e.${MovieDatabaseHelper.COLUMN_MOVIES_ID}
    """

        // Search for shows that fulfill the searchQuery and fit in the list
        val cursor: Cursor = if (searchQuery != null && searchQuery != "") {
            mDatabase.rawQuery("""
            $baseQuery
            WHERE m.${MovieDatabaseHelper.COLUMN_TITLE} LIKE ? 
            $dbOrder
        """, arrayOf("%$searchQuery%"))
        } else {
            mDatabase.rawQuery("$baseQuery $dbOrder", null)
        }

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

        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val movieId = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIES_ID))

            // Create show object if it doesn't exist for this movieId
            if (!showEpisodesMap.containsKey(movieId)) {
                val showObject = JSONObject().apply {
                    try {
                        put(ShowBaseAdapter.KEY_ID, movieId)
                        if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING))) {
                            put(ShowBaseAdapter.KEY_RATING,
                                cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING)))
                        } else {
                            put(ShowBaseAdapter.KEY_RATING,
                                cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RATING)))
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
                            cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE)))
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

            cursor.moveToNext()
        }

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
        close()
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
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", clientId ?: "")
            .build()
    }

    private fun executeRequest(request: Request, onResponse: (String) -> Unit) {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { onResponse(it) }
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
     * Closes the writable database.
     */
    private fun close() {
        mDatabaseHelper.close()
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
                    getShowsFromDatabase(query, MovieDatabaseHelper.COLUMN_ID + " DESC")
                }

                // UI updates on Main dispatcher
                mSearchShowArrayList = shows
                mSearchShowBackupArrayList = mSearchShowArrayList.clone() as ArrayList<JSONObject>
                mSearchShowAdapter = ShowBaseAdapter(
                    mSearchShowArrayList,
                    mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
                )
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
        const val SEASONS = "seasons"
        const val IS_MOVIE = "is_movie"
        const val IS_UPCOMING = "is_upcoming"
        const val UPCOMING_DATE = "upcoming_date"
        const val UPCOMING_TYPE = "upcoming_type"
        const val UPCOMING_EPISODE_NAME = "upcoming_episode_name"
        const val UPCOMING_EPISODE_NUMBER = "upcoming_episode_number"
        const val EPISODE = "episode"

        fun newSavedInstance(): ListFragment {
            return ListFragment()
        }

        fun databaseUpdate() {
            mDatabaseUpdate = true
        }
    }
}
