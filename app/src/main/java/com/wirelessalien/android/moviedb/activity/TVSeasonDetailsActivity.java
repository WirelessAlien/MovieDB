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

package com.wirelessalien.android.moviedb.activity;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.EpisodeAdapter;
import com.wirelessalien.android.moviedb.data.Episode;
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper;
import com.wirelessalien.android.moviedb.tmdb.TVSeasonDetailsThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class TVSeasonDetailsActivity extends AppCompatActivity {

    private TextView tvSeasonOverview;
    private ImageView ivSeasonPoster;
    private TextView episodeNumber;
    private TextView airDate;
    private MaterialToolbar toolbar;
    private RatingBar voteAverage;
    private RecyclerView rvEpisodes;
    private int tvShowId;
    private String showName;
    private final boolean added = false;
    private EpisodeReminderDatabaseHelper dbHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_season_details);

        Thread.setDefaultUncaughtExceptionHandler( (thread, throwable) -> {
            StringWriter crashLog = new StringWriter();
            throwable.printStackTrace(new PrintWriter(crashLog));

            try {
                String fileName = "Crash_Log.txt";
                File targetFile = new File(getApplicationContext().getFilesDir(), fileName);
                FileOutputStream fileOutputStream = new FileOutputStream(targetFile);
                fileOutputStream.write(crashLog.toString().getBytes());
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            android.os.Process.killProcess(android.os.Process.myPid());
        } );

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar( toolbar);
        tvSeasonOverview = findViewById(R.id.description);
//        ivSeasonPoster = findViewById(R.id.image);
        episodeNumber = findViewById(R.id.episodeCount);
        airDate = findViewById(R.id.date);
        voteAverage = findViewById(R.id.rating);
        rvEpisodes = findViewById(R.id.episodeRecyclerView);
        ProgressBar progressBar = findViewById(R.id.progressBar);

        tvShowId = getIntent().getIntExtra("tvShowId", -1);
        int seasonNumber = getIntent().getIntExtra("seasonNumber", -1);
        showName = getIntent().getStringExtra("tvShowName");
        Log.d( "TVSeasonDetailsActivity", "onCreate: " + tvShowId + " " + showName);

        progressBar.setVisibility( View.VISIBLE); // Show the ProgressBar

        CompletableFuture.supplyAsync(() -> {
            TVSeasonDetailsThread thread = new TVSeasonDetailsThread(tvShowId, seasonNumber, this);
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return thread;
        }).thenAccept(thread -> runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE); // Hide the ProgressBar

            toolbar.setTitle(thread.getSeasonName());
            tvSeasonOverview.setText(thread.getSeasonOverview());

//            Picasso.get()
//                    .load("https://image.tmdb.org/t/p/w500" + thread.getSeasonPosterPath())
//                    .placeholder(R.drawable.ic_broken_image)
//                    .into(ivSeasonPoster);

            voteAverage.setRating((float) thread.getSeasonVoteAverage() / 2);
            episodeNumber.setText("Episodes: " + thread.getEpisodes().size());
            airDate.setText("Air Date: " + thread.getEpisodes().get(0).getAirDate());

            EpisodeAdapter adapter = new EpisodeAdapter(this, thread.getEpisodes());
            rvEpisodes.setLayoutManager(new LinearLayoutManager(this));
            rvEpisodes.setAdapter(adapter);

            MenuItem notificationItem = toolbar.getMenu().findItem(R.id.action_notification);
            List<Episode> episodes = adapter.getEpisodes();
            Episode latestEpisode = Collections.max(episodes, Comparator.comparingInt(Episode::getEpisodeNumber));

            // Parse the air date of the latest episode
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            try {
                Date latestEpisodeDate = sdf.parse(latestEpisode.getAirDate());
                Date currentDate = new Date();

                // If the air date of the latest episode is older than the current date, disable the notification action
                if (latestEpisodeDate != null && latestEpisodeDate.before(currentDate)) {
                    notificationItem.setEnabled(false);
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        })).exceptionally(throwable -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE); // Hide the ProgressBar
                Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
            });
            return null;
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d("TVSeasonDetailsActivity", "onCreateOptionsMenu: Start");
        getMenuInflater().inflate(R.menu.notification_menu, menu);
        dbHelper = new EpisodeReminderDatabaseHelper(this);

        MenuItem notificationItem = menu.findItem(R.id.action_notification);

        if (isShowInDatabase(tvShowId)) {
            notificationItem.setIcon(R.drawable.ic_notifications_active);
        } else {
            notificationItem.setIcon(R.drawable.ic_add_alert);
        }

        return true;
    }

    public boolean isShowInDatabase(int tvShowId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String selection = EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID + " = ?";
        String[] selectionArgs = { String.valueOf(tvShowId) };

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

            dbHelper = new EpisodeReminderDatabaseHelper(this);

            if (isShowInDatabase(tvShowId)) {
                dbHelper.deleteData(tvShowId);
                Toast.makeText(this, showName + "is removed from reminder", Toast.LENGTH_SHORT).show();
                item.setIcon(R.drawable.ic_add_alert);
                return true;
            }

            SQLiteDatabase db = dbHelper.getWritableDatabase();

            EpisodeAdapter adapter = (EpisodeAdapter) rvEpisodes.getAdapter();
            if (adapter != null) {

                for (Episode episode : adapter.getEpisodes()) {

                    ContentValues values = new ContentValues();
                    values.put(EpisodeReminderDatabaseHelper.COLUMN_MOVIE_ID, tvShowId);
                    values.put(EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME, showName);
                    values.put(EpisodeReminderDatabaseHelper.COLUMN_NAME, episode.getName());
                    values.put(EpisodeReminderDatabaseHelper.COLUMN_DATE, episode.getAirDate());
                    values.put(EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER, episode.getEpisodeNumber());

                    long newRowId = db.insert(EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS, null, values);

                    if (newRowId == -1) {
                        Toast.makeText(this, "Error saving reminder for " + showName, Toast.LENGTH_SHORT).show();
                        return true; // Return early if there was an error
                    }
                }
                Toast.makeText(this, "You will get notified when episodes of " + showName + "release", Toast.LENGTH_SHORT).show();
                item.setIcon(R.drawable.ic_notifications_active);
            }

            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}