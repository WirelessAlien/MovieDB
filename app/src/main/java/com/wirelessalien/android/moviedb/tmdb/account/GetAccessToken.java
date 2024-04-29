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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GetAccessToken extends Thread {

    private final String apiKey;
    private final String requestToken;
    private final SharedPreferences preferences;

    public GetAccessToken(String apiKey, String requestToken, Context context) {
        this.apiKey = apiKey;
        this.requestToken = requestToken;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void run() {
        try {
            OkHttpClient client = new OkHttpClient();

            // With an approved request token, create a session
            JSONObject postBody = new JSONObject();
            postBody.put("request_token", requestToken);

            RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), postBody.toString());

            Request sessionRequest = new Request.Builder()
                    .url("https://api.themoviedb.org/4/auth/access_token")
                    .post(body)
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("authorization", "Bearer " + apiKey)
                    .build();

            Response sessionResponse = client.newCall(sessionRequest).execute();
            String sessionResponseBody = sessionResponse.body().string();

            // Check if the request was successful
            if (!sessionResponse.isSuccessful()) {
                Log.e("GetAccessToken", "Error creating session: " + sessionResponseBody);
                return;
            }

            // Parse the JSON response body
            JSONObject sessionResponseObject = new JSONObject(sessionResponseBody);

            String accessToken = sessionResponseObject.getString("access_token");
            String accountId = sessionResponseObject.getString("account_id");

            preferences.edit().putString("access_token", accessToken).apply();
            preferences.edit().putString("account_id", accountId).apply();

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("GetAccessToken", e.getMessage());
        }
    }
}
