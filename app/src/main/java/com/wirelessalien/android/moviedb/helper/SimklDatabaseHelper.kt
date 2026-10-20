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

class SimklDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), AutoCloseable {

    data class SyncActivities(
        val allAt: String?,
        val tvShowsAll: String?,
        val animeAll: String?,
        val moviesAll: String?,
        val ratingsAt: String?,
        val moviesRemovedAt: String?,
        val tvShowsRemovedAt: String?,
        val animeRemovedAt: String?,
        val lastCheckedMs: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_SYNC_ACTIVITIES_TABLE)
        db.execSQL(CREATE_ITEMS_TABLE)
        db.execSQL(CREATE_SEASON_EPISODE_WATCHED_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SYNC_ACTIVITIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ITEMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SEASON_EPISODE_WATCHED")
        onCreate(db)
    }

    fun getSyncActivities(): SyncActivities? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SYNC_ACTIVITIES,
            null,
            "$COL_ID = 1",
            null,
            null,
            null,
            null
        )
        val result = if (cursor.moveToFirst()) {
            val moviesRemIdx = cursor.getColumnIndex(COL_MOVIES_REMOVED_AT)
            val showsRemIdx = cursor.getColumnIndex(COL_TV_SHOWS_REMOVED_AT)
            val animeRemIdx = cursor.getColumnIndex(COL_ANIME_REMOVED_AT)
            SyncActivities(
                allAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_ALL_AT)),
                tvShowsAll = cursor.getString(cursor.getColumnIndexOrThrow(COL_TV_SHOWS_ALL)),
                animeAll = cursor.getString(cursor.getColumnIndexOrThrow(COL_ANIME_ALL)),
                moviesAll = cursor.getString(cursor.getColumnIndexOrThrow(COL_MOVIES_ALL)),
                ratingsAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_RATINGS_AT)),
                moviesRemovedAt = if (moviesRemIdx >= 0) cursor.getString(moviesRemIdx) else null,
                tvShowsRemovedAt = if (showsRemIdx >= 0) cursor.getString(showsRemIdx) else null,
                animeRemovedAt = if (animeRemIdx >= 0) cursor.getString(animeRemIdx) else null,
                lastCheckedMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_CHECKED_MS))
            )
        } else {
            null
        }
        cursor.close()
        return result
    }

    fun saveSyncActivities(
        allAt: String?,
        tvShowsAll: String?,
        animeAll: String?,
        moviesAll: String?,
        ratingsAt: String?,
        moviesRemovedAt: String? = null,
        tvShowsRemovedAt: String? = null,
        animeRemovedAt: String? = null,
        lastCheckedMs: Long = System.currentTimeMillis()
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ID, 1)
            put(COL_ALL_AT, allAt)
            put(COL_TV_SHOWS_ALL, tvShowsAll)
            put(COL_ANIME_ALL, animeAll)
            put(COL_MOVIES_ALL, moviesAll)
            put(COL_RATINGS_AT, ratingsAt)
            put(COL_MOVIES_REMOVED_AT, moviesRemovedAt)
            put(COL_TV_SHOWS_REMOVED_AT, tvShowsRemovedAt)
            put(COL_ANIME_REMOVED_AT, animeRemovedAt)
            put(COL_LAST_CHECKED_MS, lastCheckedMs)
        }
        db.insertWithOnConflict(TABLE_SYNC_ACTIVITIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun typeSelectionCondition(isMovie: Boolean): String {
        return if (isMovie) {
            "$COL_TYPE = 'movie'"
        } else {
            "($COL_TYPE = 'show' OR $COL_TYPE = 'anime')"
        }
    }

    fun upsertItem(values: ContentValues) {
        val db = writableDatabase
        val simklId = values.getAsInteger(COL_SIMKL_ID) ?: 0
        val tmdbId = values.getAsInteger(COL_TMDB) ?: 0
        val type = values.getAsString(COL_TYPE) ?: ""

        var existingId: Long? = null
        var existingRating: Int? = null
        var existingRatedAt: String? = null
        var existingStatus: String? = null

        if (simklId > 0 && type.isNotEmpty()) {
            db.query(
                TABLE_ITEMS,
                arrayOf(COL_ID, COL_USER_RATING, COL_RATED_AT, COL_STATUS),
                "$COL_SIMKL_ID = ? AND $COL_TYPE = ?",
                arrayOf(simklId.toString(), type),
                null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    existingId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID))
                    existingRating = cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_RATING))
                    existingRatedAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_RATED_AT))
                    existingStatus = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS))
                }
            }
        }

        if (existingId == null && tmdbId > 0) {
            val typeFilter = if (type == "movie") {
                "$COL_TMDB = ? AND $COL_TYPE = 'movie'"
            } else {
                "$COL_TMDB = ? AND ($COL_TYPE = 'show' OR $COL_TYPE = 'anime')"
            }
            db.query(
                TABLE_ITEMS,
                arrayOf(COL_ID, COL_USER_RATING, COL_RATED_AT, COL_STATUS),
                typeFilter,
                arrayOf(tmdbId.toString()),
                null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    existingId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID))
                    existingRating = cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_RATING))
                    existingRatedAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_RATED_AT))
                    existingStatus = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS))
                }
            }
        }

        if (existingId != null) {
            if (!values.containsKey(COL_USER_RATING) || (values.getAsInteger(COL_USER_RATING) ?: 0) == 0) {
                if ((existingRating ?: 0) > 0) {
                    values.put(COL_USER_RATING, existingRating)
                    if (!values.containsKey(COL_RATED_AT) && existingRatedAt != null) {
                        values.put(COL_RATED_AT, existingRatedAt)
                    }
                }
            }
            if (!values.containsKey(COL_STATUS) && existingStatus != null) {
                values.put(COL_STATUS, existingStatus)
            }
            db.update(TABLE_ITEMS, values, "$COL_ID = ?", arrayOf(existingId.toString()))
        } else {
            db.insertWithOnConflict(TABLE_ITEMS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun reconcileTypeSimklIds(itemType: String, remoteSimklIds: Set<Int>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val toDelete = mutableListOf<Pair<Long, Int>>()
            db.query(
                TABLE_ITEMS,
                arrayOf(COL_ID, COL_SIMKL_ID, COL_TMDB),
                "$COL_TYPE = ?",
                arrayOf(itemType),
                null, null, null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val rowId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID))
                    val simklId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SIMKL_ID))
                    val tmdbId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TMDB))
                    if (simklId > 0 && simklId !in remoteSimklIds) {
                        toDelete.add(rowId to tmdbId)
                    }
                }
            }
            toDelete.forEach { (rowId, tmdbId) ->
                db.delete(TABLE_ITEMS, "$COL_ID = ?", arrayOf(rowId.toString()))
                if (itemType != "movie" && tmdbId > 0) {
                    db.delete(TABLE_SEASON_EPISODE_WATCHED, "$COL_SHOW_TMDB_ID = ?", arrayOf(tmdbId.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearWatchedEpisodesForShow(showTmdbId: Int) {
        if (showTmdbId <= 0) return
        val db = writableDatabase
        db.delete(TABLE_SEASON_EPISODE_WATCHED, "$COL_SHOW_TMDB_ID = ?", arrayOf(showTmdbId.toString()))
    }

    fun getItemStatus(tmdbId: Int, isMovie: Boolean): String? {
        val db = readableDatabase
        val typeCondition = typeSelectionCondition(isMovie)
        val cursor = db.query(
            TABLE_ITEMS,
            arrayOf(COL_STATUS),
            "$COL_TMDB = ? AND $typeCondition",
            arrayOf(tmdbId.toString()),
            null, null, null
        )
        val status = if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS))
        } else {
            null
        }
        cursor.close()
        return status
    }

    fun getItemStatus(tmdbId: Int): String? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ITEMS,
            arrayOf(COL_STATUS),
            "$COL_TMDB = ?",
            arrayOf(tmdbId.toString()),
            null, null, null
        )
        val status = if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS))
        } else {
            null
        }
        cursor.close()
        return status
    }

    fun isItemInWatchlist(tmdbId: Int, isMovie: Boolean? = null): Boolean {
        val status = if (isMovie != null) getItemStatus(tmdbId, isMovie) else getItemStatus(tmdbId)
        return status == "plantowatch"
    }

    fun isItemWatched(tmdbId: Int, isMovie: Boolean? = null): Boolean {
        val status = if (isMovie != null) getItemStatus(tmdbId, isMovie) else getItemStatus(tmdbId)
        return status == "completed"
    }

    fun isItemInSimkl(tmdbId: Int, isMovie: Boolean? = null): Boolean {
        val status = if (isMovie != null) getItemStatus(tmdbId, isMovie) else getItemStatus(tmdbId)
        return !status.isNullOrEmpty()
    }

    fun getItemRating(tmdbId: Int, isMovie: Boolean? = null): Int {
        val db = readableDatabase
        val selection = if (isMovie != null) {
            "$COL_TMDB = ? AND ${typeSelectionCondition(isMovie)}"
        } else {
            "$COL_TMDB = ?"
        }
        val cursor = db.query(
            TABLE_ITEMS,
            arrayOf(COL_USER_RATING),
            selection,
            arrayOf(tmdbId.toString()),
            null, null, null
        )
        val rating = if (cursor.moveToFirst()) {
            cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_RATING))
        } else {
            0
        }
        cursor.close()
        return rating
    }

    fun getLastWatched(tmdbId: Int, isMovie: Boolean? = null): String? {
        val db = readableDatabase
        val selection = if (isMovie != null) {
            "$COL_TMDB = ? AND ${typeSelectionCondition(isMovie)}"
        } else {
            "$COL_TMDB = ?"
        }
        val cursor = db.query(
            TABLE_ITEMS,
            arrayOf(COL_LAST_WATCHED_AT),
            selection,
            arrayOf(tmdbId.toString()),
            null, null, null
        )
        val lastWatched = if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(COL_LAST_WATCHED_AT))
        } else {
            null
        }
        cursor.close()
        return lastWatched
    }

    fun updateItemStatus(
        tmdbId: Int,
        type: String,
        title: String,
        status: String,
        timestamp: String?
    ) {
        val normalizedType = if (type == "movie") "movie" else "show"
        val values = ContentValues().apply {
            put(COL_TMDB, tmdbId)
            put(COL_TYPE, normalizedType)
            put(COL_TITLE, title)
            put(COL_STATUS, status)
            if (status == "completed") {
                put(COL_LAST_WATCHED_AT, timestamp)
            } else {
                put(COL_ADDED_AT, timestamp)
            }
        }
        upsertItem(values)
    }

    fun removeItem(tmdbId: Int, isMovie: Boolean? = null) {
        val db = writableDatabase
        val selection = if (isMovie != null) {
            "$COL_TMDB = ? AND ${typeSelectionCondition(isMovie)}"
        } else {
            "$COL_TMDB = ?"
        }
        db.delete(TABLE_ITEMS, selection, arrayOf(tmdbId.toString()))
        if (isMovie != true) {
            db.delete(TABLE_SEASON_EPISODE_WATCHED, "$COL_SHOW_TMDB_ID = ?", arrayOf(tmdbId.toString()))
        }
    }

    fun upsertItemRating(
        tmdbId: Int,
        type: String,
        title: String,
        rating: Int,
        ratedAt: String?
    ) {
        val normalizedType = if (type == "movie") "movie" else "show"
        val values = ContentValues().apply {
            put(COL_TMDB, tmdbId)
            put(COL_TYPE, normalizedType)
            if (title.isNotEmpty()) {
                put(COL_TITLE, title)
            }
            put(COL_USER_RATING, rating)
            put(COL_RATED_AT, ratedAt)
        }
        upsertItem(values)
    }

    fun removeItemRating(tmdbId: Int, isMovie: Boolean? = null) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_USER_RATING, 0)
            putNull(COL_RATED_AT)
        }
        val selection = if (isMovie != null) {
            "$COL_TMDB = ? AND ${typeSelectionCondition(isMovie)}"
        } else {
            "$COL_TMDB = ?"
        }
        db.update(TABLE_ITEMS, values, selection, arrayOf(tmdbId.toString()))
    }

    fun insertSeasonEpisodeWatchedData(values: ContentValues) {
        val db = writableDatabase
        db.insertWithOnConflict(
            TABLE_SEASON_EPISODE_WATCHED,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun addEpisodeToWatched(
        showSimklId: Int,
        showTmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        watchedAt: String?
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SHOW_SIMKL_ID, showSimklId)
            put(COL_SHOW_TMDB_ID, showTmdbId)
            put(COL_SEASON_NUMBER, seasonNumber)
            put(COL_EPISODE_NUMBER, episodeNumber)
            put(COL_WATCHED_AT, watchedAt)
        }
        db.insertWithOnConflict(
            TABLE_SEASON_EPISODE_WATCHED,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        // Ensure show exists in TABLE_ITEMS with at least 'watching' status if not already present
        val currentStatus = getItemStatus(showTmdbId, false)
        if (currentStatus.isNullOrEmpty()) {
            val itemValues = ContentValues().apply {
                if (showSimklId > 0) put(COL_SIMKL_ID, showSimklId)
                put(COL_TMDB, showTmdbId)
                put(COL_TYPE, "show")
                put(COL_STATUS, "watching")
                put(COL_LAST_WATCHED_AT, watchedAt)
            }
            upsertItem(itemValues)
        } else {
            val updateValues = ContentValues().apply {
                put(COL_LAST_WATCHED_AT, watchedAt)
            }
            db.update(
                TABLE_ITEMS,
                updateValues,
                "$COL_TMDB = ? AND ($COL_TYPE = 'show' OR $COL_TYPE = 'anime')",
                arrayOf(showTmdbId.toString())
            )
        }
    }

    fun removeEpisodeFromWatched(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int) {
        val db = writableDatabase
        db.delete(
            TABLE_SEASON_EPISODE_WATCHED,
            "$COL_SHOW_TMDB_ID = ? AND $COL_SEASON_NUMBER = ? AND $COL_EPISODE_NUMBER = ?",
            arrayOf(showTmdbId.toString(), seasonNumber.toString(), episodeNumber.toString())
        )
    }

    fun removeSeasonFromWatched(showTmdbId: Int, seasonNumber: Int) {
        val db = writableDatabase
        db.delete(
            TABLE_SEASON_EPISODE_WATCHED,
            "$COL_SHOW_TMDB_ID = ? AND $COL_SEASON_NUMBER = ?",
            arrayOf(showTmdbId.toString(), seasonNumber.toString())
        )
    }

    fun getWatchedEpisodesForSeason(showTmdbId: Int, seasonNumber: Int): List<Int> {
        val db = readableDatabase
        val episodes = mutableListOf<Int>()
        val cursor = db.query(
            TABLE_SEASON_EPISODE_WATCHED,
            arrayOf(COL_EPISODE_NUMBER),
            "$COL_SHOW_TMDB_ID = ? AND $COL_SEASON_NUMBER = ?",
            arrayOf(showTmdbId.toString(), seasonNumber.toString()),
            null, null, "$COL_EPISODE_NUMBER ASC"
        )
        while (cursor.moveToNext()) {
            episodes.add(cursor.getInt(cursor.getColumnIndexOrThrow(COL_EPISODE_NUMBER)))
        }
        cursor.close()
        return episodes
    }

    fun isEpisodeWatched(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM $TABLE_SEASON_EPISODE_WATCHED WHERE $COL_SHOW_TMDB_ID = ? AND $COL_SEASON_NUMBER = ? AND $COL_EPISODE_NUMBER = ?",
            arrayOf(showTmdbId.toString(), seasonNumber.toString(), episodeNumber.toString())
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun isSeasonInWatched(showTmdbId: Int?, seasonNumber: Int): Boolean {
        if (showTmdbId == null) return false
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM $TABLE_SEASON_EPISODE_WATCHED WHERE $COL_SHOW_TMDB_ID = ? AND $COL_SEASON_NUMBER = ?",
            arrayOf(showTmdbId.toString(), seasonNumber.toString())
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun getEpisodeLastWatched(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int): String? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_WATCHED_AT FROM $TABLE_SEASON_EPISODE_WATCHED WHERE $COL_SHOW_TMDB_ID = ? AND $COL_SEASON_NUMBER = ? AND $COL_EPISODE_NUMBER = ?",
            arrayOf(showTmdbId.toString(), seasonNumber.toString(), episodeNumber.toString())
        )
        val watchedAt = if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(COL_WATCHED_AT))
        } else {
            null
        }
        cursor.close()
        return watchedAt
    }

    override fun close() {
        super.close()
    }

    companion object {
        const val DATABASE_NAME = "simkl.db"
        const val DATABASE_VERSION = 2

        const val TABLE_SYNC_ACTIVITIES = "sync_activities"
        const val COL_ID = "id"
        const val COL_ALL_AT = "all_at"
        const val COL_TV_SHOWS_ALL = "tv_shows_all"
        const val COL_ANIME_ALL = "anime_all"
        const val COL_MOVIES_ALL = "movies_all"
        const val COL_RATINGS_AT = "ratings_at"
        const val COL_MOVIES_REMOVED_AT = "movies_removed_at"
        const val COL_TV_SHOWS_REMOVED_AT = "tv_shows_removed_at"
        const val COL_ANIME_REMOVED_AT = "anime_removed_at"
        const val COL_LAST_CHECKED_MS = "last_checked_ms"

        const val TABLE_ITEMS = "simkl_items"
        const val COL_SIMKL_ID = "simkl_id"
        const val COL_SLUG = "slug"
        const val COL_TMDB = "tmdb"
        const val COL_IMDB = "imdb"
        const val COL_TVDB = "tvdb"
        const val COL_TITLE = "title"
        const val COL_YEAR = "year"
        const val COL_TYPE = "type"
        const val COL_STATUS = "status"
        const val COL_ADDED_AT = "added_at"
        const val COL_LAST_WATCHED_AT = "last_watched_at"
        const val COL_USER_RATING = "user_rating"
        const val COL_RATED_AT = "rated_at"
        const val COL_WATCHED_EPISODES_COUNT = "watched_episodes_count"
        const val COL_TOTAL_EPISODES_COUNT = "total_episodes_count"
        const val COL_SEASON_EPISODE_SHOW = "season_episode_show"

        const val TABLE_SEASON_EPISODE_WATCHED = "season_episode_watched"
        const val COL_SHOW_SIMKL_ID = "show_simkl_id"
        const val COL_SHOW_TMDB_ID = "show_tmdb_id"
        const val COL_SEASON_NUMBER = "season_number"
        const val COL_EPISODE_NUMBER = "episode_number"
        const val COL_WATCHED_AT = "watched_at"

        private val CREATE_SYNC_ACTIVITIES_TABLE = """
            CREATE TABLE $TABLE_SYNC_ACTIVITIES (
                $COL_ID INTEGER PRIMARY KEY DEFAULT 1,
                $COL_ALL_AT TEXT,
                $COL_TV_SHOWS_ALL TEXT,
                $COL_ANIME_ALL TEXT,
                $COL_MOVIES_ALL TEXT,
                $COL_RATINGS_AT TEXT,
                $COL_MOVIES_REMOVED_AT TEXT,
                $COL_TV_SHOWS_REMOVED_AT TEXT,
                $COL_ANIME_REMOVED_AT TEXT,
                $COL_LAST_CHECKED_MS INTEGER
            )
        """.trimIndent()

        private val CREATE_ITEMS_TABLE = """
            CREATE TABLE $TABLE_ITEMS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SIMKL_ID INTEGER,
                $COL_SLUG TEXT,
                $COL_TMDB INTEGER,
                $COL_IMDB TEXT,
                $COL_TVDB INTEGER,
                $COL_TITLE TEXT,
                $COL_YEAR INTEGER,
                $COL_TYPE TEXT,
                $COL_STATUS TEXT,
                $COL_ADDED_AT TEXT,
                $COL_LAST_WATCHED_AT TEXT,
                $COL_USER_RATING INTEGER DEFAULT 0,
                $COL_RATED_AT TEXT,
                $COL_WATCHED_EPISODES_COUNT INTEGER DEFAULT 0,
                $COL_TOTAL_EPISODES_COUNT INTEGER DEFAULT 0,
                $COL_SEASON_EPISODE_SHOW TEXT,
                UNIQUE($COL_SIMKL_ID, $COL_TYPE)
            )
        """.trimIndent()

        private val CREATE_SEASON_EPISODE_WATCHED_TABLE = """
            CREATE TABLE $TABLE_SEASON_EPISODE_WATCHED (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SHOW_SIMKL_ID INTEGER,
                $COL_SHOW_TMDB_ID INTEGER,
                $COL_SEASON_NUMBER INTEGER,
                $COL_EPISODE_NUMBER INTEGER,
                $COL_WATCHED_AT TEXT,
                UNIQUE($COL_SHOW_TMDB_ID, $COL_SEASON_NUMBER, $COL_EPISODE_NUMBER)
            )
        """.trimIndent()
    }
}
