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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.helper.ConfigHelper;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TMDbAuthThreadV4 extends Thread {

    private final String apiKey;
    private final Context context;
    private final SharedPreferences preferences;
    private String accessToken;

    public TMDbAuthThreadV4(Context context) {
        this.apiKey = ConfigHelper.getConfigValue(context, "api_read_access_token");
        this.context = context;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void run() {
        accessToken = authenticate();
        if (accessToken != null) {
            preferences.edit().putString("access_token", accessToken).apply();
        }
    }

    protected String authenticate() {
        try {
            OkHttpClient client = new OkHttpClient();
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");

            // Create JSON object with redirect_to URL
            JSONObject json = new JSONObject();
            json.put("redirect_to", "com.wirelessalien.android.moviedb://callback");

            // Generate a new request token
            Request requestTokenRequest = new Request.Builder()
                    .url("https://api.themoviedb.org/4/auth/request_token")
                    .post(RequestBody.create(JSON, json.toString())) // provide JSON object as request body
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("authorization", "Bearer " + apiKey)
                    .build();

            Response requestTokenResponse = client.newCall(requestTokenRequest).execute();
            String responseBody = requestTokenResponse.body().string();

            // Parse the JSON response body
            JSONObject jsonObject = new JSONObject(responseBody);
            String requestToken = jsonObject.getString("request_token");
            preferences.edit().putString("request_token", requestToken).apply();

            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            CustomTabsIntent customTabsIntent = builder.build();

            Uri uri = Uri.parse("https://www.themoviedb.org/auth/access?request_token=" + requestToken);

            if (customTabsIntent.intent.resolveActivity(context.getPackageManager()) != null) {
                // If available, launch the URL in a Chrome Custom Tab
                customTabsIntent.launchUrl(context, uri);
            } else {
                // If not available, launch the URL in the default browser
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                context.startActivity(browserIntent);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return accessToken;
    }
}