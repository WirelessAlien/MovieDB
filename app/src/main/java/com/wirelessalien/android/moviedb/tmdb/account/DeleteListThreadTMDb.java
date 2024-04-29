package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DeleteListThreadTMDb extends Thread {

    public interface OnListDeletedListener {
        void onListDeleted();
    }

    private final int listId;
    private final String accessToken;
    private final Activity activity;
    private final OnListDeletedListener onListDeletedListener;

    public DeleteListThreadTMDb(int listId, Activity activity, OnListDeletedListener onListDeletedListener) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.accessToken = preferences.getString("access_token", "");
        this.listId = listId;
        this.activity = activity;
        this.onListDeletedListener = onListDeletedListener;
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/4/list/" + listId)
                    .delete()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            Response response = client.newCall(request).execute();
            success = response.isSuccessful();

        } catch (Exception e) {
            e.printStackTrace();
        }

        final boolean finalSuccess = success;
        activity.runOnUiThread(() -> {
            if (finalSuccess) {
                Toast.makeText(activity, "List deleted successfully", Toast.LENGTH_SHORT).show();
                onListDeletedListener.onListDeleted();
            } else {
                Toast.makeText(activity, "Failed to delete list", Toast.LENGTH_SHORT).show();
            }
        });
    }
}