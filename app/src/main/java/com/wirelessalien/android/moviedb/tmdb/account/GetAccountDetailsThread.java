package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class GetAccountDetailsThread extends Thread {

    private final String sessionId;
    private final Activity activity;
    private Map<String, String> details = new HashMap<>();

    public GetAccountDetailsThread(String sessionId, Activity activity) {
        this.sessionId = sessionId;
        this.activity = activity;
    }

    @Override
    public void run() {
        getAccountDetails();
    }

    private void getAccountDetails() {
        try {
            URL url = new URL("https://api.themoviedb.org/3/account?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&session_id=" + sessionId);
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
            String username = response.getString("username");
            String name = response.getString("name");
            String avatarPath = response.getJSONObject("avatar").getJSONObject("tmdb").getString("avatar_path");

            details.put("username", username);
            details.put("name", name);
            details.put("avatar_path", avatarPath);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("GetAccountDetailsThread", "Error getting account details");
            Log.e("GetAccountDetailsThread", e.getMessage());
        }
    }

    public Map<String, String> getDetails() {
        return details;
    }
}