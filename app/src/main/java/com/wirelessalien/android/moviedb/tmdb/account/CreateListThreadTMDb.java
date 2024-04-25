package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.content.SharedPreferences;
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

public class CreateListThreadTMDb extends Thread {

    private final String listName;
    private final String accessToken;
    private final boolean isPublic;
    private final String description;
    private final Activity activity;

    public CreateListThreadTMDb(String listName, String description, boolean isPublic, Activity activity) {
        this.listName = listName;
        this.description = description;
        this.isPublic = isPublic;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.accessToken = preferences.getString("access_token", "");
        this.activity = activity;
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            OkHttpClient client = new OkHttpClient();

            MediaType mediaType = MediaType.parse("application/json");
            JSONObject jsonParam = new JSONObject();
            jsonParam.put("name", listName);
            jsonParam.put("description", description);
            jsonParam.put("iso_3166_1", "US");
            jsonParam.put("iso_639_1", "en");
            jsonParam.put("public", isPublic);

            RequestBody body = RequestBody.create(mediaType, jsonParam.toString());
            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/4/list")
                    .post(body)
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            Response response = client.newCall(request).execute();
            JSONObject jsonResponse = new JSONObject(response.body().string());
            success = jsonResponse.getBoolean("success");

        } catch (Exception e) {
            e.printStackTrace();
        }

        final boolean finalSuccess = success;
        activity.runOnUiThread(() -> {
            if (finalSuccess) {
                Toast.makeText(activity, "List created successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "Failed to create list", Toast.LENGTH_SHORT).show();
            }
        });
    }
}