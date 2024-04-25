package com.wirelessalien.android.moviedb.tmdb.account;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.helper.ConfigHelper;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TMDbAuthThreadV4 extends Thread {

    private final String apiKey;
    private final Context context;
    private final SharedPreferences preferences;
    private String accessToken;

    public TMDbAuthThreadV4(Context context) {
        this.apiKey = ConfigHelper.getConfigValue(context, "api_read_access_token");
        this.context = context;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void run() {
        accessToken = authenticate();
        if (accessToken != null) {
            preferences.edit().putString("access_token", accessToken).apply();
        }
    }

    protected String authenticate() {
        try {
            OkHttpClient client = new OkHttpClient();
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");

            // Create JSON object with redirect_to URL
            JSONObject json = new JSONObject();
            json.put("redirect_to", "com.wirelessalien.android.moviedb://callback");

            // Generate a new request token
            Request requestTokenRequest = new Request.Builder()
                    .url("https://api.themoviedb.org/4/auth/request_token")
                    .post(RequestBody.create(JSON, json.toString())) // provide JSON object as request body
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("authorization", "Bearer " + apiKey)
                    .build();

            Response requestTokenResponse = client.newCall(requestTokenRequest).execute();
            String responseBody = requestTokenResponse.body().string();

            // Parse the JSON response body
            JSONObject jsonObject = new JSONObject(responseBody);
            String requestToken = jsonObject.getString("request_token");
            preferences.edit().putString("request_token", requestToken).apply();

            // Send the user to TMDB asking the user to approve the token
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(context, Uri.parse("https://www.themoviedb.org/auth/access?request_token=" + requestToken));

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("TMDbAuthThreadV4", "Error authenticating with TMDb");
            Log.e("TMDbAuthThreadV4", e.getMessage());
        }

        return accessToken;
    }
}