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

package com.wirelessalien.android.moviedb.tmdb;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.recyclerview.widget.RecyclerView;

import com.wirelessalien.android.moviedb.adapter.MovieImageAdapter;
import com.wirelessalien.android.moviedb.data.MovieImage;
import com.wirelessalien.android.moviedb.helper.ConfigHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GetMovieImageThread extends Thread {

    private final int movieId;
    private final String type;
    private final Context context;
    private final RecyclerView recyclerView;
    private final String apiKey;
    private final String locale = Locale.getDefault().getCountry();

    public GetMovieImageThread(int movieId, String type, Context context, RecyclerView recyclerView) {
        this.movieId = movieId;
        this.type = type;
        this.context = context;
        this.recyclerView = recyclerView;
        this.apiKey = ConfigHelper.getConfigValue(context, "api_read_access_token");
    }

    @Override
    public void run() {
        try {
            String locale = Locale.getDefault().getCountry();

            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/3/" + type + "/" + movieId + "/images?language=" + locale + "&include_image_language=en,null")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();

            JSONObject jsonObject = new JSONObject(responseBody);
            JSONArray jsonArray = jsonObject.getJSONArray("backdrops");

            List<MovieImage> movieImages = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject imageObject = jsonArray.getJSONObject(i);
                movieImages.add(new MovieImage(imageObject.getString("file_path")));
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                MovieImageAdapter adapter = new MovieImageAdapter(context, movieImages);
                recyclerView.setAdapter(adapter);
            });

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("GetMovieImageThread", e.getMessage());
        }
    }
}
