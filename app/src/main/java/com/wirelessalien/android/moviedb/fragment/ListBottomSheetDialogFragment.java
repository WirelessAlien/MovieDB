package com.wirelessalien.android.moviedb.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.wirelessalien.android.moviedb.tmdb.account.AddToListThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.CreateListThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.FetchListThreadTMDb;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.ListAdapter;

import java.util.ArrayList;

public class ListBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private EditText newListName;
    private final int movieId;
    private final String sessionId;
    private final Activity activity;
    private final String mediaType;
    private ListAdapter listAdapter;

    public ListBottomSheetDialogFragment(int movieId, String sessionId, String mediaType, Activity activity) {
        this.movieId = movieId;
        this.sessionId = sessionId;
        this.mediaType = mediaType;
        this.activity = activity;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById( R.id.recyclerView );
        newListName = view.findViewById(R.id.newListName);
        Button createListButton = view.findViewById( R.id.createListBtn );

        listAdapter = new ListAdapter(new ArrayList<>(), listData -> {
            new AddToListThreadTMDb(sessionId, movieId, listData.getId(), mediaType, activity).start();
            dismiss();
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(listAdapter);

        createListButton.setOnClickListener( v -> {
            String listName = newListName.getText().toString();
            new CreateListThreadTMDb(sessionId, listName, activity).start();
            dismiss();
        });

        // Fetch the list data from the server and update the RecyclerView
        new FetchListThreadTMDb(sessionId, activity, listData -> listAdapter.updateData(listData) ).start();
    }
}