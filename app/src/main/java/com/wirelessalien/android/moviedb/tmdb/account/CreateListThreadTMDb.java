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
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CreateListThreadTMDb extends Thread {

    private final String listName;
    private final String accessToken;
    private final boolean isPublic;
    private final String description;
    private final Context context;

    public CreateListThreadTMDb(String listName, String description, boolean isPublic, Context context) {
        this.listName = listName;
        this.description = description;
        this.isPublic = isPublic;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.accessToken = preferences.getString("access_token", "");
        this.context = context;
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            OkHttpClient client = new OkHttpClient();

            MediaType mediaType = MediaType.parse("application/json");
            JSONObject jsonParam = new JSONObject();
            jsonParam.put("name", listName);
            jsonParam.put("description", description);
            jsonParam.put("iso_3166_1", "US");
            jsonParam.put("iso_639_1", "en");
            jsonParam.put("public", isPublic);

            RequestBody body = RequestBody.create(mediaType, jsonParam.toString());
            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/4/list")
                    .post(body)
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            Response response = client.newCall(request).execute();
            JSONObject jsonResponse = new JSONObject(response.body().string());
            success = jsonResponse.getBoolean("success");

            if (success) {
                ListDatabaseHelper listDatabaseHelper = new ListDatabaseHelper(context);
                listDatabaseHelper.addList(jsonResponse.getInt("id"), listName);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        final boolean finalSuccess = success;
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread( () -> {
                if (finalSuccess) {
                    Toast.makeText( context, R.string.list_created_successfully, Toast.LENGTH_SHORT ).show();
                } else {
                    Toast.makeText( context, R.string.failed_to_create_list, Toast.LENGTH_SHORT ).show();
                }
            } );
        }
    }
}