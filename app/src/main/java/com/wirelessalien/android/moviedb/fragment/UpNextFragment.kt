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

import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.adapter.UpNextAdapter
import com.wirelessalien.android.moviedb.databinding.FragmentUpNextBinding
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UpNextFragment : BottomSheetDialogFragment() {
    private lateinit var binding: FragmentUpNextBinding
    private lateinit var adapter: UpNextAdapter
    private val upNextList = mutableListOf<UpNextAdapter.UpNextItem>()
    private lateinit var dbHelper: MovieDatabaseHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUpNextBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbHelper = MovieDatabaseHelper(requireContext())

        setupRecyclerView()
        loadUpNextEpisodes()
    }

    private fun setupRecyclerView() {
        adapter = UpNextAdapter(requireContext(), upNextList) { showId, seasonNumber, episodeNumber ->
            markEpisodeAsWatched(showId, seasonNumber, episodeNumber)
        }
        binding.recyclerViewUpNext.adapter = adapter
        binding.recyclerViewUpNext.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadUpNextEpisodes() {
        lifecycleScope.launch(Dispatchers.IO) {
            val watchingShows = getWatchingShows()
            val upNextEpisodes = mutableListOf<UpNextAdapter.UpNextItem>()

            watchingShows.forEach { show ->
                val nextEpisode = getNextEpisode(show)
                if (nextEpisode != null) {
                    upNextEpisodes.add(nextEpisode)
                }
            }

            upNextEpisodes.sortByDescending { it.lastWatchedDate }

            withContext(Dispatchers.Main) {
                if (upNextEpisodes.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerViewUpNext.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerViewUpNext.visibility = View.VISIBLE
                    adapter.updateList(upNextEpisodes)
                }
            }
        }
    }

    private fun getWatchingShows(): List<JSONObject> {
        val shows = mutableListOf<JSONObject>()
        dbHelper.readableDatabase.use { db ->
            val cursor = db.query(
                MovieDatabaseHelper.TABLE_MOVIES,
                arrayOf(
                    MovieDatabaseHelper.COLUMN_MOVIES_ID,
                    MovieDatabaseHelper.COLUMN_TITLE
                ),
                "${MovieDatabaseHelper.COLUMN_CATEGORIES} = ? AND ${MovieDatabaseHelper.COLUMN_MOVIE} = ?",
                arrayOf(MovieDatabaseHelper.CATEGORY_WATCHING.toString(), "0"),
                null, null, null
            )

            if (cursor.moveToFirst()) {
                do {
                    val showId = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIES_ID))
                    val showTitle = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE))

                    val watchedEpisodes = getWatchedEpisodesForShow(db, showId)
                    val lastWatchedDate = getLastWatchedDateForShow(db, showId)

                    val watchedEpisodesAsStringKeys = watchedEpisodes.mapKeys { it.key.toString() }

                    shows.add(JSONObject().apply {
                        put(ShowBaseAdapter.KEY_ID, showId)
                        put(ShowBaseAdapter.KEY_TITLE, showTitle)
                        put("watched_episodes", JSONObject(watchedEpisodesAsStringKeys))
                        put("last_watched_date", lastWatchedDate)
                    })
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
        return shows
    }

    private fun getWatchedEpisodesForShow(db: SQLiteDatabase, showId: Int): Map<Int, List<Int>> {
        val watchedEpisodes = mutableMapOf<Int, MutableList<Int>>()

        val cursor = db.query(
            MovieDatabaseHelper.TABLE_EPISODES,
            arrayOf(
                MovieDatabaseHelper.COLUMN_SEASON_NUMBER,
                MovieDatabaseHelper.COLUMN_EPISODE_NUMBER
            ),
            "${MovieDatabaseHelper.COLUMN_MOVIES_ID} = ?",
            arrayOf(showId.toString()),
            null, null, null
        )

        if (cursor.moveToFirst()) {
            do {
                val season = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_SEASON_NUMBER))
                val episode = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_EPISODE_NUMBER))

                if (!watchedEpisodes.containsKey(season)) {
                    watchedEpisodes[season] = mutableListOf()
                }
                watchedEpisodes[season]?.add(episode)
            } while (cursor.moveToNext())
        }
        cursor.close()

        return watchedEpisodes
    }

    private fun getNextEpisode(show: JSONObject): UpNextAdapter.UpNextItem? {
        val showId = show.optInt(ShowBaseAdapter.KEY_ID)
        val showName = show.optString(ShowBaseAdapter.KEY_TITLE)
        val lastWatchedDate = show.optString("last_watched_date")
        val watchedEpisodes = show.optJSONObject("watched_episodes")?.let {
            parseWatchedEpisodes(it)
        } ?: emptyMap()

        val seasons = getSeasonsFromTmdbDatabase(showId)
        if (seasons.isEmpty()) return null

        for (season in seasons.sorted()) {
            val episodes = getEpisodesForSeasonFromTmdbDatabase(showId, season)
            val watchedInSeason = watchedEpisodes[season] ?: emptyList()

            for (episode in episodes.sorted()) {
                if (!watchedInSeason.contains(episode)) {
                    return UpNextAdapter.UpNextItem(
                        showId,
                        showName,
                        season,
                        episode,
                        "Episode $episode",
                        lastWatchedDate
                    )
                }
            }
        }

        return null
    }

    private fun parseWatchedEpisodes(json: JSONObject): Map<Int, List<Int>> {
        val map = mutableMapOf<Int, List<Int>>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val season = keys.next().toInt()
            val episodesArray = json.getJSONArray(season.toString())
            val episodes = mutableListOf<Int>()
            for (i in 0 until episodesArray.length()) {
                episodes.add(episodesArray.getInt(i))
            }
            map[season] = episodes
        }
        return map
    }

    private fun markEpisodeAsWatched(showId: Int, seasonNumber: Int, episodeNumber: Int) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        lifecycleScope.launch(Dispatchers.IO) {
            dbHelper.addEpisodeNumber(showId, seasonNumber, listOf(episodeNumber), currentDate)

            val nextEpisode = getNextEpisodeAfter(showId, seasonNumber, episodeNumber)

            withContext(Dispatchers.Main) {
                if (nextEpisode != null) {
                    val position = adapter.upNextList.indexOfFirst {
                        it.showId == showId &&
                                it.seasonNumber == seasonNumber &&
                                it.episodeNumber == episodeNumber
                    }

                    if (position != -1) {
                        adapter.replaceItem(position, nextEpisode)
                    }
                } else {
                    val position = adapter.upNextList.indexOfFirst {
                        it.showId == showId &&
                                it.seasonNumber == seasonNumber &&
                                it.episodeNumber == episodeNumber
                    }
                    if (position != -1) {
                        adapter.removeItem(position)
                    }
                }
            }
        }
    }

    private fun getLastWatchedDateForShow(db: SQLiteDatabase, showId: Int): String? {
        var lastWatchedDate: String? = null
        val cursor = db.query(
            MovieDatabaseHelper.TABLE_EPISODES,
            arrayOf(MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE),
            "${MovieDatabaseHelper.COLUMN_MOVIES_ID} = ?",
            arrayOf(showId.toString()),
            null,
            null,
            "${MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE} DESC",
            "1"
        )

        if (cursor.moveToFirst()) {
            lastWatchedDate = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE))
        }
        cursor.close()
        return lastWatchedDate
    }

    private fun getNextEpisodeAfter(showId: Int, seasonNumber: Int, episodeNumber: Int): UpNextAdapter.UpNextItem? {
        val showName = getShowName(showId) ?: return null
        val lastWatchedDate = dbHelper.readableDatabase.use { getLastWatchedDateForShow(it, showId) }

        val episodes = getEpisodesForSeasonFromTmdbDatabase(showId, seasonNumber).sorted()
        val nextEpisodeInSeason = episodes.find { it > episodeNumber }

        if (nextEpisodeInSeason != null) {
            return UpNextAdapter.UpNextItem(
                showId,
                showName,
                seasonNumber,
                nextEpisodeInSeason,
                "Episode $nextEpisodeInSeason",
                lastWatchedDate
            )
        }

        val seasons = getSeasonsFromTmdbDatabase(showId).sorted()
        val nextSeason = seasons.find { it > seasonNumber }

        if (nextSeason != null) {
            val nextEpisodeInNextSeason = getEpisodesForSeasonFromTmdbDatabase(showId, nextSeason).minOrNull()
            if (nextEpisodeInNextSeason != null) {
                return UpNextAdapter.UpNextItem(
                    showId,
                    showName,
                    nextSeason,
                    nextEpisodeInNextSeason,
                    "Episode $nextEpisodeInNextSeason",
                    lastWatchedDate
                )
            }
        }

        return null
    }

    private fun getShowName(showId: Int): String? {
        dbHelper.readableDatabase.use { db ->
            val cursor = db.query(
                MovieDatabaseHelper.TABLE_MOVIES,
                arrayOf(MovieDatabaseHelper.COLUMN_TITLE),
                "${MovieDatabaseHelper.COLUMN_MOVIES_ID} = ?",
                arrayOf(showId.toString()),
                null, null, null
            )

            return if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE))
            } else {
                null
            }.also { cursor.close() }
        }
    }

    private fun getSeasonsFromTmdbDatabase(showId: Int): List<Int> {
        val dbHelper = TmdbDetailsDatabaseHelper(requireContext())
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
            arrayOf(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB),
            "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ?",
            arrayOf(showId.toString()),
            null, null, null
        )
        val seasons = if (cursor.moveToFirst()) {
            parseSeasonsTmdb(cursor.getString(cursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB)))
        } else {
            emptyList()
        }
        cursor.close()
        db.close()
        return seasons
    }

    private fun getEpisodesForSeasonFromTmdbDatabase(showId: Int, seasonNumber: Int): List<Int> {
        val dbHelper = TmdbDetailsDatabaseHelper(requireContext())
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TmdbDetailsDatabaseHelper.TABLE_TMDB_DETAILS,
            arrayOf(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB),
            "${TmdbDetailsDatabaseHelper.COL_TMDB_ID} = ?",
            arrayOf(showId.toString()),
            null, null, null
        )
        val episodes = if (cursor.moveToFirst()) {
            parseEpisodesForSeasonTmdb(cursor.getString(cursor.getColumnIndexOrThrow(TmdbDetailsDatabaseHelper.SEASONS_EPISODE_SHOW_TMDB)), seasonNumber)
        } else {
            emptyList()
        }
        cursor.close()
        db.close()
        return episodes
    }

    private fun parseSeasonsTmdb(seasonsString: String): List<Int> {
        val regex = Regex("""(\d+)\{.*?\}""")
        return regex.findAll(seasonsString).map { it.groupValues[1].toInt() }.toList()
    }

    private fun parseEpisodesForSeasonTmdb(seasonsString: String, seasonNumber: Int): List<Int> {
        val regex = Regex("""$seasonNumber\{(\d+(,\d+)*)\}""")
        val matchResult = regex.find(seasonsString) ?: return emptyList()
        return matchResult.groupValues[1].split(",").map { it.toInt() }
    }
}