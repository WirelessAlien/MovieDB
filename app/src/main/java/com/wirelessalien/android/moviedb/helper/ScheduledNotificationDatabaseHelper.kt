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
import com.wirelessalien.android.moviedb.data.ScheduledNotification

class ScheduledNotificationDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "scheduled_notifications.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_SCHEDULED_NOTIFICATIONS = "scheduled_notifications"
        const val COLUMN_ID = "_id"
        const val COLUMN_NOTIFICATION_KEY = "notification_key"
        const val COLUMN_TITLE = "title"
        const val COLUMN_EPISODE_NAME = "episode_name"
        const val COLUMN_EPISODE_NUMBER = "episode_number"
        const val COLUMN_TYPE = "type"
        const val COLUMN_ALARM_TIME = "alarm_time"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_SCHEDULED_NOTIFICATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NOTIFICATION_KEY TEXT UNIQUE,
                $COLUMN_TITLE TEXT,
                $COLUMN_EPISODE_NAME TEXT,
                $COLUMN_EPISODE_NUMBER TEXT,
                $COLUMN_TYPE TEXT,
                $COLUMN_ALARM_TIME INTEGER
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SCHEDULED_NOTIFICATIONS")
        onCreate(db)
    }

    fun addScheduledNotification(notification: ScheduledNotification): Long {
        val values = ContentValues().apply {
            put(COLUMN_NOTIFICATION_KEY, notification.notificationKey)
            put(COLUMN_TITLE, notification.title)
            put(COLUMN_EPISODE_NAME, notification.episodeName)
            put(COLUMN_EPISODE_NUMBER, notification.episodeNumber)
            put(COLUMN_TYPE, notification.type)
            put(COLUMN_ALARM_TIME, notification.alarmTime)
        }
        return writableDatabase.insert(TABLE_SCHEDULED_NOTIFICATIONS, null, values)
    }

    fun deleteScheduledNotification(notificationKey: String) {
        writableDatabase.delete(TABLE_SCHEDULED_NOTIFICATIONS, "$COLUMN_NOTIFICATION_KEY = ?", arrayOf(notificationKey))
    }

    fun getAllScheduledNotifications(): List<ScheduledNotification> {
        val notifications = mutableListOf<ScheduledNotification>()
        val cursor: Cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_SCHEDULED_NOTIFICATIONS", null)
        if (cursor.moveToFirst()) {
            do {
                notifications.add(
                    ScheduledNotification(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        notificationKey = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTIFICATION_KEY)),
                        title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
                        episodeName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EPISODE_NAME)),
                        episodeNumber = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EPISODE_NUMBER)),
                        type = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE)),
                        alarmTime = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ALARM_TIME))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return notifications
    }

    fun hasNotificationBeenScheduled(notificationKey: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE_SCHEDULED_NOTIFICATIONS,
            arrayOf(COLUMN_NOTIFICATION_KEY),
            "$COLUMN_NOTIFICATION_KEY = ?",
            arrayOf(notificationKey),
            null,
            null,
            null
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun deleteAllScheduledNotifications() {
        writableDatabase.delete(TABLE_SCHEDULED_NOTIFICATIONS, null, null)
    }
}