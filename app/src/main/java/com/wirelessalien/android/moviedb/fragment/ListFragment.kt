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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.activity.ExportActivity
import com.wirelessalien.android.moviedb.activity.FilterActivity
import com.wirelessalien.android.moviedb.activity.ImportActivity
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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
    private lateinit var mDatabaseHelper: MovieDatabaseHelper

    // Used to restore scroll position
    private var mScrollPosition: Int? = null
    private lateinit var filterActivityResultLauncher: ActivityResultLauncher<Intent>
    override fun onAdapterDataChangedListener() {
        updateShowViewAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mDatabaseHelper = MovieDatabaseHelper(requireContext().applicationContext)

        filterActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                usedFilter = true
                filterAdapter()
                updateShowViewAdapter()
            }
        }

        // Get all entries from the database,
        // put them in JSONObjects and send them to the ShowBaseAdapter.
        mShowArrayList = ArrayList()
        mShowView = RecyclerView(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        createShowList()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val fragmentView = inflater.inflate(R.layout.fragment_show, container, false)
        showShowList(fragmentView)
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.setImageResource(R.drawable.ic_filter_list)
        fab.isEnabled = true
        fab.setOnClickListener {
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
        val fab2 = requireActivity().findViewById<FloatingActionButton>(R.id.fab2)
        fab2.setImageResource(R.drawable.ic_info)
        fab2.visibility = View.VISIBLE
        fab2.isEnabled = true
        fab2.setOnClickListener {
            showWatchSummaryDialog()
        }
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.database_menu, menu)
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
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onResume() {
        val progressBar = requireActivity().findViewById<CircularProgressIndicator>(R.id.progressBar)
        if (progressBar != null) {
            progressBar.visibility = View.GONE
        }
        if (mDatabaseUpdate) {
            // The database is updated, load the changes into the array list.
            updateShowViewAdapter()
            mDatabaseUpdate = false
        }

        if (preferences.getBoolean(PERSISTENT_FILTERING_PREFERENCE, false)) {
            filterAdapter()
        }

        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.setImageResource(R.drawable.ic_filter_list)
        fab.visibility = View.VISIBLE
        fab.isEnabled = true
        fab.setOnClickListener {
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

    // Usage example
    private suspend fun setupGenreChips(context: Context, chipGroup: ChipGroup) {
        val genreIds = getGenreIdsFromDatabase()
        val genreNames = getGenreNamesFromSharedPreferences(context)
        displayGenresInChipGroup(context, chipGroup, genreIds, genreNames)
    }

    private fun showWatchSummaryDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.watch_summary, null)
        val circularProgressIndicator = dialogView.findViewById<CircularProgressIndicator>(R.id.progressBar)
        val tvWatching = dialogView.findViewById<Chip>(R.id.chipWatching)
        val tvWatched = dialogView.findViewById<Chip>(R.id.chipWatched)
        val tvPlanToWatch = dialogView.findViewById<Chip>(R.id.chipPlanToWatch)
        val tvOnHold = dialogView.findViewById<Chip>(R.id.chipOnHold)
        val tvDropped = dialogView.findViewById<Chip>(R.id.chipDropped)
        val tvWatchedMovies = dialogView.findViewById<Chip>(R.id.chipWatchedMovies)
        val tvWatchedTVShows = dialogView.findViewById<Chip>(R.id.chipWatchedTVShows)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupGenres)

        val bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(dialogView)
        bottomSheetDialog.edgeToEdgeEnabled
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheetDialog.show()

        CoroutineScope(Dispatchers.Main).launch {
            circularProgressIndicator.visibility = View.VISIBLE

            val watching = getTotalItem(MovieDatabaseHelper.CATEGORY_WATCHING)
            val watched = getTotalItem(MovieDatabaseHelper.CATEGORY_WATCHED)
            val planToWatch = getTotalItem(MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH)
            val onHold = getTotalItem(MovieDatabaseHelper.CATEGORY_ON_HOLD)
            val dropped = getTotalItem(MovieDatabaseHelper.CATEGORY_DROPPED)
            val watchedMovies = getTotalMoviesInWatchedCategory()
            val watchedTVShows = getTotalTVShowsInWatchedCategory()

            tvWatching.text = getString(R.string.watching1, watching)
            tvWatched.text = getString(R.string.watched1, watched)
            tvPlanToWatch.text = getString(R.string.plan_to_watch1, planToWatch)
            tvOnHold.text = getString(R.string.on_hold1, onHold)
            tvDropped.text = getString(R.string.dropped1, dropped)
            tvWatchedMovies.text = getString(R.string.watched1, watchedMovies)
            tvWatchedTVShows.text = getString(R.string.watched1, watchedTVShows)

            // Setup genre chips
            setupGenreChips(requireContext(), chipGroup)

            circularProgressIndicator.visibility = View.GONE
        }
    }
    /**
     * Create and set the new adapter to update the show view.
     */
    private fun updateShowViewAdapter() {
        mShowArrayList = getShowsFromDatabase(null, MovieDatabaseHelper.COLUMN_ID + " DESC")
        mShowAdapter = ShowBaseAdapter(
            mShowArrayList, mShowGenreList,
            preferences.getBoolean(SHOWS_LIST_PREFERENCE, true), false
        )
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
            mShowBackupArrayList = (mShowArrayList.clone() as ArrayList<*>).filterNotNull() as ArrayList<JSONObject>
        } else {
            mSearchShowArrayList = (mSearchShowArrayList.clone() as ArrayList<*>).filterNotNull() as ArrayList<JSONObject>
        }
        val showMovie = FilterActivity.convertStringToArrayList(
            sharedPreferences.getString(FilterActivity.FILTER_SHOW_MOVIE, null), ", "
        )
        if (showMovie != null && (!showMovie.contains("movie") || !showMovie.contains("tv"))) {
            if (mSearchView) {
                mSearchShowArrayList.removeIf { showObject: JSONObject ->
                    val isTV = showObject.optString(ShowBaseAdapter.KEY_NAME) == "0"
                    showMovie.contains("movie") && isTV || showMovie.contains("tv") && !isTV
                }
            } else {
                mShowArrayList.removeIf { showObject: JSONObject ->
                    val isTV = showObject.optString(ShowBaseAdapter.KEY_NAME) == "0"
                    showMovie.contains("movie") && isTV || showMovie.contains("tv") && !isTV
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
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, true), false
            )
        } else {
            mShowView.adapter = ShowBaseAdapter(
                mShowArrayList, mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, true), false
            )
        }
    }

    /**
     * Creates the ShowBaseAdapter with the ArrayList containing shows from the database and
     * genres loaded from the API.
     */
    private fun createShowList() {
        mShowGenreList = HashMap()
        mShowArrayList = getShowsFromDatabase(null, MovieDatabaseHelper.COLUMN_ID + " DESC")
        mShowAdapter = ShowBaseAdapter(
            mShowArrayList, mShowGenreList,
            preferences.getBoolean(SHOWS_LIST_PREFERENCE, true), false
        )
        mShowView.adapter = mShowAdapter
        fetchGenreList("tv")
    }

    /**
     * Retrieves the shows from the database.
     *
     * @param searchQuery the text (if any) that the title should contain.
     * @param order       the order of the shows.
     * @return an ArrayList filled with the shows from the database
     * (optionally filtered and sorted on the given query and order).
     */
    private fun getShowsFromDatabase(searchQuery: String?, order: String?): ArrayList<JSONObject> {
        // Add the order that the output should be sorted on.
        // When no order is specified leave the string empty.
        val dbOrder: String = if (order != null) {
            " ORDER BY $order"
        } else {
            ""
        }
        open()
        mDatabaseHelper.onCreate(mDatabase)
        // Search for shows that fulfill the searchQuery and fit in the list.
        val cursor: Cursor = if (searchQuery != null && searchQuery != "") {
            mDatabase.rawQuery(
                "SELECT *, " + MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE + ", " + MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE + " FROM " + MovieDatabaseHelper.TABLE_MOVIES
                        + " WHERE " + MovieDatabaseHelper.COLUMN_TITLE + " LIKE '%"
                        + searchQuery + "%'" + dbOrder, null
            )
        } else {
            mDatabase.rawQuery(
                "SELECT *, " + MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE + ", " + MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE + " FROM " +
                        MovieDatabaseHelper.TABLE_MOVIES + dbOrder, null
            )
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
        cursor.moveToFirst()
        // Convert the cursor object to a JSONObject (that way the code can be reused).
        while (!cursor.isAfterLast) {
            val showObject = JSONObject()
            try {
                // Use the ShowBaseAdapter naming standards.
                showObject.put(
                    ShowBaseAdapter.KEY_ID,
                    cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIES_ID))
                )
                if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING))) {
                    showObject.put(
                        ShowBaseAdapter.KEY_RATING, cursor.getInt(
                            cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING)
                        )
                    )
                } else {
                    showObject.put(
                        ShowBaseAdapter.KEY_RATING, cursor.getInt(
                            cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RATING)
                        )
                    )
                }
                showObject.put(
                    ShowBaseAdapter.KEY_IMAGE, cursor.getString(
                        cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_IMAGE)
                    )
                )
                showObject.put(
                    ShowBaseAdapter.KEY_POSTER, cursor.getString(
                        cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_ICON)
                    )
                )
                showObject.put(
                    ShowBaseAdapter.KEY_TITLE, cursor.getString(
                        cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE)
                    )
                )
                showObject.put(
                    ShowBaseAdapter.KEY_DESCRIPTION, cursor.getString(
                        cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_SUMMARY)
                    )
                )
                showObject.put(
                    ShowBaseAdapter.KEY_GENRES, cursor.getString(
                        cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_GENRES_IDS)
                    )
                )
                showObject.put(
                    MovieDatabaseHelper.COLUMN_CATEGORIES, cursor.getInt(
                        cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_CATEGORIES)
                    )
                )
                showObject.put(
                    ShowBaseAdapter.KEY_DATE_MOVIE, cursor.getString(
                        cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RELEASE_DATE)
                    )
                )
                showObject.put(
                    MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE, cursor.getString(
                        cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)
                    )
                )
                showObject.put(
                    MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE, cursor.getString(
                        cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)
                    )
                )

                // Add a name key-value pair if it is a series.
                // (Otherwise ShowBaseAdapter won't recognise it as a series)
                if (cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE)) != 1) {
                    showObject.put(
                        ShowBaseAdapter.KEY_NAME, cursor.getInt(
                            cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE)
                        )
                    )

                    // Same goes for release date.
                    showObject.put(
                        ShowBaseAdapter.KEY_DATE_SERIES, cursor.getString(
                            cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_RELEASE_DATE)
                        )
                    )
                }
            } catch (je: JSONException) {
                je.printStackTrace()
            }

            // Add the JSONObject to the list and move on to the next one.
            dbShowsArrayList.add(showObject)
            cursor.moveToNext()
        }
        cursor.close()
        close()
        return dbShowsArrayList
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
            mSearchShowArrayList =
                getShowsFromDatabase(query, MovieDatabaseHelper.COLUMN_ID + " DESC")
            mSearchShowBackupArrayList = mSearchShowArrayList.clone() as ArrayList<JSONObject>
            mSearchShowAdapter = ShowBaseAdapter(
                mSearchShowArrayList, mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, true), false
            )
            mShowView.adapter = mSearchShowAdapter

            // Only use the filter if the user has gone to the FilterActivity in this session.
            if (usedFilter) {
                filterAdapter()
            }
        }
    }

    companion object {
        private var mDatabaseUpdate = false

        fun newSavedInstance(): ListFragment {
            return ListFragment()
        }

        fun databaseUpdate() {
            mDatabaseUpdate = true
        }
    }
}
