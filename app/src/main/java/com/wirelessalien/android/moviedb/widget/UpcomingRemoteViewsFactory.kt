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

package com.wirelessalien.android.moviedb.widget

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.fragment.ListFragment
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class UpcomingRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var upcomingItems = mutableListOf<JSONObject>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val todayCalendar = Calendar.getInstance()
    private val tomorrowCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    private lateinit var preferences: SharedPreferences
    private lateinit var movieDbHelper: MovieDatabaseHelper
    private lateinit var epDbHelper: EpisodeReminderDatabaseHelper
    private lateinit var traktDbHelper: TraktDatabaseHelper

    companion object {
        const val SYNC_PROVIDER_PREF_KEY = "sync_provider"
        const val SYNC_PROVIDER_LOCAL = "local"
        const val SYNC_PROVIDER_TRAKT = "trakt"
        const val SYNC_PROVIDER_TMDB = "tmdb"
        private const val TAG = "UpcomingWidgetFactory"
    }

    override fun onCreate() {
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        movieDbHelper = MovieDatabaseHelper(context)
        epDbHelper = EpisodeReminderDatabaseHelper(context)
        traktDbHelper = TraktDatabaseHelper(context)
        Log.d(TAG, "UpcomingRemoteViewsFactory created.")
    }

    override fun onDataSetChanged() {
        val syncProvider = preferences.getString(SYNC_PROVIDER_PREF_KEY, SYNC_PROVIDER_LOCAL)
        Log.d(TAG, "onDataSetChanged - Sync Provider: $syncProvider")
        upcomingItems.clear()

        when (syncProvider) {
            SYNC_PROVIDER_TRAKT -> loadUpcomingFromTraktCalendarDB()
            SYNC_PROVIDER_LOCAL, SYNC_PROVIDER_TMDB -> loadUpcomingFromReminderDB()
            else -> loadUpcomingFromReminderDB()
        }

        upcomingItems.sortWith(compareBy {
            val dateStr = it.optString(ListFragment.UPCOMING_DATE)
            if (dateStr.isNotEmpty()) {
                try {
                    dateFormat.parse(dateStr)?.time ?: Long.MAX_VALUE
                } catch (e: Exception) {
                    Long.MAX_VALUE
                }
            } else {
                Long.MAX_VALUE
            }
        })
        Log.d(TAG, "Data set changed, ${upcomingItems.size} items loaded and sorted.")
    }

    override fun onDestroy() {
        upcomingItems.clear()
    }

    override fun getCount(): Int {
        return upcomingItems.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        val item = upcomingItems.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_item_upcoming)

        val views = RemoteViews(context.packageName, R.layout.widget_item_upcoming)
        val title = item.optString("title", item.optString("name", context.getString(R.string.unknown_title)))
        val releaseDateString = item.optString(ListFragment.UPCOMING_DATE)
        val releaseTime = item.optString(ListFragment.UPCOMING_TIME)
        val isMovie = item.optInt(ListFragment.IS_MOVIE, 1) == 1

        views.setTextViewText(R.id.widget_item_title, title)
        views.setTextViewText(R.id.widget_item_release_date, formatReleaseDate(releaseDateString))
        views.setTextViewText(R.id.widget_item_release_time, formatReleaseTime(releaseTime))

        if (!isMovie) {
            val seasonEpisodeInfo = item.optString("seasonEpisode", "")
            views.setTextViewText(R.id.widget_item_season_episode, seasonEpisodeInfo)
            views.setViewVisibility(R.id.widget_item_season_episode, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_item_season_episode, View.GONE)
        }

        return views
    }

    override fun getLoadingView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_item_upcoming).apply {
            setTextViewText(R.id.widget_item_title, context.getString(R.string.loading_data))
        }
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return upcomingItems.getOrNull(position)?.optLong("id", position.toLong()) ?: position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    private fun loadUpcomingFromReminderDB() {
        Log.d(TAG, "Loading upcoming data from local EpisodeReminderDatabaseHelper...")
        val localItems = mutableListOf<JSONObject>()
        val epDb = epDbHelper.readableDatabase
        val movieDb = movieDbHelper.readableDatabase

        try {
            val reminderProjection = arrayOf(
                EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID,
                EpisodeReminderDatabaseHelper.COLUMN_DATE,
                EpisodeReminderDatabaseHelper.COL_TYPE,
                EpisodeReminderDatabaseHelper.COL_SEASON,
                EpisodeReminderDatabaseHelper.COLUMN_NAME, // Episode Name or Movie Title
                EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER
            )
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            val todayStr = sdf.format(Date())

            epDb.query(
                EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS,
                reminderProjection,
                "${EpisodeReminderDatabaseHelper.COLUMN_DATE} >= ?",
                arrayOf(todayStr),
                null, null,
                "${EpisodeReminderDatabaseHelper.COLUMN_DATE} ASC"
            ).use { epCursor ->
                while (epCursor.moveToNext()) {
                    val tmdbId = epCursor.getInt(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID))
                    val movieProjection = arrayOf(
                        MovieDatabaseHelper.COLUMN_MOVIES_ID,
                        MovieDatabaseHelper.COLUMN_TITLE,
                        MovieDatabaseHelper.COLUMN_MOVIE
                    )
                    movieDb.query(
                        MovieDatabaseHelper.TABLE_MOVIES,
                        movieProjection,
                        "${MovieDatabaseHelper.COLUMN_MOVIES_ID} = ?",
                        arrayOf(tmdbId.toString()),
                        null, null, null
                    ).use { movieCursor ->
                        if (movieCursor.moveToFirst()) {
                            createUpcomingItemDetails(movieCursor, epCursor)?.let { localItems.add(it) }
                        } else {
                            createMinimalUpcomingItem(epCursor)?.let { localItems.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading data from Reminder DB", e)
        }
        upcomingItems.addAll(localItems)
        Log.d(TAG, "Found ${localItems.size} upcoming items from Reminder DB.")
    }

    private fun loadUpcomingFromTraktCalendarDB() {
        Log.d(TAG, "Loading upcoming data from Trakt Calendar DB...")
        val traktItems = mutableListOf<JSONObject>()
        val db = traktDbHelper.readableDatabase
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayStr = sdf.format(Date())
            db.query(
                TraktDatabaseHelper.TABLE_CALENDER,
                null,
                "${TraktDatabaseHelper.COL_AIR_DATE} >= ?",
                arrayOf(todayStr),
                null, null,
                "${TraktDatabaseHelper.COL_AIR_DATE} ASC"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    createUpcomingItemFromTraktCalendarCursor(cursor)?.let { traktItems.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading data from Trakt Calendar DB", e)
        }
        upcomingItems.addAll(traktItems)
        Log.d(TAG, "Found ${traktItems.size} upcoming items from Trakt Calendar DB.")
    }

    private fun createUpcomingItemFromTraktCalendarCursor(cursor: Cursor): JSONObject? {
        try {
            val jsonObject = JSONObject()
            val type = cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TYPE))
            val isMovie = type == "movie"

            val tmdbId = if (isMovie) cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TMDB)) else cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TMDB))
            if (tmdbId == 0) {
                Log.w(TAG, "Skipping Trakt calendar item with TMDB ID 0.")
                return null
            }
            jsonObject.put("id", tmdbId)

            val airDate = cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_AIR_DATE))
            jsonObject.put(ListFragment.UPCOMING_DATE, airDate.substringBefore("T"))
            jsonObject.put(ListFragment.UPCOMING_TIME, airDate)

            val title: String?
            val seasonEpisodeInfo: String?
            if (isMovie) {
                title = cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TITLE))
                jsonObject.put("title", title)
                jsonObject.put(ListFragment.IS_MOVIE, 1) // Movie
            } else {
                val showTitle = cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SHOW_TITLE))
                val episodeTitle = cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_EPISODE_TITLE))
                val seasonNum = cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_SEASON))
                val episodeNum = cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_NUMBER))

                title = showTitle
                seasonEpisodeInfo = "S${String.format("%02d", seasonNum)}E$episodeNum: $episodeTitle"

                jsonObject.put("title", title)
                jsonObject.put("seasonEpisode", seasonEpisodeInfo)
                jsonObject.put(ListFragment.IS_MOVIE, 0) // TV Show
            }

            if (title.isNullOrEmpty()) {
                Log.w(TAG, "Title from Trakt Calendar DB is null or empty for item ID $tmdbId. Skipping.")
                return null
            }
            return jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Error creating item from Trakt Calendar cursor", e)
            return null
        }
    }

    private fun createMinimalUpcomingItem(epCursor: Cursor): JSONObject? {
        try {
            val jsonObject = JSONObject()
            val tmdbId = epCursor.getInt(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID))
            jsonObject.put("id", tmdbId)

            val type = epCursor.getString(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COL_TYPE))
            val reminderName = epCursor.getString(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_NAME)) // Episode name or original title from reminder

            val title: String?
            val seasonEpisodeInfo: String?
            if (ListFragment.EPISODE == type) {
                val showTitle = epCursor.getString(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME)) ?: reminderName // Fallback to reminderName if showTitle is null
                val seasonNum = epCursor.getInt(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COL_SEASON))
                val episodeNum = epCursor.getString(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER)) ?: "0"
                val episodeName = if (!reminderName.isNullOrEmpty()) ": $reminderName" else ""

                title = showTitle
                seasonEpisodeInfo = "S${String.format("%02d", seasonNum)}E$episodeNum$episodeName"

                jsonObject.put("title", title)
                jsonObject.put("seasonEpisode", seasonEpisodeInfo)
                jsonObject.put(ListFragment.IS_MOVIE, 0)
            } else { // Movie
                title = reminderName

                jsonObject.put("title", title)
                jsonObject.put(ListFragment.IS_MOVIE, 1)
            }

            if (title.isNullOrEmpty() || title == context.getString(R.string.unknown_title)) {
                Log.w(TAG, "Minimal item title is null, empty or unknown for reminder ID $tmdbId. Skipping.")
                return null
            }

            jsonObject.put(ListFragment.UPCOMING_DATE, epCursor.getString(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_DATE)))
            jsonObject.put(ListFragment.UPCOMING_TIME, epCursor.getString(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_DATE)))
            return jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Error creating minimal upcoming item from reminder DB", e)
            return null
        }
    }


    private fun createUpcomingItemDetails(movieCursor: Cursor, epCursor: Cursor): JSONObject? {
        try {
            val itemTitleFromDb = movieCursor.getString(movieCursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_TITLE))
            if (itemTitleFromDb.isNullOrEmpty()) {
                Log.w(TAG, "Movie title from DB is null or empty, skipping item.")
                return null
            }

            val jsonObject = JSONObject()
            val tmdbId = movieCursor.getInt(movieCursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIES_ID))
            jsonObject.put("id", tmdbId)
            val isMovie = movieCursor.getInt(movieCursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE)) == 1
            jsonObject.put(ListFragment.IS_MOVIE, if (isMovie) 1 else 0)
            val releaseDate = epCursor.getString(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_DATE))
            jsonObject.put(ListFragment.UPCOMING_DATE, releaseDate)
            jsonObject.put(ListFragment.UPCOMING_TIME, releaseDate)

            var finalTitle = itemTitleFromDb
            if (!isMovie) {
                val episodeName = epCursor.getString(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_NAME))
                val seasonNum = epCursor.getInt(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COL_SEASON))
                val episodeNum = epCursor.getString(epCursor.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER))
                // Only append episode details if episodeName is not empty, otherwise show name is enough
                if (!episodeName.isNullOrEmpty()) {
                    finalTitle = "$itemTitleFromDb - S${String.format("%02d", seasonNum)}E$episodeNum: $episodeName"
                }
                jsonObject.put("name", finalTitle)
            } else {
                jsonObject.put("title", finalTitle)
            }
            // Ensure there's a valid title field
            if (finalTitle.isEmpty() || finalTitle == context.getString(R.string.unknown_title)) {
                Log.w(TAG, "Final title is null, empty, or unknown for item ID $tmdbId. Skipping.")
                return null
            }
            return jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Error creating upcoming item details from DB cursors", e)
            return null
        }
    }

    private fun formatReleaseDate(dateString: String?): String {
        if (dateString.isNullOrEmpty()) {
            return context.getString(R.string.upcoming)
        }
        return try {
            val releaseDate = dateFormat.parse(dateString) ?: return context.getString(R.string.upcoming)
            val releaseCalendar = Calendar.getInstance().apply { time = releaseDate }

            when {
                isSameDay(releaseCalendar, todayCalendar) -> context.getString(R.string.today)
                isSameDay(releaseCalendar, tomorrowCalendar) -> context.getString(R.string.tomorrow)
                else -> {
                    try {
                        val releaseDateF = dateFormat.parse(dateString)
                        val defaultDateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT)
                        defaultDateFormat.format(releaseDateF?: "")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error formatting date: $dateString", e)
                        dateString
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting date: $dateString", e)
            context.getString(R.string.upcoming)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun formatReleaseTime(timeString: String?): String {
        if (timeString.isNullOrEmpty()) {
            return ""
        }
        return try {
            val fullDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC") // Parse as UTC
            }
            val date = fullDateFormat.parse(timeString)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault() // Format in local time zone
            }
            timeFormat.format(date?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting time: $timeString", e)
            ""
        }
    }
}
