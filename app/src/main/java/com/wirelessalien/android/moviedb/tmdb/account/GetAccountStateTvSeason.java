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
import android.os.Handler;
import android.os.Looper;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GetAccountStateTvSeason extends Thread {

    public interface OnDataFetchedListener {
        void onDataFetched(Map<Integer, Double> episodeRatings);
    }

    private final int seasonId;
    private final int seriesId;
    private final String accessToken;
    private final Map<Integer, Double> episodeRatings = new HashMap<>();
    private final OnDataFetchedListener listener;

    public GetAccountStateTvSeason(int seriesId, int seasonId, Context context, OnDataFetchedListener listener) {
        this.seasonId = seasonId;
        this.seriesId = seriesId;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.accessToken = preferences.getString("access_token", "");
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/3/tv/" + seriesId + "/season/" + seasonId + "/account_states")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();

            JSONObject jsonResponse = new JSONObject(responseBody);

            JSONArray results = jsonResponse.getJSONArray("results");
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                int episodeNumber = result.getInt("episode_number");
                Object rated = result.get("rated");
                double rating = 0.0;
                if (rated instanceof JSONObject) {
                    rating = ((JSONObject) rated).getDouble("value");
                }
                episodeRatings.put(episodeNumber, rating);
            }

            if (listener != null) {
                new Handler( Looper.getMainLooper()).post(() -> listener.onDataFetched(episodeRatings));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
