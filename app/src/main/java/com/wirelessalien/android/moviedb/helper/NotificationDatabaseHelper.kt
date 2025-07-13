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
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.wirelessalien.android.moviedb.data.NotificationItem

class NotificationDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "notifications.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NOTIFICATIONS = "notifications"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_UNIQUE_ID = "unique_id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_MESSAGE = "message"
        private const val COLUMN_DATE = "date"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NOTIFICATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_UNIQUE_ID TEXT UNIQUE,
                $COLUMN_TITLE TEXT,
                $COLUMN_MESSAGE TEXT,
                $COLUMN_DATE TEXT
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NOTIFICATIONS")
        onCreate(db)
    }

    fun addNotification(notification: NotificationItem) {
        val cursor = readableDatabase.query(
            TABLE_NOTIFICATIONS,
            arrayOf(COLUMN_UNIQUE_ID),
            "$COLUMN_UNIQUE_ID = ?",
            arrayOf(notification.uniqueId),
            null,
            null,
            null
        )

        val exists = cursor.moveToFirst()
        cursor.close()

        if (!exists) {
            val values = ContentValues().apply {
                put(COLUMN_UNIQUE_ID, notification.uniqueId)
                put(COLUMN_TITLE, notification.title)
                put(COLUMN_MESSAGE, notification.message)
                put(COLUMN_DATE, notification.date)
            }
            writableDatabase.insert(TABLE_NOTIFICATIONS, null, values)
        }
    }

    fun deleteNotification(id: Long) {
        writableDatabase.delete(TABLE_NOTIFICATIONS, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun getAllNotifications(): List<NotificationItem> {
        val notifications = mutableListOf<NotificationItem>()
        val cursor: Cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_NOTIFICATIONS ORDER BY $COLUMN_DATE DESC", null)
        if (cursor.moveToFirst()) {
            do {
                notifications.add(
                    NotificationItem(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        uniqueId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UNIQUE_ID)),
                        title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
                        message = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                        date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return notifications
    }
}
