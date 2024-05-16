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

package com.wirelessalien.android.moviedb.adapter;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.data.ListData;
import com.wirelessalien.android.moviedb.tmdb.account.DeleteListThreadTMDb;

import java.util.List;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.ViewHolder> {
    private final List<ListData> listData;
    private final OnItemClickListener onItemClickListener;
    private final boolean showDeleteButton;

    public ListAdapter(List<ListData> listData, OnItemClickListener onItemClickListener, boolean showDeleteButton) { // Modify this line
        this.listData = listData;
        this.onItemClickListener = onItemClickListener;
        this.showDeleteButton = showDeleteButton;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_list_item, parent, false);
        return new ViewHolder(view, onItemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(listData.get(position));
    }

    @Override
    public int getItemCount() {
        return listData.size();
    }

    public void updateData(List<ListData> newData) {
        this.listData.addAll(newData);
        notifyDataSetChanged();
    }

    public interface OnItemClickListener {
        void onItemClick(ListData listData);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView listNameTextView, descriptionTextView, itemCountTextView;
        private final Button deleteButton;
        private final OnItemClickListener onItemClickListener;

        public ViewHolder(@NonNull View itemView, OnItemClickListener onItemClickListener) {
            super(itemView);
            listNameTextView = itemView.findViewById(R.id.listNameTextView);
            descriptionTextView = itemView.findViewById(R.id.description);
            itemCountTextView = itemView.findViewById(R.id.itemCount);
            deleteButton = itemView.findViewById(R.id.deleteButton); // Add this line
            this.onItemClickListener = onItemClickListener;
        }

        public void bind(ListData listData) {
            listNameTextView.setText(listData.getName());

            // Check if description is null or empty
            if (listData.getDescription() == null || listData.getDescription().isEmpty()) {
                descriptionTextView.setText("No description");
            } else {
                descriptionTextView.setText(listData.getDescription());
            }

            itemCountTextView.setText("Items: " + (listData.getItemCount()));
            itemView.setTag(listData);
            itemView.setOnClickListener(v -> onItemClickListener.onItemClick((ListData) itemView.getTag()));

            if (showDeleteButton) {
                deleteButton.setVisibility(View.VISIBLE);
                deleteButton.setOnClickListener(v -> {
                    DeleteListThreadTMDb deleteListThread = new DeleteListThreadTMDb(listData.getId(), (Activity) itemView.getContext(), () -> {
                        ListAdapter.this.listData.remove(listData);
                        notifyDataSetChanged();
                    });
                    deleteListThread.start();
                });
            } else {
                deleteButton.setVisibility(View.GONE);
            }
        }
    }
}