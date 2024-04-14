package com.wirelessalien.android.moviedb;

import android.app.Activity;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AddToFavouritesThreadTMDb extends Thread {

    private final String sessionId;
    private final int movieId;
    private final int accountId;
    private final String type;
    private final Activity activity;

    public AddToFavouritesThreadTMDb(String sessionId, int movieId, int accountId, String type, Activity activity) {
        this.sessionId = sessionId;
        this.movieId = movieId;
        this.accountId = accountId;
        this.type = type;
        this.activity = activity;

    }

    @Override
    public void run() {
        boolean success = false;
        try {
            URL url = new URL("https://api.themoviedb.org/3/account/" + accountId + "/favorite?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&session_id=" + sessionId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");

            JSONObject jsonParam = new JSONObject();
            jsonParam.put("media_type", type);
            jsonParam.put("media_id", movieId);
            jsonParam.put("favorite", true);

            OutputStream os = connection.getOutputStream();
            os.write(jsonParam.toString().getBytes( StandardCharsets.UTF_8 ));
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
            Log.e("AddToFavouritesThreadTMDb", "Failed to add movie to favourites");
            Log.e("AddToFavouritesThreadTMDb", e.getMessage());
        }

        final boolean finalSuccess = success;
        activity.runOnUiThread( () -> {
            if (finalSuccess) {
                Log.d("AddToFavouritesThreadTMDb", "Movie added to favourites");
            } else {
                // Failed to add the movie to the favourites.
            }
        } );
    }
}