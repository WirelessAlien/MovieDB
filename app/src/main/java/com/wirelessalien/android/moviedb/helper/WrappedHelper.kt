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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class WrappedData(
    val year: Int,
    val totalMovies: Int,
    val totalShows: Int,
    val totalEpisodes: Int,
    val totalTitles: Int,
    val topGenres: List<Pair<String, Int>>,
    val averageRating: Float,
    val topRatedTitleIds: List<Pair<Int, Boolean>>, // TMDB IDs and isMovie for posters
    val lowestRatedTitle: String,
    val firstTitle: String,
    val firstDate: String,
    val lastTitle: String,
    val lastDate: String,
    val peakMonth: String,
    val peakMonthCount: Int,
    val peakDayOfWeek: String,
    val peakDayOfWeekPercentage: Int,
    val topBingeDate: String,
    val topBingeCount: Int,
    val personaBadge: String,
    val personaDescription: String
)

class WrappedHelper(private val context: Context) {
    private val dbHelper = MovieDatabaseHelper(context)
    private val standardFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    fun calculateWrappedData(year: Int): WrappedData? {
        val db = dbHelper.readableDatabase
        val startOfYear = "$year-01-01"
        val endOfYear = "$year-12-31"

        // 1. Movies Watched in Year
        val movieQuery = """
            SELECT ${MovieDatabaseHelper.COLUMN_TITLE}, 
                   ${MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE}, 
                   ${MovieDatabaseHelper.COLUMN_PERSONAL_RATING}, 
                   ${MovieDatabaseHelper.COLUMN_GENRES},
                   ${MovieDatabaseHelper.COLUMN_MOVIES_ID}
            FROM ${MovieDatabaseHelper.TABLE_MOVIES}
            WHERE ${MovieDatabaseHelper.COLUMN_MOVIE} = 1 
              AND ${MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE} >= ? 
              AND ${MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE} <= ?
        """
        
        // 2. Episodes Watched in Year
        val episodeQuery = """
            SELECT m.${MovieDatabaseHelper.COLUMN_TITLE}, 
                   e.${MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE}, 
                   e.${MovieDatabaseHelper.COLUMN_EPISODE_RATING},
                   m.${MovieDatabaseHelper.COLUMN_GENRES},
                   m.${MovieDatabaseHelper.COLUMN_MOVIES_ID}
            FROM ${MovieDatabaseHelper.TABLE_EPISODES} e
            INNER JOIN ${MovieDatabaseHelper.TABLE_MOVIES} m 
              ON e.${MovieDatabaseHelper.COLUMN_MOVIES_ID} = m.${MovieDatabaseHelper.COLUMN_MOVIES_ID}
            WHERE e.${MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE} >= ? 
              AND e.${MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE} <= ?
        """

        val movieCursor = db.rawQuery(movieQuery, arrayOf(startOfYear, endOfYear))
        val episodeCursor = db.rawQuery(episodeQuery, arrayOf(startOfYear, endOfYear))

        val watchedShowIds = mutableSetOf<Int>()
        val totalMovies = movieCursor.count
        val totalEpisodes = episodeCursor.count

        if (totalMovies == 0 && totalEpisodes == 0) {
            movieCursor.close()
            episodeCursor.close()
            return null
        }

        var totalRating = 0f
        var ratedCount = 0
        var lowestRating = Float.MAX_VALUE
        var lowestRatedTitle = "None"

        val genreCounts = mutableMapOf<String, Int>()
        val monthCounts = mutableMapOf<Int, Int>() // 0-11
        val dayOfWeekCounts = mutableMapOf<Int, Int>() // 1-7
        val dateCounts = mutableMapOf<String, Int>() // YYYY-MM-DD -> Count

        data class WatchEvent(val title: String, val date: String, val dateObj: Date, val isMovie: Boolean)
        val watchEvents = mutableListOf<WatchEvent>()
        val allRatedIds = mutableListOf<Triple<Int, Boolean, Float>>() // Triple of MovieId, isMovie, Rating
        val allRatedTitles = mutableListOf<Pair<String, Float>>()

        // Process Movies
        movieCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0) ?: ""
                val dateStr = cursor.getString(1) ?: ""
                val rating = cursor.getFloat(2)
                val genresStr = cursor.getString(3) ?: ""
                val movieId = cursor.getInt(4)

                if (rating > 0) {
                    totalRating += rating
                    ratedCount++
                    if (rating < lowestRating) {
                        lowestRating = rating
                        lowestRatedTitle = title
                    }
                    allRatedIds.add(Triple(movieId, true, rating))
                    allRatedTitles.add(Pair(title, rating))
                }

                genresStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { genre ->
                    genreCounts[genre] = genreCounts.getOrDefault(genre, 0) + 1
                }

                try {
                    val dateObj = standardFormat.parse(dateStr)
                    if (dateObj != null) {
                        watchEvents.add(WatchEvent(title, dateStr, dateObj, true))
                        processDateStats(dateObj, dateStr, monthCounts, dayOfWeekCounts, dateCounts)
                    }
                } catch (e: Exception) {}
            }
        }

        // Process Episodes
        episodeCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0) ?: ""
                val dateStr = cursor.getString(1) ?: ""
                val rating = cursor.getFloat(2)
                val genresStr = cursor.getString(3) ?: ""
                val movieId = cursor.getInt(4) // TV Show ID

                if (rating > 0) {
                    totalRating += rating
                    ratedCount++
                    if (rating < lowestRating) {
                        lowestRating = rating
                        lowestRatedTitle = "$title Episode"
                    }
                    allRatedIds.add(Triple(movieId, false, rating))
                    allRatedTitles.add(Pair(title, rating))
                }

                watchedShowIds.add(movieId)

                genresStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { genre ->
                    genreCounts[genre] = genreCounts.getOrDefault(genre, 0) + 1
                }

                try {
                    val dateObj = standardFormat.parse(dateStr)
                    if (dateObj != null) {
                        watchEvents.add(WatchEvent(title, dateStr, dateObj, false))
                        processDateStats(dateObj, dateStr, monthCounts, dayOfWeekCounts, dateCounts)
                    }
                } catch (e: Exception) {}
            }
        }

        val totalShows = watchedShowIds.size
        val totalTitles = totalMovies + totalShows

        watchEvents.sortBy { it.dateObj }

        val averageRating = if (ratedCount > 0) totalRating / ratedCount else 0f
        
        val topGenres = genreCounts.entries.sortedByDescending { it.value }.take(5).map { Pair<String, Int>(it.key, if (totalMovies + totalEpisodes > 0) Math.round((it.value.toDouble() / (totalMovies + totalEpisodes)) * 100.0).toInt() else 0) }
        
        val topRatedIds = allRatedIds.sortedByDescending { it.third }.map { Pair(it.first, it.second) }.distinct().take(6)
        
        val firstTitle = watchEvents.firstOrNull()?.title ?: ""
        val firstDate = watchEvents.firstOrNull()?.dateObj?.let { displayFormat.format(it) } ?: ""
        
        val lastTitle = watchEvents.lastOrNull()?.title ?: ""
        val lastDate = watchEvents.lastOrNull()?.dateObj?.let { displayFormat.format(it) } ?: ""

        val peakMonthIndex = monthCounts.maxByOrNull { it.value }?.key ?: 0
        val peakMonthCount = monthCounts.maxByOrNull { it.value }?.value ?: 0
        val peakMonthName = getMonthName(peakMonthIndex)

        val peakDayIndex = dayOfWeekCounts.maxByOrNull { it.value }?.key ?: 1
        val peakDayCount = dayOfWeekCounts.maxByOrNull { it.value }?.value ?: 0
        val peakDayName = getDayOfWeekName(peakDayIndex)
        val peakDayOfWeekPercentage = if (totalMovies + totalEpisodes > 0) ((peakDayCount.toFloat() / (totalMovies + totalEpisodes)) * 100).roundToInt() else 0

        val topBingeDateStr = dateCounts.maxByOrNull { it.value }?.key ?: ""
        val topBingeCount = dateCounts.maxByOrNull { it.value }?.value ?: 0
        val topBingeDateDisplay = try {
            val d = standardFormat.parse(topBingeDateStr)
            if (d != null) displayFormat.format(d) else topBingeDateStr
        } catch (e: Exception) {
            topBingeDateStr
        }

        // Determine Persona
        val personaBadge: String
        val personaDescription: String

        if (totalMovies == 0 && totalEpisodes == 0) {
            personaBadge = "The Ghost"
            personaDescription = "You didn't watch anything this year!"
        } else if (averageRating > 0 && averageRating < 3.0f && ratedCount > (totalMovies + totalEpisodes) * 0.2) {
            personaBadge = "The Critic"
            personaDescription = "High percentage of low ratings. Tough crowd!"
        } else if (averageRating >= 4.5f && ratedCount > (totalMovies + totalEpisodes) * 0.2) {
            personaBadge = "The Easy Pleaser"
            personaDescription = "You find the good in everything you watch."
        } else if (topGenres.isNotEmpty() && genreCounts[topGenres.first().first] ?: 0 > (totalMovies + totalEpisodes) * 0.5) {
            personaBadge = "The Genre Loyalist"
            personaDescription = "Over 50% of your watches were ${topGenres.first().first}!"
        } else if (topBingeCount >= 10) {
            personaBadge = "The Marathoner"
            personaDescription = "Unstoppable! You watched $topBingeCount titles in a single day."
        } else {
            personaBadge = "The Explorer"
            personaDescription = "A well-rounded watcher with diverse tastes."
        }

        return WrappedData(
            year = year,
            totalMovies = totalMovies,
            totalShows = totalShows,
            totalEpisodes = totalEpisodes,
            totalTitles = totalTitles,
            topGenres = topGenres,
            averageRating = averageRating,
            topRatedTitleIds = topRatedIds,
            lowestRatedTitle = lowestRatedTitle,
            firstTitle = firstTitle,
            firstDate = firstDate,
            lastTitle = lastTitle,
            lastDate = lastDate,
            peakMonth = peakMonthName,
            peakMonthCount = peakMonthCount,
            peakDayOfWeek = peakDayName,
            peakDayOfWeekPercentage = peakDayOfWeekPercentage,
            topBingeDate = topBingeDateDisplay,
            topBingeCount = topBingeCount,
            personaBadge = personaBadge,
            personaDescription = personaDescription
        )
    }

    private fun processDateStats(
        dateObj: Date,
        dateStr: String,
        monthCounts: MutableMap<Int, Int>,
        dayOfWeekCounts: MutableMap<Int, Int>,
        dateCounts: MutableMap<String, Int>
    ) {
        val calendar = Calendar.getInstance()
        calendar.time = dateObj
        
        val month = calendar.get(Calendar.MONTH)
        monthCounts[month] = monthCounts.getOrDefault(month, 0) + 1
        
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        dayOfWeekCounts[dayOfWeek] = dayOfWeekCounts.getOrDefault(dayOfWeek, 0) + 1
        
        dateCounts[dateStr] = dateCounts.getOrDefault(dateStr, 0) + 1
    }

    private fun getMonthName(month: Int): String {
        return arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")[month]
    }

    private fun getDayOfWeekName(day: Int): String {
        return arrayOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")[day]
    }
    
    fun hasDataForYear(year: Int): Boolean {
        val db = dbHelper.readableDatabase
        val startOfYear = "$year-01-01"
        val endOfYear = "$year-12-31"
        
        val movieQuery = """
            SELECT COUNT(*) FROM ${MovieDatabaseHelper.TABLE_MOVIES}
            WHERE ${MovieDatabaseHelper.COLUMN_MOVIE} = 1 
              AND ${MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE} >= ? 
              AND ${MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE} <= ?
        """
        
        val episodeQuery = """
            SELECT COUNT(*) FROM ${MovieDatabaseHelper.TABLE_EPISODES}
            WHERE ${MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE} >= ? 
              AND ${MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE} <= ?
        """
        
        var count = 0
        db.rawQuery(movieQuery, arrayOf(startOfYear, endOfYear)).use { cursor ->
            if (cursor.moveToFirst()) count += cursor.getInt(0)
        }
        db.rawQuery(episodeQuery, arrayOf(startOfYear, endOfYear)).use { cursor ->
            if (cursor.moveToFirst()) count += cursor.getInt(0)
        }
        return count > 0
    }
}
