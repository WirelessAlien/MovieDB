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

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
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
import com.wirelessalien.android.moviedb.helper.SimklDatabaseHelper
import com.wirelessalien.android.moviedb.simkl.SimklSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Date
import java.util.Locale

class EpisodeSimklAdapter(
    private val episodes: List<Int>,
    private val watchedEpisodes: MutableList<Int>,
    private val showData: JSONObject,
    private val seasonNumber: Int,
    private val context: Context,
    private val simklAccessToken: String,
    private val listener: EpisodeClickListener
) : RecyclerView.Adapter<EpisodeSimklAdapter.EpisodeViewHolder>() {

    interface EpisodeClickListener {
        fun onEpisodeClick(
            tvShowId: Int,
            simklId: Int,
            seasonNumber: Int,
            episodeNumber: Int,
            title: String
        )
        fun onEpisodeWatchedStatusChanged(
            tvShowId: Int,
            seasonNumber: Int,
            episodeNumber: Int,
            isWatched: Boolean
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.episode_trakt_item, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val episodeNumber = episodes[position]
        holder.episodeTextView.text = context.getString(R.string.episode_p, episodeNumber)

        val tvShowId = showData.optInt("id", showData.optInt("tmdb", -1))
        val simklId = showData.optInt("simkl_id", 0)
        val showTitle = showData.optString("show_title", showData.optString("title", showData.optString("name", "")))
        val imdbId = showData.optString("imdb", "")

        val isWatched = watchedEpisodes.contains(episodeNumber)
        holder.episodeStatusButton.setImageResource(
            if (isWatched) R.drawable.ic_done_2 else R.drawable.ic_close
        )

        holder.episodeStatusButton.setOnClickListener {
            val currentlyWatched = watchedEpisodes.contains(episodeNumber)
            val currentDateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val endpoint = if (currentlyWatched) "sync/history/remove" else "sync/history"
            val episodeJson = JSONObject().apply {
                put("number", episodeNumber)
                if (!currentlyWatched) {
                    put("watched_at", currentDateTime)
                }
            }
            val seasonJson = JSONObject().apply {
                put("number", seasonNumber)
                put("episodes", JSONArray().put(episodeJson))
            }
            val idsJson = JSONObject().apply {
                put("tmdb", tvShowId)
                if (imdbId.isNotEmpty() && imdbId != "null") {
                    put("imdb", imdbId)
                }
                if (simklId > 0) {
                    put("simkl", simklId)
                }
            }
            val showObj = JSONObject().apply {
                if (!currentlyWatched && showTitle.isNotEmpty()) {
                    put("title", showTitle)
                }
                put("ids", idsJson)
                put("seasons", JSONArray().put(seasonJson))
            }
            val payload = JSONObject().apply {
                put("shows", JSONArray().put(showObj))
            }

            val simklSync = SimklSync(simklAccessToken, context)
            simklSync.post(endpoint, payload, object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            context.getString(R.string.failed_to_sync, endpoint),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val isSuccess = response.isSuccessful
                    val msg = response.message
                    response.close()
                    if (isSuccess) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = SimklDatabaseHelper(context)
                            if (endpoint == "sync/history") {
                                db.addEpisodeToWatched(
                                    simklId,
                                    tvShowId,
                                    seasonNumber,
                                    episodeNumber,
                                    currentDateTime
                                )
                            } else {
                                db.removeEpisodeFromWatched(tvShowId, seasonNumber, episodeNumber)
                            }
                        }
                    }
                    Handler(Looper.getMainLooper()).post {
                        if (isSuccess) {
                            if (endpoint == "sync/history") {
                                if (!watchedEpisodes.contains(episodeNumber)) {
                                    watchedEpisodes.add(episodeNumber)
                                }
                                holder.episodeStatusButton.setImageResource(R.drawable.ic_done_2)
                            } else {
                                watchedEpisodes.remove(episodeNumber)
                                holder.episodeStatusButton.setImageResource(R.drawable.ic_close)
                            }
                            listener.onEpisodeWatchedStatusChanged(
                                tvShowId,
                                seasonNumber,
                                episodeNumber,
                                currentlyWatched
                            )
                            Toast.makeText(context, context.getString(R.string.success), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }

        holder.itemView.setOnClickListener {
            listener.onEpisodeClick(tvShowId, simklId, seasonNumber, episodeNumber, showTitle)
        }
    }

    override fun getItemCount(): Int = episodes.size

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
