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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.wirelessalien.android.moviedb.adapter.ListAdapter;
import com.wirelessalien.android.moviedb.databinding.ActivityMyListsBinding;
import com.wirelessalien.android.moviedb.fragment.ListBottomSheetDialogFragment;
import com.wirelessalien.android.moviedb.tmdb.account.FetchListThreadTMDb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

public class MyListsActivity extends AppCompatActivity {

    private ListAdapter listAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMyListsBinding binding = ActivityMyListsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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

        MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        RecyclerView recyclerView = binding.recyclerView;
        ProgressBar progressBar = binding.progressBar;
        FloatingActionButton fab = binding.fab;

        listAdapter = new ListAdapter(new ArrayList<>(), listData -> {
            Intent intent = new Intent(MyListsActivity.this, MyListDetailsActivity.class);
            intent.putExtra("listId", listData.getId());
            startActivity(intent);
        }, true);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(listAdapter);

        progressBar.setVisibility( View.VISIBLE);

        FetchListThreadTMDb fetcher = new FetchListThreadTMDb(this, null);
        fetcher.fetchLists().thenAccept(listData -> runOnUiThread(() -> {
            listAdapter.updateData(listData);
            progressBar.setVisibility(View.GONE);
        }) );

        fab.setOnClickListener(v -> {
            ListBottomSheetDialogFragment listBottomSheetDialogFragment = new ListBottomSheetDialogFragment(0 , null, this, false);
            listBottomSheetDialogFragment.show(getSupportFragmentManager(), listBottomSheetDialogFragment.getTag());
        });
    }
}