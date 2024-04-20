package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AddToListThreadTMDb extends Thread {

    private final String sessionId;
    private final int mediaId;
    private final int listId;
    private final Activity activity;
    private final String mediaType; // "movie" or "tv"

    public AddToListThreadTMDb(String sessionId, int mediaId, int listId, String mediaType, Activity activity) {
        this.sessionId = sessionId;
        this.mediaId = mediaId;
        this.listId = listId;
        this.mediaType = mediaType;
        this.activity = activity;
    }

    private boolean checkListExists() throws Exception {
        URL url = new URL("https://api.themoviedb.org/3/list/" + listId + "?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&session_id=" + sessionId);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        return responseCode == HttpURLConnection.HTTP_OK;
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            if (!checkListExists()) {
                System.err.println("List with ID " + listId + " does not exist.");
                return;
            }

            URL url = new URL("https://api.themoviedb.org/3/list/" + listId + "/add_item?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&session_id=" + sessionId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");

            JSONObject jsonParam = new JSONObject();
            jsonParam.put("media_id", mediaId);
            jsonParam.put("media_type", mediaType); // Add media type

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
            Log.d("AddToListThreadTMDb", mediaType + " added to list: " + success);

        } catch (Exception e) {
            e.printStackTrace();
        }

        final boolean finalSuccess = success;
        activity.runOnUiThread(() -> {
            if (finalSuccess) {
                // Media was successfully added to the list.
            } else {
                // Failed to add the media to the list.
            }
        });
    }
}