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

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.AlignSelf;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.data.MovieImage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MovieImageAdapter extends RecyclerView.Adapter<MovieImageAdapter.ViewHolder> {

    private final List<MovieImage> movieImages;
    private final Context context;

    public MovieImageAdapter(Context context, List<MovieImage> movieImages) {
        this.context = context;
        this.movieImages = movieImages;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.movie_image_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final String imageUrl = "https://image.tmdb.org/t/p/w300" + movieImages.get(position).getFilePath();

        Picasso.get()
                .load(imageUrl)
                .placeholder(R.drawable.ic_broken_image)
                .into(holder.imageView);

        ViewGroup.LayoutParams lp = holder.imageView.getLayoutParams();
        if (lp instanceof FlexboxLayoutManager.LayoutParams flexboxLp) {
            flexboxLp.setFlexGrow(1.0f);
            flexboxLp.setAlignSelf(AlignItems.STRETCH);
        }

        holder.imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int[] position = {holder.getBindingAdapterPosition()};
                if (position[0] != RecyclerView.NO_POSITION) {
                    View popupView = LayoutInflater.from(context).inflate(R.layout.dialog_image_view, null);
                    PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    popupWindow.setOutsideTouchable(true);
                    popupWindow.setFocusable(true);

                    ImageView dialogImageView = popupView.findViewById(R.id.dialog_image);
                    Button rotateButton = popupView.findViewById(R.id.rotate_btn);
                    Button loadOriginalButton = popupView.findViewById(R.id.load_original_btn);
                    ProgressBar progressBar = popupView.findViewById(R.id.progress_bar);
                    Button dismissButton = popupView.findViewById(R.id.dismiss_btn);
                    Button nextButton = popupView.findViewById(R.id.next_btn);
                    Button prevButton = popupView.findViewById(R.id.prev_btn);
                    Button zoomInButton = popupView.findViewById(R.id.zoom_in_btn);
                    Button zoomOutButton = popupView.findViewById(R.id.zoom_out_btn);


                    String hDImageUrl = "https://image.tmdb.org/t/p/w780" + movieImages.get( position[0] ).getFilePath();
                    String originalImageUrl = "https://image.tmdb.org/t/p/original" + movieImages.get( position[0] ).getFilePath();

                    progressBar.setVisibility(View.VISIBLE);
                    popupWindow.showAtLocation(v, Gravity.CENTER, 0, 0);

                    CompletableFuture.runAsync(() -> {
                        try {
                            Bitmap bitmap = Picasso.get().load(hDImageUrl).get();
                            Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
                            dialogImageView.post(() -> {
                                dialogImageView.setImageDrawable(drawable);
                                progressBar.setVisibility(View.GONE);
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                    rotateButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialogImageView.setRotation(dialogImageView.getRotation() + 90);
                        }
                    });

                    dismissButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            popupWindow.dismiss();
                        }
                    });

                    loadOriginalButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            progressBar.setVisibility(View.VISIBLE);
                            CompletableFuture.runAsync(() -> {
                                try {
                                    Bitmap bitmap = Picasso.get().load(originalImageUrl).get();
                                    Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
                                    dialogImageView.post(() -> {
                                        dialogImageView.setImageDrawable(drawable);
                                        progressBar.setVisibility(View.GONE);
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    });

                    //zoom in btn
                    zoomInButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialogImageView.setScaleX(dialogImageView.getScaleX() + 0.1f);
                            dialogImageView.setScaleY(dialogImageView.getScaleY() + 0.1f);
                        }
                    });

                    //zoom out btn
                    zoomOutButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialogImageView.setScaleX(dialogImageView.getScaleX() - 0.1f);
                            dialogImageView.setScaleY(dialogImageView.getScaleY() - 0.1f);
                        }
                    });

                    nextButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (position[0] < movieImages.size() - 1) {
                                position[0]++;
                                String nextImageUrl = "https://image.tmdb.org/t/p/w780" + movieImages.get( position[0] ).getFilePath();
                                progressBar.setVisibility(View.VISIBLE);
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        Bitmap bitmap = Picasso.get().load(nextImageUrl).get();
                                        Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
                                        dialogImageView.post(() -> {
                                            dialogImageView.setImageDrawable(drawable);
                                            progressBar.setVisibility(View.GONE);
                                        });
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        }
                    });

                    prevButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (position[0] > 0) {
                                position[0]--;
                                String prevImageUrl = "https://image.tmdb.org/t/p/w780" + movieImages.get( position[0] ).getFilePath();
                                progressBar.setVisibility(View.VISIBLE);
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        Bitmap bitmap = Picasso.get().load(prevImageUrl).get();
                                        Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
                                        dialogImageView.post(() -> {
                                            dialogImageView.setImageDrawable(drawable);
                                            progressBar.setVisibility(View.GONE);
                                        });
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return movieImages.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.movie_image);
        }
    }
}
