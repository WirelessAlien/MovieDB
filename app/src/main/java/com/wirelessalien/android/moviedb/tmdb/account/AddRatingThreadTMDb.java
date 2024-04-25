package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AddRatingThreadTMDb extends Thread {

    private final int movieId;
    private final double rating;
    private final String type;
    private final Activity activity;
    private final String accessToken;

    public AddRatingThreadTMDb(int movieId, double rating, String type, Activity activity) {
        this.movieId = movieId;
        this.rating = rating;
        this.type = type;
        this.activity = activity;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.accessToken = preferences.getString("access_token", "");
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            OkHttpClient client = new OkHttpClient();
            MediaType mediaType = MediaType.parse("application/json;charset=utf-8");
            JSONObject jsonParam = new JSONObject();
            jsonParam.put("value", rating);
            RequestBody body = RequestBody.create(mediaType, jsonParam.toString());

            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/3/" + type + "/" + movieId + "/rating?api_key=" + accessToken)
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
        }

        final boolean finalSuccess = success;
        activity.runOnUiThread(() -> {
            if (finalSuccess) {
                Toast.makeText(activity, "Rating added successfully.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "Failed to add rating.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}