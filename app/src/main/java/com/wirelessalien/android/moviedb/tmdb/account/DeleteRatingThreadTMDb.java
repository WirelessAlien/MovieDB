package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.helper.ConfigHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DeleteRatingThreadTMDb extends Thread {

    private final int movieId;
    private final String type;
    private final String accessToken;
    private final Activity activity;

    public DeleteRatingThreadTMDb(int movieId, String type, Activity activity) {
        this.movieId = movieId;
        this.type = type;
        this.activity = activity;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences( activity );
        this.accessToken = preferences.getString( "access_token", "" );

    }

    @Override
    public void run() {
        boolean success = false;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://api.themoviedb.org/3/" + type + "/" + movieId + "/rating")
                .delete()
                .addHeader("accept", "application/json")
                .addHeader("Content-Type", "application/json;charset=utf-8")
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            String statusMessage = jsonResponse.getString("status_message");
            success = statusMessage.equals("The item/record was deleted successfully.");

            final boolean finalSuccess = success;
            activity.runOnUiThread(() -> {
                if (finalSuccess) {
                    Toast.makeText(activity, "Rating deleted successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "Failed to delete rating", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }
}