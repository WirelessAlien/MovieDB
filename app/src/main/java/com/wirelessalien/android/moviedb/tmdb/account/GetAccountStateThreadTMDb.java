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

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GetAccountStateThreadTMDb extends Thread {

    private final int movieId;
    private boolean isInFavourites;
    private boolean isInWatchlist;
    private double rating;
    private final String typeCheck;
    private final String accessToken;

    public GetAccountStateThreadTMDb(int movieId, String typeCheck, Activity activity ) {
        this.movieId = movieId;
        this.typeCheck = typeCheck;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences( activity );
        this.accessToken = preferences.getString( "access_token", "" );
    }

    @Override
    public void run() {
        try {
            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/3/" + typeCheck + "/" + movieId + "/account_states")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();

            JSONObject jsonResponse = new JSONObject(responseBody);

            isInFavourites = jsonResponse.getBoolean("favorite");
            isInWatchlist = jsonResponse.getBoolean("watchlist");
            if (!jsonResponse.isNull("rated")) {
                Object rated = jsonResponse.get("rated");
                if (rated instanceof JSONObject) {
                    rating = ((JSONObject) rated).getDouble("value");
                } else if (rated instanceof Boolean && !(Boolean) rated) {
                    rating = 0;
                }
            } else {
                rating = 0;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isInFavourites() {
        return isInFavourites;
    }

    public boolean isInWatchlist() {
        return isInWatchlist;
    }

    public double getRating() {
        return rating;
    }
}