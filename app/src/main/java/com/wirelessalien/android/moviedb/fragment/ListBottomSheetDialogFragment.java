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

package com.wirelessalien.android.moviedb.fragment;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.wirelessalien.android.moviedb.adapter.ListDataAdapter;
import com.wirelessalien.android.moviedb.data.ListData;
import com.wirelessalien.android.moviedb.data.ListDetailsData;
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper;
import com.wirelessalien.android.moviedb.tmdb.account.AddToListThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.CreateListThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.FetchListThreadTMDb;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.ListAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ListBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private EditText newListName, listDescription;
    private MaterialRadioButton privateList;
    private final int movieId;
    private final Activity activity;
    private final String mediaType;
    private final boolean fetchList;
    private ListDataAdapter listDataAdapter;
    public ListBottomSheetDialogFragment(int movieId, String mediaType, Activity activity, boolean fetchList) {
        this.movieId = movieId;
        this.mediaType = mediaType;
        this.activity = activity;
        this.fetchList = fetchList;
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
        listDescription = view.findViewById(R.id.listDescription);
        Button createListButton = view.findViewById( R.id.createListBtn );
        privateList = view.findViewById(R.id.privateRadioBtn);
        TextView previousLists = view.findViewById(R.id.previousListText);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(listDataAdapter);

        createListButton.setOnClickListener( v -> {
            String listName = newListName.getText().toString();
            String description = listDescription.getText().toString();
            boolean isPublic = !privateList.isChecked(); // when checked private radio button, the value is false else true

            new CreateListThreadTMDb(listName, description, isPublic, activity).start();
            dismiss();
        });

        if (!fetchList) {
            previousLists.setVisibility(View.GONE);
        }

        if (fetchList) {
            CompletableFuture.runAsync(() -> {
                ListDatabaseHelper listdatabaseHelper = new ListDatabaseHelper(activity);
                SQLiteDatabase listdb = listdatabaseHelper.getReadableDatabase();

                Cursor cursor = listdb.query(
                        true,
                        ListDatabaseHelper.TABLE_LISTS,
                        new String[]{ListDatabaseHelper.COLUMN_LIST_ID, ListDatabaseHelper.COLUMN_LIST_NAME},
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

                List<ListDetailsData> listData = new ArrayList<>();
                while(cursor.moveToNext()) {
                    int listId = cursor.getInt(cursor.getColumnIndexOrThrow(ListDatabaseHelper.COLUMN_LIST_ID));
                    String listName = cursor.getString(cursor.getColumnIndexOrThrow(ListDatabaseHelper.COLUMN_LIST_NAME));
                    boolean isMovieInList = checkIfMovieInList(movieId, listName);
                    listData.add(new ListDetailsData(movieId, listId, listName, mediaType, isMovieInList));
                }
                cursor.close();

                activity.runOnUiThread(() -> {
                    ListDataAdapter adapter = new ListDataAdapter(listData, activity,null);
                    recyclerView.setAdapter(adapter);
                });
            });
        }
    }

    private boolean checkIfMovieInList(int movieId, String listName) {
        String selection = ListDatabaseHelper.COLUMN_MOVIE_ID + " = ? AND " + ListDatabaseHelper.COLUMN_LIST_NAME + " = ?";
        String[] selectionArgs = { String.valueOf(movieId), listName };
        ListDatabaseHelper listdatabaseHelper = new ListDatabaseHelper(activity);
        SQLiteDatabase listdb = listdatabaseHelper.getReadableDatabase();

        String[] projection = {
                ListDatabaseHelper.COLUMN_MOVIE_ID,
                ListDatabaseHelper.COLUMN_LIST_NAME
        };

        Cursor cursor = listdb.query(
                ListDatabaseHelper.TABLE_LIST_DATA, projection, selection, selectionArgs, null, null, null
        );

        boolean isMovieInList = cursor.getCount() > 0;
        cursor.close();

        return isMovieInList;
    }
}