/*
 *     This file is part of Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     Movie DB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movie DB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movie DB.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GetAccountDetailsThread extends Thread {

    private final String accountId;
    private int accountIdInt;
    private final String accessToken;
    private final Context context;
    private final OkHttpClient client;

    private final AccountDataCallback callback;

    public interface AccountDataCallback {
        void onAccountDataReceived(int accountId, String name, String username, String avatarPath, String gravatar);
    }

    public GetAccountDetailsThread(Context context, AccountDataCallback callback) {
        this.context = context;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.accountId = preferences.getString( "account_id", "" );
        this.accessToken = preferences.getString( "access_token", "" );
        this.client = new OkHttpClient();
        this.callback = callback;
    }


    @Override
    public void run() {
        try {
            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/3/account/" + accountId)
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            Response response = client.newCall(request).execute();

            if (response.isSuccessful()) {
                JSONObject jsonResponse = new JSONObject(response.body().string());
                accountIdInt = jsonResponse.getInt("id");
                String name = jsonResponse.getString("name");
                String username = jsonResponse.getString("username");

                //avatar path - object tmdb
                JSONObject avatar = jsonResponse.getJSONObject("avatar");
                JSONObject tmdb = avatar.getJSONObject("tmdb");
                String avatarPath = tmdb.getString("avatar_path");
                String gravatar = avatar.getJSONObject( "gravatar" ).getString( "hash" );

                if (callback != null) {
                    callback.onAccountDataReceived(accountIdInt, name, username, avatarPath, gravatar);
                }

                ((Activity) context).runOnUiThread(() -> {
                    if (accountId != null) {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                        SharedPreferences.Editor myEdit = preferences.edit();
                        myEdit.putInt("accountIdInt", accountIdInt);
                        myEdit.apply();
                    } else {
                        Log.e("GetAccountDetailsThread", "Failed to get account id");
                    }
                });
            } else {
                Log.e("GetAccountDetailsThread", "Failed to get account id");
            }

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }
}