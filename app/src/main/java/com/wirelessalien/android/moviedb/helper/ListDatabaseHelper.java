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

public class ListDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "list_database.db";
    private static final int DATABASE_VERSION = 2;
    public static final String TABLE_LIST_DATA = "list_data";
    public static final String TABLE_LISTS = "lists";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_MOVIE_ID = "movie_id";
    public static final String COLUMN_MEDIA_TYPE = "media_type";
    public static final String COLUMN_LIST_ID = "list_id";
    public static final String COLUMN_LIST_NAME = "list_name";
    public static final String COLUMN_DATE_ADDED = "date_added";
    public static final String COLUMN_IS_ADDED = "is_added";

    public ListDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_LIST_DATA_TABLE = "CREATE TABLE " + TABLE_LIST_DATA + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_MOVIE_ID + " INTEGER,"
                + COLUMN_MEDIA_TYPE + " TEXT,"
                + COLUMN_LIST_ID + " INTEGER,"
                + COLUMN_LIST_NAME + " TEXT,"
                + COLUMN_DATE_ADDED + " TEXT,"
                + COLUMN_IS_ADDED + " INTEGER" + ")";
        db.execSQL(CREATE_LIST_DATA_TABLE);

        String CREATE_LISTS_TABLE = "CREATE TABLE " + TABLE_LISTS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_LIST_ID + " INTEGER,"
                + COLUMN_LIST_NAME + " TEXT" + ")";
        db.execSQL(CREATE_LISTS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LIST_DATA);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LISTS);
        onCreate(db);
    }

    public void addList(int id, String name) {
        SQLiteDatabase db = this.getWritableDatabase();

        String selectQuery = "SELECT * FROM " + TABLE_LISTS + " WHERE " +
                COLUMN_LIST_ID + " = ? AND " + COLUMN_LIST_NAME + " = ?";
        Cursor cursor = db.rawQuery(selectQuery, new String[]{String.valueOf(id), name});

        if (!cursor.moveToFirst()) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_LIST_ID, id);
            values.put(COLUMN_LIST_NAME, name);
            db.insert(TABLE_LISTS, null, values);
        }

        cursor.close();
        db.close();
    }

    public void addListDetails(int listId, String listName, int movieId, String mediaType) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Check if the movie id and list id combination already exists in the database
        String selectQuery = "SELECT * FROM " + TABLE_LIST_DATA + " WHERE " +
                COLUMN_LIST_ID + " = ? AND " + COLUMN_MOVIE_ID + " = ?";
        Cursor cursor = db.rawQuery(selectQuery, new String[]{String.valueOf(listId), String.valueOf(movieId)});

        // If the combination does not exist, add the data to the database
        if (!cursor.moveToFirst()) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_LIST_ID, listId);
            values.put(COLUMN_LIST_NAME, listName);
            values.put(COLUMN_MOVIE_ID, movieId);
            values.put(COLUMN_MEDIA_TYPE, mediaType);

            db.insert(TABLE_LIST_DATA, null, values);
        }

        cursor.close();
        db.close();
    }
    public void deleteData(int movieId, int listId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_LIST_DATA, COLUMN_MOVIE_ID + "=" + movieId + " AND " + COLUMN_LIST_ID + "=" + listId, null);
        db.close();
    }
}
