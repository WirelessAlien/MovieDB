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
package com.wirelessalien.android.moviedb.tmdb.account

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.Locale

class AddToList(
    private val mediaId: Int,
    private val listId: Int,
    private val type: String,
    private val context: Context
) {
    private val accessToken: String?

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        accessToken = preferences.getString("access_token", "")
    }

    suspend fun addToList() {
        var success = false
        try {
            val client = OkHttpClient()
            val mediaType = MediaType.parse("application/json")
            val jsonParam = JSONObject()
            val itemsArray = JSONArray()
            val itemObject = JSONObject()
            itemObject.put("media_type", type)
            itemObject.put("media_id", mediaId)
            itemsArray.put(itemObject)
            jsonParam.put("items", itemsArray)
            val body = RequestBody.create(mediaType, jsonParam.toString())
            val request = Request.Builder()
                .url("https://api.themoviedb.org/4/list/$listId/items")
                .post(body)
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            val jsonResponse = withContext(Dispatchers.IO) {
                JSONObject(response.body()!!.string())
            }

            success = jsonResponse.getBoolean("success")
            if (success) {
                val dbHelper = ListDatabaseHelper(context)
                val db = dbHelper.writableDatabase

                // Query the database to get the list name
                var listName = ""
                val selectQuery =
                    "SELECT list_name FROM " + ListDatabaseHelper.TABLE_LISTS + " WHERE list_id = " + listId
                val cursor = db.rawQuery(selectQuery, null)
                if (cursor.moveToFirst()) {
                    listName = cursor.getString(cursor.getColumnIndexOrThrow("list_name"))
                }
                cursor.close()
                val values = ContentValues()
                values.put(ListDatabaseHelper.COLUMN_MOVIE_ID, mediaId)
                values.put(ListDatabaseHelper.COLUMN_LIST_ID, listId)
                values.put(
                    ListDatabaseHelper.COLUMN_DATE_ADDED,
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                        Date()
                    )
                )
                values.put(ListDatabaseHelper.COLUMN_IS_ADDED, 1)
                values.put(ListDatabaseHelper.COLUMN_MEDIA_TYPE, type)
                values.put(ListDatabaseHelper.COLUMN_LIST_NAME, listName)
                Log.d("AddToListCoroutineTMDb", "Adding media to list: $mediaId $listId")
                db.insert(ListDatabaseHelper.TABLE_LIST_DATA, null, values)
                db.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val finalSuccess = success
        if (context is Activity) {
            context.runOnUiThread {
                if (finalSuccess) {
                    Toast.makeText(context, R.string.media_added_to_list, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        R.string.failed_to_add_media_to_list,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}