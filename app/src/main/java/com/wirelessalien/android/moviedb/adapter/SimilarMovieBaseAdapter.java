/*
 * Copyright (c) 2018.
 *
 * This file is part of MovieDB.
 *
 * MovieDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MovieDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MovieDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.adapter;

import android.content.Context;
import android.content.Intent;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.DetailActivity;

import org.json.JSONException;
import org.json.JSONObject;


import java.util.ArrayList;

/*
* This class is quite similar to the MovieBaseAdapter.
* The main difference is the orientation, which needs the RecyclerView.
* This class is primarily used for the "Similar Movies" list in DetailActivity.
*/

// TODO: Currently, this class is almost the same as ShowBaseAdapter, it would be nice if they could be merged.
public class SimilarMovieBaseAdapter extends RecyclerView.Adapter<SimilarMovieBaseAdapter.MovieItemViewHolder> {

    private final ArrayList<JSONObject> similarMovieList;
    private final Context context;

    // Create the adapter with the list of similar shows and the context.
    public SimilarMovieBaseAdapter(ArrayList<JSONObject> similarMovieList,
                                   Context context) {
        this.similarMovieList = similarMovieList;
        this.context = context;
    }

    @Override
    public int getItemCount() {
        // Return the amount of items in the list.
        return similarMovieList.size();
    }

    @NonNull
    @Override
    public MovieItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Create a new CardItem when needed.
        View view = LayoutInflater.from(parent.getContext())
                .inflate( R.layout.movie_card, parent, false);
        return new MovieItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MovieItemViewHolder holder, int position) {
        // Fill the views with the needed data.
        final JSONObject movieData = similarMovieList.get(position);

        try {
            // Depending if it is a movie or a series, it needs to get the title or name.
            String title;
            if (movieData.has("title")) {
                title = "title";
            } else {
                title = "name";
            }

            holder.movieTitle.setText(movieData.getString(title));

            // Either show the poster or an icon indicating that the poster is not available.
            if (movieData.getString("poster_path") == null) {
                holder.movieImage.setImageDrawable( ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_broken_image, null));
            } else {
                Picasso.get().load("https://image.tmdb.org/t/p/w300" +
                        movieData.getString("poster_path"))
                        .into(holder.movieImage);
            }

            // Quickly fade in the poster when loaded.
            Animation animation = AnimationUtils.loadAnimation(context,
                    R.anim.fade_in_fast);
            holder.movieImage.startAnimation(animation);
        } catch (JSONException je) {
            je.printStackTrace();
        }

        // Send the movie data and the user to DetailActivity when clicking on a card.
        holder.itemView.setOnClickListener( view -> {
            Intent intent = new Intent(view.getContext(), DetailActivity.class);
            intent.putExtra("movieObject", movieData.toString());
            if (movieData.has("name")) {
                intent.putExtra("isMovie", false);
            }
            view.getContext().startActivity(intent);
        } );
    }

    @Override
    public long getItemId(int position) {
        // The id is the same as the position,
        // therefore returning the position is enough.
        return position;
    }

    // Views that each CardItem will contain.
    public static class MovieItemViewHolder extends RecyclerView.ViewHolder {
        final CardView cardView;
        final TextView movieTitle;
        final ImageView movieImage;

        MovieItemViewHolder(View itemView) {
            super(itemView);
            cardView = (CardView) itemView.findViewById(R.id.cardView);
            movieTitle = (TextView) itemView.findViewById(R.id.movieTitle);
            movieImage = (ImageView) itemView.findViewById(R.id.movieImage);
        }
    }

}
