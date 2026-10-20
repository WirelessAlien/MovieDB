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

package com.wirelessalien.android.moviedb.simkl

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.wirelessalien.android.moviedb.helper.SimklDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GetSimklSyncData(
    private val context: Context,
    private val accessToken: String?,
    private val clientId: String?
) {

    suspend fun fetchData(forceFullSync: Boolean = false) {
        if (accessToken.isNullOrEmpty()) {
            Log.w("GetSimklSyncData", "Skipping Simkl sync: access token is null or empty.")
            return
        }

        withContext(Dispatchers.IO) {
            val simklSync = SimklSync(accessToken, context)
            val dbHelper = SimklDatabaseHelper(context)

            try {
                // Step 1: Mandatory Two-Phase Incremental Sync Gate (GET /sync/activities)
                val activitiesBody = fetchEndpointBody(simklSync, "sync/activities") ?: return@withContext
                val activitiesJson = JSONObject(activitiesBody)

                val remoteAll = activitiesJson.optString("all", "").takeIf { it.isNotEmpty() }
                val tvShowsObj = activitiesJson.optJSONObject("tv_shows")
                val animeObj = activitiesJson.optJSONObject("anime")
                val moviesObj = activitiesJson.optJSONObject("movies")
                val settingsObj = activitiesJson.optJSONObject("settings")

                val remoteShowsAll = tvShowsObj?.optString("all", "")?.takeIf { it.isNotEmpty() }
                val remoteAnimeAll = animeObj?.optString("all", "")?.takeIf { it.isNotEmpty() }
                val remoteMoviesAll = moviesObj?.optString("all", "")?.takeIf { it.isNotEmpty() }
                val remoteRatingsAt = listOfNotNull(
                    tvShowsObj?.optString("rated_at", "")?.takeIf { it.isNotEmpty() },
                    animeObj?.optString("rated_at", "")?.takeIf { it.isNotEmpty() },
                    moviesObj?.optString("rated_at", "")?.takeIf { it.isNotEmpty() },
                    settingsObj?.optString("all", "")?.takeIf { it.isNotEmpty() }
                ).maxOrNull() ?: remoteAll

                // Step 2: Read stored timestamps from SimklDatabaseHelper.TABLE_SYNC_ACTIVITIES
                val savedActivities = dbHelper.getSyncActivities()
                val savedAll = savedActivities?.allAt

                // Step 3: Initial Sync (savedAll == null || forceFullSync)
                // Execute sequentially (never in parallel) using SimklSync's 10 req/sec RateLimiter
                if (savedAll == null || forceFullSync) {
                    Log.d("GetSimklSyncData", "Running Initial Full Sync sequentially...")

                    fetchEndpointBody(simklSync, "sync/all-items/movies")?.let { body ->
                        parseAndSaveAllItems(dbHelper, body, defaultType = "movie")
                    }

                    fetchEndpointBody(simklSync, "sync/all-items/shows?episode_watched_at=yes")?.let { body ->
                        parseAndSaveAllItems(dbHelper, body, defaultType = "show")
                    }

                    fetchEndpointBody(simklSync, "sync/all-items/anime?episode_watched_at=yes")?.let { body ->
                        parseAndSaveAllItems(dbHelper, body, defaultType = "anime")
                    }

                    fetchEndpointBody(simklSync, "sync/ratings/movies")?.let { body ->
                        parseAndSaveRatings(dbHelper, body, defaultType = "movie")
                    }

                    fetchEndpointBody(simklSync, "sync/ratings/shows")?.let { body ->
                        parseAndSaveRatings(dbHelper, body, defaultType = "show")
                    }

                    fetchEndpointBody(simklSync, "sync/ratings/anime")?.let { body ->
                        parseAndSaveRatings(dbHelper, body, defaultType = "anime")
                    }
                } else if (savedAll != remoteAll) {
                    // Step 4: Delta Sync (savedAll != null && savedAll != remoteAll)
                    Log.d("GetSimklSyncData", "Running Incremental Delta Sync...")
                    val savedMoviesAll = savedActivities.moviesAll
                    val savedShowsAll = savedActivities.tvShowsAll
                    val savedAnimeAll = savedActivities.animeAll

                    if (remoteMoviesAll != null && remoteMoviesAll != savedMoviesAll) {
                        val dateParam = if (!savedMoviesAll.isNullOrEmpty()) "?date_from=$savedMoviesAll" else ""
                        fetchEndpointBody(simklSync, "sync/all-items/movies$dateParam")?.let { body ->
                            parseAndSaveAllItems(dbHelper, body, defaultType = "movie")
                        }
                        fetchEndpointBody(simklSync, "sync/ratings/movies$dateParam")?.let { body ->
                            parseAndSaveRatings(dbHelper, body, defaultType = "movie")
                        }
                    }

                    if (remoteShowsAll != null && remoteShowsAll != savedShowsAll) {
                        val allItemsQuery = if (!savedShowsAll.isNullOrEmpty()) {
                            "sync/all-items/shows?date_from=$savedShowsAll&episode_watched_at=yes"
                        } else {
                            "sync/all-items/shows?episode_watched_at=yes"
                        }
                        val ratingsQuery = if (!savedShowsAll.isNullOrEmpty()) {
                            "sync/ratings/shows?date_from=$savedShowsAll"
                        } else {
                            "sync/ratings/shows"
                        }
                        fetchEndpointBody(simklSync, allItemsQuery)?.let { body ->
                            parseAndSaveAllItems(dbHelper, body, defaultType = "show")
                        }
                        fetchEndpointBody(simklSync, ratingsQuery)?.let { body ->
                            parseAndSaveRatings(dbHelper, body, defaultType = "show")
                        }
                    }

                    if (remoteAnimeAll != null && remoteAnimeAll != savedAnimeAll) {
                        val allItemsQuery = if (!savedAnimeAll.isNullOrEmpty()) {
                            "sync/all-items/anime?date_from=$savedAnimeAll&episode_watched_at=yes"
                        } else {
                            "sync/all-items/anime?episode_watched_at=yes"
                        }
                        val ratingsQuery = if (!savedAnimeAll.isNullOrEmpty()) {
                            "sync/ratings/anime?date_from=$savedAnimeAll"
                        } else {
                            "sync/ratings/anime"
                        }
                        fetchEndpointBody(simklSync, allItemsQuery)?.let { body ->
                            parseAndSaveAllItems(dbHelper, body, defaultType = "anime")
                        }
                        fetchEndpointBody(simklSync, ratingsQuery)?.let { body ->
                            parseAndSaveRatings(dbHelper, body, defaultType = "anime")
                        }
                    }
                } else {
                    Log.d("GetSimklSyncData", "No changes detected in /sync/activities.")
                }

                // Step 5: Save updated timestamps into TABLE_SYNC_ACTIVITIES
                dbHelper.saveSyncActivities(
                    allAt = remoteAll,
                    tvShowsAll = remoteShowsAll,
                    animeAll = remoteAnimeAll,
                    moviesAll = remoteMoviesAll,
                    ratingsAt = remoteRatingsAt,
                    lastCheckedMs = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e("GetSimklSyncData", "Error during Simkl sync: ${e.message}", e)
            } finally {
                dbHelper.close()
            }
        }
    }

    private suspend fun fetchEndpointBody(simklSync: SimklSync, endpoint: String): String? {
        return try {
            simklSync.executeGetSync(endpoint).use { response ->
                if (!response.isSuccessful) {
                    Log.w("GetSimklSyncData", "Endpoint $endpoint returned HTTP ${response.code}")
                    null
                } else {
                    val body = response.body?.string()
                    if (body.isNullOrEmpty() || body == "null") null else body
                }
            }
        } catch (e: Exception) {
            Log.e("GetSimklSyncData", "Failed GET $endpoint: ${e.message}")
            null
        }
    }

    private fun parseAndSaveAllItems(
        dbHelper: SimklDatabaseHelper,
        responseBody: String,
        defaultType: String
    ) {
        val trimmed = responseBody.trim()
        if (trimmed.isEmpty() || trimmed == "null") return

        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            root.optJSONArray("movies")?.let { parseItemsArray(dbHelper, it, "movie") }
            root.optJSONArray("shows")?.let { parseItemsArray(dbHelper, it, "show") }
            root.optJSONArray("anime")?.let { parseItemsArray(dbHelper, it, "anime") }
        } else if (trimmed.startsWith("[")) {
            parseItemsArray(dbHelper, JSONArray(trimmed), defaultType)
        }
    }

    private fun parseItemsArray(
        dbHelper: SimklDatabaseHelper,
        itemsArray: JSONArray,
        itemType: String
    ) {
        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.optJSONObject(i) ?: continue
            val status = itemObj.optString("status", "")
            val lastWatchedAt = itemObj.optString("last_watched_at", "").takeIf { it.isNotEmpty() && it != "null" }
            val addedAt = itemObj.optString("added_to_watchlist_at", "").takeIf { it.isNotEmpty() && it != "null" }
            val userRating = itemObj.optInt("user_rating", 0)
            val watchedEpisodesCount = itemObj.optInt("watched_episodes_count", 0)
            val totalEpisodesCount = itemObj.optInt("total_episodes_count", 0)

            val mediaObj = when (itemType) {
                "movie" -> itemObj.optJSONObject("movie")
                "show" -> itemObj.optJSONObject("show") ?: itemObj.optJSONObject("anime")
                "anime" -> itemObj.optJSONObject("show") ?: itemObj.optJSONObject("anime")
                else -> itemObj.optJSONObject("movie") ?: itemObj.optJSONObject("show")
            } ?: continue

            val title = mediaObj.optString("title", "")
            val year = mediaObj.optInt("year", 0)
            val idsObj = mediaObj.optJSONObject("ids") ?: JSONObject()

            val simklId = idsObj.optInt("simkl", idsObj.optInt("simkl_id", 0))
            val slug = idsObj.optString("slug", "")
            val tmdbId = idsObj.optString("tmdb", "").toIntOrNull() ?: idsObj.optInt("tmdb", 0)
            val imdbId = idsObj.optString("imdb", "")
            val tvdbId = idsObj.optString("tvdb", "").toIntOrNull() ?: idsObj.optInt("tvdb", 0)

            val seasonsArray = itemObj.optJSONArray("seasons")
            val seasonEpisodeSummary = StringBuilder()

            if ((itemType == "show" || itemType == "anime") && seasonsArray != null) {
                var firstSeason = true
                for (s in 0 until seasonsArray.length()) {
                    val seasonObj = seasonsArray.optJSONObject(s) ?: continue
                    val seasonNumber = seasonObj.optInt("number", -1)
                    if (seasonNumber < 0) continue

                    val episodesArray = seasonObj.optJSONArray("episodes") ?: continue
                    val epNumbers = mutableListOf<Int>()

                    for (e in 0 until episodesArray.length()) {
                        val epObj = episodesArray.optJSONObject(e) ?: continue
                        val episodeNumber = epObj.optInt("number", -1)
                        if (episodeNumber < 0) continue
                        val epWatchedAt = epObj.optString("watched_at", "").takeIf { it.isNotEmpty() && it != "null" } ?: lastWatchedAt

                        epNumbers.add(episodeNumber)

                        if (tmdbId > 0) {
                            val epValues = ContentValues().apply {
                                put(SimklDatabaseHelper.COL_SHOW_SIMKL_ID, simklId)
                                put(SimklDatabaseHelper.COL_SHOW_TMDB_ID, tmdbId)
                                put(SimklDatabaseHelper.COL_SEASON_NUMBER, seasonNumber)
                                put(SimklDatabaseHelper.COL_EPISODE_NUMBER, episodeNumber)
                                put(SimklDatabaseHelper.COL_WATCHED_AT, epWatchedAt)
                            }
                            dbHelper.insertSeasonEpisodeWatchedData(epValues)
                        }
                    }

                    if (epNumbers.isNotEmpty()) {
                        if (!firstSeason) {
                            seasonEpisodeSummary.append(",")
                        }
                        seasonEpisodeSummary.append("$seasonNumber{${epNumbers.joinToString(",")}}")
                        firstSeason = false
                    }
                }
            }

            val itemValues = ContentValues().apply {
                if (simklId > 0) put(SimklDatabaseHelper.COL_SIMKL_ID, simklId)
                put(SimklDatabaseHelper.COL_SLUG, slug)
                if (tmdbId > 0) put(SimklDatabaseHelper.COL_TMDB, tmdbId)
                put(SimklDatabaseHelper.COL_IMDB, imdbId)
                if (tvdbId > 0) put(SimklDatabaseHelper.COL_TVDB, tvdbId)
                put(SimklDatabaseHelper.COL_TITLE, title)
                if (year > 0) put(SimklDatabaseHelper.COL_YEAR, year)
                put(SimklDatabaseHelper.COL_TYPE, itemType)
                if (status.isNotEmpty()) put(SimklDatabaseHelper.COL_STATUS, status)
                if (addedAt != null) put(SimklDatabaseHelper.COL_ADDED_AT, addedAt)
                if (lastWatchedAt != null) put(SimklDatabaseHelper.COL_LAST_WATCHED_AT, lastWatchedAt)
                if (userRating > 0) put(SimklDatabaseHelper.COL_USER_RATING, userRating)
                put(SimklDatabaseHelper.COL_WATCHED_EPISODES_COUNT, watchedEpisodesCount)
                put(SimklDatabaseHelper.COL_TOTAL_EPISODES_COUNT, totalEpisodesCount)
                if (seasonEpisodeSummary.isNotEmpty()) {
                    put(SimklDatabaseHelper.COL_SEASON_EPISODE_SHOW, seasonEpisodeSummary.toString())
                }
            }
            dbHelper.upsertItem(itemValues)
        }
    }

    private fun parseAndSaveRatings(
        dbHelper: SimklDatabaseHelper,
        responseBody: String,
        defaultType: String
    ) {
        val trimmed = responseBody.trim()
        if (trimmed.isEmpty() || trimmed == "null") return

        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            root.optJSONArray("movies")?.let { parseRatingsArray(dbHelper, it, "movie") }
            root.optJSONArray("shows")?.let { parseRatingsArray(dbHelper, it, "show") }
            root.optJSONArray("anime")?.let { parseRatingsArray(dbHelper, it, "anime") }
        } else if (trimmed.startsWith("[")) {
            parseRatingsArray(dbHelper, JSONArray(trimmed), defaultType)
        }
    }

    private fun parseRatingsArray(
        dbHelper: SimklDatabaseHelper,
        ratingsArray: JSONArray,
        itemType: String
    ) {
        for (i in 0 until ratingsArray.length()) {
            val itemObj = ratingsArray.optJSONObject(i) ?: continue
            val rating = itemObj.optInt("user_rating", itemObj.optInt("rating", 0))
            val ratedAt = itemObj.optString("user_rated_at", itemObj.optString("rated_at", ""))
                .takeIf { it.isNotEmpty() && it != "null" }
            val status = itemObj.optString("status", "")

            val mediaObj = when (itemType) {
                "movie" -> itemObj.optJSONObject("movie")
                "show" -> itemObj.optJSONObject("show") ?: itemObj.optJSONObject("anime")
                "anime" -> itemObj.optJSONObject("show") ?: itemObj.optJSONObject("anime")
                else -> itemObj.optJSONObject("movie") ?: itemObj.optJSONObject("show")
            } ?: continue

            val title = mediaObj.optString("title", "")
            val year = mediaObj.optInt("year", 0)
            val idsObj = mediaObj.optJSONObject("ids") ?: JSONObject()

            val simklId = idsObj.optInt("simkl", idsObj.optInt("simkl_id", 0))
            val slug = idsObj.optString("slug", "")
            val tmdbId = idsObj.optString("tmdb", "").toIntOrNull() ?: idsObj.optInt("tmdb", 0)
            val imdbId = idsObj.optString("imdb", "")
            val tvdbId = idsObj.optString("tvdb", "").toIntOrNull() ?: idsObj.optInt("tvdb", 0)

            val values = ContentValues().apply {
                if (simklId > 0) put(SimklDatabaseHelper.COL_SIMKL_ID, simklId)
                put(SimklDatabaseHelper.COL_SLUG, slug)
                if (tmdbId > 0) put(SimklDatabaseHelper.COL_TMDB, tmdbId)
                put(SimklDatabaseHelper.COL_IMDB, imdbId)
                if (tvdbId > 0) put(SimklDatabaseHelper.COL_TVDB, tvdbId)
                put(SimklDatabaseHelper.COL_TITLE, title)
                if (year > 0) put(SimklDatabaseHelper.COL_YEAR, year)
                put(SimklDatabaseHelper.COL_TYPE, itemType)
                if (status.isNotEmpty()) put(SimklDatabaseHelper.COL_STATUS, status)
                if (rating > 0) put(SimklDatabaseHelper.COL_USER_RATING, rating)
                if (ratedAt != null) put(SimklDatabaseHelper.COL_RATED_AT, ratedAt)
            }
            dbHelper.upsertItem(values)
        }
    }
}
