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
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.DetailActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class NowPlayingMovieAdapter extends RecyclerView.Adapter<NowPlayingMovieAdapter.ShowItemViewHolder> {
    // API key names
    public static final String KEY_ID = "id";
    public static final String KEY_IMAGE = "backdrop_path";
    public static final String KEY_POSTER = "poster_path";
    public static final String KEY_TITLE = "title";
    public static final String KEY_NAME = "name";
    public static final String KEY_DESCRIPTION = "overview";
    public static final String KEY_RATING = "vote_average";
    public static final String KEY_DATE_MOVIE = "release_date";
    public static final String KEY_DATE_SERIES = "first_air_date";
    private static final String HD_IMAGE_SIZE = "key_hq_images";

    private final ArrayList<JSONObject> mShowArrayList;

    /**
     * Sets up the adapter with the necessary configurations.
     *
     * @param showList  the list of shows that will be shown.
     */
    public NowPlayingMovieAdapter(ArrayList<JSONObject> showList) {
        mShowArrayList = showList;
    }

    @Override
    public int getItemCount() {
        // Return the amount of items in the list.
        return mShowArrayList.size();
    }

    @NonNull
    @Override
    public ShowItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Create a new CardItem when needed.
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.home_card_one, parent, false);
        return new ShowItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ShowItemViewHolder holder, int position) {
        // Fill the views with the needed data.
        final JSONObject showData = mShowArrayList.get(position);

        Context context = holder.showView.getContext();

        if (showData == null) {
            // Handle the null case, e.g., set default values or hide the view.
            holder.showTitle.setText(R.string.title);
            holder.showImage.setBackgroundColor(ResourcesCompat.getColor(holder.showView.getContext().getResources(), R.color.md_theme_primary, null));
            holder.showDate.setText("");
            return;
        }
        // Fills the views with show details.
        try {
            // Load the thumbnail with Picasso.
            SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            boolean loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false);

            String imageSize = loadHDImage ? "w780" : "w500";

            if (showData.getString(KEY_POSTER).equals("null")) {
                holder.showImage.setImageDrawable(ResourcesCompat.getDrawable(context.getResources(), (R.drawable.ic_broken_image), null));
            } else {
                Picasso.get().load("https://image.tmdb.org/t/p/" + imageSize + showData.getString(KEY_POSTER)).into(holder.showImage);
            }

            // Check if the object has "title" if not,
            // it is a series and "name" is used.
            String name = (showData.has(KEY_TITLE)) ?
                    showData.getString(KEY_TITLE) : showData.getString(KEY_NAME);

            // Set the title and description.
            holder.showTitle.setText(name);

            // Check if the object has "title" if not,
            // it is a series and "name" is used.
            String dateString = (showData.has(KEY_DATE_MOVIE)) ?
                    showData.getString(KEY_DATE_MOVIE) : showData.getString(KEY_DATE_SERIES);

            // Convert date to locale.
            SimpleDateFormat originalFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            try {
                Date date = originalFormat.parse(dateString);
                DateFormat localFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault());
                dateString = localFormat.format(date);
            } catch (ParseException e) {
                e.printStackTrace();
            }

            holder.showDate.setText(dateString);



        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Send the movie data and the user to DetailActivity when clicking on a card.
        holder.itemView.setOnClickListener(view -> {
            Intent intent = new Intent(view.getContext(), DetailActivity.class);
            intent.putExtra("movieObject", showData.toString());
            if (showData.has(KEY_NAME)) {
                intent.putExtra("isMovie", false);
            }
            view.getContext().startActivity(intent);
        });
    }

    @Override
    public long getItemId(int position) {
        // The id is the same as the position,
        // therefore returning the position is enough.
        return position;
    }

    /**
     * The View of every item that is displayed in the list.
     */
    public static class ShowItemViewHolder extends RecyclerView.ViewHolder {
        final View showView;
        final TextView showTitle;
        final ImageView showImage;
        final TextView showDate;
        final Button deleteButton;

        ShowItemViewHolder(View itemView) {
            super(itemView);
            showView = itemView.findViewById(R.id.cardView);
            showTitle = itemView.findViewById(R.id.title);
            showImage = itemView.findViewById(R.id.image);

            // Only used if presented in a list.
            showDate = itemView.findViewById(R.id.date);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}
