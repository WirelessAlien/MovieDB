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

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ListItemBinding
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper.Companion.COL_ITEM_COUNT
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper.Companion.COL_TRAKT_ID
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper.Companion.USER_LISTS
import com.wirelessalien.android.moviedb.trakt.TraktSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class ListDataAdapterTkt(
    private val context: Context,
    private val movieId: Int,
    private val type: String,
    private val tktaccessToken: String?,
    private val mediaObject: JSONObject,
    private val movieDataObject: JSONObject
) : RecyclerView.Adapter<ListDataAdapterTkt.ViewHolder>() {

    private val dbHelper = TraktDatabaseHelper(context)
    private val lists: MutableList<ListItem> = mutableListOf()

    fun setLists(newLists: List<ListItem>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return lists.size
            }

            override fun getNewListSize(): Int {
                return newLists.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return lists[oldItemPosition].id == newLists[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return lists[oldItemPosition] == newLists[newItemPosition]
            }
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        lists.clear()
        lists.addAll(newLists)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listItem = lists[position]
        val (_, season, episodeNumber) = getSEIdFromMediaObject(mediaObject)
        holder.binding.listName.text = listItem.name
        holder.binding.listSwitch.setOnCheckedChangeListener(null)
        holder.binding.listSwitch.isChecked = if (type == "season" || type == "episode") {
            isEpisodeInList(listItem.id, season ?: -1, episodeNumber ?: -1)
        } else {
            isMovieInList(listItem.id)
        }
        val listId = listItem.id

        holder.binding.listSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            val endpoint = if (isChecked) "users/me/lists/$listId/items" else "users/me/lists/$listId/items/remove"
            traktSync(endpoint, mediaObject) { success ->
                if (success) {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (isChecked) {
                            addMovieToList(listId, mediaObject)
                            addItemtoTmdb()
                            updateUserListItemCount(listId, 1)
                        } else {
                            removeMovieFromList(listId)
                            updateUserListItemCount(listId, -1)
                        }
                        withContext(Dispatchers.Main) {
                            val message = if (isChecked) {
                                context.getString(R.string.media_added_to_list)
                            } else {
                                context.getString(R.string.media_removed_from_list)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    holder.binding.listSwitch.setOnCheckedChangeListener(null)
                    holder.binding.listSwitch.isChecked = !isChecked
                    holder.binding.listSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                        traktSync(endpoint, mediaObject) { success ->
                            if (success) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    if (isChecked) {
                                        addMovieToList(listId, mediaObject)
                                        addItemtoTmdb()
                                        updateUserListItemCount(listId, 1)
                                    } else {
                                        removeMovieFromList(listId)
                                        updateUserListItemCount(listId, -1)
                                    }
                                    withContext(Dispatchers.Main) {
                                        val message = if (isChecked) {
                                            context.getString(R.string.media_added_to_list)
                                        } else {
                                            context.getString(R.string.media_removed_from_list)
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                holder.binding.listSwitch.isChecked = !isChecked
                                Toast.makeText(context, context.getString(R.string.failed_to_add_media_to_list), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Toast.makeText(context, context.getString(R.string.failed_to_add_media_to_list), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return lists.size
    }

    private fun updateUserListItemCount(listId: Int, increment: Int) {
        val db = dbHelper.writableDatabase
        val query = "UPDATE $USER_LISTS SET $COL_ITEM_COUNT = $COL_ITEM_COUNT + ? WHERE $COL_TRAKT_ID = ?"
        val statement = db.compileStatement(query)
        statement.bindLong(1, increment.toLong())
        statement.bindLong(2, listId.toLong())
        statement.executeUpdateDelete()
    }

    private fun isMovieInList(listId: Int): Boolean {
        val db = dbHelper.readableDatabase
        val query = "SELECT 1 FROM ${TraktDatabaseHelper.TABLE_LIST_ITEM} WHERE ${TraktDatabaseHelper.COL_TMDB} = ? AND ${TraktDatabaseHelper.COL_LIST_ID} = ?"
        val cursor = db.rawQuery(query, arrayOf(movieId.toString(), listId.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    private fun isEpisodeInList(listId: Int, season: Int, number: Int): Boolean {
        val db = dbHelper.readableDatabase
        val query = "SELECT 1 FROM ${TraktDatabaseHelper.TABLE_LIST_ITEM} WHERE ${TraktDatabaseHelper.COL_SHOW_TMDB} = ? AND ${TraktDatabaseHelper.COL_LIST_ID} = ? AND ${TraktDatabaseHelper.COL_SEASON} = ? AND ${TraktDatabaseHelper.COL_NUMBER} = ?"
        val cursor = db.rawQuery(query, arrayOf(movieId.toString(), listId.toString(), season.toString(), number.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    private fun traktSync(endpoint: String, mediaObject: JSONObject, callback: (Boolean) -> Unit) {
        val traktApiService = TraktSync(tktaccessToken!!, context)
        traktApiService.post(endpoint, mediaObject, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as Activity).runOnUiThreadIfNeeded {
                    callback(false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                (context as Activity).runOnUiThreadIfNeeded {
                    callback(success)
                }
            }
        })
    }

    private fun addItemtoTmdb() {
        val dbHelper = TmdbDetailsDatabaseHelper(context)
        val tmdbId = movieDataObject.optInt("id")
        val name = if (type == "movie") movieDataObject.optString("title") else movieDataObject.optString("name")
        val backdropPath = movieDataObject.optString("backdrop_path")
        val posterPath = movieDataObject.optString("poster_path")
        val summary = movieDataObject.optString("overview")
        val voteAverage = movieDataObject.optDouble("vote_average")
        val type1 = if (type == "movie") "movie" else "show"
        val releaseDate = if (type == "movie") movieDataObject.optString("release_date") else movieDataObject.optString("first_air_date")
        val genreIds = movieDataObject.optJSONArray("genres")?.let { genresArray ->
            val ids = (0 until genresArray.length()).joinToString(",") { i ->
                genresArray.getJSONObject(i).getInt("id").toString()
            }
            "[$ids]"
        }
        val seasonEpisodeCount = movieDataObject.optJSONArray("seasons")
        val seasonsEpisodes = StringBuilder()

        for (i in 0 until (seasonEpisodeCount?.length() ?: 0)) {
            val season = seasonEpisodeCount?.getJSONObject(i)
            val seasonNumber = season?.getInt("season_number")

            // Skip specials (season_number == 0)
            if (seasonNumber == 0) continue

            val episodeCount = season?.getInt("episode_count")?: 0
            val episodesList = (1..episodeCount).toList()

            seasonsEpisodes.append("$seasonNumber{${episodesList.joinToString(",")}}")
            if (i < (seasonEpisodeCount?.length() ?: 0) - 1) {
                seasonsEpisodes.append(",")
            }
        }

        dbHelper.addItem(
            tmdbId,
            name,
            backdropPath,
            posterPath,
            summary,
            voteAverage,
            releaseDate,
            genreIds?: "",
            seasonsEpisodes.toString(),
            type1
        )
    }

    fun Activity.runOnUiThreadIfNeeded(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            runOnUiThread(action)
        }
    }

    private fun addMovieToList(listId: Int, mediaObject: JSONObject) {
        val db = dbHelper.writableDatabase
        val (_, season, episodeNumber) = getSEIdFromMediaObject(mediaObject)
        val values = ContentValues().apply {
            put(TraktDatabaseHelper.COL_LIST_ID, listId)
            put(TraktDatabaseHelper.COL_TYPE, type)
            if (type == "movie" || type == "show") {
                put(TraktDatabaseHelper.COL_TMDB, movieId)
            } else {
                put(TraktDatabaseHelper.COL_SHOW_TMDB, movieId)
                put(TraktDatabaseHelper.COL_SEASON, season)
                put(TraktDatabaseHelper.COL_NUMBER, episodeNumber)
            }
        }
        db.insert(TraktDatabaseHelper.TABLE_LIST_ITEM, null, values)
    }

    private fun getSEIdFromMediaObject(mediaObject: JSONObject): Triple<Int?, Int?, Int?> {
        val episodesArray = mediaObject.optJSONArray("episodes") ?: return Triple(null, null, null)
        val firstEpisode = episodesArray.optJSONObject(0) ?: return Triple(null, null, null)
        val idsObject = firstEpisode.optJSONObject("ids") ?: return Triple(null, null, null)
        val tmdbId = idsObject.optInt("tmdb", -1).takeIf { it != -1 }
        val season = firstEpisode.optInt("season", -1).takeIf { it != -1 }
        val episodeNumber = firstEpisode.optInt("number", -1).takeIf { it != -1 }
        return Triple(tmdbId, season, episodeNumber)
    }

    private fun removeMovieFromList(listId: Int) {
        val db = dbHelper.writableDatabase
        db.delete(
            TraktDatabaseHelper.TABLE_LIST_ITEM,
            "${TraktDatabaseHelper.COL_LIST_ID} = ? AND ${TraktDatabaseHelper.COL_TMDB} = ?",
            arrayOf(listId.toString(), movieId.toString())
        )
    }

    class ViewHolder(val binding: ListItemBinding) : RecyclerView.ViewHolder(binding.root)

    data class ListItem(val id: Int, val name: String, val isInList: Boolean)
}