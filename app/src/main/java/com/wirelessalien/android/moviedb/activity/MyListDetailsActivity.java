package com.wirelessalien.android.moviedb.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.ListDetailsAdapter;
import com.wirelessalien.android.moviedb.data.ListDetails;
import com.wirelessalien.android.moviedb.tmdb.account.ListDetailsThreadTMDb;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MyListDetailsActivity extends AppCompatActivity implements ListDetailsThreadTMDb.OnFetchListDetailsListener {

    private RecyclerView recyclerView;
    private ListDetailsAdapter adapter;
    private int listId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.activity_my_lists);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Get the list ID from the intent
        listId = getIntent().getIntExtra("listId", 0);
        // Fetch the data
        ListDetailsThreadTMDb thread = new ListDetailsThreadTMDb(listId, this, this);
        thread.start();

        adapter = new ListDetailsAdapter(new ArrayList<>(), new ListDetailsAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ListDetails listData) {
                ListDetails listDetails = new ListDetails(listData.getMediaType(), listData.getTitle(), listData.getPosterPath(), listData.getOverview(), listData.getReleaseDate(), listData.getVoteAverage(), listData.getVoteCount(), listData.getId(), listData.getBackdropPath());
                Intent intent = new Intent(MyListDetailsActivity.this, DetailActivity.class);
                intent.putExtra("movieObject", listDetails);
                intent.putExtra("isMovie", listDetails.getMediaType().equals("movie"));
                startActivity(intent);
            }
        });

    }

    @Override
    public void onFetchListDetails(List<ListDetails> listDetailsData) {
        adapter = new ListDetailsAdapter(listDetailsData, new ListDetailsAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ListDetails listData) {
                try {
                    // Convert ListDetails object to JSONObject
                    JSONObject movieObject = new JSONObject();
                    movieObject.put("mediaType", listData.getMediaType());
                    movieObject.put("title", listData.getTitle());
                    movieObject.put("posterPath", listData.getPosterPath());
                    movieObject.put("overview", listData.getOverview());
                    movieObject.put("releaseDate", listData.getReleaseDate());
                    movieObject.put("voteAverage", listData.getVoteAverage());
                    movieObject.put("voteCount", listData.getVoteCount());
                    movieObject.put("id", listData.getId());
                    movieObject.put("backdropPath", listData.getBackdropPath());

                    // Convert JSONObject to String
                    String movieObjectString = movieObject.toString();

                    // Put the string in the intent as an extra
                    Intent intent = new Intent(MyListDetailsActivity.this, DetailActivity.class);
                    intent.putExtra("movieObject", movieObjectString);
                    intent.putExtra("isMovie", listData.getMediaType().equals("movie"));
                    startActivity(intent);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        recyclerView.setAdapter(adapter);
    }
}