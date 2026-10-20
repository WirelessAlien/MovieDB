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

                val remoteMoviesRatedAt = moviesObj?.optString("rated_at", "")?.takeIf { it.isNotEmpty() }
                val remoteShowsRatedAt = tvShowsObj?.optString("rated_at", "")?.takeIf { it.isNotEmpty() }
                val remoteAnimeRatedAt = animeObj?.optString("rated_at", "")?.takeIf { it.isNotEmpty() }
                val remoteRatingsAt = listOfNotNull(
                    remoteShowsRatedAt,
                    remoteAnimeRatedAt,
                    remoteMoviesRatedAt,
                    settingsObj?.optString("all", "")?.takeIf { it.isNotEmpty() }
                ).maxOrNull() ?: remoteAll

                val remoteMoviesRemovedAt = moviesObj?.optString("removed_from_list", "")?.takeIf { it.isNotEmpty() }
                val remoteShowsRemovedAt = tvShowsObj?.optString("removed_from_list", "")?.takeIf { it.isNotEmpty() }
                val remoteAnimeRemovedAt = animeObj?.optString("removed_from_list", "")?.takeIf { it.isNotEmpty() }

                // Step 2: Read stored timestamps from SimklDatabaseHelper.TABLE_SYNC_ACTIVITIES
                val savedActivities = dbHelper.getSyncActivities()
                val savedAll = savedActivities?.allAt
                val savedMoviesAll = savedActivities?.moviesAll
                val savedShowsAll = savedActivities?.tvShowsAll
                val savedAnimeAll = savedActivities?.animeAll
                val savedRatingsAt = savedActivities?.ratingsAt
                val savedMoviesRemovedAt = savedActivities?.moviesRemovedAt
                val savedShowsRemovedAt = savedActivities?.tvShowsRemovedAt
                val savedAnimeRemovedAt = savedActivities?.animeRemovedAt

                var nextMoviesAll = savedMoviesAll
                var nextShowsAll = savedShowsAll
                var nextAnimeAll = savedAnimeAll
                var nextRatingsAt = savedRatingsAt
                var nextMoviesRemovedAt = savedMoviesRemovedAt
                var nextShowsRemovedAt = savedShowsRemovedAt
                var nextAnimeRemovedAt = savedAnimeRemovedAt
                var allSucceeded = true

                // Step 3: Initial Sync (savedAll == null || forceFullSync)
                // Execute sequentially (never in parallel) using SimklSync's 10 req/sec RateLimiter
                if (savedAll == null || forceFullSync) {
                    Log.d("GetSimklSyncData", "Running Initial Full Sync sequentially...")

                    if (remoteMoviesAll != null) {
                        val body = fetchEndpointBody(simklSync, "sync/all-items/movies?extended=full")
                        if (body != null) {
                            parseAndSaveAllItems(dbHelper, body, defaultType = "movie", isFullSync = true)
                            nextMoviesAll = remoteMoviesAll
                            nextMoviesRemovedAt = remoteMoviesRemovedAt
                        } else {
                            allSucceeded = false
                        }
                    } else {
                        nextMoviesAll = null
                        nextMoviesRemovedAt = remoteMoviesRemovedAt
                    }

                    if (remoteShowsAll != null) {
                        val body = fetchEndpointBody(simklSync, "sync/all-items/shows?extended=full&episode_watched_at=yes")
                        if (body != null) {
                            parseAndSaveAllItems(dbHelper, body, defaultType = "show", isFullSync = true)
                            nextShowsAll = remoteShowsAll
                            nextShowsRemovedAt = remoteShowsRemovedAt
                        } else {
                            allSucceeded = false
                        }
                    } else {
                        nextShowsAll = null
                        nextShowsRemovedAt = remoteShowsRemovedAt
                    }

                    if (remoteAnimeAll != null) {
                        val body = fetchEndpointBody(simklSync, "sync/all-items/anime?extended=full&episode_watched_at=yes")
                        if (body != null) {
                            parseAndSaveAllItems(dbHelper, body, defaultType = "anime", isFullSync = true)
                            nextAnimeAll = remoteAnimeAll
                            nextAnimeRemovedAt = remoteAnimeRemovedAt
                        } else {
                            allSucceeded = false
                        }
                    } else {
                        nextAnimeAll = null
                        nextAnimeRemovedAt = remoteAnimeRemovedAt
                    }

                    if (allSucceeded) {
                        nextRatingsAt = remoteRatingsAt
                    }
                } else if (savedAll != remoteAll ||
                    savedMoviesRemovedAt != remoteMoviesRemovedAt ||
                    savedShowsRemovedAt != remoteShowsRemovedAt ||
                    savedAnimeRemovedAt != remoteAnimeRemovedAt
                ) {
                    // Step 4: Delta Sync (savedAll != null && changes detected)
                    Log.d("GetSimklSyncData", "Running Incremental Delta Sync...")

                    if (remoteMoviesAll != null && remoteMoviesAll != savedMoviesAll) {
                        val query = if (!savedMoviesAll.isNullOrEmpty()) {
                            "sync/all-items/movies?extended=full&date_from=$savedMoviesAll"
                        } else {
                            "sync/all-items/movies?extended=full"
                        }
                        val body = fetchEndpointBody(simklSync, query)
                        if (body != null) {
                            parseAndSaveAllItems(
                                dbHelper,
                                body,
                                defaultType = "movie",
                                isFullSync = savedMoviesAll.isNullOrEmpty()
                            )
                            nextMoviesAll = remoteMoviesAll
                        } else {
                            allSucceeded = false
                        }
                    } else if (remoteMoviesRatedAt != null && remoteMoviesRatedAt != savedRatingsAt) {
                        val dateParam = if (!savedRatingsAt.isNullOrEmpty()) "?date_from=$savedRatingsAt" else ""
                        fetchEndpointBody(simklSync, "sync/ratings/movies$dateParam")?.let { body ->
                            parseAndSaveRatings(dbHelper, body, defaultType = "movie")
                        }
                    }

                    if (remoteShowsAll != null && remoteShowsAll != savedShowsAll) {
                        val query = if (!savedShowsAll.isNullOrEmpty()) {
                            "sync/all-items/shows?extended=full&episode_watched_at=yes&date_from=$savedShowsAll"
                        } else {
                            "sync/all-items/shows?extended=full&episode_watched_at=yes"
                        }
                        val body = fetchEndpointBody(simklSync, query)
                        if (body != null) {
                            parseAndSaveAllItems(
                                dbHelper,
                                body,
                                defaultType = "show",
                                isFullSync = savedShowsAll.isNullOrEmpty()
                            )
                            nextShowsAll = remoteShowsAll
                        } else {
                            allSucceeded = false
                        }
                    } else if (remoteShowsRatedAt != null && remoteShowsRatedAt != savedRatingsAt) {
                        val dateParam = if (!savedRatingsAt.isNullOrEmpty()) "?date_from=$savedRatingsAt" else ""
                        fetchEndpointBody(simklSync, "sync/ratings/shows$dateParam")?.let { body ->
                            parseAndSaveRatings(dbHelper, body, defaultType = "show")
                        }
                    }

                    if (remoteAnimeAll != null && remoteAnimeAll != savedAnimeAll) {
                        val query = if (!savedAnimeAll.isNullOrEmpty()) {
                            "sync/all-items/anime?extended=full&episode_watched_at=yes&date_from=$savedAnimeAll"
                        } else {
                            "sync/all-items/anime?extended=full&episode_watched_at=yes"
                        }
                        val body = fetchEndpointBody(simklSync, query)
                        if (body != null) {
                            parseAndSaveAllItems(
                                dbHelper,
                                body,
                                defaultType = "anime",
                                isFullSync = savedAnimeAll.isNullOrEmpty()
                            )
                            nextAnimeAll = remoteAnimeAll
                        } else {
                            allSucceeded = false
                        }
                    } else if (remoteAnimeRatedAt != null && remoteAnimeRatedAt != savedRatingsAt) {
                        val dateParam = if (!savedRatingsAt.isNullOrEmpty()) "?date_from=$savedRatingsAt" else ""
                        fetchEndpointBody(simklSync, "sync/ratings/anime$dateParam")?.let { body ->
                            parseAndSaveRatings(dbHelper, body, defaultType = "anime")
                        }
                    }

                    // Reconcile deletions if removed_from_list changed
                    if (remoteMoviesRemovedAt != null && remoteMoviesRemovedAt != savedMoviesRemovedAt) {
                        if (reconcileDeletionsForType(simklSync, dbHelper, "movies", "movie")) {
                            nextMoviesRemovedAt = remoteMoviesRemovedAt
                        } else {
                            allSucceeded = false
                        }
                    }
                    if (remoteShowsRemovedAt != null && remoteShowsRemovedAt != savedShowsRemovedAt) {
                        if (reconcileDeletionsForType(simklSync, dbHelper, "shows", "show")) {
                            nextShowsRemovedAt = remoteShowsRemovedAt
                        } else {
                            allSucceeded = false
                        }
                    }
                    if (remoteAnimeRemovedAt != null && remoteAnimeRemovedAt != savedAnimeRemovedAt) {
                        if (reconcileDeletionsForType(simklSync, dbHelper, "anime", "anime")) {
                            nextAnimeRemovedAt = remoteAnimeRemovedAt
                        } else {
                            allSucceeded = false
                        }
                    }

                    if (allSucceeded) {
                        nextRatingsAt = remoteRatingsAt
                    }
                } else {
                    Log.d("GetSimklSyncData", "No changes detected in /sync/activities.")
                }

                // Step 5: Save updated timestamps into TABLE_SYNC_ACTIVITIES only for successful fetches
                dbHelper.saveSyncActivities(
                    allAt = if (allSucceeded) remoteAll else savedAll,
                    tvShowsAll = nextShowsAll,
                    animeAll = nextAnimeAll,
                    moviesAll = nextMoviesAll,
                    ratingsAt = nextRatingsAt,
                    moviesRemovedAt = nextMoviesRemovedAt,
                    tvShowsRemovedAt = nextShowsRemovedAt,
                    animeRemovedAt = nextAnimeRemovedAt,
                    lastCheckedMs = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e("GetSimklSyncData", "Error during Simkl sync: ${e.message}", e)
            }
        }
    }

    private suspend fun reconcileDeletionsForType(
        simklSync: SimklSync,
        dbHelper: SimklDatabaseHelper,
        endpointType: String,
        dbType: String
    ): Boolean {
        val body = fetchEndpointBody(simklSync, "sync/all-items/$endpointType?extended=ids_only") ?: return false
        val trimmed = body.trim()
        val remoteIds = mutableSetOf<Int>()

        fun collectIdsFromArray(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val itemObj = arr.optJSONObject(i) ?: continue
                val mediaObj = itemObj.optJSONObject("movie")
                    ?: itemObj.optJSONObject("show")
                    ?: itemObj.optJSONObject("anime")
                    ?: itemObj
                val idsObj = mediaObj.optJSONObject("ids") ?: itemObj.optJSONObject("ids")
                val simklId = idsObj?.optInt("simkl", idsObj.optInt("simkl_id", 0))
                    ?: itemObj.optInt("simkl", 0)
                if (simklId > 0) {
                    remoteIds.add(simklId)
                }
            }
        }

        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            collectIdsFromArray(root.optJSONArray("movies"))
            collectIdsFromArray(root.optJSONArray("shows"))
            collectIdsFromArray(root.optJSONArray("anime"))
        } else if (trimmed.startsWith("[")) {
            collectIdsFromArray(JSONArray(trimmed))
        }

        dbHelper.reconcileTypeSimklIds(dbType, remoteIds)
        return true
    }

    private suspend fun fetchEndpointBody(simklSync: SimklSync, endpoint: String): String? {
        return try {
            simklSync.executeGetSync(endpoint).use { response ->
                if (!response.isSuccessful) {
                    Log.w("GetSimklSyncData", "Endpoint $endpoint returned HTTP ${response.code}")
                    null
                } else {
                    val body = response.body?.string()
                    if (body == null || body == "null") null else body
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
        defaultType: String,
        isFullSync: Boolean = false
    ) {
        val trimmed = responseBody.trim()
        if (trimmed.isEmpty() || trimmed == "null") {
            if (isFullSync) {
                dbHelper.reconcileTypeSimklIds(defaultType, emptySet())
            }
            return
        }

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                root.optJSONArray("movies")?.let {
                    val ids = parseItemsArray(dbHelper, it, "movie", isFullSync)
                    if (isFullSync) dbHelper.reconcileTypeSimklIds("movie", ids)
                }
                root.optJSONArray("shows")?.let {
                    val ids = parseItemsArray(dbHelper, it, "show", isFullSync)
                    if (isFullSync) dbHelper.reconcileTypeSimklIds("show", ids)
                }
                root.optJSONArray("anime")?.let {
                    val ids = parseItemsArray(dbHelper, it, "anime", isFullSync)
                    if (isFullSync) dbHelper.reconcileTypeSimklIds("anime", ids)
                }
            } else if (trimmed.startsWith("[")) {
                val ids = parseItemsArray(dbHelper, JSONArray(trimmed), defaultType, isFullSync)
                if (isFullSync) {
                    dbHelper.reconcileTypeSimklIds(defaultType, ids)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun parseItemsArray(
        dbHelper: SimklDatabaseHelper,
        itemsArray: JSONArray,
        itemType: String,
        isFullSync: Boolean
    ): Set<Int> {
        val seenSimklIds = mutableSetOf<Int>()
        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.optJSONObject(i) ?: continue
            val status = itemObj.optString("status", "")
            val lastWatchedAt = itemObj.optString("last_watched_at", "").takeIf { it.isNotEmpty() && it != "null" }
            val addedAt = itemObj.optString("added_to_watchlist_at", "").takeIf { it.isNotEmpty() && it != "null" }
            val userRating = itemObj.optInt("user_rating", 0)
            val userRatedAt = itemObj.optString("user_rated_at", itemObj.optString("rated_at", ""))
                .takeIf { it.isNotEmpty() && it != "null" }
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
            if (simklId > 0) {
                seenSimklIds.add(simklId)
            }
            val slug = idsObj.optString("slug", "")
            val tmdbId = idsObj.optString("tmdb", "").toIntOrNull() ?: idsObj.optInt("tmdb", 0)
            val imdbId = idsObj.optString("imdb", "")
            val tvdbId = idsObj.optString("tvdb", "").toIntOrNull() ?: idsObj.optInt("tvdb", 0)

            val seasonsArray = itemObj.optJSONArray("seasons")
            val seasonEpisodeSummary = StringBuilder()

            if ((itemType == "show" || itemType == "anime") && seasonsArray != null) {
                if (isFullSync && tmdbId > 0) {
                    dbHelper.clearWatchedEpisodesForShow(tmdbId)
                }
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
                if (userRatedAt != null) put(SimklDatabaseHelper.COL_RATED_AT, userRatedAt)
                put(SimklDatabaseHelper.COL_WATCHED_EPISODES_COUNT, watchedEpisodesCount)
                put(SimklDatabaseHelper.COL_TOTAL_EPISODES_COUNT, totalEpisodesCount)
                if (seasonEpisodeSummary.isNotEmpty()) {
                    put(SimklDatabaseHelper.COL_SEASON_EPISODE_SHOW, seasonEpisodeSummary.toString())
                }
            }
            dbHelper.upsertItem(itemValues)
        }
        return seenSimklIds
    }

    private fun parseAndSaveRatings(
        dbHelper: SimklDatabaseHelper,
        responseBody: String,
        defaultType: String
    ) {
        val trimmed = responseBody.trim()
        if (trimmed.isEmpty() || trimmed == "null") return

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                root.optJSONArray("movies")?.let { parseRatingsArray(dbHelper, it, "movie") }
                root.optJSONArray("shows")?.let { parseRatingsArray(dbHelper, it, "show") }
                root.optJSONArray("anime")?.let { parseRatingsArray(dbHelper, it, "anime") }
            } else if (trimmed.startsWith("[")) {
                parseRatingsArray(dbHelper, JSONArray(trimmed), defaultType)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
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
