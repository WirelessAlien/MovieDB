package com.wirelessalien.android.moviedb.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.wirelessalien.android.moviedb.adapter.ListAdapter;
import com.wirelessalien.android.moviedb.databinding.ActivityMyListsBinding;
import com.wirelessalien.android.moviedb.tmdb.account.FetchListThreadTMDb;

import java.util.ArrayList;

public class MyListsActivity extends AppCompatActivity {

    private ListAdapter listAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMyListsBinding binding = ActivityMyListsBinding.inflate( getLayoutInflater() );
        setContentView( binding.getRoot());

        MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar( toolbar );
        RecyclerView recyclerView = binding.recyclerView;

        listAdapter = new ListAdapter(new ArrayList<>(), listData -> {
            Intent intent = new Intent(MyListsActivity.this, MyListDetailsActivity.class);
            intent.putExtra("listId", listData.getId());
            startActivity(intent);
        } );

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(listAdapter);

        new FetchListThreadTMDb(this, listData -> listAdapter.updateData(listData) ).start();
    }
}