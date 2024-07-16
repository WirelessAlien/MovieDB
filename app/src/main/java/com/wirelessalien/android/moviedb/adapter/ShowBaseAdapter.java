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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.DetailActivity;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.tmdb.account.DeleteFromListThreadTMDb;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class ShowBaseAdapter extends RecyclerView.Adapter<ShowBaseAdapter.ShowItemViewHolder> {
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
    public static final String KEY_GENRES = "genre_ids";
    public static final String KEY_RELEASE_DATE = "release_date";
    private static final String HD_IMAGE_SIZE = "key_hq_images";

    private static final String KEY_CATEGORIES = MovieDatabaseHelper.COLUMN_CATEGORIES;
    private final ArrayList<JSONObject> mShowArrayList;
    private final HashMap<String, String> mGenreHashMap;
    private final boolean mGridView;
    private final boolean showDeleteButton;
    private String genreType;

    /**
     * Sets up the adapter with the necessary configurations.
     *
     * @param showList  the list of shows that will be shown.
     * @param genreList the mapping of ids to genres.
     * @param gridView  if the user wants the shows displayed in a grid or a list.
     */
    public ShowBaseAdapter(ArrayList<JSONObject> showList,
                           HashMap<String, String> genreList, boolean gridView, boolean showDeleteButton) {

        this.showDeleteButton = showDeleteButton;
        // Get the right "type" of genres.
        if (genreType == null || genreType.equals("2")) {
            this.genreType = null;
        } else if (genreType.equals("1")) {
            this.genreType = SectionsPagerAdapter.MOVIE;
        } else {
            this.genreType = SectionsPagerAdapter.TV;
        }

        mShowArrayList = showList;
        mGenreHashMap = genreList;

        mGridView = gridView;
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
        View view;
        if (mGridView) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate( R.layout.show_grid_card, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.show_card, parent, false);
        }
        return new ShowItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ShowItemViewHolder holder, int position) {
        // Fill the views with the needed data.
        final JSONObject showData = mShowArrayList.get(position);

        Context context = holder.showView.getContext();

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

            // Set the right category color if available.
            if (showData.has(KEY_CATEGORIES)) {
                String categoryText = switch (showData.getInt( KEY_CATEGORIES )) {
                    case 0 -> "Plan to watch";
                    case 1 -> "Watched";
                    case 2 -> "Watching";
                    case 3 -> "On hold";
                    case 4 -> "Dropped";
                    default -> "Unknown";
                };
                ((TextView) holder.categoryColorView).setText(categoryText);
                holder.categoryColorView.setVisibility(View.VISIBLE);
            } else {
                holder.categoryColorView.setVisibility(View.GONE);
            }

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

            // Only if the shows are presented in a list.
            if (!mGridView) {
                holder.showDescription.setText(showData.getString(KEY_DESCRIPTION));

                // Divide the rating in two so it fits in the five stars.
                holder.showRating.setRating(Float.parseFloat(showData.getString(KEY_RATING)) / 2);

                // Remove the [ and ] from the String
                String genreIds = showData.getString(KEY_GENRES)
                        .substring(1, showData.getString(KEY_GENRES)
                                .length() - 1);

                // Split the String with ids and set them into an array.
                String[] genreArray = genreIds.split(",");

                // Get sharedPreferences in case the genreHashMap doesn't have the value
                SharedPreferences sharedPreferences = context.getSharedPreferences(
                        "GenreList", Context.MODE_PRIVATE);

                // Add all the genres in one String.
                StringBuilder genreNames = new StringBuilder();
                for (String aGenreArray : genreArray) {
                    if (mGenreHashMap.get(aGenreArray) != null) {
                        genreNames.append(", ").append(mGenreHashMap.get(aGenreArray));
                    } else {
                        genreNames.append(", ").append(sharedPreferences.getString(aGenreArray, ""));
                    }
                }

                // Remove the first ", " from the string and set the text.
                holder.showGenre.setText(genreNames.substring(2));

            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Send the movie data and the user to DetailActivity when clicking on a card.
        holder.itemView.setOnClickListener( view -> {
            Intent intent = new Intent(view.getContext(), DetailActivity.class);
            intent.putExtra("movieObject", showData.toString());
            if (showData.has(KEY_NAME)) {
                intent.putExtra("isMovie", false);
            }
            view.getContext().startActivity(intent);
        } );

        if (showDeleteButton) {
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setOnClickListener(v -> {
                int mediaId;
                String type;
                try {
                    mediaId = showData.getInt(KEY_ID);
                    type = showData.has(KEY_TITLE) ? "movie" : "tv";
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }
                int listId = PreferenceManager.getDefaultSharedPreferences(context).getInt("listId", 0);
                Activity activity = (Activity) context;
                DeleteFromListThreadTMDb deleteThread = new DeleteFromListThreadTMDb(mediaId, listId, type, activity, position, mShowArrayList, this);
                deleteThread.start();
            });
        } else {
            holder.deleteButton.setVisibility(View.GONE);
        }
    }

    @Override
    public long getItemId(int position) {
        // The id is the same as the position,
        // therefore returning the position is enough.
        return position;
    }

    /**
     * The View of every item that is displayed in the grid/list.
     */
    public static class ShowItemViewHolder extends RecyclerView.ViewHolder {
        final CardView showView;
        final TextView showTitle;
        final ImageView showImage;
        final TextView showDescription;
        final TextView showGenre;
        final RatingBar showRating;
        final View categoryColorView;
        final TextView showDate;
        final Button deleteButton;

        ShowItemViewHolder(View itemView) {
            super(itemView);
            showView = itemView.findViewById(R.id.cardView);
            showTitle = itemView.findViewById(R.id.title);
            showImage = itemView.findViewById(R.id.image);
            categoryColorView = itemView.findViewById(R.id.categoryColor);

            // Only used if presented in a list.
            showDescription = itemView.findViewById(R.id.description);
            showGenre = itemView.findViewById(R.id.genre);
            showRating = itemView.findViewById(R.id.rating);
            showDate = itemView.findViewById(R.id.date);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}
