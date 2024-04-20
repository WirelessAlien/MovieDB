package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.data.ListData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FetchListThreadTMDb extends Thread {

    private final String sessionId;
    private final Activity activity;
    private final OnFetchListsListener listener;

    public FetchListThreadTMDb(String sessionId, Activity activity, OnFetchListsListener listener) {
        this.sessionId = sessionId;
        this.activity = activity;
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            String accountId = String.valueOf( PreferenceManager.getDefaultSharedPreferences( activity ).getInt( "accountId", 0 ) );
            URL url = new URL("https://api.themoviedb.org/3/account/" + accountId + "/lists?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&session_id=" + sessionId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder builder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            JSONObject response = new JSONObject(builder.toString());
            JSONArray results = response.getJSONArray("results");

            List<ListData> listData = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                listData.add(new ListData(result.getInt("id"), result.getString("name")));
                Log.d("FetchListThreadTMDb", "Fetched list name: " + result.getString("name"));
            }

            activity.runOnUiThread(() -> listener.onFetchLists(listData) );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface OnFetchListsListener {
        void onFetchLists(List<ListData> listData);
    }
}