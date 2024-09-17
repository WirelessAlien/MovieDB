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
package com.wirelessalien.android.moviedb.helper

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class PeopleDatabaseHelper(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY, " +
                COLUMN_NAME + " TEXT, " +
                COLUMN_BIRTHDAY + " TEXT, " +
                COLUMN_DEATHDAY + " TEXT, " +
                COLUMN_BIOGRAPHY + " TEXT, " +
                COLUMN_PLACE_OF_BIRTH + " TEXT, " +
                COLUMN_POPULARITY + " REAL, " +
                COLUMN_PROFILE_PATH + " TEXT, " +
                COLUMN_IMDB_ID + " TEXT, " +
                COLUMN_HOMEPAGE + " TEXT);"
        db.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(DROP_TABLE)
        onCreate(db)
    }

    fun deleteAll() {
        val db = this.writableDatabase
        db.execSQL(DELETE_ALL)
        db.close()
    }

    fun deleteById(id: Int) {
        val db = this.writableDatabase
        db.execSQL(DELETE_BY_ID, arrayOf(id.toString()))
        db.close()
    }

    fun personExists(actorId: Int): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_NAME WHERE $COLUMN_ID = $actorId",
            null
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun insert(id: Int, name: String?, birthday: String?, deathday: String?, biography: String?, placeOfBirth: String?, popularity: Double, profilePath: String?, imdbId: String?, homepage: String?)
    {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_ID, id)
        values.put(COLUMN_NAME, name)
        values.put(COLUMN_BIRTHDAY, birthday)
        values.put(COLUMN_DEATHDAY, deathday)
        values.put(COLUMN_BIOGRAPHY, biography)
        values.put(COLUMN_PLACE_OF_BIRTH, placeOfBirth)
        values.put(COLUMN_POPULARITY, popularity)
        values.put(COLUMN_PROFILE_PATH, profilePath)
        values.put(COLUMN_IMDB_ID, imdbId)
        values.put(COLUMN_HOMEPAGE, homepage)
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Log.d("PeopleDatabaseHelper", "Inserted $values")
        db.close()
    }

    fun update(
        id: Int,
        name: String,
        birthday: String,
        deathday: String,
        biography: String,
        placeOfBirth: String,
        popularity: Double,
        profilePath: String,
        imdbId: String,
        homepage: String
    ) {
        val db = this.writableDatabase
        db.execSQL(
            UPDATE,
            arrayOf(
                name,
                birthday,
                deathday,
                biography,
                placeOfBirth,
                popularity.toString(),
                profilePath,
                imdbId,
                homepage,
                id.toString()
            )
        )
        db.close()
    }

    companion object {
        const val TABLE_NAME = "people"
        const val COLUMN_ID = "_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_BIRTHDAY = "birthday"
        const val COLUMN_DEATHDAY = "deathday"
        const val COLUMN_BIOGRAPHY = "biography"
        const val COLUMN_PLACE_OF_BIRTH = "place_of_birth"
        const val COLUMN_POPULARITY = "popularity"
        const val COLUMN_PROFILE_PATH = "profile_path"
        const val COLUMN_IMDB_ID = "imdb_id"
        const val COLUMN_HOMEPAGE = "homepage"
        const val DATABASE_NAME = "people.db"
        const val DATABASE_VERSION = 2
        const val DROP_TABLE = "DROP TABLE IF EXISTS $TABLE_NAME"
        const val DELETE_BY_ID = "DELETE FROM $TABLE_NAME WHERE $COLUMN_ID = ?"
        const val DELETE_ALL = "DELETE FROM $TABLE_NAME"
        const val UPDATE = "UPDATE " + TABLE_NAME + " SET " + COLUMN_NAME + " = ?, " +
                COLUMN_BIRTHDAY + " = ?, " +
                COLUMN_DEATHDAY + " = ?, " +
                COLUMN_BIOGRAPHY + " = ?, " +
                COLUMN_PLACE_OF_BIRTH + " = ?, " +
                COLUMN_POPULARITY + " = ?, " +
                COLUMN_PROFILE_PATH + " = ?, " +
                COLUMN_IMDB_ID + " = ?, " +
                COLUMN_HOMEPAGE + " = ? WHERE " + COLUMN_ID + " = ?"
        const val SELECT_ALL = "SELECT * FROM $TABLE_NAME"
        const val SELECT_ALL_SORTED_BY_NAME =
            "SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_NAME ASC"
    }
}
