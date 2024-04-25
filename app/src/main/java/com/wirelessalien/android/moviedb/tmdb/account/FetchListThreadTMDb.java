package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.data.ListData;
import com.wirelessalien.android.moviedb.helper.ConfigHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FetchListThreadTMDb extends Thread {

    private final String accessToken;
    private final String accountId;
    private final Activity activity;
    private final OnFetchListsListener listener;

    public FetchListThreadTMDb(Activity activity, OnFetchListsListener listener) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.accessToken = preferences.getString("access_token", "");
        this.accountId = preferences.getString("account_id", "");
        this.activity = activity;
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/4/account/" + accountId + "/lists?page=1")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            Response response = client.newCall(request).execute();
            JSONObject jsonResponse = new JSONObject(response.body().string());
            JSONArray results = jsonResponse.getJSONArray("results");

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