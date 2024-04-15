package com.wirelessalien.android.moviedb;

import android.app.Activity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AddRatingThreadTMDb extends Thread {

    private final String sessionId;
    private final int movieId;
    private final double rating;
    private final String type;
    private final Activity activity;

    public AddRatingThreadTMDb(String sessionId, int movieId, double rating, String type, Activity activity) {
        this.sessionId = sessionId;
        this.movieId = movieId;
        this.rating = rating;
        this.type = type;
        this.activity = activity;
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            URL url = new URL("https://api.themoviedb.org/3/" + type + "/" + movieId + "/rating?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&session_id=" + sessionId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");

            JSONObject jsonParam = new JSONObject();
            jsonParam.put("value", rating);

            OutputStream os = connection.getOutputStream();
            os.write(jsonParam.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

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
                // Rating was successfully added.
            } else {
                // Failed to add the rating.
            }
        });
    }
}