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

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ShowTraktAdapter
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CollectionFragmentTkt : BaseFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ShowTraktAdapter
    private val collectionList = ArrayList<JSONObject>()
    private val fullCollectionList = ArrayList<JSONObject>()
    private lateinit var dbHelper: TraktDatabaseHelper
    private lateinit var tmdbHelper: TmdbDetailsDatabaseHelper
    private lateinit var chipGroup: ChipGroup
    private lateinit var progressBar: ProgressBar
    private lateinit var linearLayoutManager: LinearLayoutManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = TraktDatabaseHelper(requireContext())
        tmdbHelper = TmdbDetailsDatabaseHelper(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_progress_tkt, container, false)
        recyclerView = view.findViewById(R.id.showRecyclerView)

        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {

            if (recyclerView.layoutManager !is GridLayoutManager) {
                adapter = ShowTraktAdapter(collectionList, preferences.getBoolean(SHOWS_LIST_PREFERENCE, true))
            }

            val mShowGridView = GridLayoutManager(requireActivity(), preferences.getInt(GRID_SIZE_PREFERENCE, 3))
            recyclerView.layoutManager = mShowGridView
            linearLayoutManager = mShowGridView
        } else {

            if (recyclerView.layoutManager !is LinearLayoutManager) {
                adapter = ShowTraktAdapter(collectionList, preferences.getBoolean(SHOWS_LIST_PREFERENCE, true))
            }
            linearLayoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)
            recyclerView.layoutManager = linearLayoutManager
        }

        recyclerView.adapter = adapter

        chipGroup = view.findViewById(R.id.chipGroup)
        progressBar = view.findViewById(R.id.progressBar)

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds[0]) {
                    R.id.chipMovie -> filterCollectionData("movie")
                    R.id.chipShow -> filterCollectionData("show")
                }
            }
        }

        loadCollectionData()
        return view
    }

    private fun loadCollectionData() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            withContext(Dispatchers.IO) {
                val db = dbHelper.readableDatabase
                val tmdbDb = tmdbHelper.readableDatabase

                val cursor = db.query(
                    TraktDatabaseHelper.TABLE_COLLECTION, null, null, null, null, null, null)

                if (cursor.moveToFirst()) {
                    do {
                        val jsonObject = JSONObject().apply {
                            put("collected_at", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_COLLECTED_AT)))
                            put("updated_at", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_UPDATED_AT)))
                            put("type", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TYPE)))
                            put("title", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TITLE)))
                            put("year", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_YEAR)))
                            put("trakt_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TRAKT_ID)))
                            put("slug", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SLUG)))
                            put("imdb", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_IMDB)))
                            put("tmdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TMDB)))
                            put("tvdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TVDB)))
                            put("season", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SEASON)))
                            put("number", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_NUMBER)))
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
                        }
                        tmdbCursor.close()

                        Log.d("WatchlistFragmentTkt", "Loaded watchlist item: $jsonObject")
                        fullCollectionList.add(jsonObject)
                    } while (cursor.moveToNext())
                }
                cursor.close()
            }
            collectionList.clear()
            collectionList.addAll(fullCollectionList)
            adapter.notifyDataSetChanged()
            progressBar.visibility = View.GONE
        }
    }

    private fun filterCollectionData(type: String) {
        val filteredList = ArrayList(fullCollectionList.filter { it.getString("type") == type })
        collectionList.clear()
        collectionList.addAll(filteredList)
        adapter.notifyDataSetChanged()
    }
}