package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CreateListThreadTMDb extends Thread {

    private final String sessionId;
    private final String listName;
    private final Activity activity;

    public CreateListThreadTMDb(String sessionId, String listName, Activity activity) {
        this.sessionId = sessionId;
        this.listName = listName;
        this.activity = activity;
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            URL url = new URL("https://api.themoviedb.org/3/list?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&session_id=" + sessionId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");

            JSONObject jsonParam = new JSONObject();
            jsonParam.put("name", listName);
            jsonParam.put("description", "This is a list created by user.");

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
                // List was successfully created.
            } else {
                // Failed to create the list.
            }
        });
    }
}