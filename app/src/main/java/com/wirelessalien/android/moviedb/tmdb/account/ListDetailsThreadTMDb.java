package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ListDetailsThreadTMDb extends Thread {

    private final int listId;
    private final String accessToken;
    private final Activity activity;
    private final OnFetchListDetailsListener listener;

    public ListDetailsThreadTMDb(int listId, Activity activity, OnFetchListDetailsListener listener) {
        this.listId = listId;
        this.activity = activity;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.accessToken = preferences.getString("access_token", "");
        this.listener = listener;
    }

    @Override
    public void run() {
        int currentPage = 1;
        boolean hasMorePages = true;

        while (hasMorePages) {
            try {
                OkHttpClient client = new OkHttpClient();

                Request request = new Request.Builder()
                        .url("https://api.themoviedb.org/4/list/" + listId + "?page=" + currentPage)
                        .get()
                        .addHeader("accept", "application/json")
                        .addHeader("Authorization", "Bearer " + accessToken) // assuming accessToken is defined and initialized
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray items = jsonResponse.getJSONArray("results");

                ArrayList<JSONObject> listDetailsData = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    listDetailsData.add(item);
                }

                activity.runOnUiThread(() -> listener.onFetchListDetails(listDetailsData));

                // Check if there are more pages
                int totalPages = jsonResponse.getInt("total_pages");
                if (currentPage >= totalPages) {
                    hasMorePages = false;
                } else {
                    currentPage++;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public interface OnFetchListDetailsListener {
        void onFetchListDetails(ArrayList<JSONObject> listDetailsData);
    }
}