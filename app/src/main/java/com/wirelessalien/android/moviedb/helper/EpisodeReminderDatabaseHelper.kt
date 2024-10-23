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
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EPISODE_REMINDERS")
        onCreate(db)
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
        private const val DATABASE_VERSION = 1
        const val TABLE_EPISODE_REMINDERS = "episode_reminders"
        private const val COLUMN_ID = "_id"
        const val COLUMN_MOVIE_ID = "movie_id"
        const val COLUMN_TV_SHOW_NAME = "tv_show_name"
        const val COLUMN_NAME = "name"
        const val COLUMN_EPISODE_NUMBER = "episode_number"
        const val COLUMN_DATE = "date"
        private const val DATABASE_CREATE = ("create table "
                + TABLE_EPISODE_REMINDERS + "(" + COLUMN_ID
                + " integer primary key autoincrement, " + COLUMN_MOVIE_ID
                + " integer not null, " + COLUMN_TV_SHOW_NAME
                + " integer not null, " + COLUMN_NAME
                + " text not null, " + COLUMN_EPISODE_NUMBER
                + " text not null, " + COLUMN_DATE
                + " text not null);")
    }
}
