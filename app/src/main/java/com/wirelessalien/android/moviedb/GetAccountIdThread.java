package com.wirelessalien.android.moviedb;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GetAccountIdThread extends Thread {

    private final String sessionId;

    private Integer accountId;
    private final Activity activity;

    public GetAccountIdThread(String sessionId, Activity activity) {
        this.sessionId = sessionId;

        this.activity = activity;
    }


    @Override
    public void run() {
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
            accountId = response.getInt("id");

            activity.runOnUiThread( () -> {
                if (accountId != null) {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
                    SharedPreferences.Editor myEdit = preferences.edit();
                    myEdit.putInt("accountId", accountId);
                    myEdit.apply();
                } else {
                    Log.e("GetAccountIdThread", "Failed to get account id");
                }
            } );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
