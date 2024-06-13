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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.EpisodeAdapter;
import com.wirelessalien.android.moviedb.data.Episode;
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.tmdb.TVSeasonDetailsThread;

import java.text.ParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SeasonDetailsFragment extends Fragment {
    private static final String ARG_TV_SHOW_ID = "tvShowId";
    private static final String ARG_SEASON_NUMBER = "seasonNumber";
    private static final String ARG_TV_SHOW_NAME = "tvShowName";
    private RecyclerView rvEpisodes;
    private MaterialToolbar toolbar;
    private ViewPager2 viewPager;
    private int tvShowId;
    private int seasonNumber;
    private int currentTabNumber = 1;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;
    private EpisodeReminderDatabaseHelper dbHelper;


    public static SeasonDetailsFragment newInstance(int tvShowId, int seasonNumber, String tvShowName) {
        SeasonDetailsFragment fragment = new SeasonDetailsFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TV_SHOW_ID, tvShowId);
        args.putInt(ARG_SEASON_NUMBER, seasonNumber);
        args.putString(ARG_TV_SHOW_NAME, tvShowName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate( R.layout.fragment_tv_season_details, container, false );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(true);

        tvShowId = getArguments().getInt(ARG_TV_SHOW_ID);
        seasonNumber = getArguments().getInt(ARG_SEASON_NUMBER);

        rvEpisodes = view.findViewById(R.id.episodeRecyclerView);
        toolbar = view.findViewById(R.id.toolbar);
        AppBarLayout appBarLayout = view.findViewById(R.id.appBarLayout);
        appBarLayout.setBackgroundColor(Color.TRANSPARENT);

        viewPager = requireActivity().findViewById(R.id.view_pager);
        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentTabNumber = position + 1;
                requireActivity().invalidateOptionsMenu();
            }
        };
        viewPager.registerOnPageChangeCallback(pageChangeCallback);

        ProgressBar progressBar = view.findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);

        CompletableFuture.supplyAsync(() -> {
            TVSeasonDetailsThread thread = new TVSeasonDetailsThread(tvShowId, seasonNumber, getActivity());
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return thread.getEpisodes();
        }).thenAccept(episodes -> {
            requireActivity().runOnUiThread(() -> {
                EpisodeAdapter adapter = new EpisodeAdapter(requireContext(), episodes, currentTabNumber, tvShowId);
                rvEpisodes.setLayoutManager(new LinearLayoutManager(requireContext()));
                rvEpisodes.setAdapter(adapter);
                progressBar.setVisibility(View.GONE);
                requireActivity().invalidateOptionsMenu();
            });
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (viewPager != null) {
            viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate( R.menu.notification_menu, menu );
        dbHelper = new EpisodeReminderDatabaseHelper( requireContext() );

        MenuItem notificationItem = menu.findItem( R.id.action_notification );

        int tvShowId = getArguments().getInt( ARG_TV_SHOW_ID );
        if (isShowInDatabase( tvShowId )) {
            notificationItem.setIcon( R.drawable.ic_notifications_active );
        } else {
            notificationItem.setIcon( R.drawable.ic_add_alert );
        }

        EpisodeAdapter adapter = (EpisodeAdapter) rvEpisodes.getAdapter();

        if (adapter != null) {
            List<Episode> episodes = adapter.getEpisodes();
            Episode latestEpisode = Collections.max( episodes, Comparator.comparingInt( Episode::getEpisodeNumber ) );

            // Parse the air date of the latest episode
            SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd", Locale.US );
            try {
                Date latestEpisodeDate = sdf.parse( latestEpisode.getAirDate() );
                Date currentDate = new Date();

                // If the air date of the latest episode is older than the current date, disable the notification action
                if (latestEpisodeDate != null && latestEpisodeDate.before( currentDate )) {
                    notificationItem.setEnabled( false );
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        super.onCreateOptionsMenu( menu, inflater );
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuItem watchedItem = menu.findItem(R.id.action_watched);

        EpisodeAdapter adapter = (EpisodeAdapter) rvEpisodes.getAdapter();
        if (adapter != null) {
            List<Episode> episodes = adapter.getEpisodes();
            MovieDatabaseHelper db = new MovieDatabaseHelper(requireContext());

            boolean allEpisodesInDatabase = true;
            for (Episode episode : episodes) {
                Map<Integer, List<Integer>> seasonEpisodeNumbers = new HashMap<>();
                seasonEpisodeNumbers.put(currentTabNumber, Collections.singletonList(episode.getEpisodeNumber()));

                if (!db.isEpisodeInDatabase(tvShowId, currentTabNumber, Collections.singletonList( episode.getEpisodeNumber() ) )) {
                    allEpisodesInDatabase = false;
                    break;
                }
            }

            if (allEpisodesInDatabase) {
                watchedItem.setIcon( R.drawable.ic_visibility_fill );
            } else {
                watchedItem.setIcon( R.drawable.ic_visibility );
            }
        }
    }

    public boolean isShowInDatabase(int tvShowId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String selection = EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID + " = ?";
        String[] selectionArgs = {String.valueOf( tvShowId )};

        Cursor cursor = db.query(
                EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS,
                null, selection, selectionArgs, null, null, null );

        boolean exists = (cursor.getCount() > 0);
        cursor.close();

        return exists;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_notification) {

            dbHelper = new EpisodeReminderDatabaseHelper( requireContext() );

            if (isShowInDatabase( getArguments().getInt( ARG_TV_SHOW_ID ) )) {
                dbHelper.deleteData( getArguments().getInt( ARG_TV_SHOW_ID ) );
                String message = getString( R.string.removed_from_reminder, ARG_TV_SHOW_NAME );
                Toast.makeText( requireContext(), message, Toast.LENGTH_SHORT ).show();
                item.setIcon( R.drawable.ic_add_alert );
                return true;
            }

            SQLiteDatabase db = dbHelper.getWritableDatabase();

            EpisodeAdapter adapter = (EpisodeAdapter) rvEpisodes.getAdapter();
            if (adapter != null) {

                for (Episode episode : adapter.getEpisodes()) {

                    ContentValues values = new ContentValues();
                    int tvShowId = getArguments().getInt( ARG_TV_SHOW_ID );
                    values.put( EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID, tvShowId );

                    String tvShowName = getArguments().getString( ARG_TV_SHOW_NAME );

                    values.put( EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME, tvShowName );
                    values.put( EpisodeReminderDatabaseHelper.COLUMN_NAME, episode.getName() );
                    values.put( EpisodeReminderDatabaseHelper.COLUMN_DATE, episode.getAirDate() );
                    values.put( EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER, episode.getEpisodeNumber() );

                    long newRowId = db.insert( EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS, null, values );

                    if (newRowId == -1) {
                        String message = getString( R.string.error_reminder_episode, tvShowName );
                        Toast.makeText( requireContext(), message, Toast.LENGTH_SHORT ).show();
                        return true;
                    }
                }

                String message = getString( R.string.get_notified_for_episode, getArguments().getString( ARG_TV_SHOW_NAME ) );
                Toast.makeText( requireContext(), message, Toast.LENGTH_SHORT ).show();
                item.setIcon( R.drawable.ic_notifications_active );
                return true;
            }
            return super.onOptionsItemSelected( item );
        } else if (item.getItemId() == R.id.action_watched) {
            EpisodeAdapter adapter = (EpisodeAdapter) rvEpisodes.getAdapter();
            if (adapter != null) {
                List<Episode> episodes = adapter.getEpisodes();
                MovieDatabaseHelper db = new MovieDatabaseHelper(requireContext());

                boolean allEpisodesInDatabase = true;
                for (Episode episode : episodes) {
                    if (!db.isEpisodeInDatabase(tvShowId, currentTabNumber, Collections.singletonList(episode.getEpisodeNumber()))) {
                        allEpisodesInDatabase = false;
                        break;
                    }
                }

                if (!allEpisodesInDatabase) {
                    for (Episode episode : episodes) {
                        if (!db.isEpisodeInDatabase(tvShowId, currentTabNumber, Collections.singletonList(episode.getEpisodeNumber()))) {
                            db.addEpisodeNumber(tvShowId, currentTabNumber, Collections.singletonList(episode.getEpisodeNumber()));
                        }
                    }
                    item.setIcon( R.drawable.ic_visibility_fill );
                    Toast.makeText(requireContext(), R.string.episodes_removed, Toast.LENGTH_SHORT).show();
                } else {
                    for (Episode episode : episodes) {
                        db.removeEpisodeNumber(tvShowId, currentTabNumber, Collections.singletonList(episode.getEpisodeNumber()));
                    }
                    item.setIcon( R.drawable.ic_visibility );
                    Toast.makeText(requireContext(), R.string.episodes_added, Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        } else {
            return super.onOptionsItemSelected( item );
        }
    }
}