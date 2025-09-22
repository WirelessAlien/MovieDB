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

package com.wirelessalien.android.moviedb.adapter

import android.content.ContentValues
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.trakt.TraktSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Date
import java.util.Locale

class EpisodeTraktAdapter(
    private val episodes: List<Int>,
    private val watchedEpisodes: MutableList<Int>,
    private val showData: JSONObject,
    private val seasonNumber: Int,
    private val context: Context,
    private val traktAccessToken: String,
    private val clientId: String,
    private val listener: EpisodeClickListener
) : RecyclerView.Adapter<EpisodeTraktAdapter.EpisodeViewHolder>() {
    private var mediaObject: JSONObject? = null
    private var episodeTraktId: Int = -1

    interface EpisodeClickListener {
        fun onEpisodeClick(tvShowId: Int, traktId: Int, seasonNumber: Int, episodeNumber: Int, episodeTraktId: Int, title: String)
        fun onEpisodeWatchedStatusChanged(tvShowId: Int, seasonNumber: Int, episodeNumber: Int, isWatched: Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.episode_trakt_item, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val episodeNumber = episodes[position]
        holder.episodeTextView.text = context.getString(R.string.episode_p, episodeNumber)

        val tvShowId = showData.optInt("id", -1)
        val isWatched = watchedEpisodes.contains(episodeNumber)
        val iconRes = if (isWatched) {
            R.drawable.ic_done_2
        } else {
            R.drawable.ic_close
        }
        holder.episodeStatusButton.setImageResource(iconRes)

        holder.episodeStatusButton.setOnClickListener {
            val currentDateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).format(
                Date()
            )
            CoroutineScope(Dispatchers.Main).launch {
                val episodeData = fetchEpisodeData(showData.optInt("trakt_id", -1), seasonNumber, episodeNumber, traktAccessToken)
                val episodeObject = if (episodeData != null) {
                    createTraktEpisodeObject(
                        episodeSeason = seasonNumber,
                        episodeNumber = episodeNumber,
                        episodeTitle = episodeData.getString("title"),
                        episodeTmdbId = episodeData.getJSONObject("ids").getInt("tmdb"),
                        episodeTraktId = episodeData.getJSONObject("ids").getInt("trakt"),
                        episodeTvdbId = episodeData.getJSONObject("ids").getInt("tvdb"),
                        episodeImdbId = episodeData.getJSONObject("ids").getString("imdb")
                    )
                } else {
                    null
                }
                episodeTraktId = episodeData?.getJSONObject("ids")?.getInt("trakt")?: -1
                mediaObject = episodeObject
                val endpoint = if (isWatched) "sync/history/remove" else "sync/history"
                traktSync(endpoint, holder, episodeNumber, currentDateTime)
            }
        }

        holder.itemView.setOnClickListener {
            listener.onEpisodeClick(tvShowId, showData.optInt("trakt_id", -1), seasonNumber, episodeNumber, episodeTraktId, showData.optString("show_title", "NULL"))
        }
    }

    override fun getItemCount(): Int = episodes.size

    private suspend fun fetchEpisodeData(tvShowId: Int, seasonNumber: Int, episodeNumber: Int, accessToken: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.trakt.tv/shows/$tvShowId/seasons/$seasonNumber/episodes/$episodeNumber")
                .get()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("trakt-api-version", "2")
                .addHeader("trakt-api-key", clientId)
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                    null
                } else {
                    val jsonResponse = JSONObject(responseBody)
                    JSONObject().apply {
                        put("season", jsonResponse.getInt("season"))
                        put("number", jsonResponse.getInt("number"))
                        put("title", jsonResponse.getString("title"))
                        put("ids", jsonResponse.getJSONObject("ids"))
                        put("traktid", jsonResponse.getJSONObject("ids").getInt("trakt"))
                        put("tvdbid", jsonResponse.getJSONObject("ids").getInt("tvdb"))
                        put("imdbid", jsonResponse.getJSONObject("ids").getString("imdb"))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun createTraktEpisodeObject(
        episodeSeason: Int,
        episodeNumber: Int,
        episodeTitle: String,
        episodeTmdbId: Int,
        episodeTraktId: Int,
        episodeTvdbId: Int,
        episodeImdbId: String
    ): JSONObject {
        val episodeIds = JSONObject().apply {
            put("trakt", episodeTraktId)
            put("tvdb", episodeTvdbId)
            put("imdb", episodeImdbId)
            put("tmdb", episodeTmdbId)
        }

        val episodeObject = JSONObject().apply {
            put("season", episodeSeason)
            put("number", episodeNumber)
            put("title", episodeTitle)
            put("ids", episodeIds)
        }

        return JSONObject().apply {
            put("episodes", JSONArray().put(episodeObject))
        }
    }

    private fun traktSync(endpoint: String, holder: EpisodeViewHolder, episodeNumber: Int, currentTime: String) {
        val isWatched = watchedEpisodes.contains(episodeNumber)
        val traktApiService = TraktSync(traktAccessToken, context)
        val jsonBody = mediaObject ?: JSONObject()
        traktApiService.post(endpoint, jsonBody, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, context.getString(R.string.failed_to_sync, endpoint), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                Handler(Looper.getMainLooper()).post {
                    val message = if (response.isSuccessful) {
                        val dbHelper = TraktDatabaseHelper(context)
                        val db = dbHelper.writableDatabase
                        val traktId = showData.optInt("trakt_id", -1)
                        val tmdbId = showData.optInt("id", -1)

                        if (endpoint == "sync/history") {
                            val values = ContentValues().apply {
                                put(TraktDatabaseHelper.COL_SHOW_TRAKT_ID, traktId)
                                put(TraktDatabaseHelper.COL_SHOW_TMDB_ID, showData.optInt("id", -1))
                                put(TraktDatabaseHelper.COL_SEASON_NUMBER, seasonNumber)
                                put(TraktDatabaseHelper.COL_EPISODE_NUMBER, episodeNumber)
                                put(TraktDatabaseHelper.COL_LAST_WATCHED_AT, currentTime)
                            }
                            dbHelper.insertSeasonEpisodeWatchedData(values)
                            dbHelper.addEpisodeToHistory(showData.optString("show_title", "NULL"), traktId, showData.optInt("id", -1), "episode", seasonNumber, episodeNumber, currentTime)
                            watchedEpisodes.add(episodeNumber)
                            holder.episodeStatusButton.setImageResource(R.drawable.ic_done_2)
                        } else if (endpoint == "sync/history/remove") {
                            dbHelper.removeEpisodeFromWatched(tmdbId, seasonNumber, episodeNumber)
                            dbHelper.removeEpisodeFromHistory(tmdbId, seasonNumber, episodeNumber)
                            watchedEpisodes.remove(episodeNumber)
                            holder.episodeStatusButton.setImageResource(R.drawable.ic_close)
                        }
                        listener.onEpisodeWatchedStatusChanged(showData.optInt("id"), seasonNumber, episodeNumber, isWatched)

                        db.close()
                        context.getString(R.string.success)
                    } else {
                        response.message
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    fun updateEpisodeWatched(episodeNumberToUpdate: Int, isWatched: Boolean) {
        val episodeIndex = episodes.indexOf(episodeNumberToUpdate)
        if (episodeIndex != -1) {
            if (isWatched && !watchedEpisodes.contains(episodeNumberToUpdate)) {
                watchedEpisodes.add(episodeNumberToUpdate)
            } else if (!isWatched && watchedEpisodes.contains(episodeNumberToUpdate)) {
                watchedEpisodes.remove(episodeNumberToUpdate)
            }
            notifyItemChanged(episodeIndex)
        }
    }

    class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val episodeTextView: TextView = itemView.findViewById(R.id.episodeTextView)
        val episodeStatusButton: ImageButton = itemView.findViewById(R.id.episodeStatusButton)
    }
}