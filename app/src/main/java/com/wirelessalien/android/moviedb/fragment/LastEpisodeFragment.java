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

import android.content.SharedPreferences;
import android.graphics.Color;
import android.icu.text.DateFormat;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.databinding.FragmentLastEpisodeBinding;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.tmdb.account.AddEpisodeRatingThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.DeleteEpisodeRatingThreadTMDb;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class LastEpisodeFragment extends Fragment {
    private static final String ARG_EPISODE_DETAILS = "episode_details";
    private static final String ARG_LABEL_TEXT = "label_text";
    private final static String DYNAMIC_COLOR_DETAILS_ACTIVITY = "dynamic_color_details_activity";

    private FragmentLastEpisodeBinding binding;
    private MovieDatabaseHelper databaseHelper;
    private int movieId;
    private int seasonNumber;
    private int episodeNumber;
    private String episodeName;

    public static LastEpisodeFragment newInstance(JSONObject episodeDetails, String labelText) {
        LastEpisodeFragment fragment = new LastEpisodeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EPISODE_DETAILS, episodeDetails.toString());
        args.putString(ARG_LABEL_TEXT, labelText);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLastEpisodeBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences( requireContext() );

        if (preferences.getBoolean( DYNAMIC_COLOR_DETAILS_ACTIVITY, false )) {
            binding.lastEpisodeCard.setStrokeWidth( 5 );
            binding.lastEpisodeCard.setCardBackgroundColor( Color.TRANSPARENT );
        }

        String sessionId = preferences.getString( "access_token", null );
        String accountId = preferences.getString( "account_id", null );

        binding.episodeRateBtn.setEnabled( sessionId != null && accountId != null );

        if (getArguments() != null) {
            try {
                JSONObject episodeDetails = new JSONObject(getArguments().getString(ARG_EPISODE_DETAILS));
                movieId = episodeDetails.getInt("show_id");
                seasonNumber = episodeDetails.getInt("season_number");
                episodeNumber = episodeDetails.getInt("episode_number");
                episodeName = episodeDetails.getString("name");

                String labelText = getArguments().getString( ARG_LABEL_TEXT );
                binding.episodeRecencyText.setText( labelText );
                binding.seasonNo.setText("S: " + seasonNumber);
                binding.episodeNo.setText("E: " + episodeNumber);
                binding.episodeName.setText(episodeName);
                binding.ratingAverage.setText(String.format( Locale.getDefault(), "%.2f/10", episodeDetails.getDouble("vote_average")));
                String overview = episodeDetails.getString("overview");
                if (overview.isEmpty()) {
                    binding.episodeOverview.setText(R.string.overview_not_available);
                } else {
                    binding.episodeOverview.setText(overview);
                }
                String episodeAirDateStr = episodeDetails.getString("air_date");
                SimpleDateFormat originalFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = originalFormat.parse(episodeAirDateStr);
                DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault());
                String formattedDate = dateFormat.format(date);
                binding.episodeAirDate.setText(formattedDate);


                databaseHelper = new MovieDatabaseHelper(getContext());
                if (databaseHelper.isEpisodeInDatabase(movieId, seasonNumber, Collections.singletonList(episodeNumber))) {
                    binding.episodeWathchBtn.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_visibility_fill));
                } else {
                    binding.episodeWathchBtn.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_visibility));
                }

                binding.episodeRateBtn.setOnClickListener(v -> {
                    BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
                    LayoutInflater dialogInflater = LayoutInflater.from(getContext());
                    View dialogView = dialogInflater.inflate(R.layout.rating_dialog, null);
                    dialog.setContentView(dialogView);
                    dialog.show();

                    RatingBar ratingBar = dialogView.findViewById(R.id.ratingBar);
                    Button submitButton = dialogView.findViewById(R.id.btnSubmit);
                    Button cancelButton = dialogView.findViewById(R.id.btnCancel);
                    Button deleteButton = dialogView.findViewById(R.id.btnDelete);
                    TextView episodeTitle = dialogView.findViewById(R.id.tvTitle);

                    episodeTitle.setText("S:" + seasonNumber + " E:" + episodeNumber + " " + episodeName);

                    submitButton.setOnClickListener(v1 -> CompletableFuture.runAsync(() -> {
                        double rating = ratingBar.getRating();
                        new AddEpisodeRatingThreadTMDb(movieId, seasonNumber, episodeNumber, rating, getContext()).start();
                        requireActivity().runOnUiThread(dialog::dismiss);
                    }));

                    deleteButton.setOnClickListener(v12 -> CompletableFuture.runAsync(() -> {
                        new DeleteEpisodeRatingThreadTMDb(movieId, seasonNumber, episodeNumber, getContext()).start();
                        requireActivity().runOnUiThread(dialog::dismiss);
                    }));

                    cancelButton.setOnClickListener(v12 -> dialog.dismiss());
                });

                binding.episodeWathchBtn.setOnClickListener(v -> {
                    if (databaseHelper == null) {
                        databaseHelper = new MovieDatabaseHelper(getContext());
                    }

                    if (databaseHelper.isEpisodeInDatabase( movieId, seasonNumber, Collections.singletonList( episodeNumber ) )) {
                        databaseHelper.removeEpisodeNumber( movieId, seasonNumber, Collections.singletonList( episodeNumber ) );
                        binding.episodeWathchBtn.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_visibility));
                    } else {
                        databaseHelper.addEpisodeNumber( movieId, seasonNumber, Collections.singletonList( episodeNumber ) );
                        binding.episodeWathchBtn.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_visibility_fill));
                    }
                });

            } catch (JSONException | ParseException e) {
                e.printStackTrace();
            }
        }
        return view;
    }
}