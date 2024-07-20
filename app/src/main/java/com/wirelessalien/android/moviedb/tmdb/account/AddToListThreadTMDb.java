/*
 *     This file is part of Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     Movie DB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movie DB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movie DB.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.icu.text.SimpleDateFormat;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AddToListThreadTMDb extends Thread {

    private final String accessToken;
    private final int mediaId;
    private final int listId;
    private final Context context;
    private final String type; // "movie" or "tv"

    public AddToListThreadTMDb(int mediaId, int listId, String type, Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.accessToken = preferences.getString("access_token", "");
        this.mediaId = mediaId;
        this.listId = listId;
        this.type = type;
        this.context = context;
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            OkHttpClient client = new OkHttpClient();

            MediaType mediaType = MediaType.parse("application/json");
            JSONObject jsonParam = new JSONObject();
            JSONArray itemsArray = new JSONArray();
            JSONObject itemObject = new JSONObject();
            itemObject.put("media_type", type);
            itemObject.put("media_id", mediaId);
            itemsArray.put(itemObject);
            jsonParam.put("items", itemsArray);

            RequestBody body = RequestBody.create(mediaType, jsonParam.toString());
            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/4/list/" + listId + "/items")
                    .post(body)
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            Response response = client.newCall(request).execute();
            JSONObject jsonResponse = new JSONObject(response.body().string());
            success = jsonResponse.getBoolean("success");

            if (success) {
                ListDatabaseHelper dbHelper = new ListDatabaseHelper(context);
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                // Query the database to get the list name
                String listName = "";
                String selectQuery = "SELECT list_name FROM " + ListDatabaseHelper.TABLE_LISTS + " WHERE list_id = " + listId;
                Cursor cursor = db.rawQuery(selectQuery, null);
                if (cursor.moveToFirst()) {
                    listName = cursor.getString(cursor.getColumnIndexOrThrow("list_name"));
                }
                cursor.close();

                ContentValues values = new ContentValues();
                values.put(ListDatabaseHelper.COLUMN_MOVIE_ID, mediaId);
                values.put(ListDatabaseHelper.COLUMN_LIST_ID, listId);
                values.put(ListDatabaseHelper.COLUMN_DATE_ADDED, new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
                values.put(ListDatabaseHelper.COLUMN_IS_ADDED, 1);
                values.put(ListDatabaseHelper.COLUMN_MEDIA_TYPE, type);
                values.put(ListDatabaseHelper.COLUMN_LIST_NAME, listName);

                Log.d("AddToListThreadTMDb", "Adding media to list: " + mediaId + " " + listId);
                db.insert(ListDatabaseHelper.TABLE_LIST_DATA, null, values);
                db.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        final boolean finalSuccess = success;
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread( () -> {
                if (finalSuccess) {
                    Toast.makeText( context, R.string.media_added_to_list, Toast.LENGTH_SHORT ).show();
                } else {
                    Toast.makeText( context, R.string.failed_to_add_media_to_list, Toast.LENGTH_SHORT ).show();
                }
            } );
        }
    }
}