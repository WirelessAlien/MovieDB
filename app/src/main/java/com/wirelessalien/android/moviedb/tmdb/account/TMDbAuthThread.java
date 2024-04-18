package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.tmdb.account.GetAccountIdThread;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TMDbAuthThread extends Thread {

    private final String username;
    private final String password;
    private String sessionId;
    private final Activity activity;
    private final SharedPreferences preferences;


    public TMDbAuthThread(String username, String password, Activity activity) {
        this.username = username;
        this.password = password;
        this.activity = activity;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(activity);

    }


    @Override
    public void run() {
        sessionId = authenticate();
        if (sessionId != null) {
            preferences.edit().putString("session_id", sessionId).apply();
            GetAccountIdThread getAccountIdThread = new GetAccountIdThread(sessionId, activity);
            getAccountIdThread.start();
        }
    }


    private String authenticate() {
        try {
            URL url = new URL("https://api.themoviedb.org/3/authentication/token/new?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5");
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
            String requestToken = response.getString("request_token");

            url = new URL("https://api.themoviedb.org/3/authentication/token/validate_with_login?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&username=" + username + "&password=" + password + "&request_token=" + requestToken);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            builder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            response = new JSONObject(builder.toString());
            boolean success = response.getBoolean("success");

            if (success) {
                url = new URL("https://api.themoviedb.org/3/authentication/session/new?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&request_token=" + requestToken);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                builder = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }

                response = new JSONObject(builder.toString());
                sessionId = response.getString("session_id");
                Log.d("TMDbAuthThread", "Session ID: " + sessionId);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("TMDbAuthThread", "Error authenticating with TMDb");
            Log.e("TMDbAuthThread", e.getMessage());
        }

        return sessionId;
    }
}

