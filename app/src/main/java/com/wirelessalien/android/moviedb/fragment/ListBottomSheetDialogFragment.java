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
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.wirelessalien.android.moviedb.activity.MainActivity;
import com.wirelessalien.android.moviedb.adapter.ListDataAdapter;
import com.wirelessalien.android.moviedb.data.ListData;
import com.wirelessalien.android.moviedb.data.ListDetailsData;
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper;
import com.wirelessalien.android.moviedb.tmdb.account.AddToListThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.CreateListThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.FetchListThreadTMDb;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.ListAdapter;
import com.wirelessalien.android.moviedb.tmdb.account.ListDetailsThreadTMDb;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ListBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private EditText newListName, listDescription;
    private MaterialRadioButton privateList;
    private final int movieId;
    private final Context context;
    private final String mediaType;
    private final boolean fetchList;
    private ListDataAdapter listDataAdapter;
    public ListBottomSheetDialogFragment(int movieId, String mediaType, Context context, boolean fetchList) {
        this.movieId = movieId;
        this.mediaType = mediaType;
        this.context = context;
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
        Button infoButton = view.findViewById(R.id.infoBtn);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(listDataAdapter);

        infoButton.setOnClickListener( v -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
            builder.setTitle( R.string.lists_state_info_title);
            builder.setMessage( R.string.list_state_info);
            builder.setPositiveButton("Refresh", (dialog, which) -> {
                fetchList();
                dialog.dismiss();
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
            builder.show();
        });

        createListButton.setOnClickListener( v -> {
            String listName = newListName.getText().toString();
            String description = listDescription.getText().toString();
            boolean isPublic = !privateList.isChecked(); // when checked private radio button, the value is false else true

            new CreateListThreadTMDb(listName, description, isPublic, context).start();
            dismiss();
        });

        if (!fetchList) {
            previousLists.setVisibility(View.GONE);
        }

        if (fetchList) {
            CompletableFuture.runAsync(() -> {
                ListDatabaseHelper listdatabaseHelper = new ListDatabaseHelper(context);
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

                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread( () -> {
                        ListDataAdapter adapter = new ListDataAdapter( listData, context, null );
                        recyclerView.setAdapter( adapter );
                    } );
                }
            });
        }
    }

    private boolean checkIfMovieInList(int movieId, String listName) {
        String selection = ListDatabaseHelper.COLUMN_MOVIE_ID + " = ? AND " + ListDatabaseHelper.COLUMN_LIST_NAME + " = ?";
        String[] selectionArgs = { String.valueOf(movieId), listName };
        ListDatabaseHelper listdatabaseHelper = new ListDatabaseHelper(context);
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

    //fetch list function
    public void fetchList() {
        ListDatabaseHelper listDatabaseHelper = new ListDatabaseHelper( context);
        SQLiteDatabase db = listDatabaseHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + ListDatabaseHelper.TABLE_LISTS, null);
        if (cursor.getCount() > 0) {
            new Handler( Looper.getMainLooper());

            AlertDialog progressDialog = new MaterialAlertDialogBuilder(context)
                    .setView(R.layout.dialog_progress)
                    .setCancelable(false)
                    .create();
            progressDialog.show();

            FetchListThreadTMDb fetchListThreadTMDb = new FetchListThreadTMDb(context, listData -> {
                for (ListData data : listData) {
                    listDatabaseHelper.addList(data.getId(), data.getName());

                    ListDetailsThreadTMDb listDetailsThreadTMDb = new ListDetailsThreadTMDb(data.getId(), context, listDetailsData -> {

                        for (JSONObject item : listDetailsData) {
                            try {
                                int movieId = item.getInt("id");
                                String mediaType = item.getString("media_type");

                                listDatabaseHelper.addListDetails(data.getId(), data.getName(), movieId, mediaType);
                            } catch (JSONException e) {
                                e.printStackTrace();
                                progressDialog.dismiss();
                                Toast.makeText(context, R.string.error_occurred_in_list_data, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    listDetailsThreadTMDb.start();
                }
                progressDialog.dismiss();
            });
            fetchListThreadTMDb.fetchLists();
        }
        cursor.close();
    }
}