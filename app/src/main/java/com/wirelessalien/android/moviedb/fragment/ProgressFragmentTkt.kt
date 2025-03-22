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

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ShowProgressTraktAdapter
import com.wirelessalien.android.moviedb.databinding.FragmentProgressTktBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ProgressFragmentTkt : BaseFragment() {

    private lateinit var adapter: ShowProgressTraktAdapter
    private val watchedList = ArrayList<JSONObject>()
    private val fullWatchedList = ArrayList<JSONObject>()
    private lateinit var dbHelper: TraktDatabaseHelper
    private lateinit var tmdbHelper: TmdbDetailsDatabaseHelper
    private var traktAccessToken: String? = null
    private var clientId: String? = null
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var binding: FragmentProgressTktBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        dbHelper = TraktDatabaseHelper(requireContext())
        tmdbHelper = TmdbDetailsDatabaseHelper(requireContext())
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        traktAccessToken = sharedPreferences.getString("trakt_access_token", null)
        clientId = ConfigHelper.getConfigValue(requireContext(), "client_id")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProgressTktBinding.inflate(inflater, container, false)
        val view = binding.root

        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {

            if (binding.showRecyclerView.layoutManager !is GridLayoutManager) {
                adapter = ShowProgressTraktAdapter(
                    watchedList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true),
                    traktAccessToken?: "",
                    clientId?: ""
                )
            }

            val mShowGridView = GridLayoutManager(requireActivity(), preferences.getInt(GRID_SIZE_PREFERENCE, 3))
            binding.showRecyclerView.layoutManager = mShowGridView
            linearLayoutManager = mShowGridView
        } else {

            if (binding.showRecyclerView.layoutManager !is LinearLayoutManager) {
                adapter = ShowProgressTraktAdapter(
                    watchedList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true),
                    traktAccessToken?: "",
                    clientId?: ""
                )
            }
            linearLayoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)
            binding.showRecyclerView.layoutManager = linearLayoutManager
        }

        binding.showRecyclerView.adapter = adapter

        binding.chipAll.isChecked = true
        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds[0]) {
                    R.id.chipAll -> adapter.updateShowList(fullWatchedList)
                    R.id.chipMovie -> filterWatchedData("movie")
                    R.id.chipShow -> filterWatchedData("show")
                }
            }
        }

        loadCollectionData()
        return view
    }

    private fun loadCollectionData() {
        lifecycleScope.launch {
            binding.shimmerFrameLayout1.visibility = View.VISIBLE
            binding.shimmerFrameLayout1.startShimmer()
            withContext(Dispatchers.IO) {
                val db = dbHelper.readableDatabase
                val tmdbDb = tmdbHelper.readableDatabase

                val cursor = db.query(
                    TraktDatabaseHelper.TABLE_WATCHED, null, null, null, null, null, null)

                if (cursor.moveToFirst()) {
                    do {
                        val jsonObject = JSONObject().apply {
                            put("auto_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_ID)))
                            put("plays", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_PLAYS)))
                            put("last_watched_at", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_LAST_WATCHED_AT)))
                            put("last_updated_at", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_LAST_UPDATED_AT)))
                            put("type", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TYPE)))
                            put("title", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TITLE)))
                            put("year", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_YEAR)))
                            put("trakt_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TRAKT_ID)))
                            put("slug", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SLUG)))
                            put("imdb", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_IMDB)))
                            put("tmdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TMDB)))
                            put("tvdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TVDB)))
                            put("season", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SEASON)))
                            put("episode", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_EPISODE)))
                            put("episode_plays", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_EPISODE_PLAYS)))
                            put("episode_last_watched_at", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_EPISODE_LAST_WATCHED_AT)))
                            put("show_tmdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TMDB)))
                        }
                        // Fetch additional details from TMDB table
                        val tmdbId = if (jsonObject.getString("type") == "season" || jsonObject.getString("type") == "episode") {
                            cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TMDB))
                        } else {
                            cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TMDB))
                        }

                        val tmdbType = if (jsonObject.getString("type") == "season" || jsonObject.getString("type") == "episode") {
                            "show"
                        } else {
                            jsonObject.getString("type")
                        }

                        val tmdbCursor = tmdbDb.query(
                            TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS, null,
                            "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ? AND ${TmdbDetailsDatabaseHelper.COL_TYPE} = ?",
                            arrayOf(
                                tmdbId,
                                tmdbType
                            ),
                            null, null, null
                        )

                        if (tmdbCursor.moveToFirst()) {
                            jsonObject.put("id", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_TMDB_ID)))
                            jsonObject.put("name", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_NAME)))
                            jsonObject.put("backdrop_path", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_BACKDROP_PATH)))
                            jsonObject.put("poster_path", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_POSTER_PATH)))
                            jsonObject.put("overview", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_SUMMARY)))
                            jsonObject.put("vote_average", tmdbCursor.getDouble(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_VOTE_AVERAGE)))
                            jsonObject.put("release_date", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_RELEASE_DATE)))
                            jsonObject.put("genre_ids", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_GENRE_IDS)))
                            jsonObject.put("seasons_episode_show_tmdb", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB)))

                        }
                        tmdbCursor.close()

                        Log.d("WatchlistFragmentTkt", "Loaded watchlist item: $jsonObject")
                        fullWatchedList.add(jsonObject)
                    } while (cursor.moveToNext())
                }
                cursor.close()
            }
            applySorting()
            binding.shimmerFrameLayout1.visibility = View.GONE
            binding.shimmerFrameLayout1.stopShimmer()
        }
    }

    private fun applySorting() {
        val criteria = preferences.getString("tkt_sort_criteria", "name")
        val order = preferences.getString("tkt_sort_order", "asc")

        val comparator = when (criteria) {
            "name" -> compareBy<JSONObject> { it.optString("title", "") }
            "date" -> compareBy { it.optString("last_watched_at", "") }
            else -> compareBy { it.optString("title", "") }
        }

        if (order == "desc") {
            fullWatchedList.sortWith(comparator.reversed())
        } else {
            fullWatchedList.sortWith(comparator)
        }

        adapter.updateShowList(fullWatchedList)
    }
    private fun filterWatchedData(type: String) {
        val filteredList = ArrayList(fullWatchedList.filter { it.getString("type") == type })
        adapter.updateShowList(filteredList)
    }
}