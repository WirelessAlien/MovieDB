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

package com.wirelessalien.android.moviedb.helper

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TmdbDetailsDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), AutoCloseable {

    companion object {
        private const val DATABASE_NAME = "tmdbDetails.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_TMDB_DETAILS = "tmdbDetails"
        const val COL_ID = "id"
        const val COL_TMDB_ID = "tmdb_id"
        const val COL_NAME = "name"
        const val COL_BACKDROP_PATH = "backdrop_path"
        const val COL_POSTER_PATH = "poster_path"
        const val COL_SUMMARY = "overview"
        const val COL_VOTE_AVERAGE = "vote_average"
        const val COL_RELEASE_DATE = "release_date"
        const val COL_GENRE_IDS = "genre_ids"
        const val COL_TYPE = "type"
        const val SEASONS_EPISODE_SHOW_TMDB = "seasons_episode_show_tmdb"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_TMDB_DETAILS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TMDB_ID INTEGER,
                $COL_NAME TEXT,
                $COL_BACKDROP_PATH TEXT,
                $COL_POSTER_PATH TEXT,
                $COL_SUMMARY TEXT,
                $COL_VOTE_AVERAGE REAL,
                $COL_RELEASE_DATE TEXT,
                $COL_GENRE_IDS TEXT,
                $COL_TYPE TEXT,
                $SEASONS_EPISODE_SHOW_TMDB TEXT
            )
        """
        db.execSQL(createTable)
    }

    //add movie to database
    fun addItem(tmdbId: Int, name: String, backdropPath: String, posterPath: String, summary: String, voteAverage: Double, releaseDate: String, genreIds: String, seasonEpisode: String, type: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TMDB_ID, tmdbId)
            put(COL_NAME, name)
            put(COL_BACKDROP_PATH, backdropPath)
            put(COL_POSTER_PATH, posterPath)
            put(COL_SUMMARY, summary)
            put(COL_VOTE_AVERAGE, voteAverage)
            put(COL_RELEASE_DATE, releaseDate)
            put(COL_GENRE_IDS, genreIds)
            put(SEASONS_EPISODE_SHOW_TMDB, seasonEpisode)
            put(COL_TYPE, type)
        }
        db.insert(TABLE_TMDB_DETAILS, null, values)
        db.close()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TMDB_DETAILS")
        onCreate(db)
    }

    override fun close() {
        super.close()
    }

    private fun parseSeasonsEpisodeString(seasonsEpisodeStr: String?): Map<Int, List<Int>> {
        if (seasonsEpisodeStr.isNullOrEmpty()) return emptyMap()
        val seasonMap = mutableMapOf<Int, List<Int>>()
        val seasonParts = seasonsEpisodeStr.split("},")
        for (part in seasonParts) {
            val aPart = if (!part.endsWith("}")) "$part}" else part
            val seasonMatch = Regex("(\\d+)\\{([^}]+)\\}").find(aPart)
            if (seasonMatch != null) {
                val seasonNum = seasonMatch.groupValues[1].toIntOrNull()
                val episodesStr = seasonMatch.groupValues[2]
                if (seasonNum != null && seasonNum != 0) { // Typically ignore season 0 (specials)
                    val episodeNumbers = episodesStr.split(",")
                        .mapNotNull { it.toIntOrNull() }
                        .sorted()
                    seasonMap[seasonNum] = episodeNumbers
                }
            }
        }
        return seasonMap
    }

    fun getSeasonsForShow(showId: Int): List<Int> {
        val db = readableDatabase
        var seasonsEpisodeStr: String? = null
        val cursor = db.query(
            TABLE_TMDB_DETAILS,
            arrayOf(SEASONS_EPISODE_SHOW_TMDB),
            "$COL_TMDB_ID = ?",
            arrayOf(showId.toString()),
            null, null, null, "1"
        )
        if (cursor.moveToFirst()) {
            seasonsEpisodeStr = cursor.getString(cursor.getColumnIndexOrThrow(SEASONS_EPISODE_SHOW_TMDB))
        }
        cursor.close()
        return parseSeasonsEpisodeString(seasonsEpisodeStr).keys.sorted()
    }

    fun getEpisodesForSeason(showId: Int, seasonNumber: Int): List<Int> {
        val db = readableDatabase
        var seasonsEpisodeStr: String? = null
        val cursor = db.query(
            TABLE_TMDB_DETAILS,
            arrayOf(SEASONS_EPISODE_SHOW_TMDB),
            "$COL_TMDB_ID = ?",
            arrayOf(showId.toString()),
            null, null, null, "1"
        )
        if (cursor.moveToFirst()) {
            seasonsEpisodeStr = cursor.getString(cursor.getColumnIndexOrThrow(SEASONS_EPISODE_SHOW_TMDB))
        }
        cursor.close()
        return parseSeasonsEpisodeString(seasonsEpisodeStr)[seasonNumber] ?: emptyList()
    }

    fun getTotalEpisodesForShow(showId: Int): Int {
        val db = readableDatabase
        var seasonsEpisodeStr: String? = null
        val cursor = db.query(
            TABLE_TMDB_DETAILS,
            arrayOf(SEASONS_EPISODE_SHOW_TMDB),
            "$COL_TMDB_ID = ?",
            arrayOf(showId.toString()),
            null, null, null, "1"
        )
        if (cursor.moveToFirst()) {
            seasonsEpisodeStr = cursor.getString(cursor.getColumnIndexOrThrow(SEASONS_EPISODE_SHOW_TMDB))
        }
        cursor.close()
        return parseSeasonsEpisodeString(seasonsEpisodeStr).values.sumOf { it.size }
    }
}