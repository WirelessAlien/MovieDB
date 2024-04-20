package com.wirelessalien.android.moviedb.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.ListAdapter;
import com.wirelessalien.android.moviedb.data.ListData;
import com.wirelessalien.android.moviedb.tmdb.account.FetchListThreadTMDb;

import java.util.ArrayList;
import java.util.List;

public class MyListsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ListAdapter listAdapter;
    private String sessionId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_lists); // Assuming you have a layout file named activity_my_lists

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        sessionId = preferences.getString("session_id", "");
        recyclerView = findViewById( R.id.recyclerView); // Assuming you have a RecyclerView in your layout file with id recyclerView

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

        new FetchListThreadTMDb(sessionId, this, new FetchListThreadTMDb.OnFetchListsListener() {
            @Override
            public void onFetchLists(List<ListData> listData) {
                listAdapter.updateData(listData);
            }
        }).start();
    }
}