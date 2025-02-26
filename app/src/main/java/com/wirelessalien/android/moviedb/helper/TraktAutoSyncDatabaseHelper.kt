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
import android.icu.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TraktAutoSyncDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_SYNCED_ITEMS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MOVIE_ID INTEGER,
                $COL_IS_MOVIE INTEGER,
                $COL_SYNC_DATE TEXT,
                $COL_SYNC_TYPE TEXT,
                UNIQUE($COL_MOVIE_ID, $COL_IS_MOVIE, $COL_SYNC_TYPE)
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SYNCED_ITEMS")
        onCreate(db)
    }

    fun addSyncedItem(movieId: Int, isMovie: Boolean, syncType: String) {
        val values = ContentValues().apply {
            put(COL_MOVIE_ID, movieId)
            put(COL_IS_MOVIE, if (isMovie) 1 else 0)
            put(COL_SYNC_DATE, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(
                Date()
            ))
            put(COL_SYNC_TYPE, syncType)
        }
        writableDatabase.insertWithOnConflict(TABLE_SYNCED_ITEMS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun isItemSynced(movieId: Int, isMovie: Boolean, syncType: String): Boolean {
        return readableDatabase.query(
            TABLE_SYNCED_ITEMS,
            null,
            "$COL_MOVIE_ID = ? AND $COL_IS_MOVIE = ? AND $COL_SYNC_TYPE = ?",
            arrayOf(movieId.toString(), if (isMovie) "1" else "0", syncType),
            null,
            null,
            null
        ).use { cursor ->
            cursor.count > 0
        }
    }

    companion object {
        const val DATABASE_NAME = "trakt_auto_sync.db"
        const val DATABASE_VERSION = 1
        const val TABLE_SYNCED_ITEMS = "synced_items"
        const val COL_ID = "_id"
        const val COL_MOVIE_ID = "movie_id"
        const val COL_IS_MOVIE = "is_movie"
        const val COL_SYNC_DATE = "sync_date"
        const val COL_SYNC_TYPE = "sync_type"
    }
}