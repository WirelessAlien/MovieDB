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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.icu.text.DateFormat;
import android.icu.text.SimpleDateFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.data.Episode;
import com.wirelessalien.android.moviedb.data.EpisodeDbDetails;
import com.wirelessalien.android.moviedb.databinding.EpisodeItemBinding;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.tmdb.account.AddEpisodeRatingThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.DeleteEpisodeRatingThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountStateTvSeason;

import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder> {

    private final List<Episode> episodes;
    private final Context context;
    int tvShowId;
    int seasonNumber;
    private final GetAccountStateTvSeason accountState;
    private static final String HD_IMAGE_SIZE = "key_hq_images";


    public EpisodeAdapter(Context context, List<Episode> episodes, int seasonNumber, int tvShowId) {
        this.context = context;
        this.episodes = episodes;
        PreferenceManager.getDefaultSharedPreferences( context );
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

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false);

        String imageSize = loadHDImage ? "w780" : "w500";
        Episode episode = episodes.get(position);
        holder.binding.title.setText(episode.getName());
        holder.binding.episodeNumber.setText( "(" + episode.getEpisodeNumber() + ")");
        holder.binding.description.setText(episode.getOverview());
        holder.binding.date.setText(episode.getAirDate());
        holder.binding.runtime.setText(context.getString(R.string.runtime_minutes, episode.getRuntime()));
        holder.binding.averageRating.setText(context.getString(R.string.average_rating, episode.getVoteAverage()));
        Picasso.get()
                .load("https://image.tmdb.org/t/p/" + imageSize + episode.getPosterPath())
                .placeholder(R.color.md_theme_surface)
                .into(holder.binding.image);

        holder.itemView.setBackgroundColor( Color.TRANSPARENT );

        double rating = accountState.getEpisodeRating(episode.getEpisodeNumber());
        if (rating == 0) {
            holder.binding.rating.setText( R.string.episode_rating_tmdb_not_set);
        } else {
            holder.binding.rating.setText("Rating (TMDb): " + String.format(Locale.getDefault(), "%.1f/10", rating));
        }

        try (MovieDatabaseHelper db = new MovieDatabaseHelper(context)) {
            if (db.isEpisodeInDatabase(tvShowId, seasonNumber, Collections.singletonList( episode.getEpisodeNumber()))) {
                holder.binding.watched.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_visibility_fill));
            } else {
                holder.binding.watched.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_visibility));
            }

            holder.binding.watched.setOnClickListener( v -> {
                // If the episode is in the database, remove it
                if (db.isEpisodeInDatabase(tvShowId, seasonNumber, Collections.singletonList( episode.getEpisodeNumber()))) {
                    db.removeEpisodeNumber(tvShowId, seasonNumber, Collections.singletonList( episode.getEpisodeNumber()));
                    holder.binding.watched.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_visibility));
                } else {
                    // If the episode is not in the database, add it
                    db.addEpisodeNumber( tvShowId, seasonNumber, Collections.singletonList( episode.getEpisodeNumber()));
                    holder.binding.watched.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_visibility_fill));
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        try (MovieDatabaseHelper db = new MovieDatabaseHelper(context)) {
            EpisodeDbDetails details = db.getEpisodeDetails(tvShowId, seasonNumber, episode.getEpisodeNumber());
            if (details != null) {
                if (details.rating != null) {
                    holder.binding.episodeDbRating.setText(context.getString( R.string.rating_db) + details.rating + "/10");
                }
                if (details.watchDate != null) {
                    SimpleDateFormat originalFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                    try {
                        Date date = originalFormat.parse(details.watchDate);
                        String formattedDate = DateFormat.getDateInstance( DateFormat.DEFAULT).format(date);
                        holder.binding.watchedDate.setText(context.getString( R.string.watched_on) + formattedDate);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        holder.binding.editDetails.setOnClickListener(v -> {
            BottomSheetDialog dialog = new BottomSheetDialog(context);
            LayoutInflater inflater = LayoutInflater.from(context);
            View dialogView = inflater.inflate(R.layout.dialog_edit_episode, null);
            dialog.setContentView(dialogView);
            dialog.show();

            TextView dateTextView = dialogView.findViewById(R.id.dateTextView);
            RatingBar ratingBar = dialogView.findViewById(R.id.episodeRatingBar);
            Button submitButton = dialogView.findViewById(R.id.btnSubmit);
            Button cancelButton = dialogView.findViewById(R.id.btnCancel);
            Button dateButton = dialogView.findViewById(R.id.dateButton);
            TextView episodeTitle = dialogView.findViewById(R.id.tvTitle);

            episodeTitle.setText(  "S:" + seasonNumber + " " + "E:" + episode.getEpisodeNumber() + " " + episode.getName());

            // Fetch episode details from the database
            try (MovieDatabaseHelper db = new MovieDatabaseHelper(context)) {
                EpisodeDbDetails details = db.getEpisodeDetails(tvShowId, seasonNumber, episode.getEpisodeNumber());
                if (details != null) {
                    if (details.watchDate != null) {
                        dateTextView.setText(details.watchDate);
                    }
                    if (details.rating != null) {
                        ratingBar.setRating(details.rating.floatValue());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            dateButton.setOnClickListener(v13 -> {
                MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker().build();
                datePicker.show(((FragmentActivity) context).getSupportFragmentManager(), datePicker.toString());
                datePicker.addOnPositiveButtonClickListener(selection -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                    String selectedDate = sdf.format(new Date(selection));
                    dateTextView.setText(selectedDate);
                });
            });

            submitButton.setOnClickListener(v1 -> {
                String date = dateTextView.getText().toString();
                double episodeRating = ratingBar.getRating();
                int adapterPosition = holder.getBindingAdapterPosition();
                Episode episode1 = episodes.get(adapterPosition);

                episode1.setWatchDate(date);
                episode1.setRating(episodeRating);

                try (MovieDatabaseHelper movieDatabaseHelper = new MovieDatabaseHelper(context)) {
                    movieDatabaseHelper.addOrUpdateEpisode(tvShowId, seasonNumber, episode1.getEpisodeNumber(), (int) episodeRating, date);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                notifyItemChanged(adapterPosition);

                dialog.dismiss();
            });

            cancelButton.setOnClickListener(v12 -> dialog.dismiss());
        });

        holder.binding.rateBtn.setOnClickListener(v -> {
            BottomSheetDialog dialog = new BottomSheetDialog(context);
            LayoutInflater inflater = LayoutInflater.from(context);
            View dialogView = inflater.inflate(R.layout.rating_dialog, null);
            dialog.setContentView(dialogView);
            dialog.show();

            RatingBar ratingBar = dialogView.findViewById(R.id.ratingBar);
            Button submitButton = dialogView.findViewById(R.id.btnSubmit);
            Button cancelButton = dialogView.findViewById(R.id.btnCancel);
            Button deleteButton = dialogView.findViewById(R.id.btnDelete);
            TextView episodeTitle = dialogView.findViewById(R.id.tvTitle);

            episodeTitle.setText(  "S:" + seasonNumber + " " + "E:" + episode.getEpisodeNumber() + " " + episode.getName());

            Handler mainHandler = new Handler(Looper.getMainLooper());

            submitButton.setOnClickListener(v1 -> CompletableFuture.runAsync(() -> {
                double ratingS = ratingBar.getRating();
                new AddEpisodeRatingThreadTMDb(tvShowId, seasonNumber, episode.getEpisodeNumber(), ratingS, context).start();
                mainHandler.post(dialog::dismiss);
            }));

            deleteButton.setOnClickListener(v12 -> CompletableFuture.runAsync(() -> {
                new DeleteEpisodeRatingThreadTMDb(tvShowId, seasonNumber, episode.getEpisodeNumber(), context).start();
                mainHandler.post(dialog::dismiss);
            }));

            cancelButton.setOnClickListener(v12 -> dialog.dismiss());
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