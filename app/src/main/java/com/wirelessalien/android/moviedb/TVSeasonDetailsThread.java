package com.wirelessalien.android.moviedb;

import android.util.Log;

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

    public TVSeasonDetailsThread(int tvShowId, int seasonNumber) {
        this.tvShowId = tvShowId;
        this.seasonNumber = seasonNumber;
    }

    @Override
    public void run() {
        try {
            URL url = new URL("https://api.themoviedb.org/3/tv/" + tvShowId + "/season/" + seasonNumber + "?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5");
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
            Log.e("TVSeasonDetailsThread", "Error fetching TV season details", e);
        }
    }

    public List<Episode> getEpisodes() {
        Log.d("TVSeasonDetailsThread", "Episodes: " + episodes);
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