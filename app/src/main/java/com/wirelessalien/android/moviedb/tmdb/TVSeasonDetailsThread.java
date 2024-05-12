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

import android.content.Context;
import android.util.Log;

import com.wirelessalien.android.moviedb.data.Episode;
import com.wirelessalien.android.moviedb.helper.ConfigHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TVSeasonDetailsThread extends Thread {

    private final int tvShowId;
    private final int seasonNumber;
    private List<Episode> episodes;
    private String seasonName;
    private String seasonOverview;
    private String seasonPosterPath;
    private double seasonVoteAverage;
    private final String apiKey;

    public TVSeasonDetailsThread(int tvShowId, int seasonNumber, Context context) {
        this.tvShowId = tvShowId;
        this.seasonNumber = seasonNumber;
        this.apiKey = ConfigHelper.getConfigValue(context,"api_key");
    }

    @Override
    public void run() {
        try {
            URL url = new URL("https://api.themoviedb.org/3/tv/" + tvShowId + "/season/" + seasonNumber + "?api_key=" + apiKey);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            String line;
            StringBuilder builder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            JSONObject jsonResponse = new JSONObject(builder.toString());
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
                int runtime;
                if (!episodeJson.isNull("runtime")) {
                    runtime = episodeJson.getInt("runtime");
                } else {
                    runtime = 0;
                }
                String posterPath = episodeJson.getString("still_path");
                double voteAverage = episodeJson.getDouble("vote_average");

                episodes.add(new Episode(airDate, episodeNumber, name, overview, runtime, posterPath, voteAverage));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    public String getSeasonName() {
        return seasonName;
    }

    public String getSeasonOverview() {
        return seasonOverview;
    }

    public String getSeasonPosterPath() {
        return seasonPosterPath;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public double getSeasonVoteAverage() {
        return seasonVoteAverage;
    }
}