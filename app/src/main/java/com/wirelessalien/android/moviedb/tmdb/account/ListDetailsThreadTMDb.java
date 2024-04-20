package com.wirelessalien.android.moviedb.tmdb.account;

import android.app.Activity;
import android.util.Log;

import com.wirelessalien.android.moviedb.data.ListDetails;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ListDetailsThreadTMDb extends Thread {

    private final int listId;
    private final Activity activity;
    private final OnFetchListDetailsListener listener;

    public ListDetailsThreadTMDb(int listId, Activity activity, OnFetchListDetailsListener listener) {
        this.listId = listId;
        this.activity = activity;
        this.listener = listener;
    }

    @Override
    public void run() {
        int currentPage = 1;
        boolean hasMorePages = true;

        while (hasMorePages) {
            try {
                URL url = new URL("https://api.themoviedb.org/3/list/" + listId + "?api_key=54b3ccfdeee9c0c2c869d38b1a8724c5&page=" + currentPage);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                StringBuilder builder = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }

                JSONObject response = new JSONObject(builder.toString());
                JSONArray items = response.getJSONArray("items");

                List<ListDetails> listDetailsData = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String mediaType = item.has("media_type") ? item.getString("media_type") : null;
                    String titleKey = "title";
                    if ("tv".equals(mediaType)) {
                        titleKey = "name";
                    }
                    String title = item.has(titleKey) ? item.getString(titleKey) : null;
                    String posterPath = item.has("poster_path") ? item.getString("poster_path") : null;
                    String overview = item.has("overview") ? item.getString("overview") : null;
                    String releaseDate = item.has("release_date") ? item.getString("release_date") : null;
                    Double voteAverage = item.has("vote_average") ? item.getDouble("vote_average") : null;
                    Integer voteCount = item.has("vote_count") ? item.getInt("vote_count") : null;
                    Integer id = item.has("id") ? item.getInt("id") : null;
                    String backdropPath = item.has("backdrop_path") ? item.getString("backdrop_path") : null;

                    listDetailsData.add(new ListDetails(mediaType, title, posterPath, overview, releaseDate, voteAverage, voteCount, id, backdropPath));
                }

                activity.runOnUiThread(() -> listener.onFetchListDetails(listDetailsData));

                // Check if there are more pages
                int totalPages = response.getInt("total_pages");
                if (currentPage >= totalPages) {
                    hasMorePages = false;
                } else {
                    currentPage++;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public interface OnFetchListDetailsListener {
        void onFetchListDetails(List<ListDetails> listDetailsData);
    }
}