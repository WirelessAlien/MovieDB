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
import com.wirelessalien.android.moviedb.data.CollectionDetails

class TraktDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_COLLECTION_TABLE)
        db.execSQL(CREATE_WATCHED_TABLE)
        db.execSQL(CREATE_HISTORY_TABLE)
        db.execSQL(CREATE_RATING_TABLE)
        db.execSQL(CREATE_WATCHLIST_TABLE)
        db.execSQL(CREATE_FAVORITE_TABLE)
        db.execSQL(CREATE_USER_LISTS_TABLE)
        db.execSQL(CREATE_SEASON_EPISODE_TABLE)
        db.execSQL(CREATE_LIST_ITEM_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_COLLECTION")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WATCHED")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RATING")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WATCHLIST")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FAVORITE")
        db.execSQL("DROP TABLE IF EXISTS $USER_LISTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SEASON_EPISODE_WATCHED")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LIST_ITEM")
        onCreate(db)
    }

    fun insertSeasonEpisodeWatchedData(values: ContentValues) {
        val db = writableDatabase
        val showTraktId = values.getAsInteger(COL_SHOW_TRAKT_ID)
        val showTmdbId = values.getAsInteger(COL_SHOW_TMDB_ID)
        val seasonNumber = values.getAsInteger(COL_SEASON_NUMBER)
        val episodeNumber = values.getAsInteger(COL_EPISODE_NUMBER)

        val cursor = db.query(
            TABLE_SEASON_EPISODE_WATCHED,
            arrayOf(COL_ID),
            "$COL_SHOW_TRAKT_ID = ? AND $COL_SHOW_TMDB_ID = ? AND $COL_SEASON_NUMBER = ? AND $COL_EPISODE_NUMBER = ?",
            arrayOf(showTraktId.toString(), showTmdbId.toString(), seasonNumber.toString(), episodeNumber.toString()),
            null,
            null,
            null
        )

        if (cursor.count > 0) {
            // Data exists, update it
            db.update(
                TABLE_SEASON_EPISODE_WATCHED,
                values,
                "$COL_SHOW_TRAKT_ID = ? AND $COL_SHOW_TMDB_ID = ? AND $COL_SEASON_NUMBER = ? AND $COL_EPISODE_NUMBER = ?",
                arrayOf(showTraktId.toString(), showTmdbId.toString(), seasonNumber.toString(), episodeNumber.toString())
            )
        } else {
            // Data does not exist, insert it
            db.insert(TABLE_SEASON_EPISODE_WATCHED, null, values)
        }
        cursor.close()
    }

    fun insertCollectionData(values: ContentValues) {
        val db = writableDatabase
        val traktId = values.getAsInteger(COL_TRAKT_ID)
        val type = values.getAsString(COL_TYPE)

        val cursor = db.query(
            TABLE_COLLECTION,
            arrayOf(COL_ID),
            "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
            arrayOf(traktId.toString(), type),
            null,
            null,
            null
        )

        if (cursor.count > 0) {
            // Data exists, update it
            db.update(
                TABLE_COLLECTION,
                values,
                "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
                arrayOf(traktId.toString(), type)
            )
        } else {
            // Data does not exist, insert it
            db.insert(TABLE_COLLECTION, null, values)
        }
        cursor.close()
    }

    fun insertWatchedData(values: ContentValues) {
        val db = writableDatabase
        val traktId = values.getAsInteger(COL_TRAKT_ID)
        val type = values.getAsString(COL_TYPE)

        val cursor = db.query(
            TABLE_WATCHED,
            arrayOf(COL_ID),
            "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
            arrayOf(traktId.toString(), type),
            null,
            null,
            null
        )

        if (cursor.count > 0) {
            // Data exists, update it
            db.update(
                TABLE_WATCHED,
                values,
                "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
                arrayOf(traktId.toString(), type)
            )
        } else {
            // Data does not exist, insert it
            db.insert(TABLE_WATCHED, null, values)
        }
        cursor.close()
    }

    fun insertWatchedShowData(values: ContentValues) {
        val db = writableDatabase
        val traktId = values.getAsInteger(COL_TRAKT_ID)
        val type = values.getAsString(COL_TYPE)

        val cursor = db.query(
            TABLE_WATCHED,
            arrayOf(COL_ID),
            "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
            arrayOf(traktId.toString(), type),
            null,
            null,
            null
        )

        if (cursor.count > 0) {
            // Data exists, update it
            db.update(
                TABLE_WATCHED,
                values,
                "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
                arrayOf(traktId.toString(), type)
            )
        } else {
            // Data does not exist, insert it
            db.insert(TABLE_WATCHED, null, values)
        }
        cursor.close()
    }

    fun insertHistoryData(values: ContentValues) {
        val db = writableDatabase
        val traktId = values.getAsInteger(COL_TRAKT_ID)
        val type = values.getAsString(COL_TYPE)

        val cursor = db.query(
            TABLE_HISTORY,
            arrayOf(COL_ID),
            "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
            arrayOf(traktId.toString(), type),
            null,
            null,
            null
        )

        if (cursor.count > 0) {
            // Data exists, update it
            db.update(
                TABLE_HISTORY,
                values,
                "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
                arrayOf(traktId.toString(), type)
            )
        } else {
            // Data does not exist, insert it
            db.insert(TABLE_HISTORY, null, values)
        }
        cursor.close()
    }

    fun insertRatingData(values: ContentValues) {
        val db = writableDatabase
        val traktId = values.getAsInteger(COL_TRAKT_ID)
        val type = values.getAsString(COL_TYPE)

        val selection: String
        val selectionArgs: Array<String>

        when (type) {
            "movie" -> {
                selection = "$COL_TRAKT_ID = ? AND $COL_TYPE = ?"
                selectionArgs = arrayOf(traktId.toString(), type)
            }
            "show" -> {
                selection = "$COL_TRAKT_ID = ? AND $COL_TYPE = ?"
                selectionArgs = arrayOf(traktId.toString(), type)
            }
            "season" -> {
                val showTraktId = values.getAsInteger(COL_SHOW_TRAKT_ID)
                val seasonNumber = values.getAsInteger(COL_SEASON)
                selection = "$COL_SHOW_TRAKT_ID = ? AND $COL_SEASON = ? AND $COL_TYPE = ?"
                selectionArgs = arrayOf(showTraktId.toString(), seasonNumber.toString(), type)
            }
            "episode" -> {
                val showTraktId = values.getAsInteger(COL_SHOW_TRAKT_ID)
                val seasonNumber = values.getAsInteger(COL_SEASON)
                val episodeNumber = values.getAsInteger(COL_NUMBER)
                selection = "$COL_SHOW_TRAKT_ID = ? AND $COL_SEASON = ? AND $COL_NUMBER = ? AND $COL_TYPE = ?"
                selectionArgs = arrayOf(showTraktId.toString(), seasonNumber.toString(), episodeNumber.toString(), type)
            }
            else -> {
                selection = "$COL_TRAKT_ID = ? AND $COL_TYPE = ?"
                selectionArgs = arrayOf(traktId.toString(), type)
            }
        }

        val cursor = db.query(
            TABLE_RATING,
            arrayOf(COL_ID),
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        if (cursor.count > 0) {
            // Data exists, update it
            db.update(
                TABLE_RATING,
                values,
                selection,
                selectionArgs
            )
        } else {
            // Data does not exist, insert it
            db.insert(TABLE_RATING, null, values)
        }
        cursor.close()
    }

    fun insertWatchlistData(values: ContentValues) {
        val db = writableDatabase
        val traktId = values.getAsInteger(COL_TRAKT_ID)
        val type = values.getAsString(COL_TYPE)

        val selection: String
        val selectionArgs: Array<String>

        when (type) {
            "movie" -> {
                selection = "$COL_TRAKT_ID = ? AND $COL_TYPE = ?"
                selectionArgs = arrayOf(traktId.toString(), type)
            }
            "show" -> {
                selection = "$COL_TRAKT_ID = ? AND $COL_TYPE = ?"
                selectionArgs = arrayOf(traktId.toString(), type)
            }
            "season" -> {
                val showTraktId = values.getAsInteger(COL_SHOW_TRAKT_ID)
                val seasonNumber = values.getAsInteger(COL_SEASON)
                selection = "$COL_SHOW_TRAKT_ID = ? AND $COL_SEASON = ? AND $COL_TYPE = ?"
                selectionArgs = arrayOf(showTraktId.toString(), seasonNumber.toString(), type)
            }
            "episode" -> {
                val showTraktId = values.getAsInteger(COL_SHOW_TRAKT_ID)
                val seasonNumber = values.getAsInteger(COL_SEASON)
                val episodeNumber = values.getAsInteger(COL_NUMBER)
                selection = "$COL_SHOW_TRAKT_ID = ? AND $COL_SEASON = ? AND $COL_NUMBER = ? AND $COL_TYPE = ?"
                selectionArgs = arrayOf(showTraktId.toString(), seasonNumber.toString(), episodeNumber.toString(), type)
            }
            else -> {
                selection = "$COL_TRAKT_ID = ? AND $COL_TYPE = ?"
                selectionArgs = arrayOf(traktId.toString(), type)
            }
        }

        val cursor = db.query(
            TABLE_WATCHLIST,
            arrayOf(COL_ID),
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        if (cursor.count > 0) {
            // Data exists, update it
            db.update(
                TABLE_WATCHLIST,
                values,
                selection,
                selectionArgs
            )
        } else {
            // Data does not exist, insert it
            db.insert(TABLE_WATCHLIST, null, values)
        }
        cursor.close()
    }

    fun insertFavoriteData(values: ContentValues) {
        val db = writableDatabase
        val traktId = values.getAsInteger(COL_TRAKT_ID)
        val type = values.getAsString(COL_TYPE)

        val cursor = db.query(
            TABLE_FAVORITE,
            arrayOf(COL_ID),
            "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
            arrayOf(traktId.toString(), type),
            null,
            null,
            null
        )

        if (cursor.count > 0) {
            // Data exists, update it
            db.update(
                TABLE_FAVORITE,
                values,
                "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
                arrayOf(traktId.toString(), type)
            )
        } else {
            // Data does not exist, insert it
            db.insert(TABLE_FAVORITE, null, values)
        }
        cursor.close()
    }

    fun insertUserListData(values: ContentValues) {
        val db = writableDatabase
        val traktId = values.getAsInteger(COL_TRAKT_ID)

        val cursor = db.query(
            USER_LISTS,
            arrayOf(COL_ID),
            "$COL_TRAKT_ID = ?",
            arrayOf(traktId.toString()),
            null,
            null,
            null
        )

        if (cursor.count > 0) {
            // Data exists, update it
            db.update(
                USER_LISTS,
                values,
                "$COL_TRAKT_ID = ?",
                arrayOf(traktId.toString())
            )
        } else {
            // Data does not exist, insert it
            db.insert(USER_LISTS, null, values)
        }
        cursor.close()
    }

    fun insertListItemData(values: ContentValues) {
        val db = writableDatabase
        val traktId = values.getAsInteger(COL_TRAKT_ID)
        val type = values.getAsString(COL_TYPE)

        val cursor = db.query(
            TABLE_LIST_ITEM,
            arrayOf(COL_ID),
            "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
            arrayOf(traktId.toString(), type),
            null,
            null,
            null
        )

        if (cursor.count > 0) {
            // Data exists, update it
            db.update(
                TABLE_LIST_ITEM,
                values,
                "$COL_TRAKT_ID = ? AND $COL_TYPE = ?",
                arrayOf(traktId.toString(), type)
            )
        } else {
            // Data does not exist, insert it
            db.insert(TABLE_LIST_ITEM, null, values)
        }
        cursor.close()
    }

    companion object {
        private const val DATABASE_NAME = "trakt.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_COLLECTION = "collection"
        const val TABLE_WATCHED = "watched"
        const val TABLE_HISTORY = "history"
        const val TABLE_RATING = "rating"
        const val TABLE_WATCHLIST = "watchlist"
        const val TABLE_FAVORITE = "favorite"
        const val USER_LISTS = "user_lists"
        const val TABLE_LIST_ITEM = "list_item"


        const val TABLE_SEASON_EPISODE_WATCHED = "season_episode_watched"
        const val COL_SHOW_TMDB_ID = "show_tmdb_id"
        const val COL_SEASON_NUMBER = "season_number"
        const val COL_EPISODE_NUMBER = "episode_number"

        const val COL_ID = "id"
        const val COL_COLLECTED_AT = "collected_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_TYPE = "type"
        const val COL_TITLE = "title"
        const val COL_YEAR = "year"
        const val COL_TRAKT_ID = "trakt_id"
        const val COL_SLUG = "slug"
        const val COL_IMDB = "imdb"
        const val COL_TMDB = "tmdb"
        const val COL_TVDB = "tvdb"
        const val COL_SEASON = "season"
        const val COL_EPISODE = "episode"
        const val COL_PLAYS = "plays"
        const val COL_LAST_WATCHED_AT = "last_watched_at"
        const val COL_LAST_UPDATED_AT = "last_updated_at"
        const val COL_EPISODE_PLAYS = "episode_plays"
        const val COL_EPISODE_LAST_WATCHED_AT = "episode_last_watched_at"
        const val COL_ACTION = "action_"
        const val COL_NUMBER = "number"
        const val COL_WATCHED_AT = "watched_at"
        const val COL_SHOW_TITLE = "show_title"
        const val COL_SHOW_YEAR = "show_year"
        const val COL_SHOW_TRAKT_ID = "show_trakt_id"
        const val COL_SHOW_SLUG = "show_slug"
        const val COL_SHOW_TVDB = "show_tvdb"
        const val COL_SHOW_IMDB = "show_imdb"
        const val COL_SHOW_TMDB = "show_tmdb"
        const val COL_RATED_AT = "rated_at"
        const val COL_RATING = "rating"
        const val COL_RANK = "rank"
        const val COL_LISTED_AT = "listed_at"
        const val COL_LIST_ID = "list_id"
        const val COL_NOTES = "notes"
        const val COL_NAME = "name"
        const val COL_DESCRIPTION = "description"
        const val COL_PRIVACY = "privacy"
        const val COL_SHARE_LINK = "share_link"
        const val COL_DISPLAY_NUMBERS = "display_numbers"
        const val COL_ALLOW_COMMENTS = "allow_comments"
        const val COL_SORT_BY = "sort_by"
        const val COL_SORT_HOW = "sort_how"
        const val COL_CREATED_AT = "created_at"
        const val COL_ITEM_COUNT = "item_count"
        const val COL_COMMENT_COUNT = "comment_count"
        const val COL_LIKES = "likes"
        const val COL_SEASON_EPISODE_SHOW = "season_episode_show"
        const val COL_MEDIA_TYPE = "media_type"
        const val COL_RESOLUTION = "resolution"
        const val COL_HDR = "hdr"
        const val COL_AUDIO = "audio"
        const val COL_AUDIO_CHANNELS = "audio_channels"
        const val COL_THD = "thd"

        private val CREATE_COLLECTION_TABLE = """
        CREATE TABLE $TABLE_COLLECTION (
            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_COLLECTED_AT TEXT,
            $COL_UPDATED_AT TEXT,
            $COL_TYPE TEXT,
            $COL_TITLE TEXT,
            $COL_YEAR INTEGER,
            $COL_TRAKT_ID INTEGER,
            $COL_SLUG TEXT,
            $COL_IMDB TEXT,
            $COL_TMDB INTEGER,
            $COL_TVDB INTEGER,
            $COL_SEASON INTEGER,
            $COL_NUMBER INTEGER,
            $COL_SHOW_TMDB INTEGER,
            $COL_MEDIA_TYPE TEXT,
            $COL_RESOLUTION TEXT,
            $COL_HDR TEXT,
            $COL_AUDIO TEXT,
            $COL_AUDIO_CHANNELS TEXT,
            $COL_THD INTEGER 
       
        )
    """.trimIndent()

        private val CREATE_WATCHED_TABLE = """
        CREATE TABLE $TABLE_WATCHED (
            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_PLAYS INTEGER,
            $COL_LAST_WATCHED_AT TEXT,
            $COL_LAST_UPDATED_AT TEXT,
            $COL_TYPE TEXT,
            $COL_TITLE TEXT,
            $COL_YEAR INTEGER,
            $COL_TRAKT_ID INTEGER,
            $COL_SLUG TEXT,
            $COL_IMDB TEXT,
            $COL_TMDB INTEGER,
            $COL_TVDB INTEGER,
            $COL_SEASON INTEGER,
            $COL_EPISODE INTEGER,
            $COL_EPISODE_PLAYS INTEGER,
            $COL_EPISODE_LAST_WATCHED_AT TEXT,
            $COL_SHOW_TMDB INTEGER,
            $COL_SEASON_EPISODE_SHOW TEXT

        )
    """.trimIndent()

        private val CREATE_HISTORY_TABLE = """
        CREATE TABLE $TABLE_HISTORY (
            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_ACTION TEXT,
            $COL_NUMBER INTEGER,
            $COL_SEASON INTEGER,
            $COL_WATCHED_AT TEXT,
            $COL_IMDB TEXT,
            $COL_TMDB INTEGER,
            $COL_TVDB INTEGER,
            $COL_TYPE TEXT,
            $COL_TITLE TEXT,
            $COL_TRAKT_ID INTEGER,
            $COL_SLUG TEXT,
            $COL_YEAR INTEGER,
            $COL_SHOW_TITLE TEXT,
            $COL_SHOW_YEAR INTEGER,
            $COL_SHOW_TRAKT_ID INTEGER, 
            $COL_SHOW_SLUG TEXT, 
            $COL_SHOW_TVDB INTEGER, 
            $COL_SHOW_IMDB TEXT, 
            $COL_SHOW_TMDB INTEGER
        )
    """.trimIndent()

        private val CREATE_RATING_TABLE = """
        CREATE TABLE $TABLE_RATING (
            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_RATED_AT TEXT,
            $COL_RATING INTEGER,
            $COL_TYPE TEXT,
            $COL_TITLE TEXT,
            $COL_YEAR INTEGER,
            $COL_TRAKT_ID INTEGER,
            $COL_SLUG TEXT,
            $COL_IMDB TEXT,
            $COL_TMDB INTEGER,
            $COL_TVDB INTEGER,
            $COL_SHOW_TITLE TEXT,
            $COL_SHOW_YEAR INTEGER,
            $COL_SHOW_TRAKT_ID INTEGER, 
            $COL_SHOW_SLUG TEXT, 
            $COL_SHOW_TVDB INTEGER, 
            $COL_SHOW_IMDB TEXT, 
            $COL_SHOW_TMDB INTEGER,
            $COL_NUMBER INTEGER,
            $COL_SEASON INTEGER
        )
    """.trimIndent()

        private val CREATE_WATCHLIST_TABLE = """
        CREATE TABLE $TABLE_WATCHLIST (
            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_RANK INTEGER,
            $COL_LISTED_AT TEXT,
            $COL_NOTES TEXT,
            $COL_TYPE TEXT,
            $COL_TITLE TEXT,
            $COL_YEAR INTEGER,
            $COL_TRAKT_ID INTEGER,
            $COL_SLUG TEXT,
            $COL_IMDB TEXT,
            $COL_TMDB INTEGER,
            $COL_TVDB INTEGER,
            $COL_SEASON INTEGER,
            $COL_NUMBER INTEGER,
            $COL_SHOW_TITLE TEXT,
            $COL_SHOW_YEAR INTEGER,
            $COL_SHOW_TRAKT_ID INTEGER, 
            $COL_SHOW_SLUG TEXT, 
            $COL_SHOW_TVDB INTEGER, 
            $COL_SHOW_IMDB TEXT, 
            $COL_SHOW_TMDB INTEGER
        )
    """.trimIndent()

        private val CREATE_FAVORITE_TABLE = """
        CREATE TABLE $TABLE_FAVORITE (
            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_RANK INTEGER,
            $COL_LISTED_AT TEXT,
            $COL_NOTES TEXT,
            $COL_TYPE TEXT,
            $COL_TITLE TEXT,
            $COL_YEAR INTEGER,
            $COL_TRAKT_ID INTEGER,
            $COL_SLUG TEXT,
            $COL_IMDB TEXT,
            $COL_TMDB INTEGER,
            $COL_TVDB INTEGER,
            $COL_SHOW_TMDB INTEGER
        )
    """.trimIndent()

        private val CREATE_USER_LISTS_TABLE = """
        CREATE TABLE $USER_LISTS (
            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_NAME TEXT,
            $COL_DESCRIPTION TEXT,
            $COL_PRIVACY TEXT,
            $COL_SHARE_LINK TEXT,
            $COL_TYPE TEXT,
            $COL_DISPLAY_NUMBERS INTEGER,
            $COL_ALLOW_COMMENTS INTEGER,
            $COL_SORT_BY TEXT,
            $COL_SORT_HOW TEXT,
            $COL_CREATED_AT TEXT,
            $COL_UPDATED_AT TEXT,
            $COL_ITEM_COUNT INTEGER,
            $COL_COMMENT_COUNT INTEGER,
            $COL_LIKES INTEGER,
            $COL_TRAKT_ID INTEGER,
            $COL_SLUG TEXT
        )
    """.trimIndent()

        private val CREATE_LIST_ITEM_TABLE = """
        CREATE TABLE $TABLE_LIST_ITEM (
            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_LIST_ID INTEGER,
            $COL_RANK INTEGER,
            $COL_LISTED_AT TEXT,
            $COL_NOTES TEXT,
            $COL_TYPE TEXT,
            $COL_TITLE TEXT,
            $COL_YEAR INTEGER,
            $COL_TRAKT_ID INTEGER,
            $COL_SLUG TEXT,
            $COL_IMDB TEXT,
            $COL_TMDB INTEGER,
            $COL_TVDB INTEGER,
            $COL_SEASON INTEGER,
            $COL_NUMBER INTEGER,
            $COL_SHOW_TITLE TEXT,
            $COL_SHOW_YEAR INTEGER,
            $COL_SHOW_TRAKT_ID INTEGER,
            $COL_SHOW_SLUG TEXT,
            $COL_SHOW_TVDB INTEGER,
            $COL_SHOW_IMDB TEXT,
            $COL_SHOW_TMDB INTEGER,
            $COL_NAME TEXT
        )
    """.trimIndent()

        private val CREATE_SEASON_EPISODE_TABLE = """
        CREATE TABLE $TABLE_SEASON_EPISODE_WATCHED (
            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_SHOW_TRAKT_ID INTEGER,
            $COL_SHOW_TMDB_ID INTEGER,
            $COL_SEASON_NUMBER INTEGER,
            $COL_EPISODE_NUMBER INTEGER,
            $COL_PLAYS INTEGER,
            $COL_LAST_WATCHED_AT TEXT
        )
    """.trimIndent()
    }


    fun isEpisodeInCollection(tvShowId: Int, seasonNumber: Int, episodeNumber: Int): Boolean {
        val db = readableDatabase
        val query = "SELECT 1 FROM $TABLE_COLLECTION WHERE $COL_TMDB = ? AND $COL_SEASON = ? AND $COL_NUMBER = ?"
        val cursor = db.rawQuery(query, arrayOf(tvShowId.toString(), seasonNumber.toString(), episodeNumber.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun isEpisodeInWatchlist(tvShowId: Int, seasonNumber: Int, episodeNumber: Int): Boolean {
        val db = readableDatabase
        val query = "SELECT 1 FROM $TABLE_WATCHLIST WHERE $COL_SHOW_TMDB = ? AND $COL_SEASON = ? AND $COL_NUMBER = ?"
        val cursor = db.rawQuery(query, arrayOf(tvShowId.toString(), seasonNumber.toString(), episodeNumber.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun isEpisodeInRating(tvShowId: Int, seasonNumber: Int, episodeNumber: Int): Boolean {
        val db = readableDatabase
        val query = "SELECT 1 FROM $TABLE_RATING WHERE $COL_SHOW_TMDB = ? AND $COL_SEASON = ? AND $COL_NUMBER = ?"
        val cursor = db.rawQuery(query, arrayOf(tvShowId.toString(), seasonNumber.toString(), episodeNumber.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    //is movie in collection
    fun isMovieInCollection(movieId: Int): Boolean {
        val db = readableDatabase
        val query = "SELECT 1 FROM $TABLE_COLLECTION WHERE $COL_TMDB = ?"
        val cursor = db.rawQuery(query, arrayOf(movieId.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun isMovieInWatched(movieId: Int): Boolean {
        val db = readableDatabase
        val query = "SELECT 1 FROM $TABLE_WATCHED WHERE $COL_TMDB = ?"
        val cursor = db.rawQuery(query, arrayOf(movieId.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    //is movie in watchlist
    fun isMovieInWatchlist(movieId: Int): Boolean {
        val db = readableDatabase
        val query = "SELECT 1 FROM $TABLE_WATCHLIST WHERE $COL_TMDB = ?"
        val cursor = db.rawQuery(query, arrayOf(movieId.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    //is movie in favorite
    fun isMovieInFavorite(movieId: Int): Boolean {
        val db = readableDatabase
        val query = "SELECT 1 FROM $TABLE_FAVORITE WHERE $COL_TMDB = ?"
        val cursor = db.rawQuery(query, arrayOf(movieId.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun isMovieInRating(movieId: Int): Boolean {
        val db = readableDatabase
        val query = "SELECT 1 FROM $TABLE_RATING WHERE $COL_TMDB = ?"
        val cursor = db.rawQuery(query, arrayOf(movieId.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    //get movie rating
    fun getMovieRating(movieId: Int): Int {
        val db = readableDatabase
        val query = "SELECT $COL_RATING FROM $TABLE_RATING WHERE $COL_TMDB = ?"
        val cursor = db.rawQuery(query, arrayOf(movieId.toString()))
        val rating = if (cursor.moveToFirst()) {
            cursor.getInt(cursor.getColumnIndexOrThrow(COL_RATING))
        } else {
            0
        }
        cursor.close()
        return rating
    }

    fun getEpisodeRating(tvShowId: Int, seasonNumber: Int, episodeNumber: Int): Int {
        val db = readableDatabase
        val query = "SELECT $COL_RATING FROM $TABLE_RATING WHERE $COL_SHOW_TMDB = ? AND $COL_SEASON = ? AND $COL_NUMBER = ?"
        val cursor = db.rawQuery(query, arrayOf(tvShowId.toString(), seasonNumber.toString(), episodeNumber.toString()))
        val rating = if (cursor.moveToFirst()) {
            cursor.getInt(cursor.getColumnIndexOrThrow(COL_RATING))
        } else {
            0
        }
        cursor.close()
        return rating
    }

    fun addMovieToWatchlist(title: String, type: String, tmdbId: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_TYPE, type)
            put(COL_TMDB, tmdbId)
        }
        db.insert(TABLE_WATCHLIST, null, values)
    }

    fun removeMovieFromWatchlist(tmdbId: Int) {
        val db = writableDatabase
        db.delete(TABLE_WATCHLIST, "$COL_TMDB = ?", arrayOf(tmdbId.toString()))
    }

    fun addMovieToFavorites(title: String, type: String, tmdbId: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_TYPE, type)
            put(COL_TMDB, tmdbId)
        }
        db.insert(TABLE_FAVORITE, null, values)
    }

    fun removeMovieFromFavorites(tmdbId: Int) {
        val db = writableDatabase
        db.delete(TABLE_FAVORITE, "$COL_TMDB = ?", arrayOf(tmdbId.toString()))
    }

    fun addMovieToCollection(title: String, type: String, tmdbId: Int, collectedAt: String?, mediaType: String?, resolution: String?, hdr: String?, audio: String?, audioChannels: String?, is3D: Boolean?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_TYPE, type)
            put(COL_TMDB, tmdbId)
            put(COL_COLLECTED_AT, collectedAt)
            put(COL_MEDIA_TYPE, mediaType)
            put(COL_RESOLUTION, resolution)
            put(COL_HDR, hdr)
            put(COL_AUDIO, audio)
            put(COL_AUDIO_CHANNELS, audioChannels)
            put(COL_THD, if (is3D == true) 1 else 0)
        }
        db.insert(TABLE_COLLECTION, null, values)
    }

    fun addMovieToHistory(title: String, type: String, tmdbId: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_TYPE, type)
            put(COL_TMDB, tmdbId)
        }
        db.insert(TABLE_HISTORY, null, values)
    }

    fun addMovieToWatched(title: String, type: String, tmdbId: Int, watchedAt: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_TYPE, type)
            put(COL_TMDB, tmdbId)
            put(COL_LAST_WATCHED_AT, watchedAt)
        }
        db.insert(TABLE_WATCHED, null, values)
    }

    fun addMovieRating(title: String, type: String, tmdbId: Int, rating: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_TYPE, type)
            put(COL_TMDB, tmdbId)
            put(COL_RATING, rating)
        }
        db.insert(TABLE_RATING, null, values)
    }

    fun removeMovieRating(tmdbId: Int) {
        val db = writableDatabase
        db.delete(TABLE_RATING, "$COL_TMDB = ?", arrayOf(tmdbId.toString()))
    }

    fun getTimesPlayed(movieId: Int): Int {
        val db = readableDatabase
        val query = "SELECT $COL_PLAYS FROM $TABLE_WATCHED WHERE $COL_TMDB = ?"
        val cursor = db.rawQuery(query, arrayOf(movieId.toString()))
        val timesPlayed = if (cursor.moveToFirst()) {
            cursor.getInt(cursor.getColumnIndexOrThrow(COL_PLAYS))
        } else {
            0
        }
        cursor.close()
        return timesPlayed
    }

    fun getLastWatched(movieId: Int): String? {
        val db = readableDatabase
        val query = "SELECT $COL_LAST_WATCHED_AT FROM $TABLE_WATCHED WHERE $COL_TMDB = ?"
        val cursor = db.rawQuery(query, arrayOf(movieId.toString()))
        val lastWatched = if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(COL_LAST_WATCHED_AT))
        } else {
            null
        }
        cursor.close()
        return lastWatched
    }

    fun getEpisodeTimesPlayed(tvShowId: Int, seasonNumber: Int, episodeNumber: Int): Int {
        val db = readableDatabase
        val query = "SELECT $COL_PLAYS FROM $TABLE_SEASON_EPISODE_WATCHED WHERE $COL_SHOW_TMDB_ID = ? AND $COL_SEASON_NUMBER = ? AND $COL_EPISODE_NUMBER = ?"
        val cursor = db.rawQuery(query, arrayOf(tvShowId.toString(), seasonNumber.toString(), episodeNumber.toString()))
        val timesPlayed = if (cursor.moveToFirst()) {
            cursor.getInt(cursor.getColumnIndexOrThrow(COL_PLAYS))
        } else {
            0
        }
        cursor.close()
        return timesPlayed
    }

    fun getEpisodeLastWatched(tvShowId: Int, seasonNumber: Int, episodeNumber: Int): String? {
        val db = readableDatabase
        val query = "SELECT $COL_LAST_WATCHED_AT FROM $TABLE_SEASON_EPISODE_WATCHED WHERE $COL_SHOW_TMDB_ID = ? AND $COL_SEASON_NUMBER = ? AND $COL_EPISODE_NUMBER = ?"
        val cursor = db.rawQuery(query, arrayOf(tvShowId.toString(), seasonNumber.toString(), episodeNumber.toString()))
        val lastWatched = if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(COL_LAST_WATCHED_AT))
        } else {
            null
        }
        cursor.close()
        return lastWatched
    }

    fun addEpisodeToWatched(
        title: String,
        showTraktId: Int,
        showTmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_SHOW_TRAKT_ID, showTraktId)
            put(COL_SHOW_TMDB_ID, showTmdbId)
            put(COL_SEASON_NUMBER, seasonNumber)
            put(COL_EPISODE_NUMBER, episodeNumber)
        }
        db.insert(TABLE_SEASON_EPISODE_WATCHED, null, values)
    }

    fun removeEpisodeFromWatched(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int) {
        val db = writableDatabase
        db.delete(TABLE_SEASON_EPISODE_WATCHED, "$COL_SHOW_TMDB_ID = ? AND $COL_SEASON_NUMBER = ? AND $COL_EPISODE_NUMBER = ?", arrayOf(showTmdbId.toString(), seasonNumber.toString(), episodeNumber.toString()))
    }

    fun removeMovieFromHistory(tmdbId: Int) {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, "$COL_TMDB = ?", arrayOf(tmdbId.toString()))
    }

    fun removeMovieFromWatched(tmdbId: Int) {
        val db = writableDatabase
        db.delete(TABLE_WATCHED, "$COL_TMDB = ?", arrayOf(tmdbId.toString()))
    }

    fun removeFromCollection(tmdbId: Int) {
        val db = writableDatabase
        db.delete(TABLE_COLLECTION, "$COL_TMDB = ?", arrayOf(tmdbId.toString()))
    }

    fun addEpisodeToCollection(title: String, showTraktId: Int, showTmdbId: Int, type: String, seasonNumber: Int, episodeNumber: Int, collectedAt: String?, mediaType: String?, resolution: String?, hdr: String?, audio: String?, audioChannels: String?, is3D: Boolean?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_TRAKT_ID, showTraktId)
            put(COL_TMDB, showTmdbId)
            put(COL_TYPE, type)
            put(COL_SEASON, seasonNumber)
            put(COL_NUMBER, episodeNumber)
            put(COL_COLLECTED_AT, collectedAt)
            put(COL_MEDIA_TYPE, mediaType)
            put(COL_RESOLUTION, resolution)
            put(COL_HDR, hdr)
            put(COL_AUDIO, audio)
            put(COL_AUDIO_CHANNELS, audioChannels)
            put(COL_THD, if (is3D == true) 1 else 0)
        }
        db.insert(TABLE_COLLECTION, null, values)
    }

    fun removeEpisodeFromCollection(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int) {
        val db = writableDatabase
        db.delete(TABLE_COLLECTION, "$COL_TMDB = ? AND $COL_SEASON = ? AND $COL_NUMBER = ?", arrayOf(showTmdbId.toString(), seasonNumber.toString(), episodeNumber.toString()))
    }

    fun addEpisodeToHistory(title: String, showTraktId: Int, showTmdbId: Int, type: String, seasonNumber: Int, episodeNumber: Int, watchedAt: String?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SHOW_TITLE, title)
            put(COL_SHOW_TRAKT_ID, showTraktId)
            put(COL_SHOW_TMDB, showTmdbId)
            put(COL_TYPE, type)
            put(COL_SEASON, seasonNumber)
            put(COL_NUMBER, episodeNumber)
            put(COL_WATCHED_AT, watchedAt)
        }
        db.insert(TABLE_HISTORY, null, values)
    }

    fun removeEpisodeFromHistory(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int) {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, "$COL_SHOW_TMDB = ? AND $COL_SEASON = ? AND $COL_NUMBER = ?", arrayOf(showTmdbId.toString(), seasonNumber.toString(), episodeNumber.toString()))
    }

    fun addEpisodeToWatchlist(title: String, showTraktId: Int, showTmdbId: Int, type: String, seasonNumber: Int, episodeNumber: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SHOW_TITLE, title)
            put(COL_SHOW_TRAKT_ID, showTraktId)
            put(COL_SHOW_TMDB, showTmdbId)
            put(COL_TYPE, type)
            put(COL_SEASON, seasonNumber)
            put(COL_NUMBER, episodeNumber)
        }
        db.insert(TABLE_WATCHLIST, null, values)
    }

    fun removeEpisodeFromWatchlist(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int) {
        val db = writableDatabase
        db.delete(TABLE_WATCHLIST, "$COL_SHOW_TMDB = ? AND $COL_SEASON = ? AND $COL_NUMBER = ?", arrayOf(showTmdbId.toString(), seasonNumber.toString(), episodeNumber.toString()))
    }

    fun addEpisodeRating(title: String, showTraktId: Int, showTmdbId: Int, type: String, seasonNumber: Int, episodeNumber: Int, rating: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SHOW_TITLE, title)
            put(COL_SHOW_TRAKT_ID, showTraktId)
            put(COL_SHOW_TMDB, showTmdbId)
            put(COL_TYPE, type)
            put(COL_SEASON, seasonNumber)
            put(COL_NUMBER, episodeNumber)
            put(COL_RATING, rating)
        }
        db.insert(TABLE_RATING, null, values)
    }

    fun removeEpisodeRating(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int) {
        val db = writableDatabase
        db.delete(TABLE_RATING, "$COL_SHOW_TMDB = ? AND $COL_SEASON = ? AND $COL_NUMBER = ?", arrayOf(showTmdbId.toString(), seasonNumber.toString(), episodeNumber.toString()))
    }

    fun getEpisodeCollectionDetails(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int): CollectionDetails? {
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_COLLECTION WHERE $COL_TMDB = ? AND $COL_SEASON = ? AND $COL_NUMBER = ?"
        val cursor = db.rawQuery(query, arrayOf(showTmdbId.toString(), seasonNumber.toString(), episodeNumber.toString()))
        val collectionDetails = if (cursor.moveToFirst()) {
            CollectionDetails(
                collectedAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_COLLECTED_AT)),
                mediaType = cursor.getString(cursor.getColumnIndexOrThrow(COL_MEDIA_TYPE)),
                resolution = cursor.getString(cursor.getColumnIndexOrThrow(COL_RESOLUTION)),
                hdr = cursor.getString(cursor.getColumnIndexOrThrow(COL_HDR)),
                audio = cursor.getString(cursor.getColumnIndexOrThrow(COL_AUDIO)),
                audioChannels = cursor.getString(cursor.getColumnIndexOrThrow(COL_AUDIO_CHANNELS)),
                thd = cursor.getInt(cursor.getColumnIndexOrThrow(COL_THD))
            )
        } else {
            null
        }
        cursor.close()
        return collectionDetails
    }

    fun getMovieCollectionDetails(tmdbId: Int): CollectionDetails? {
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_COLLECTION WHERE $COL_TMDB = ?"
        val cursor = db.rawQuery(query, arrayOf(tmdbId.toString()))
        val collectionDetails = if (cursor.moveToFirst()) {
            CollectionDetails(
                collectedAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_COLLECTED_AT)),
                mediaType = cursor.getString(cursor.getColumnIndexOrThrow(COL_MEDIA_TYPE)),
                resolution = cursor.getString(cursor.getColumnIndexOrThrow(COL_RESOLUTION)),
                hdr = cursor.getString(cursor.getColumnIndexOrThrow(COL_HDR)),
                audio = cursor.getString(cursor.getColumnIndexOrThrow(COL_AUDIO)),
                audioChannels = cursor.getString(cursor.getColumnIndexOrThrow(COL_AUDIO_CHANNELS)),
                thd = cursor.getInt(cursor.getColumnIndexOrThrow(COL_THD))
            )
        } else {
            null
        }
        cursor.close()
        return collectionDetails
    }
}