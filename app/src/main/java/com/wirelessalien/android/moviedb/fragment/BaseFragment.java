/*
 * Copyright (c) 2018.
 *
 * This file is part of MovieDB.
 *
 * MovieDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MovieDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MovieDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wirelessalien.android.moviedb.helper.ConfigHelper;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.BaseActivity;
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class contains some basic functionality that would
 * otherwise be duplicated in multiple fragments.
 */
public class BaseFragment extends Fragment {

    final static String SHOWS_LIST_PREFERENCE = "key_show_shows_grid";
    final static String GRID_SIZE_PREFERENCE = "key_grid_size_number";
    final static String PERSISTENT_FILTERING_PREFERENCE = "key_persistent_filtering";
    @SuppressWarnings("OctalInteger")
    final static int FILTER_REQUEST_CODE = 0002;
    private String API_KEY;
    RecyclerView mShowView;
    ShowBaseAdapter mShowAdapter;
    ShowBaseAdapter mSearchShowAdapter;
    ArrayList<JSONObject> mShowArrayList;
    ArrayList<JSONObject> mSearchShowArrayList;
    LinearLayoutManager mShowLinearLayoutManager;
    HashMap<String, String> mShowGenreList;
    boolean mSearchView;
    SharedPreferences preferences;

    boolean mGenreListLoaded = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        API_KEY = ConfigHelper.getConfigValue(requireContext().getApplicationContext(), "api_key");

        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate( R.menu.filter_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    public void doNetworkWork() {}

    /**
     * Sets up and displays the grid or list view of shows.
     *
     * @param fragmentView the view of the fragment (that the show view will be placed in).
     */
    void showShowList(View fragmentView) {
        mShowView = fragmentView.findViewById(R.id.showRecyclerView);

        // Set the layout of the RecyclerView.
        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
            // If the user changed from a list layout to a grid layout, reload the ShowBaseAdapter.
            if (!(mShowView.getLayoutManager() instanceof GridLayoutManager)) {
                mShowAdapter = new ShowBaseAdapter(mShowArrayList, mShowGenreList,
                        preferences.getBoolean(SHOWS_LIST_PREFERENCE, true));
            }
            GridLayoutManager mShowGridView = new GridLayoutManager(getActivity(),
                    preferences.getInt(GRID_SIZE_PREFERENCE, 3));
            mShowView.setLayoutManager(mShowGridView);
            mShowLinearLayoutManager = mShowGridView;
        } else {
            // If the user changed from a list layout to a grid layout, reload the ShowBaseAdapter.
            if (!(mShowView.getLayoutManager() instanceof LinearLayoutManager)) {
                mShowAdapter = new ShowBaseAdapter(mShowArrayList, mShowGenreList,
                        preferences.getBoolean(SHOWS_LIST_PREFERENCE, true));
            }
            mShowLinearLayoutManager = new LinearLayoutManager(getActivity(),
                    LinearLayoutManager.VERTICAL, false);
            mShowView.setLayoutManager(mShowLinearLayoutManager);
        }

        if (mShowAdapter != null) {
            mShowView.setAdapter(mShowAdapter);
        }
    }

    /**
     * Sets the search view and adapter back to normal.
     */
    public void cancelSearch() {
        mSearchView = false;
        mShowView.setAdapter(mShowAdapter);
    }

    /**
     * Uses Thread to retrieve the id to genre mapping.
     */
    class GenreListThread extends Thread {

        private final String mGenreType;
        private final Handler handler;

        public GenreListThread(String mGenreType, Handler handler) {
            this.mGenreType = mGenreType;
            this.handler = handler;
        }

        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
            String line;
            StringBuilder stringBuilder = new StringBuilder();

            // Load the genre webpage.
            try {
                URL url = new URL("https://api.themoviedb.org/3/genre/"
                        + mGenreType + "/list?api_key=" +
                        API_KEY + BaseActivity.getLanguageParameter(getContext()));

                URLConnection urlConnection = url.openConnection();
                try {
                    BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(
                                    urlConnection.getInputStream()));

                    // Create one long string of the webpage.
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }

                    // Close connection and return the data from the webpage.
                    bufferedReader.close();
                    String response = stringBuilder.toString();

                    // Use handler to post the result back to the main thread
                    handler.post(() -> handleResponse(response));

                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        private void handleResponse(String response) {
            if (isAdded() && response != null && !response.isEmpty()) {
                // Save GenreList to sharedPreferences, this way it can be used anywhere.
                SharedPreferences sharedPreferences = requireContext().getApplicationContext()
                        .getSharedPreferences("GenreList", Context.MODE_PRIVATE);
                SharedPreferences.Editor prefsEditor =
                        sharedPreferences.edit();

                // Convert the JSON data from the webpage to JSONObjects in an ArrayList.
                try {
                    JSONObject reader = new JSONObject(response);
                    JSONArray genreArray = reader.getJSONArray("genres");
                    for (int i = 0; genreArray.optJSONObject(i) != null; i++) {
                        JSONObject websiteData = genreArray.getJSONObject(i);
                        mShowGenreList.put(websiteData.getString("id"),
                                websiteData.getString("name"));

                        // Temporary fix until I find a way to handle this efficiently.
                        prefsEditor.putString(websiteData.getString("id"),
                                websiteData.getString("name"));
                        prefsEditor.apply();
                    }

                    prefsEditor.putString(mGenreType + "GenreJSONArrayList", genreArray.toString());
                    prefsEditor.commit();

                    mShowAdapter.notifyDataSetChanged();
                    mGenreListLoaded = true;
                } catch (JSONException je) {
                    je.printStackTrace();
                }
            }
        }
    }
}
