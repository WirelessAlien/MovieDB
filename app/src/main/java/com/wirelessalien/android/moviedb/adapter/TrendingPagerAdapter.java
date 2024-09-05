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

import static com.google.android.material.animation.AnimationUtils.lerp;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.palette.graphics.Palette;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.MaskableFrameLayout;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.DetailActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class TrendingPagerAdapter extends RecyclerView.Adapter<TrendingPagerAdapter.ShowItemViewHolder> {
    private final ArrayList<JSONObject> mShowArrayList;    // API key names
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


    /**
     * Sets up the adapter with the necessary configurations.
     *
     * @param showList  the list of shows that will be shown.
     */
    public TrendingPagerAdapter(ArrayList<JSONObject> showList) {
        mShowArrayList = showList;
    }

    public void updateData(ArrayList<JSONObject> newTrendingList) {
        this.mShowArrayList.clear();
        this.mShowArrayList.addAll(newTrendingList);
    }

    @Override
    public int getItemCount() {
        // Return the amount of items in the list.
        return mShowArrayList.size();
    }

    @NonNull
    @Override
    public ShowItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.trending_card, parent, false);
        return new ShowItemViewHolder(view);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onBindViewHolder(@NonNull ShowItemViewHolder holder, int position) {

        final JSONObject showData = (mShowArrayList != null && !mShowArrayList.isEmpty()) ? mShowArrayList.get(position % mShowArrayList.size()) : null;
        if (showData == null) {
            return;
        }

        Context context = holder.showView.getContext();

        // Fills the views with show details.
        try {
            // Load the thumbnail with Picasso.
            SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            boolean loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false);

            String imageSize = loadHDImage ? "w780" : "w500";

            UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            boolean isDarkTheme = uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES;


            try {
                if (showData.getString( KEY_IMAGE ).equals( "null" )) {
                    holder.showImage.setImageDrawable( ResourcesCompat.getDrawable( context.getResources(), (R.drawable.ic_broken_image), null ) );
                } else {
                    String imageUrl = "https://image.tmdb.org/t/p/" + imageSize + showData.getString( KEY_IMAGE );
                    holder.target = new Target() {
                        @Override
                        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                            holder.showImage.setImageBitmap(bitmap);

                            Palette palette = Palette.from(bitmap).generate();
                            int darkMutedColor = palette.getDarkMutedColor(palette.getMutedColor(Color.BLACK));
                            int lightMutedColor = palette.getLightMutedColor(palette.getMutedColor(Color.WHITE));

                            GradientDrawable foregroundGradientDrawable;
                            if (isDarkTheme) {
                                foregroundGradientDrawable = new GradientDrawable(
                                        GradientDrawable.Orientation.BOTTOM_TOP,
                                        new int[]{darkMutedColor, darkMutedColor, Color.TRANSPARENT, Color.TRANSPARENT}
                                );
                            } else {
                                foregroundGradientDrawable = new GradientDrawable(
                                        GradientDrawable.Orientation.BOTTOM_TOP,
                                        new int[]{lightMutedColor, lightMutedColor, lightMutedColor, Color.TRANSPARENT}
                                );
                            }

                            holder.movieInfo.setBackground(foregroundGradientDrawable);
                        }

                        @Override
                        public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                            holder.showImage.setImageDrawable(ResourcesCompat.getDrawable(context.getResources(), (R.drawable.ic_broken_image), null));
                        }

                        @Override
                        public void onPrepareLoad(Drawable placeHolderDrawable) {
                            holder.showImage.setImageDrawable(placeHolderDrawable);
                        }
                    };
                    Picasso.get().load( imageUrl ).into( holder.target );
                }
            } catch (JSONException e) {
                e.printStackTrace();
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

        if (holder.itemView instanceof MaskableFrameLayout) {
            ((MaskableFrameLayout) holder.itemView).setOnMaskChangedListener(maskRect -> {
                // Any custom motion to run when mask size changes
                holder.showTitle.setTranslationX(maskRect.left);
                holder.showDate.setTranslationX(maskRect.left);
                holder.showTitle.setAlpha(lerp(1F, 0F, 0F, 80F, maskRect.left));
                holder.showDate.setAlpha(lerp(1F, 0F, 0F, 80F, maskRect.left));
            });
        }
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
        RelativeLayout movieInfo;
        Target target;

        ShowItemViewHolder(View itemView) {
            super(itemView);
            showView = itemView.findViewById(R.id.cardView2);
            showTitle = itemView.findViewById(R.id.movieTitle);
            showImage = itemView.findViewById(R.id.movieImage);
            movieInfo = itemView.findViewById(R.id.movieInfo);

            // Only used if presented in a list.
            showDate = itemView.findViewById(R.id.date);
        }
    }
}
