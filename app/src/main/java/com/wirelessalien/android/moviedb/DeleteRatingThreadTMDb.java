package com.wirelessalien.android.moviedb;

import android.app.Activity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DeleteRatingThreadTMDb extends Thread {

    private final String sessionId;
    private final int movieId;
    private final String type;
    private final Activity activity;

    public DeleteRatingThreadTMDb(String sessionId, int movieId, String type, Activity activity) {
        this.sessionId = sessionId;
        this.movieId = movieId;
        this.type = type;
        this.activity = activity;
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            URL url = new URL("https://api.themoviedb.org/3/" + type + "/" + movieId + "/rating?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&session_id=" + sessionId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder builder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            JSONObject response = new JSONObject(builder.toString());
            success = response.getBoolean("success");

        } catch (Exception e) {
            e.printStackTrace();
        }

        final boolean finalSuccess = success;
        activity.runOnUiThread(() -> {
            if (finalSuccess) {
                // Rating was successfully deleted.
            } else {
                // Failed to delete the rating.
            }
        });
    }
}