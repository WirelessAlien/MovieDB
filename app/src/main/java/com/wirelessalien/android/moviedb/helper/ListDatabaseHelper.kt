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

class ListDatabaseHelper(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_LIST_DATA_TABLE = ("CREATE TABLE " + TABLE_LIST_DATA + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_MOVIE_ID + " INTEGER,"
                + COLUMN_MEDIA_TYPE + " TEXT,"
                + COLUMN_LIST_ID + " INTEGER,"
                + COLUMN_LIST_NAME + " TEXT,"
                + COLUMN_DATE_ADDED + " TEXT,"
                + COLUMN_IS_ADDED + " INTEGER" + ")")
        db.execSQL(CREATE_LIST_DATA_TABLE)
        val CREATE_LISTS_TABLE = ("CREATE TABLE " + TABLE_LISTS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_LIST_ID + " INTEGER,"
                + COLUMN_LIST_NAME + " TEXT" + ")")
        db.execSQL(CREATE_LISTS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LIST_DATA")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LISTS")
        onCreate(db)
    }

    fun addList(id: Int, name: String) {
        val db = this.writableDatabase
        val selectQuery = "SELECT * FROM " + TABLE_LISTS + " WHERE " +
                COLUMN_LIST_ID + " = ? AND " + COLUMN_LIST_NAME + " = ?"
        val cursor = db.rawQuery(selectQuery, arrayOf(id.toString(), name))
        if (!cursor.moveToFirst()) {
            val values = ContentValues()
            values.put(COLUMN_LIST_ID, id)
            values.put(COLUMN_LIST_NAME, name)
            db.insert(TABLE_LISTS, null, values)
        }
        cursor.close()
        db.close()
    }

    fun addListDetails(listId: Int, listName: String?, movieId: Int, mediaType: String?) {
        val db = this.writableDatabase

        // Check if the movie id and list id combination already exists in the database
        val selectQuery = "SELECT * FROM " + TABLE_LIST_DATA + " WHERE " +
                COLUMN_LIST_ID + " = ? AND " + COLUMN_MOVIE_ID + " = ?"
        val cursor = db.rawQuery(selectQuery, arrayOf(listId.toString(), movieId.toString()))

        // If the combination does not exist, add the data to the database
        if (!cursor.moveToFirst()) {
            val values = ContentValues()
            values.put(COLUMN_LIST_ID, listId)
            values.put(COLUMN_LIST_NAME, listName)
            values.put(COLUMN_MOVIE_ID, movieId)
            values.put(COLUMN_MEDIA_TYPE, mediaType)
            db.insert(TABLE_LIST_DATA, null, values)
        }
        cursor.close()
        db.close()
    }

    fun deleteData(movieId: Int, listId: Int) {
        val db = this.writableDatabase
        db.delete(
            TABLE_LIST_DATA,
            "$COLUMN_MOVIE_ID=$movieId AND $COLUMN_LIST_ID=$listId",
            null
        )
        db.close()
    }

    fun deleteAllData() {
        val db = this.writableDatabase
        db.delete(TABLE_LIST_DATA, null, null)
        db.delete(TABLE_LISTS, null, null)
        db.close()
    }

    companion object {
        private const val DATABASE_NAME = "list_database.db"
        private const val DATABASE_VERSION = 2
        const val TABLE_LIST_DATA = "list_data"
        const val TABLE_LISTS = "lists"
        const val COLUMN_ID = "_id"
        const val COLUMN_MOVIE_ID = "movie_id"
        const val COLUMN_MEDIA_TYPE = "media_type"
        const val COLUMN_LIST_ID = "list_id"
        const val COLUMN_LIST_NAME = "list_name"
        const val COLUMN_DATE_ADDED = "date_added"
        const val COLUMN_IS_ADDED = "is_added"
    }
}
