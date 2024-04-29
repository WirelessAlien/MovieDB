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
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DeleteFromListThreadTMDb extends Thread {

    private final String accessToken;
    private final int mediaId;
    private final int listId;
    private final Activity activity;
    private final String type; // "movie" or "tv"
    private final int position; // position of the item in the list
    private final ArrayList<JSONObject> showList; // reference to the show list
    private final ShowBaseAdapter adapter; // reference to the adapter

    public DeleteFromListThreadTMDb(int mediaId, int listId, String type, Activity activity, int position, ArrayList<JSONObject> showList, ShowBaseAdapter adapter) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.accessToken = preferences.getString("access_token", "");
        this.mediaId = mediaId;
        this.listId = listId;
        this.type = type;
        this.activity = activity;
        this.position = position;
        this.showList = showList;
        this.adapter = adapter;
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
                    .delete(body)
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            Response response = client.newCall(request).execute();
            JSONObject jsonResponse = new JSONObject(response.body().string());
            success = jsonResponse.getBoolean("success");

        } catch (Exception e) {
            e.printStackTrace();
        }

        final boolean finalSuccess = success;
        activity.runOnUiThread(() -> {
            if (finalSuccess) {
                Toast.makeText(activity, "Media removed from list", Toast.LENGTH_SHORT).show();
                showList.remove(position);
                adapter.notifyItemRemoved(position);
            } else {
                Toast.makeText(activity, "Failed to remove media from list", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
