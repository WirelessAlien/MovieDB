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

package com.wirelessalien.android.moviedb.tmdb;

import static com.wirelessalien.android.moviedb.activity.BaseActivity.getLanguageParameter;

import android.content.Context;

import com.wirelessalien.android.moviedb.data.Episode;
import com.wirelessalien.android.moviedb.helper.ConfigHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TVSeasonDetailsThread extends Thread {

    private final int tvShowId;
    private final int seasonNumber;
    private List<Episode> episodes;
    private String seasonName;
    private String seasonOverview;
    private String seasonPosterPath;
    private double seasonVoteAverage;
    Context context;
    private final String apiKey;

    public TVSeasonDetailsThread(int tvShowId, int seasonNumber, Context context) {
        this.tvShowId = tvShowId;
        this.seasonNumber = seasonNumber;
        this.context = context;
        this.apiKey = ConfigHelper.getConfigValue(context,"api_key");
    }

    @Override
    public void run() {
        try {
            String baseUrl = "https://api.themoviedb.org/3/tv/" + tvShowId + "/season/" + seasonNumber + "?api_key=" + apiKey;
            String urlWithLanguage = baseUrl + getLanguageParameter(context);

            JSONObject jsonResponse = fetchSeasonDetails(urlWithLanguage);

            // Check if overview is empty
            if (jsonResponse.getString("overview").isEmpty()) {
                jsonResponse = fetchSeasonDetails(baseUrl);
            }

            seasonName = jsonResponse.getString("name");
            seasonOverview = jsonResponse.getString("overview");
            seasonPosterPath = jsonResponse.getString("poster_path");
            seasonVoteAverage = jsonResponse.getDouble("vote_average");

            JSONArray response = jsonResponse.getJSONArray("episodes");
            episodes = new ArrayList<>();

            for (int i = 0; i < response.length(); i++) {
                JSONObject episodeJson = response.getJSONObject(i);
                String name = episodeJson.getString("name");
                String overview = episodeJson.getString("overview");
                String airDate = episodeJson.getString("air_date");
                int episodeNumber = episodeJson.getInt("episode_number");
                int runtime = episodeJson.isNull("runtime") ? 0 : episodeJson.getInt("runtime");
                String posterPath = episodeJson.getString("still_path");
                double voteAverage = episodeJson.getDouble("vote_average");

                episodes.add(new Episode(airDate, episodeNumber, name, overview, runtime, posterPath, voteAverage));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JSONObject fetchSeasonDetails(String urlString) throws Exception {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(urlString)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            String responseBody = null;
            if (response.body() != null) {
                responseBody = response.body().string();
            }
            if (responseBody != null) {
                return new JSONObject( responseBody );
            }
        }
        return null;
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

//    public String getSeasonName() {
//        return seasonName;
//    }
//
//    public String getSeasonOverview() {
//        return seasonOverview;
//    }

//    public String getSeasonPosterPath() {
//        return seasonPosterPath;
//    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

//    public double getSeasonVoteAverage() {
//        return seasonVoteAverage;
//    }
}