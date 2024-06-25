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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class PeopleDatabaseHelper extends SQLiteOpenHelper {
    public static final String TABLE_NAME = "people";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_BIRTHDAY = "birthday";
    public static final String COLUMN_DEATHDAY = "deathday";
    public static final String COLUMN_BIOGRAPHY = "biography";
    public static final String COLUMN_PLACE_OF_BIRTH = "place_of_birth";
    public static final String COLUMN_POPULARITY = "popularity";
    public static final String COLUMN_PROFILE_PATH = "profile_path";
    public static final String COLUMN_IMDB_ID = "imdb_id";
    public static final String COLUMN_HOMEPAGE = "homepage";
    public static final String DATABASE_NAME = "people.db";
    public static final int DATABASE_VERSION = 1;
    public static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;
    public static final String DELETE_BY_ID = "DELETE FROM " + TABLE_NAME + " WHERE " + COLUMN_ID + " = ?";
    public static final String DELETE_ALL = "DELETE FROM " + TABLE_NAME;

    public static final String UPDATE = "UPDATE " + TABLE_NAME + " SET " + COLUMN_NAME + " = ?, " +
            COLUMN_BIRTHDAY + " = ?, " +
            COLUMN_DEATHDAY + " = ?, " +
            COLUMN_BIOGRAPHY + " = ?, " +
            COLUMN_PLACE_OF_BIRTH + " = ?, " +
            COLUMN_POPULARITY + " = ?, " +
            COLUMN_PROFILE_PATH + " = ?, " +
            COLUMN_IMDB_ID + " = ?, " +
            COLUMN_HOMEPAGE + " = ? WHERE " + COLUMN_ID + " = ?";

    public static final String SELECT_ALL = "SELECT * FROM " + TABLE_NAME;
    public static final String SELECT_ALL_SORTED_BY_NAME = "SELECT * FROM " + TABLE_NAME + " ORDER BY " + COLUMN_NAME + " ASC";

    public PeopleDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
       String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY, " +
                COLUMN_NAME + " TEXT, " +
                COLUMN_BIRTHDAY + " TEXT, " +
                COLUMN_DEATHDAY + " TEXT, " +
                COLUMN_BIOGRAPHY + " TEXT, " +
                COLUMN_PLACE_OF_BIRTH + " TEXT, " +
                COLUMN_POPULARITY + " REAL, " +
                COLUMN_PROFILE_PATH + " TEXT, " +
                COLUMN_IMDB_ID + " TEXT, " +
                COLUMN_HOMEPAGE + " TEXT);";
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(DROP_TABLE);
        onCreate(db);
    }

    public void deleteAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(DELETE_ALL);
        db.close();
    }

    public void deleteById(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(DELETE_BY_ID, new String[]{String.valueOf(id)});
        db.close();
    }

    public boolean personExists(int actorId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + COLUMN_ID + " = " + actorId, null);
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        return exists;
    }

    public void insert(int id, String name, String birthday, String deathday, String biography, String placeOfBirth, double popularity, String profilePath, String imdbId, String homepage) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, id);
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_BIRTHDAY, birthday);
        values.put(COLUMN_DEATHDAY, deathday);
        values.put(COLUMN_BIOGRAPHY, biography);
        values.put(COLUMN_PLACE_OF_BIRTH, placeOfBirth);
        values.put(COLUMN_POPULARITY, popularity);
        values.put(COLUMN_PROFILE_PATH, profilePath);
        values.put(COLUMN_IMDB_ID, imdbId);
        values.put(COLUMN_HOMEPAGE, homepage);

        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public void update(int id, String name, String birthday, String deathday, String biography, String placeOfBirth, double popularity, String profilePath, String imdbId, String homepage) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL(UPDATE, new String[]{name, birthday, deathday, biography, placeOfBirth, String.valueOf(popularity), profilePath, imdbId, homepage, String.valueOf(id)});
        db.close();
    }

}
