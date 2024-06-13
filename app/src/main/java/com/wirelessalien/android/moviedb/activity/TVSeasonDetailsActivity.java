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

import static com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper.TABLE_MOVIES;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.EpisodeAdapter;
import com.wirelessalien.android.moviedb.data.Episode;
import com.wirelessalien.android.moviedb.fragment.SeasonDetailsFragment;
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.tmdb.TVSeasonDetailsThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class TVSeasonDetailsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_tv_season_details );

        Thread.setDefaultUncaughtExceptionHandler( (thread, throwable) -> {
            StringWriter crashLog = new StringWriter();
            throwable.printStackTrace( new PrintWriter( crashLog ) );

            try {
                String fileName = "Crash_Log.txt";
                File targetFile = new File( getApplicationContext().getFilesDir(), fileName );
                FileOutputStream fileOutputStream = new FileOutputStream( targetFile );
                fileOutputStream.write( crashLog.toString().getBytes() );
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            android.os.Process.killProcess( android.os.Process.myPid() );
        } );

        toolbar = findViewById( R.id.toolbar );
        setSupportActionBar( toolbar );
        AppBarLayout appBarLayout = findViewById( R.id.appBarLayout );
        appBarLayout.setBackgroundColor( Color.TRANSPARENT );

        int tvShowId = getIntent().getIntExtra( "tvShowId", -1 );
        int seasonNumber = getIntent().getIntExtra( "seasonNumber", 1 );
        int numSeasons = getIntent().getIntExtra( "numSeasons", 1 );
        String showName = getIntent().getStringExtra( "tvShowName" );

        ViewPager2 viewPager = findViewById( R.id.view_pager );
        TabLayout tabLayout = findViewById( R.id.tab_layout );

        viewPager.setAdapter( new FragmentStateAdapter( this ) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return SeasonDetailsFragment.newInstance( tvShowId, position + 1, showName );
            }

            @Override
            public int getItemCount() {
                return numSeasons;
            }
        } );

        viewPager.setCurrentItem( seasonNumber - 1, false );

        new TabLayoutMediator( tabLayout, viewPager,
                (tab, position) -> tab.setText( "Season " + (position + 1) )
        ).attach();
    }
}