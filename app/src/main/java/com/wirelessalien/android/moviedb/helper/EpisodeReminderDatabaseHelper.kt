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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class EpisodeReminderDatabaseHelper(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(DATABASE_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Add new columns to existing table
            val alterTableQueries = arrayOf(
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_TYPE TEXT;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_YEAR INTEGER;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_TRAKT_ID INTEGER;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_SLUG TEXT;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_IMDB TEXT;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_TMDB INTEGER;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_SEASON INTEGER;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_EPISODE_TRAKT_ID INTEGER;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_EPISODE_TVDB INTEGER;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_EPISODE_IMDB TEXT;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_EPISODE_TMDB INTEGER;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_SHOW_YEAR INTEGER;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_SHOW_TRAKT_ID INTEGER;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_SHOW_SLUG TEXT;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_SHOW_TVDB INTEGER;",
                "ALTER TABLE $TABLE_EPISODE_REMINDERS ADD COLUMN $COL_SHOW_IMDB TEXT;"
            )

            alterTableQueries.forEach { query ->
                db.execSQL(query)
            }
        }
    }

    fun deleteData(movieId: Int) {
        val db = this.writableDatabase
        db.delete(TABLE_EPISODE_REMINDERS, "$COLUMN_MOVIE_ID=$movieId", null)
        db.close()
    }

    fun getShowNameById(tvShowId: Int): String? {
        val db = this.readableDatabase
        val selection = "$COLUMN_MOVIE_ID = ?"
        val selectionArgs = arrayOf(tvShowId.toString())
        val cursor = db.query(
            TABLE_EPISODE_REMINDERS,
            arrayOf(COLUMN_TV_SHOW_NAME),
            selection,
            selectionArgs,
            null,
            null,
            null
        )
        var showName: String? = null
        if (cursor.moveToFirst()) {
            showName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TV_SHOW_NAME))
        }
        cursor.close()
        return showName
    }

    companion object {
        private const val DATABASE_NAME = "episode_reminder.db"
        private const val DATABASE_VERSION = 2
        const val TABLE_EPISODE_REMINDERS = "episode_reminders"

        // Existing columns
        private const val COLUMN_ID = "_id"
        const val COLUMN_MOVIE_ID = "movie_id"
        const val COLUMN_TV_SHOW_NAME = "tv_show_name"
        const val COLUMN_NAME = "name"
        const val COLUMN_EPISODE_NUMBER = "episode_number"
        const val COLUMN_DATE = "date"

        // New columns
        const val COL_TYPE = "type"
        const val COL_YEAR = "year"
        const val COL_TRAKT_ID = "trakt_id"
        const val COL_SLUG = "slug"
        const val COL_IMDB = "imdb"
        const val COL_TMDB = "tmdb"
        const val COL_SEASON = "season"
        const val COL_EPISODE_TRAKT_ID = "episode_trakt_id"
        const val COL_EPISODE_TVDB = "episode_tvdb"
        const val COL_EPISODE_IMDB = "episode_imdb"
        const val COL_EPISODE_TMDB = "episode_tmdb"
        const val COL_SHOW_YEAR = "show_year"
        const val COL_SHOW_TRAKT_ID = "show_trakt_id"
        const val COL_SHOW_SLUG = "show_slug"
        const val COL_SHOW_TVDB = "show_tvdb"
        const val COL_SHOW_IMDB = "show_imdb"

        // Updated CREATE statement with new columns
        private val DATABASE_CREATE = """
            CREATE TABLE $TABLE_EPISODE_REMINDERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_MOVIE_ID INTEGER NOT NULL,
                $COLUMN_TV_SHOW_NAME TEXT NOT NULL,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_EPISODE_NUMBER TEXT NOT NULL,
                $COLUMN_DATE TEXT NOT NULL,
                $COL_TYPE TEXT,
                $COL_YEAR INTEGER,
                $COL_TRAKT_ID INTEGER,
                $COL_SLUG TEXT,
                $COL_IMDB TEXT,
                $COL_TMDB INTEGER,
                $COL_SEASON INTEGER,
                $COL_EPISODE_TRAKT_ID INTEGER,
                $COL_EPISODE_TVDB INTEGER,
                $COL_EPISODE_IMDB TEXT,
                $COL_EPISODE_TMDB INTEGER,
                $COL_SHOW_YEAR INTEGER,
                $COL_SHOW_TRAKT_ID INTEGER,
                $COL_SHOW_SLUG TEXT,
                $COL_SHOW_TVDB INTEGER,
                $COL_SHOW_IMDB TEXT
            )
        """.trimIndent()
    }
}