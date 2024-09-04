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

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.carousel.CarouselLayoutManager;
import com.google.android.material.carousel.CarouselSnapHelper;
import com.google.android.material.carousel.FullScreenCarouselStrategy;
import com.google.android.material.carousel.HeroCarouselStrategy;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.BaseActivity;
import com.wirelessalien.android.moviedb.activity.PersonActivity;
import com.wirelessalien.android.moviedb.adapter.NowPlayingMovieAdapter;
import com.wirelessalien.android.moviedb.adapter.TrendingPagerAdapter;
import com.wirelessalien.android.moviedb.helper.ConfigHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HomeFragment extends BaseFragment {

    private String mSearchQuery;
    private int currentSearchPage = 0;
    private boolean loading = true;
    private RecyclerView tvShowView;
    private RecyclerView mUpcomingTVShowView;
    private RecyclerView mUpcomingMovieView;
    private String api_key;
    private boolean mShowListLoaded = false;
    private NowPlayingMovieAdapter mShowAdapter;
    private ArrayList<JSONObject> mTVShowArrayList;
    private ArrayList<JSONObject> mShowArrayList;
    private ArrayList<JSONObject> mUpcomingTVShowArrayList;
    private ArrayList<JSONObject> mUpcomingMovieArrayList;
    private NowPlayingMovieAdapter mTVShowAdapter;
    private NowPlayingMovieAdapter mUpcomingMovieAdapter;
    private NowPlayingMovieAdapter mUpcomingTVAdapter;
    private ViewPager2 viewPager;
    private RecyclerView trandingRv;
    private SearchBar searchBar;
    private SearchView searchView;
    private RecyclerView searchResultsRecyclerView;
    private NowPlayingMovieAdapter mSearchShowAdapter;
    private ArrayList<JSONObject> mSearchShowArrayList;

    public HomeFragment() {
        // Required empty public constructor
    }

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );

        api_key = ConfigHelper.getConfigValue(requireContext().getApplicationContext(), "api_read_access_token");
        preferences = PreferenceManager.getDefaultSharedPreferences( requireContext() );

        createShowList();
    }

    @Override
    public void doNetworkWork() {
        if (!mShowListLoaded) {
            fetchNowPlayingMovies();
            fetchNowPlayingTVShows();
            fetchTrendingList();
            fetchUpcomingMovies();
            fetchUpcomingTVShows();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View fragmentView = inflater.inflate( R.layout.fragment_home, container, false );
        showMovieList( fragmentView );
        showTVShowList( fragmentView );
        showTrendingList(fragmentView);
        showUpcomingMovieList(fragmentView);
        showUpcomingTVShowList(fragmentView);

        FloatingActionButton fab = requireActivity().findViewById( R.id.fab );
        fab.setVisibility( View.GONE );


        searchBar = fragmentView.findViewById( R.id.searchbar );
        searchView = requireActivity().findViewById( R.id.search_view );
        searchResultsRecyclerView = requireActivity().findViewById( R.id.search_results_recycler_view );

        searchBar.setOnClickListener( v -> searchView.show() );

        searchView.getEditText().addTextChangedListener(new TextWatcher() {
            private final Handler handler = new Handler( Looper.getMainLooper());
            private Runnable workRunnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (workRunnable != null) {
                    handler.removeCallbacks(workRunnable);
                }
                workRunnable = () -> performSearch(s.toString());
                handler.postDelayed(workRunnable, 300);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Setup RecyclerView and Adapter for search results
        mSearchShowArrayList = new ArrayList<>();
        mSearchShowAdapter = new NowPlayingMovieAdapter( mSearchShowArrayList );
        GridLayoutManager gridLayoutManager = new GridLayoutManager( getContext(), 3 );
        searchResultsRecyclerView.setLayoutManager( gridLayoutManager );
        searchResultsRecyclerView.setAdapter( mSearchShowAdapter );
        searchResultsRecyclerView.addOnScrollListener( new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                    int visibleItemCount = gridLayoutManager.getChildCount();
                    int totalItemCount = gridLayoutManager.getItemCount();
                    int pastVisibleItems = gridLayoutManager.findFirstVisibleItemPosition();

                    if (!loading && (visibleItemCount + pastVisibleItems) >= totalItemCount) {
                        loading = true;
                        currentSearchPage++;
                        performSearch( searchView.getEditText().getText().toString() );
                    }
                }
            }
        } );

        Button peopleBtn = fragmentView.findViewById( R.id.peopleBtn );
        peopleBtn.setOnClickListener( v -> {
            Intent intent = new Intent( requireContext(), PersonActivity.class );
            startActivity( intent );
        } );


        return fragmentView;
    }

    private void showTrendingList(View fragmentView) {
        trandingRv = fragmentView.findViewById(R.id.trendingViewPager);
        CarouselLayoutManager layoutManager = new CarouselLayoutManager();
        trandingRv.setLayoutManager(layoutManager);
        CarouselSnapHelper snapHelper = new CarouselSnapHelper();
        snapHelper.attachToRecyclerView(trandingRv);
        TrendingPagerAdapter adapter = new TrendingPagerAdapter(new ArrayList<>());
        trandingRv.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        FloatingActionButton fab = requireActivity().findViewById( R.id.fab );
        fab.setVisibility( View.GONE );
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu( menu, inflater );
        MenuItem item = menu.findItem( R.id.action_search );
        if (item != null) {
            item.setVisible( false );
            item.setEnabled( false );
        }
    }

    private void performSearch(String query) {
        if (!query.equals(mSearchQuery)) {
            currentSearchPage = 1;
            mSearchShowArrayList.clear();
        }
        mSearchQuery = query;
        searchAsync(query);
    }

    private void createShowList() {
        mShowArrayList = new ArrayList<>();
        mShowAdapter = new NowPlayingMovieAdapter(mShowArrayList);

        mTVShowArrayList = new ArrayList<>();
        mTVShowAdapter = new NowPlayingMovieAdapter(mTVShowArrayList);

        mSearchShowArrayList = new ArrayList<>();
        mSearchShowAdapter = new NowPlayingMovieAdapter(mSearchShowArrayList);

        mUpcomingTVShowArrayList = new ArrayList<>();
        mUpcomingTVAdapter = new NowPlayingMovieAdapter(mUpcomingTVShowArrayList);

        mUpcomingMovieArrayList = new ArrayList<>();
        mUpcomingMovieAdapter = new NowPlayingMovieAdapter(mUpcomingMovieArrayList);

        ((BaseActivity) requireActivity()).checkNetwork();
    }

    private void setupRecyclerView(RecyclerView recyclerView, LinearLayoutManager layoutManager, RecyclerView.Adapter<?> adapter) {
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
    }

    private void showMovieList(final View fragmentView) {
        mShowView = fragmentView.findViewById(R.id.nowPlayingRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false);
        setupRecyclerView(mShowView, layoutManager, mShowAdapter);
    }

    private void showTVShowList(final View fragmentView) {
        tvShowView = fragmentView.findViewById(R.id.nowPlayingTVRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false);
        setupRecyclerView(tvShowView, layoutManager, mTVShowAdapter);
    }

    private void showUpcomingTVShowList(final View fragmentView) {
        mUpcomingTVShowView = fragmentView.findViewById(R.id.upcomingTVRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false);
        setupRecyclerView(mUpcomingTVShowView, layoutManager, mUpcomingTVAdapter);
    }

    private void showUpcomingMovieList(final View fragmentView) {
        mUpcomingMovieView = fragmentView.findViewById(R.id.upcomingMovieRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false);
        setupRecyclerView(mUpcomingMovieView, layoutManager, mUpcomingMovieAdapter);
    }

    public void search(String query) {
        mSearchShowArrayList = new ArrayList<>();
        mSearchShowAdapter = new NowPlayingMovieAdapter(mSearchShowArrayList);

        currentSearchPage = 1;
        mSearchQuery = query;
        searchAsync(query);
    }

    public void cancelSearch() {
        mSearchView = false;
        mShowView.setAdapter( mShowAdapter );
    }

    private void fetchNowPlayingMovies() {
        CompletableFuture.supplyAsync(() -> {
            String response = null;
            try {
                String language = Locale.getDefault().getLanguage();
                URL url = new URL("https://api.themoviedb.org/3/movie/now_playing?language=" + language + "&page=1");
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Content-Type", "application/json;charset=utf-8")
                        .addHeader("Authorization", "Bearer " + api_key)
                        .build();
                try (Response res = client.newCall(request).execute()) {
                    if (res.body() != null) {
                        response = res.body().string();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return response;
        }).thenAccept(response -> {
            if (isAdded() && response != null && !response.isEmpty()) {
                handleMovieResponse(response);
            }
        });
    }

    private void handleMovieResponse(String response) {
        if (isAdded() && response != null && !response.isEmpty()) {
            try {
                JSONObject reader = new JSONObject( response );
                JSONArray arrayData = reader.getJSONArray( "results" );
                for (int i = 0; i < arrayData.length(); i++) {
                    JSONObject websiteData = arrayData.getJSONObject( i );
                    mShowArrayList.add( websiteData );
                }

                if (mShowView != null) {
                    mShowView.setAdapter( mShowAdapter );

                }
                mShowListLoaded = true;
                hideProgressBar();
            } catch (JSONException je) {
                je.printStackTrace();
                hideProgressBar();
            }
        }
        loading = false;
    }


    private void fetchNowPlayingTVShows() {
        CompletableFuture.supplyAsync(() -> {
            String response = null;
            try {
                String language = Locale.getDefault().getLanguage();
                URL url = new URL("https://api.themoviedb.org/3/tv/airing_today?language=" + language + "&page=1");
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Content-Type", "application/json;charset=utf-8")
                        .addHeader("Authorization", "Bearer " + api_key)
                        .build();
                try (Response res = client.newCall(request).execute()) {
                    if (res.body() != null) {
                        response = res.body().string();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return response;
        }).thenAccept(response -> {
            if (isAdded() && response != null && !response.isEmpty()) {
                handleTVResponse(response);
            }
        });
    }

    private void handleTVResponse(String response) {
        if (isAdded() && response != null && !response.isEmpty()) {

            try {
                JSONObject reader = new JSONObject( response );
                JSONArray arrayData = reader.getJSONArray( "results" );
                for (int i = 0; i < arrayData.length(); i++) {
                    JSONObject websiteData = arrayData.getJSONObject( i );
                    mTVShowArrayList.add( websiteData );

                }

                if (tvShowView != null) {
                    tvShowView.setAdapter( mTVShowAdapter );

                }
                mShowListLoaded = true;
                hideProgressBar();
            } catch (JSONException je) {
                je.printStackTrace();
                hideProgressBar();
            }
        }
        loading = false;
    }

    private void fetchUpcomingTVShows() {
        CompletableFuture.supplyAsync(() -> {
            String response = null;
            try {
                String language = Locale.getDefault().getLanguage();
                URL url = new URL("https://api.themoviedb.org/3/tv/on_the_air?language=" + language + "&page=1");
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Content-Type", "application/json;charset=utf-8")
                        .addHeader("Authorization", "Bearer " + api_key)
                        .build();
                try (Response res = client.newCall(request).execute()) {
                    if (res.body() != null) {
                        response = res.body().string();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return response;
        }).thenAccept(response -> {
            if (isAdded() && response != null && !response.isEmpty()) {
                handleUpcomingTVResponse(response);
            }
        });
    }

    private void handleUpcomingTVResponse(String response) {
        if (isAdded() && response != null && !response.isEmpty()) {

            try {
                JSONObject reader = new JSONObject( response );
                JSONArray arrayData = reader.getJSONArray( "results" );
                for (int i = 0; i < arrayData.length(); i++) {
                    JSONObject websiteData = arrayData.getJSONObject( i );
                    mUpcomingTVShowArrayList.add( websiteData );

                }

                if (mUpcomingTVShowView != null) {
                    mUpcomingTVShowView.setAdapter( mUpcomingTVAdapter );

                }
                mShowListLoaded = true;
                hideProgressBar();
            } catch (JSONException je) {
                je.printStackTrace();
                hideProgressBar();
            }
        }
        loading = false;
    }

    private void fetchUpcomingMovies() {
        CompletableFuture.supplyAsync(() -> {
            String response = null;
            try {
                String language = Locale.getDefault().getLanguage();
                URL url = new URL("https://api.themoviedb.org/3/movie/upcoming?language=" + language + "&page=1");
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Content-Type", "application/json;charset=utf-8")
                        .addHeader("Authorization", "Bearer " + api_key)
                        .build();
                try (Response res = client.newCall(request).execute()) {
                    if (res.body() != null) {
                        response = res.body().string();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return response;
        }).thenAccept(response -> {
            if (isAdded() && response != null && !response.isEmpty()) {
                handleUpcomingMovieResponse(response);
            }
        });
    }

    private void handleUpcomingMovieResponse(String response) {
        if (isAdded() && response != null && !response.isEmpty()) {

            try {
                JSONObject reader = new JSONObject( response );
                JSONArray arrayData = reader.getJSONArray( "results" );
                for (int i = 0; i < arrayData.length(); i++) {
                    JSONObject websiteData = arrayData.getJSONObject( i );
                    mUpcomingMovieArrayList.add( websiteData );

                }

                if (mUpcomingMovieView != null) {
                    mUpcomingMovieView.setAdapter( mUpcomingMovieAdapter );

                }
                mShowListLoaded = true;
                hideProgressBar();
            } catch (JSONException je) {
                je.printStackTrace();
                hideProgressBar();
            }
        }
        loading = false;
    }


    private void fetchTrendingList() {
        CompletableFuture.supplyAsync(() -> {
            String response = null;
            try {
                String language = Locale.getDefault().getLanguage();
                URL url = new URL("https://api.themoviedb.org/3/trending/all/day?language=" + language);
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Content-Type", "application/json;charset=utf-8")
                        .addHeader("Authorization", "Bearer " + api_key)
                        .build();
                try (Response res = client.newCall(request).execute()) {
                    if (res.body() != null) {
                        response = res.body().string();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return response;
        }).thenAccept(response -> {
            if (isAdded() && response != null && !response.isEmpty()) {
                handleTrendingResponse(response);
            }
        });
    }

    private void handleTrendingResponse(String response) {
        if (isAdded() && response != null && !response.isEmpty()) {
            ArrayList<JSONObject> trendingArrayList = new ArrayList<>();
            try {
                JSONObject reader = new JSONObject(response);
                JSONArray arrayData = reader.getJSONArray("results");
                for (int i = 0; i < arrayData.length(); i++) {
                    JSONObject websiteData = arrayData.getJSONObject(i);
                    trendingArrayList.add(websiteData);
                }

                TrendingPagerAdapter adapter = (TrendingPagerAdapter) trandingRv.getAdapter();
                if (adapter != null) {
                    adapter.updateData(trendingArrayList);
                    adapter.notifyDataSetChanged();
                }

                hideProgressBar();
            } catch (JSONException je) {
                je.printStackTrace();
                hideProgressBar();
            }
        }
    }

    private void searchAsync(String query) {
        Optional<ProgressBar> progressBar = Optional.ofNullable(searchView.findViewById(R.id.search_progress_bar));
        progressBar.ifPresent(bar -> bar.setVisibility(View.VISIBLE));
        CompletableFuture.supplyAsync(() -> doInBackground(query))
                .thenAccept(response -> {
                    if (response != null) {
                        requireActivity().runOnUiThread(() -> {
                            onPostExecute(response);
                            hideSearchProgressBar();
                        });
                    } else {
                        requireActivity().runOnUiThread(this::hideSearchProgressBar);
                    }
                });
    }

    private String doInBackground(String query) {
        String response = null;
        try {
            URL url = new URL("https://api.themoviedb.org/3/search/multi?"
                    + "query=" + query + "&page=" + currentSearchPage);

            Log.d("Search URL", url.toString());
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer " + api_key)
                    .build();

            try (Response res = client.newCall(request).execute()) {
                if (res.body() != null) {
                    response = res.body().string();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return response;
    }

    private void onPostExecute(String response) {
        try {
            JSONObject reader = new JSONObject(response);
            JSONArray arrayData = reader.getJSONArray("results");
            for (int i = 0; i < arrayData.length(); i++) {
                JSONObject movieData = arrayData.getJSONObject(i);
                mSearchShowArrayList.add(movieData);
            }
            mSearchShowAdapter.notifyDataSetChanged();
            loading = false;
        } catch (JSONException je) {
            je.printStackTrace();
        }
    }

    private void hideProgressBar() {
        if (isAdded()) {
            Optional<ProgressBar> progressBar = Optional.ofNullable(requireActivity().findViewById(R.id.progressBar));
            progressBar.ifPresent(bar -> bar.setVisibility(View.GONE));
        }
    }

    private void hideSearchProgressBar() {
        Optional<ProgressBar> progressBar = Optional.ofNullable(searchView.findViewById(R.id.search_progress_bar));
        progressBar.ifPresent(bar -> bar.setVisibility(View.GONE));
    }

}
