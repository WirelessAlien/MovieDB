package com.wirelessalien.android.moviedb.tmdb.account;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LogoutThread extends Thread {

    private final Context context;
    private final SharedPreferences preferences;

    public LogoutThread(Context context) {
        this.context = context;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void run() {
        logout();
    }

    protected void logout() {
        try {
            OkHttpClient client = new OkHttpClient();
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");

            String accessToken = preferences.getString("access_token", null);
            if (accessToken != null) {
                JSONObject json = new JSONObject();
                json.put("access_token", accessToken);

                RequestBody body = RequestBody.create(JSON, json.toString());
                Request request = new Request.Builder()
                        .url("https://api.themoviedb.org/4/auth/access_token")
                        .delete(body)
                        .addHeader("accept", "application/json")
                        .addHeader("content-type", "application/json")
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    preferences.edit().remove("access_token").apply();
                    Log.d("TMDbLogoutThread", "Logged out successfully");
                } else {
                    Log.e("TMDbLogoutThread", "Error logging out");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("TMDbLogoutThread", "Error logging out");
            Log.e("TMDbLogoutThread", e.getMessage());
        }

    }


}