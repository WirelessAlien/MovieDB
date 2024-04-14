package com.wirelessalien.android.moviedb;

import android.app.Activity;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CheckWatchlistThreadTMDb extends Thread {

    private final String sessionId;
    private final int movieId;
    private final int accountId;
    private final String typeCheck;
    private boolean isInWatchlist;

    public CheckWatchlistThreadTMDb(String sessionId, int movieId, int accountId, String typeCheck) {
        this.sessionId = sessionId;
        this.movieId = movieId;
        this.accountId = accountId;
        this.typeCheck = typeCheck;
    }

    @Override
    public void run() {
        try {
            URL url = new URL("https://api.themoviedb.org/3/account/" + accountId + "/watchlist/" + typeCheck + "?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&session_id=" + sessionId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder builder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            JSONObject response = new JSONObject(builder.toString());
            JSONArray results = response.getJSONArray("results");

            for (int i = 0; i < results.length(); i++) {
                JSONObject movie = results.getJSONObject(i);
                if (movie.getInt("id") == movieId) {
                    isInWatchlist = true;
                    Log.d("CheckWatchlistThreadTMDb", "Movie is in watchlist");
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("CheckWatchlistThreadTMDb", "Failed to check if movie is in watchlist");
        }
    }

    public boolean isInWatchlist() {
        return isInWatchlist;
    }
}
