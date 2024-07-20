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

package com.wirelessalien.android.moviedb.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.BaseActivity;
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WatchlistFragment extends BaseFragment {

    private String mListType;

    private int visibleThreshold = 3; // Three times the amount of items in a row
    private int currentPage = 0;

    private int previousTotal = 0;
    private boolean loading = true;
    private int pastVisibleItems;
    private int visibleItemCount;
    private int totalItemCount;
    private volatile boolean isLoadingData = false;

    private boolean mShowListLoaded = false;

    public WatchlistFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        int gridSizePreference = preferences.getInt(GRID_SIZE_PREFERENCE, 3);
        visibleThreshold = gridSizePreference * gridSizePreference;

        createShowList(mListType);
    }

    @Override
    public void doNetworkWork() {
        if (!mShowListLoaded) {
            new WatchlistFragment.WatchListThread( mListType, 1).start();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View fragmentView = inflater.inflate(R.layout.fragment_show, container, false);
        // Initialize mListType with "movie" on first load

        mListType = "movie";
        new WatchListThread(mListType, 1).start();
        showShowList(fragmentView);

        FloatingActionButton fab = requireActivity().findViewById(R.id.fab);
        updateFabIcon(fab, mListType);
        fab.setOnClickListener(v -> toggleListTypeAndLoad());
        return fragmentView;
    }

    private void toggleListTypeAndLoad() {
        if (isLoadingData) {
            Toast.makeText(getContext(), R.string.loading_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        mShowArrayList.clear();
        currentPage = 1;
        mListType = "movie".equals(mListType) ? "tv" : "movie";
        new WatchlistFragment.WatchListThread(mListType, 1).start();
        FloatingActionButton fab = requireActivity().findViewById(R.id.fab);
        updateFabIcon(fab, mListType);
    }

    @Override
    public void onResume() {
        super.onResume();
        FloatingActionButton fab = requireActivity().findViewById(R.id.fab);
        fab.setVisibility(View.VISIBLE);
        updateFabIcon(fab, mListType);
        fab.setOnClickListener(v -> toggleListTypeAndLoad());
    }

    private void updateFabIcon(FloatingActionButton fab, String listType) {
        if ("movie".equals(listType)) {
            fab.setImageResource(R.drawable.ic_movie);
        } else {
            fab.setImageResource(R.drawable.ic_tv_show);
        }
    }


    /**
     * Loads a list of shows from the API.
     *
     */
    private void createShowList(String mode) {

        // Create a MovieBaseAdapter and load the first page
        mShowArrayList = new ArrayList<>();
        mShowGenreList = new HashMap<>();
        mShowAdapter = new ShowBaseAdapter(mShowArrayList, mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, false), false);

        ((BaseActivity) requireActivity()).checkNetwork();

    }

    /**
     * Visualises the list of shows on the screen.
     *
     * @param fragmentView the view to attach the ListView to.
     */
    void showShowList(final View fragmentView) {
        super.showShowList(fragmentView);

        // Dynamically load new pages when user scrolls down.
        mShowView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) { // Check for scroll down and if user is actively scrolling.
                    visibleItemCount = mShowLinearLayoutManager.getChildCount();
                    totalItemCount = mShowLinearLayoutManager.getItemCount();
                    pastVisibleItems = mShowLinearLayoutManager.findFirstVisibleItemPosition();

                    if (loading) {
                        if (totalItemCount > previousTotal) {
                            loading = false;
                            previousTotal = totalItemCount;
                            currentPage++;
                        }
                    }

                    int threshold = visibleThreshold;
                    if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
                        // It is a grid view, so the threshold should be bigger.
                        int gridSizePreference = preferences.getInt(GRID_SIZE_PREFERENCE, 3);
                        threshold = gridSizePreference * gridSizePreference;
                    }

                    // When no new pages are being loaded,
                    // but the user is at the end of the list, load the new page.
                    if (!loading && (visibleItemCount + pastVisibleItems + threshold) >= totalItemCount) {
                        // Load the next page of the content in the background.
                        if (mShowArrayList.size() > 0) {
                            new WatchlistFragment.WatchListThread(mListType, Integer.parseInt(String.valueOf(currentPage))).start();

                        }
                        loading = true;
                        currentPage++;
                    }
                }
            }
        });
    }


    private class WatchListThread extends Thread {
        private final Handler handler;
        private final String listType;
        private final int page;

        public WatchListThread(String listType, int page) {
            handler = new Handler(Looper.getMainLooper());
            this.listType = listType;
            this.page = page;
        }

        @Override
        public void run() {
            isLoadingData = true;

            if (!isAdded()) {
                return;
            }
            handler.post(() -> {
                if (isAdded()) {
                    Optional<ProgressBar> progressBar = Optional.ofNullable(requireActivity().findViewById(R.id.progressBar));
                    progressBar.ifPresent(bar -> bar.setVisibility(View.VISIBLE));
                }
            });

            String access_token = preferences.getString("access_token", "");
            String accountId = preferences.getString("account_id", "");
            String url = "https://api.themoviedb.org/4/account/" + accountId + "/" + listType + "/watchlist?page=" + page;
            Log.d("WatchlistFragment", "URL: " + url);

            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer " + access_token)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = null;
                if (response.body() != null) {
                    responseBody = response.body().string();
                }
                handleResponse(responseBody);
            } catch (IOException e) {
                e.printStackTrace();
                handler.post(() -> {
                    if (isAdded()) {
                        Optional<ProgressBar> progressBar = Optional.ofNullable(requireActivity().findViewById(R.id.progressBar));
                        progressBar.ifPresent(bar -> bar.setVisibility(View.GONE));
                    }
                });
            } finally {
                isLoadingData = false;
            }
        }

        private void handleResponse(String response) {
            handler.post(() -> {
                if (isAdded() && response != null && !response.isEmpty()) {
                    // Keep the user at the same position in the list.
                    int position;
                    try {
                        position = mShowLinearLayoutManager.findFirstVisibleItemPosition();
                    } catch (NullPointerException npe) {
                        position = 0;
                    }


                    // Convert the JSON webpage to JSONObjects
                    // Add the JSONObjects to the list with movies/series.
                    try {
                        JSONObject reader = new JSONObject(response);
                        JSONArray arrayData = reader.getJSONArray("results");
                        for (int i = 0; i < arrayData.length(); i++) {
                            JSONObject websiteData = arrayData.getJSONObject(i);
                            mShowArrayList.add(websiteData);
                        }

                        // Reload the adapter (with the new page)
                        // and set the user to his old position.
                        if (mShowView != null) {
                            mShowView.setAdapter( mShowAdapter );
                            mShowView.scrollToPosition( position );
                        }
                        mShowListLoaded = true;
                        Optional<ProgressBar> progressBar = Optional.ofNullable(requireActivity().findViewById(R.id.progressBar));
                        progressBar.ifPresent(bar -> bar.setVisibility(View.GONE));
                    } catch (JSONException je) {
                        je.printStackTrace();
                        Optional<ProgressBar> progressBar = Optional.ofNullable(requireActivity().findViewById(R.id.progressBar));
                        progressBar.ifPresent(bar -> bar.setVisibility(View.GONE));
                    }
                }
                loading = false;
            });
        }
    }
}
