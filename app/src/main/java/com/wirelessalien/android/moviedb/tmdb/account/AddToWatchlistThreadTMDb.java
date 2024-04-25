package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.helper.ConfigHelper;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AddToWatchlistThreadTMDb extends Thread {

    private final int movieId;
    private final String accountId;
    private final String type;
    private final String accessToken;
    private final boolean trueOrFalse;
    private final Activity activity;
    private final OkHttpClient client;

    public AddToWatchlistThreadTMDb(int movieId, String type, boolean trueOrFalse, Activity activity) {
        this.movieId = movieId;
        this.type = type;
        this.trueOrFalse = trueOrFalse;
        this.activity = activity;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.accountId = preferences.getString("account_id", "");
        this.accessToken = preferences.getString("access_token", "");
        this.client = new OkHttpClient();
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            MediaType mediaType = MediaType.parse("application/json;charset=utf-8");
            JSONObject jsonParam = new JSONObject();
            jsonParam.put("media_type", type);
            jsonParam.put("media_id", movieId);
            jsonParam.put("watchlist", trueOrFalse);
            RequestBody body = RequestBody.create(mediaType, jsonParam.toString());

            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/3/account/" + accountId + "/watchlist?api_key=" + accessToken)
                    .post(body)
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            String statusMessage = jsonResponse.getString("status_message");
            success = statusMessage.equals("Success.");

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("AddToWatchlistThreadTMDb", "Failed to add movie to watchlist");
            Log.e("AddToWatchlistThreadTMDb", e.getMessage());
        }

        final boolean finalSuccess = success;
        activity.runOnUiThread(() -> {
            if (finalSuccess) {
                Toast.makeText(activity, "Added to watchlist", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "Removed from watchlist", Toast.LENGTH_SHORT).show();
            }
        });
    }
}