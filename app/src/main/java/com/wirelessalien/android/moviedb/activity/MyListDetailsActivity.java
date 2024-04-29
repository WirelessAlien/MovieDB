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

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter;
import com.wirelessalien.android.moviedb.tmdb.account.ListDetailsThreadTMDb;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

public class MyListDetailsActivity extends AppCompatActivity implements ListDetailsThreadTMDb.OnFetchListDetailsListener {

    private RecyclerView recyclerView;
    private ShowBaseAdapter adapter; // Change to ShowBaseAdapter
    private int listId = 0;
    HashMap<String, String> mShowGenreList;
    final static String SHOWS_LIST_PREFERENCE = "key_show_shows_grid";
    SharedPreferences preferences;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.activity_my_lists);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar( toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mShowGenreList = new HashMap<>();

        // Get the list ID from the intent
        listId = getIntent().getIntExtra("listId", 0);
        preferences.edit().putInt("listId", listId).apply();
        ListDetailsThreadTMDb thread = new ListDetailsThreadTMDb(listId, this, this);
        thread.start();

        adapter = new ShowBaseAdapter(new ArrayList<>(), null, false, true);
    }

    @Override
    public void onFetchListDetails(ArrayList<JSONObject> listDetailsData) {
        adapter = new ShowBaseAdapter(listDetailsData, mShowGenreList, false, true);
        recyclerView.setAdapter(adapter);
    }
}