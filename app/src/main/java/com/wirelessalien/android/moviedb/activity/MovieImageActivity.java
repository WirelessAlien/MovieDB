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

import android.app.Dialog;
import android.os.Bundle;
import android.widget.PopupWindow;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.AlignContent;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.MovieImageAdapter;
import com.wirelessalien.android.moviedb.data.MovieImage;
import com.wirelessalien.android.moviedb.tmdb.GetMovieImageThread;

import java.util.Collections;
import java.util.List;

public class MovieImageActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private List<MovieImage> movieImages;
    private int movieId;
    private String type;
    private PopupWindow popupWindow;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.activity_movie_image);

        movieId = getIntent().getIntExtra("movieId", 0);
        type = getIntent().getBooleanExtra("isMovie", true) ? "movie" : "tv";
        movieImages = Collections.emptyList();

        popupWindow = new PopupWindow(this);
        recyclerView = findViewById(R.id.movieimageRv);
        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(this);
        layoutManager.setFlexWrap(FlexWrap.WRAP);
        layoutManager.setFlexDirection(FlexDirection.ROW);
        layoutManager.setAlignItems( AlignItems.STRETCH);
        layoutManager.setJustifyContent( JustifyContent.SPACE_EVENLY );
        recyclerView.setLayoutManager(layoutManager);
        RecyclerView.Adapter<MovieImageAdapter.ViewHolder> adapter = new MovieImageAdapter( this, movieImages);
        recyclerView.setAdapter(adapter);

        new GetMovieImageThread(movieId, type, this, recyclerView).start();
    }

    @Override
    public void onBackPressed() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        } else {
            super.onBackPressed();
        }
    }
}
