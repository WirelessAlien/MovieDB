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

import android.os.Bundle;
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
import com.wirelessalien.android.moviedb.tmdb.TVSeasonDetailsThread;

import java.util.concurrent.CompletableFuture;

public class TVSeasonDetailsActivity extends AppCompatActivity {

    private TextView tvSeasonOverview;
    private ImageView ivSeasonPoster;
    private TextView episodeNumber;
    private TextView airDate;
    private MaterialToolbar toolbar;
    private RatingBar voteAverage;
    private RecyclerView rvEpisodes;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_season_details);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar( toolbar);
        tvSeasonOverview = findViewById(R.id.description);
//        ivSeasonPoster = findViewById(R.id.image);
        episodeNumber = findViewById(R.id.episodeCount);
        airDate = findViewById(R.id.date);
        voteAverage = findViewById(R.id.rating);
        rvEpisodes = findViewById(R.id.episodeRecyclerView);
        ProgressBar progressBar = findViewById(R.id.progressBar);

        int tvShowId = getIntent().getIntExtra("tvShowId", -1);
        int seasonNumber = getIntent().getIntExtra("seasonNumber", -1);

        progressBar.setVisibility( View.VISIBLE); // Show the ProgressBar

        CompletableFuture.supplyAsync(() -> {
            TVSeasonDetailsThread thread = new TVSeasonDetailsThread(tvShowId, seasonNumber);
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
        })).exceptionally(throwable -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE); // Hide the ProgressBar
                Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
            });
            return null;
        });
    }
}