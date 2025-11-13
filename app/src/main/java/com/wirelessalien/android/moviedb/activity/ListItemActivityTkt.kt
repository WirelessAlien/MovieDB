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

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ShowTraktAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityListItemBinding
import com.wirelessalien.android.moviedb.helper.ThemeHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ListItemActivityTkt : AppCompatActivity() {

    private lateinit var adapter: ShowTraktAdapter
    private val listItemList = ArrayList<JSONObject>()
    private val fullListItemList = ArrayList<JSONObject>()
    private lateinit var binding: ActivityListItemBinding
    private lateinit var dbHelper: TraktDatabaseHelper
    private lateinit var tmdbHelper: TmdbDetailsDatabaseHelper
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var listId: Int? = null
    private var listName: String? = null
    lateinit var preferences: SharedPreferences
    private var isInitialLoad = true


    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyAmoledTheme(this)

        super.onCreate(savedInstanceState)
        binding = ActivityListItemBinding.inflate(layoutInflater)
        setContentView(binding.root)
        isInitialLoad = true

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        listId = intent.getIntExtra("trakt_list_id", 0)
        listName = intent.getStringExtra("trakt_list_name")

        binding.toolbar.title = listName

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        dbHelper = TraktDatabaseHelper(this)
        tmdbHelper = TmdbDetailsDatabaseHelper(this)

        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
            if (binding.recyclerView.layoutManager !is GridLayoutManager) {
                adapter = ShowTraktAdapter(listItemList, preferences.getBoolean(SHOWS_LIST_PREFERENCE, true))
            }

            val mShowGridView = GridLayoutManager(this, preferences.getInt(GRID_SIZE_PREFERENCE, 3))
            binding.recyclerView.layoutManager = mShowGridView
            linearLayoutManager = mShowGridView
        } else {
            if (binding.recyclerView.layoutManager !is LinearLayoutManager) {
                adapter = ShowTraktAdapter(listItemList, preferences.getBoolean(SHOWS_LIST_PREFERENCE, true))
            }
            linearLayoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            binding.recyclerView.layoutManager = linearLayoutManager
        }

        binding.recyclerView.adapter = adapter

        binding.chipGroup.visibility = View.VISIBLE
        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds[0]) {
                    R.id.chipAll -> adapter.updateShowList(fullListItemList)
                    R.id.chipMovie -> filterListItemData("movie")
                    R.id.chipShow -> filterListItemData("show")
                    R.id.chipSeason -> filterListItemData("season")
                    R.id.chipEpisode -> filterListItemData("episode")
                }
            }
        }

        loadListItemData()
    }

    private fun loadListItemData() {
        lifecycleScope.launch {
            binding.shimmerFrameLayout.visibility = View.VISIBLE
            withContext(Dispatchers.IO) {
                val db = dbHelper.readableDatabase
                val tmdbDb = tmdbHelper.readableDatabase

                val cursor = db.query(
                    TraktDatabaseHelper.TABLE_LIST_ITEM,
                    null,
                    "${TraktDatabaseHelper.COL_LIST_ID} = ? AND ${TraktDatabaseHelper.COL_TYPE} != ?",
                    arrayOf(listId.toString(), "person"),
                    null,
                    null,
                    null
                )

                if (cursor.moveToFirst()) {
                    do {
                        val type = cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TYPE))
                        // Skip person type items
                        if (type == "person") continue

                        val jsonObject = JSONObject().apply {
                            put("auto_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_ID)))
                            put("listed_at", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_LISTED_AT)))
                            put("type", type)
                            put("title", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TITLE)))
                            put("year", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_YEAR)))
                            when (type) {
                                "movie", "show" -> {
                                    put("sort_title", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TITLE)))
                                    put("trakt_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TRAKT_ID)))
                                }
                                "episode" -> {
                                    put("sort_title", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TITLE)))
                                    put("episode_trakt_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TRAKT_ID)))
                                    put("trakt_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TRAKT_ID)))
                                }
                                "season" -> {
                                    put("sort_title", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TITLE)))
                                    put("trakt_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TRAKT_ID)))
                                }
                            }
                            put("slug", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SLUG)))
                            put("imdb", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_IMDB)))
                            put("tmdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TMDB)))
                            put("tvdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TVDB)))
                            put("season", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SEASON)))
                            put("number", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_NUMBER)))
                            put("rank", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_RANK)))
                            put("notes", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_NOTES)))
                            put("show_title", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TITLE)))
                            put("show_year", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_YEAR)))
                            put("show_slug", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_SLUG)))
                            put("show_tvdb", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TVDB)))
                            put("show_imdb", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_IMDB)))
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
                            jsonObject.put("show_title", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_NAME)))
                            jsonObject.put("backdrop_path", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_BACKDROP_PATH)))
                            jsonObject.put("poster_path", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_POSTER_PATH)))
                            jsonObject.put("overview", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_SUMMARY)))
                            jsonObject.put("vote_average", tmdbCursor.getDouble(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_VOTE_AVERAGE)))
                            jsonObject.put("release_date", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_RELEASE_DATE)))
                            jsonObject.put("genre_ids", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_GENRE_IDS)))
                            jsonObject.put("seasons_episode_show_tmdb", tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB)))
                        }
                        tmdbCursor.close()
                        fullListItemList.add(jsonObject)
                    } while (cursor.moveToNext())
                }
                cursor.close()
            }
            applySorting()
            if (isInitialLoad) {
                if (preferences.getBoolean(DEFAULT_MEDIA_TYPE, false)) {
                    binding.chipGroup.check(R.id.chipShow)
                } else {
                    binding.chipGroup.check(R.id.chipMovie)
                }
                isInitialLoad = false
            }
            binding.shimmerFrameLayout.visibility = View.GONE
            binding.shimmerFrameLayout.stopShimmer()
        }
    }

    private fun applySorting() {
        val criteria = preferences.getString("tkt_sort_criteria", "name")
        val order = preferences.getString("tkt_sort_order", "asc")

        val comparator = when (criteria) {
            "name" -> compareBy<JSONObject> { it.optString("sort_title", "") }
            "date" -> compareBy { it.optString("listed_at", "") }
            else -> compareBy { it.optString("sort_title", "") }
        }

        if (order == "desc") {
            fullListItemList.sortWith(comparator.reversed())
        } else {
            fullListItemList.sortWith(comparator)
        }

        adapter.updateShowList(fullListItemList)
    }

    private fun filterListItemData(type: String) {
        val filteredList = ArrayList(fullListItemList.filter { it.getString("type") == type })
        adapter.updateShowList(filteredList)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        private const val DEFAULT_MEDIA_TYPE = "key_default_media_type"
        private var SHOWS_LIST_PREFERENCE = "key_show_shows_grid"
        private var GRID_SIZE_PREFERENCE = "key_grid_size_number"
    }
}