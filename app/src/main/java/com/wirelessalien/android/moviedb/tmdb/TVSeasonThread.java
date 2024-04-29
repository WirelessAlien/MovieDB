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
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.data.TVSeason;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TVSeasonThread extends Thread {

    private final int tvShowId;
    private List<TVSeason> seasons;

    private final SharedPreferences preferences;

    public TVSeasonThread(int tvShowId, Context context) {
        this.tvShowId = tvShowId;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }
    @Override
    public void run() {
        try {
            URL url = new URL("https://api.themoviedb.org/3/tv/" + tvShowId + "?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder builder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            JSONObject jsonResponse = new JSONObject(builder.toString());
            JSONArray response = jsonResponse.getJSONArray("seasons");
            seasons = new ArrayList<>();

            for (int i = 0; i < response.length(); i++) {
                JSONObject seasonJson = response.getJSONObject(i);
                int seasonNumber = seasonJson.getInt("season_number");

                if (seasonNumber > 0) {
                    TVSeason season = new TVSeason();
                    season.setAirDate(seasonJson.getString("air_date"));
                    season.setEpisodeCount(seasonJson.getInt("episode_count"));
                    season.setId(seasonJson.getInt("id"));
                    season.setName(seasonJson.getString("name"));
                    season.setOverview(seasonJson.getString("overview"));
                    season.setPosterPath(seasonJson.getString("poster_path"));
                    season.setSeasonNumber(seasonNumber);
                    season.setVoteAverage(seasonJson.getDouble("vote_average"));
                    seasons.add(season);
                }
            }
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("tvShowId", tvShowId);
            editor.apply();

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("TVSeasonThread", "Error fetching TV seasons", e);
        }
    }

    public List<TVSeason> getSeasons() {
        Log.d("TVSeasonThread", "Seasons: " + seasons);
        return seasons;

    }
}