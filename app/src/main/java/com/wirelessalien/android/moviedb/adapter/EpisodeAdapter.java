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

package com.wirelessalien.android.moviedb.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RatingBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.data.Episode;
import com.wirelessalien.android.moviedb.databinding.EpisodeItemBinding;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.tmdb.account.AddEpisodeRatingThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.DeleteEpisodeRatingThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountStateTvSeason;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder> {

    private final List<Episode> episodes;
    private final Context context;
    int tvShowId;
    int seasonNumber;
    private final GetAccountStateTvSeason accountState;

    private final SharedPreferences preferences;

    public EpisodeAdapter(Context context, List<Episode> episodes, int seasonNumber, int tvShowId) { // Modify this line
        this.context = context;
        this.episodes = episodes;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.tvShowId = tvShowId;
        this.seasonNumber = seasonNumber;
        this.accountState = new GetAccountStateTvSeason(tvShowId, seasonNumber, context);
        this.accountState.start();
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    @NonNull
    @Override
    public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        EpisodeItemBinding binding = EpisodeItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new EpisodeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        Episode episode = episodes.get(position);
        holder.binding.title.setText(episode.getName());
        holder.binding.episodeNumber.setText( "(" + episode.getEpisodeNumber() + ")");
        holder.binding.description.setText(episode.getOverview());
        holder.binding.date.setText(episode.getAirDate());
        holder.binding.runtime.setText( episode.getRuntime() + " minutes");
        holder.binding.averageRating.setText( episode.getVoteAverage() + "/10");
        Picasso.get()
                .load(episode.getPosterPath())
                .placeholder(R.color.md_theme_surface)
                .into(holder.binding.image);

        holder.itemView.setBackgroundColor( Color.TRANSPARENT );

        double rating = accountState.getEpisodeRating(episode.getEpisodeNumber());
        holder.binding.rating.setRating((float) rating / 2);

        MovieDatabaseHelper db = new MovieDatabaseHelper(context);
        if (db.isEpisodeInDatabase(tvShowId, seasonNumber, Collections.singletonList( episode.getEpisodeNumber() ) )) {
            holder.binding.watched.setImageResource( R.drawable.ic_visibility_fill );
        } else {
            holder.binding.watched.setImageResource( R.drawable.ic_visibility );
        }

        holder.binding.watched.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If the episode is in the database, remove it
                if (db.isEpisodeInDatabase(tvShowId, seasonNumber, Collections.singletonList( episode.getEpisodeNumber() ) )) {
                    db.removeEpisodeNumber(tvShowId, seasonNumber, Collections.singletonList( episode.getEpisodeNumber() ) );
                    holder.binding.watched.setImageResource( R.drawable.ic_visibility );
                } else {
                    // If the episode is not in the database, add it
                    db.addEpisodeNumber(tvShowId, seasonNumber, Collections.singletonList( episode.getEpisodeNumber() ) );
                    holder.binding.watched.setImageResource( R.drawable.ic_visibility_fill );
                }
            }
        });

        holder.binding.rateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open a dialog to rate the episode
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                LayoutInflater inflater = LayoutInflater.from(context);
                View dialogView = inflater.inflate( R.layout.rating_dialog, null );
                builder.setView( dialogView );
                final AlertDialog dialog = builder.create();
                dialog.show();

                RatingBar ratingBar = dialogView.findViewById( R.id.ratingBar );
                Button submitButton = dialogView.findViewById( R.id.btnSubmit );
                Button cancelButton = dialogView.findViewById( R.id.btnCancel );
                Button deleteButton = dialogView.findViewById( R.id.btnDelete );

                Handler mainHandler = new Handler( Looper.getMainLooper());

                CompletableFuture.runAsync(() -> {
                    submitButton.setOnClickListener(v1 -> CompletableFuture.runAsync(() -> {
                        double rating = ratingBar.getRating() * 2;
                        new AddEpisodeRatingThreadTMDb(tvShowId, seasonNumber, episode.getEpisodeNumber(),rating, context).start();
                        mainHandler.post(dialog::dismiss);
                    }));

                    deleteButton.setOnClickListener(v12 -> CompletableFuture.runAsync(() -> {
                        new DeleteEpisodeRatingThreadTMDb(tvShowId, seasonNumber, episode.getEpisodeNumber(), context).start();
                        mainHandler.post(dialog::dismiss);
                    }));

                    cancelButton.setOnClickListener(v12 -> dialog.dismiss());
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return (episodes != null) ? episodes.size() : 0;
    }

    public static class EpisodeViewHolder extends RecyclerView.ViewHolder {
        EpisodeItemBinding binding;

        public EpisodeViewHolder(@NonNull EpisodeItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}