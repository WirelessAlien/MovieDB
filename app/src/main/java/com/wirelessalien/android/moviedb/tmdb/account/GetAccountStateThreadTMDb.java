package com.wirelessalien.android.moviedb.tmdb.account;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GetAccountStateThreadTMDb extends Thread {

    private final String sessionId;
    private final int movieId;
    private boolean isInFavourites;
    private boolean isInWatchlist;
    private double rating;
    private final int accountId;
    private final String typeCheck;

    public GetAccountStateThreadTMDb(String sessionId, int movieId, int accountId, String typeCheck) {
        this.sessionId = sessionId;
        this.movieId = movieId;
        this.accountId = accountId;
        this.typeCheck = typeCheck;
    }

    @Override
    public void run() {
        try {
            URL url = new URL("https://api.themoviedb.org/3/" + typeCheck + "/" + movieId + "/account_states?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&session_id=" + sessionId);
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

            isInFavourites = response.getBoolean("favorite");
            isInWatchlist = response.getBoolean("watchlist");
            if (!response.isNull("rated")) {
                Object rated = response.get("rated");
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
            Log.e("GetAccountStateThreadTMDb", "Error while getting account state");
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