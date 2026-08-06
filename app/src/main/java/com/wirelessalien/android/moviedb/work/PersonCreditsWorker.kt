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

package com.wirelessalien.android.moviedb.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wirelessalien.android.moviedb.NotificationReceiver
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.NotificationItem
import com.wirelessalien.android.moviedb.data.ScheduledNotification
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.NotificationDatabaseHelper
import com.wirelessalien.android.moviedb.helper.PeopleDatabaseHelper
import com.wirelessalien.android.moviedb.helper.ScheduledNotificationDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PersonCreditsWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val client = com.wirelessalien.android.moviedb.NetworkClient.client

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val peopleDbHelper = PeopleDatabaseHelper(applicationContext)
            val scheduledDbHelper = ScheduledNotificationDatabaseHelper(applicationContext)
            val notificationDbHelper = NotificationDatabaseHelper(applicationContext)
            val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val cursor = peopleDbHelper.readableDatabase.rawQuery(PeopleDatabaseHelper.SELECT_ALL, null)
            val peopleIds = mutableListOf<Int>()
            val peopleNames = mutableMapOf<Int, String>()
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow(PeopleDatabaseHelper.COLUMN_ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(PeopleDatabaseHelper.COLUMN_NAME))
                    peopleIds.add(id)
                    peopleNames[id] = name
                } while (cursor.moveToNext())
            }
            cursor.close()

            val apiReadAccessToken = ConfigHelper.getConfigValue(applicationContext, "api_read_access_token")

            for (personId in peopleIds) {
                delay(100) // Rate limiting

                val personName = peopleNames[personId] ?: ""
                val url = "https://api.themoviedb.org/3/person/$personId/combined_credits"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer $apiReadAccessToken")
                    .build()

                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val jsonObject = JSONObject(responseBody)
                            processCredits(jsonObject.optJSONArray("cast"), personName, scheduledDbHelper, notificationDbHelper, alarmManager)
                            processCredits(jsonObject.optJSONArray("crew"), personName, scheduledDbHelper, notificationDbHelper, alarmManager)
                        }
                    }
                } catch (e: IOException) {
                    Log.e("PersonCreditsWorker", "Error fetching credits for $personId", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("PersonCreditsWorker", "Error in worker", e)
            Result.failure()
        }
    }

    private fun processCredits(
        creditsArray: org.json.JSONArray?,
        personName: String,
        scheduledDbHelper: ScheduledNotificationDatabaseHelper,
        notificationDbHelper: NotificationDatabaseHelper,
        alarmManager: AlarmManager
    ) {
        if (creditsArray == null) return

        val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = Date()

        for (i in 0 until creditsArray.length()) {
            val credit = creditsArray.optJSONObject(i) ?: continue
            val mediaType = credit.optString("media_type") // movie or tv
            val id = credit.optInt("id")
            val releaseDateStr = if (mediaType == "movie") credit.optString("release_date") else credit.optString("first_air_date")
            val title = if (mediaType == "movie") credit.optString("title") else credit.optString("name")

            if (releaseDateStr.isNullOrEmpty() || releaseDateStr == "null") continue

            try {
                val releaseDate = apiDateFormat.parse(releaseDateStr) ?: continue

                // Check if release date is in the future
                if (releaseDate.after(currentDate)) {
                    val cal1Week = Calendar.getInstance()
                    cal1Week.time = releaseDate
                    cal1Week.add(Calendar.DAY_OF_YEAR, -7)

                    // Schedule 1 week advance
                    if (cal1Week.time.after(currentDate)) {
                        scheduleNotification(
                            mediaType, id, title, personName, releaseDateStr, cal1Week.timeInMillis, 
                            "1week_$id", scheduledDbHelper, notificationDbHelper, alarmManager
                        )
                    }

                    // Schedule day of release
                    if (releaseDate.after(currentDate)) {
                        val calDayOf = Calendar.getInstance()
                        calDayOf.time = releaseDate
                  
                        scheduleNotification(
                            mediaType, id, title, personName, releaseDateStr, calDayOf.timeInMillis, 
                            "dayof_$id", scheduledDbHelper, notificationDbHelper, alarmManager
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("PersonCreditsWorker", "Error parsing date: $releaseDateStr", e)
            }
        }
    }

    private fun scheduleNotification(
        mediaType: String,
        mediaId: Int,
        title: String,
        personName: String,
        dateStr: String,
        alarmTime: Long,
        keyPrefix: String,
        scheduledDbHelper: ScheduledNotificationDatabaseHelper,
        notificationDbHelper: NotificationDatabaseHelper,
        alarmManager: AlarmManager
    ) {
        val type = if (mediaType == "movie") "person_movie" else "person_tv"
        val notificationKey = "person_credit_${keyPrefix}_$dateStr"

        if (scheduledDbHelper.hasNotificationBeenScheduled(notificationKey)) return
        if (alarmTime <= System.currentTimeMillis()) return

        val message = applicationContext.getString(R.string.notification_for_person_credit, personName, title)

        val scheduledNotification = ScheduledNotification(
            id = 0,
            notificationKey = notificationKey,
            title = personName, 
            episodeName = title,
            episodeNumber = mediaId.toString(), // Store media ID here to use it in intent
            type = type,
            alarmTime = alarmTime
        )
        val notificationId = scheduledDbHelper.addScheduledNotification(scheduledNotification)

        val notificationItem = NotificationItem(
            id = 0,
            uniqueId = notificationKey,
            title = personName,
            message = message,
            date = dateStr
        )
        notificationDbHelper.addNotification(notificationItem)

        val intent = Intent(applicationContext, NotificationReceiver::class.java).apply {
            putExtra("title", personName)
            putExtra("episodeName", title)
            putExtra("episodeNumber", mediaId.toString())
            putExtra("notificationKey", notificationKey)
            putExtra("type", type)
            putExtra("notificationId", notificationId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            notificationId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
        }
    }
}
