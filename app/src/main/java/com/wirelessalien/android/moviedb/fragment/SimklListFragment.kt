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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ShowSimklAdapter
import com.wirelessalien.android.moviedb.databinding.FragmentProgressTktBinding
import com.wirelessalien.android.moviedb.helper.SimklDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SimklListFragment : BaseFragment() {

    private lateinit var adapter: ShowSimklAdapter
    private val itemList = ArrayList<JSONObject>()
    private val fullItemList = ArrayList<JSONObject>()
    private lateinit var dbHelper: SimklDatabaseHelper
    private lateinit var tmdbHelper: TmdbDetailsDatabaseHelper
    private var simklAccessToken: String? = null
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var binding: FragmentProgressTktBinding
    private var isInitialLoad = true
    private var categoryFilter: String = "watching"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        categoryFilter = arguments?.getString(ARG_CATEGORY, "watching") ?: "watching"
        dbHelper = SimklDatabaseHelper(requireContext())
        tmdbHelper = TmdbDetailsDatabaseHelper(requireContext())
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        simklAccessToken = sharedPreferences.getString("simkl_access_token", null)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProgressTktBinding.inflate(inflater, container, false)
        val view = binding.root
        isInitialLoad = true

        val isGrid = preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
        adapter = ShowSimklAdapter(
            itemList,
            isGrid,
            simklAccessToken ?: ""
        )

        linearLayoutManager = if (isGrid) {
            GridLayoutManager(
                requireActivity(),
                preferences.getInt(GRID_SIZE_PREFERENCE, 3).coerceAtLeast(1)
            )
        } else {
            LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)
        }
        binding.showRecyclerView.layoutManager = linearLayoutManager
        binding.showRecyclerView.adapter = adapter

        if (categoryFilter == "watching" || categoryFilter == "hold") {
            // Simkl movies do not support 'watching' or 'hold'
            binding.chipMovie.visibility = View.GONE
        }

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds[0]) {
                    R.id.chipAll -> adapter.updateShowList(fullItemList)
                    R.id.chipMovie -> filterData("movie")
                    R.id.chipShow -> filterData("show")
                }
            }
        }

        loadSimklItems()
        return view
    }

    private fun loadSimklItems() {
        lifecycleScope.launch {
            binding.shimmerFrameLayout1.visibility = View.VISIBLE
            binding.shimmerFrameLayout1.startShimmer()
            fullItemList.clear()

            withContext(Dispatchers.IO) {
                val db = dbHelper.readableDatabase
                val tmdbDb = tmdbHelper.readableDatabase

                val selection: String
                val selectionArgs: Array<String>?
                if (categoryFilter == "rated") {
                    selection = "${SimklDatabaseHelper.COL_USER_RATING} > 0"
                    selectionArgs = null
                } else {
                    selection = "${SimklDatabaseHelper.COL_STATUS} = ?"
                    selectionArgs = arrayOf(categoryFilter)
                }

                val cursor = db.query(
                    SimklDatabaseHelper.TABLE_ITEMS,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
                )

                while (cursor.moveToNext()) {
                    val rawType = cursor.getString(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_TYPE)) ?: "show"
                    val normalizedType = if (rawType == "movie") "movie" else "show"
                    val tmdbId = cursor.getInt(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_TMDB))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_TITLE)) ?: ""

                    val jsonObject = JSONObject().apply {
                        put("auto_id", cursor.getInt(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_ID)).toString())
                        put("simkl_id", cursor.getInt(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_SIMKL_ID)))
                        put("slug", cursor.getString(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_SLUG)))
                        put("tmdb", tmdbId)
                        put("id", tmdbId)
                        put("imdb", cursor.getString(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_IMDB)))
                        put("tvdb", cursor.getInt(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_TVDB)))
                        put("title", title)
                        put("name", title)
                        put("show_title", title)
                        put("sort_title", title)
                        put("year", cursor.getInt(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_YEAR)))
                        put("type", normalizedType)
                        put("simkl_type", rawType)
                        put("status", cursor.getString(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_STATUS)))
                        put("added_at", cursor.getString(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_ADDED_AT)))
                        put("last_watched_at", cursor.getString(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_LAST_WATCHED_AT)))
                        put("user_rating", cursor.getInt(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_USER_RATING)))
                        put("rating", cursor.getInt(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_USER_RATING)))
                        put("rated_at", cursor.getString(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_RATED_AT)))
                        put("watched_episodes_count", cursor.getInt(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_WATCHED_EPISODES_COUNT)))
                        put("total_episodes_count", cursor.getInt(cursor.getColumnIndexOrThrow(SimklDatabaseHelper.COL_TOTAL_EPISODES_COUNT)))
                    }

                    if (tmdbId > 0) {
                        val tmdbCursor = tmdbDb.query(
                            TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
                            null,
                            "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ? AND ${TmdbDetailsDatabaseHelper.COL_TYPE} = ?",
                            arrayOf(tmdbId.toString(), normalizedType),
                            null,
                            null,
                            null
                        )

                        if (tmdbCursor.moveToFirst()) {
                            val tmdbName = tmdbCursor.getString(
                                tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_NAME)
                            )
                            jsonObject.put("id", tmdbCursor.getInt(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_TMDB_ID)))
                            if (!tmdbName.isNullOrEmpty()) {
                                jsonObject.put("name", tmdbName)
                                jsonObject.put("title", tmdbName)
                                jsonObject.put("show_title", tmdbName)
                                jsonObject.put("sort_title", tmdbName)
                            }
                            jsonObject.put(
                                "backdrop_path",
                                tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_BACKDROP_PATH))
                            )
                            jsonObject.put(
                                "poster_path",
                                tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_POSTER_PATH))
                            )
                            jsonObject.put(
                                "overview",
                                tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_SUMMARY))
                            )
                            jsonObject.put(
                                "vote_average",
                                tmdbCursor.getDouble(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_VOTE_AVERAGE))
                            )
                            jsonObject.put(
                                "release_date",
                                tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_RELEASE_DATE))
                            )
                            jsonObject.put(
                                "genre_ids",
                                tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.COL_GENRE_IDS))
                            )
                            jsonObject.put(
                                "seasons_episode_show_tmdb",
                                tmdbCursor.getString(tmdbCursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB))
                            )
                        }
                        tmdbCursor.close()
                    }

                    fullItemList.add(jsonObject)
                }
                cursor.close()
            }

            applySorting()
            if (isInitialLoad) {
                if (categoryFilter == "watching" || categoryFilter == "hold") {
                    binding.chipGroup.check(R.id.chipShow)
                } else {
                    binding.chipGroup.check(R.id.chipAll)
                }
                isInitialLoad = false
            }
            binding.shimmerFrameLayout1.visibility = View.GONE
            binding.shimmerFrameLayout1.stopShimmer()
        }
    }

    private fun applySorting() {
        val criteria = preferences.getString("simkl_sort_criteria", "name")
        val order = preferences.getString("simkl_sort_order", "asc")

        val comparator = when (criteria) {
            "name" -> compareBy<JSONObject> { it.optString("sort_title", "") }
            "date" -> compareBy {
                it.optString("last_watched_at", "").ifEmpty {
                    it.optString("rated_at", "").ifEmpty {
                        it.optString("added_at", "")
                    }
                }
            }
            else -> compareBy { it.optString("sort_title", "") }
        }

        if (order == "desc") {
            fullItemList.sortWith(comparator.reversed())
        } else {
            fullItemList.sortWith(comparator)
        }

        adapter.updateShowList(fullItemList)
    }

    private fun filterData(type: String) {
        val filteredList = ArrayList(fullItemList.filter { it.optString("type") == type })
        adapter.updateShowList(filteredList)
    }

    override fun onResume() {
        super.onResume()
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab?.visibility = View.GONE
    }

    companion object {
        private const val ARG_CATEGORY = "arg_category"

        fun newInstance(category: String): SimklListFragment {
            return SimklListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY, category)
                }
            }
        }
    }
}
