/*
 *     This file is part of Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     Movie DB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movie DB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movie DB.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.helper;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class EpisodeReminderDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "episode_reminder.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_EPISODE_REMINDERS = "episode_reminders";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_MOVIE_ID = "movie_id";
    public static final String COLUMN_TV_SHOW_NAME = "tv_show_name";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_EPISODE_NUMBER = "episode_number";
    public static final String COLUMN_DATE = "date";

    private static final String DATABASE_CREATE = "create table "
            + TABLE_EPISODE_REMINDERS + "(" + COLUMN_ID
            + " integer primary key autoincrement, " + COLUMN_MOVIE_ID
            + " integer not null, " + COLUMN_TV_SHOW_NAME
            + " integer not null, " + COLUMN_NAME
            + " text not null, " + COLUMN_EPISODE_NUMBER
            + " text not null, " + COLUMN_DATE
            + " text not null);";

    public EpisodeReminderDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EPISODE_REMINDERS);
        onCreate(db);
    }

    public void deleteData(int movieId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_EPISODE_REMINDERS, COLUMN_MOVIE_ID + "=" + movieId, null);
        db.close();
    }
}
