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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.trakt.TraktSync
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
    private val mediaObject: JSONObject
) : RecyclerView.Adapter<ListDataAdapterTkt.ViewHolder>() {

    private val dbHelper = TraktDatabaseHelper(context)
    private val lists: MutableList<ListItem> = mutableListOf()

    fun setLists(newLists: List<ListItem>) {
        lists.clear()
        lists.addAll(newLists)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listItem = lists[position]
        val (_, season, episodeNumber) = getSEIdFromMediaObject(mediaObject)
        holder.nameTextView.text = listItem.name
        holder.switch.setOnCheckedChangeListener(null)
        holder.switch.isChecked = if (type == "season" || type == "episode") {
            isEpisodeInList(listItem.id, season?: -1, episodeNumber?: -1)
        } else {
            isMovieInList(listItem.id)
        }
        val listId = listItem.id

        holder.switch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            Log.d("ListDataAdapterTkt", "onBindViewHolder: $listId")
            val endpoint = if (isChecked) "users/me/lists/$listId/items" else "users/me/lists/$listId/items/remove"
            traktSync(endpoint, mediaObject) { success ->
                if (success) {
                    if (isChecked) {
                        addMovieToList(listId, mediaObject)
                    } else {
                        removeMovieFromList(listId)
                    }
                    Toast.makeText(context, "Synced with Trakt", Toast.LENGTH_SHORT).show()
                } else {
                    holder.switch.setOnCheckedChangeListener(null)
                    holder.switch.isChecked = !isChecked
                    holder.switch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                        traktSync(endpoint, mediaObject) { success ->
                            if (success) {
                                if (isChecked) {
                                    addMovieToList(listId, mediaObject)
                                } else {
                                    removeMovieFromList(listId)
                                }
                                Toast.makeText(context, "Synced with Trakt", Toast.LENGTH_SHORT).show()
                            } else {
                                holder.switch.isChecked = !isChecked
                                Toast.makeText(context, "Failed to sync with Trakt", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Toast.makeText(context, "Failed to sync with Trakt", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return lists.size
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
        val traktApiService = TraktSync(tktaccessToken!!)
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

    fun Activity.runOnUiThreadIfNeeded(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            runOnUiThread(action)
        }
    }

    private fun addMovieToList(listId: Int, mediaObject: JSONObject) {
        val db = dbHelper.writableDatabase
        val (tmdbId, season, episodeNumber) = getSEIdFromMediaObject(mediaObject)
        val values = ContentValues().apply {
            put(TraktDatabaseHelper.COL_LIST_ID, listId)
            if (type == "season" || type == "episode") {
                put(TraktDatabaseHelper.COL_SHOW_TMDB, movieId)
            } else {
                put(TraktDatabaseHelper.COL_TMDB, movieId)
            }
            if (type == "episode" || type == "season") {
                put(TraktDatabaseHelper.COL_SEASON, season)
                put(TraktDatabaseHelper.COL_NUMBER, episodeNumber)
                put(TraktDatabaseHelper.COL_TMDB, tmdbId)
            }
            put(TraktDatabaseHelper.COL_TYPE, type)
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


    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.list_name)
        val switch: MaterialSwitch = itemView.findViewById(R.id.list_switch)
    }

    data class ListItem(val id: Int, val name: String, val isInList: Boolean)
}