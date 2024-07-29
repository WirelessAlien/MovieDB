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

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.fragment.SeasonDetailsFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class TVSeasonDetailsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_tv_season_details );

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            StringWriter crashLog = new StringWriter();
            PrintWriter printWriter = new PrintWriter(crashLog);
            throwable.printStackTrace(printWriter);

            String osVersion = android.os.Build.VERSION.RELEASE;
            String appVersion = "";
            try {
                appVersion = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            printWriter.write("\nDevice OS Version: " + osVersion);
            printWriter.write("\nApp Version: " + appVersion);
            printWriter.close();

            try {
                String fileName = "Crash_Log.txt";
                File targetFile = new File(getApplicationContext().getFilesDir(), fileName);
                FileOutputStream fileOutputStream = new FileOutputStream(targetFile, true);
                fileOutputStream.write((crashLog + "\n").getBytes());
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            android.os.Process.killProcess(android.os.Process.myPid());
        });

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