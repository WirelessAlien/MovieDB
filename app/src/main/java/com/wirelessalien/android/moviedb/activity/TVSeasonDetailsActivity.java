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

import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.tmdb.TVSeasonDetailsThread;
import com.wirelessalien.android.moviedb.adapter.EpisodeAdapter;

import java.util.concurrent.CompletableFuture;

public class TVSeasonDetailsActivity extends AppCompatActivity {

    private TextView tvSeasonName;
    private TextView tvSeasonOverview;
    private ImageView ivSeasonPoster;
    private TextView episodeNumber;
    private TextView airDate;

    private RatingBar voteAverage;
    private RecyclerView rvEpisodes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_season_details);

        tvSeasonName = findViewById(R.id.title);
        tvSeasonOverview = findViewById(R.id.description);
        ivSeasonPoster = findViewById(R.id.image);
        episodeNumber = findViewById(R.id.episodeCount);
        airDate = findViewById(R.id.date);
        voteAverage = findViewById(R.id.rating);
        rvEpisodes = findViewById(R.id.episodeRecyclerView);
        ProgressBar progressBar = findViewById(R.id.progressBar); // Assuming you have a ProgressBar in your layout

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

            tvSeasonName.setText(thread.getSeasonName());
            tvSeasonOverview.setText(thread.getSeasonOverview());

            Picasso.get()
                    .load("https://image.tmdb.org/t/p/w500" + thread.getSeasonPosterPath())
                    .placeholder(R.drawable.ic_broken_image)
                    .into(ivSeasonPoster);

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