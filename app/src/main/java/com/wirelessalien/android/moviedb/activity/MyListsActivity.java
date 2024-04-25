package com.wirelessalien.android.moviedb.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.ListAdapter;
import com.wirelessalien.android.moviedb.data.ListData;
import com.wirelessalien.android.moviedb.tmdb.account.FetchListThreadTMDb;

import java.util.ArrayList;
import java.util.List;

public class MyListsActivity extends AppCompatActivity {

    private ListAdapter listAdapter;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_lists);

        RecyclerView recyclerView = findViewById( R.id.recyclerView );

        listAdapter = new ListAdapter(new ArrayList<>(), new ListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ListData listData) {
                Intent intent = new Intent(MyListsActivity.this, MyListDetailsActivity.class);
                intent.putExtra("listId", listData.getId());
                startActivity(intent);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(listAdapter);

        new FetchListThreadTMDb(this, new FetchListThreadTMDb.OnFetchListsListener() {
            @Override
            public void onFetchLists(List<ListData> listData) {
                listAdapter.updateData(listData);
            }
        }).start();
    }
}