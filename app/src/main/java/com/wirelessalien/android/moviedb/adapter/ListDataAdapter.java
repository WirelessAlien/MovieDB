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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.data.ListDetailsData;
import com.wirelessalien.android.moviedb.tmdb.account.AddToListThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.DeleteFromListThreadTMDb;

import java.util.List;

public class ListDataAdapter extends RecyclerView.Adapter<ListDataAdapter.ViewHolder> {
    private final List<ListDetailsData> listData;
    private final ListDataAdapter.OnItemClickListener onItemClickListener;
    private final Context context;

    public ListDataAdapter(List<ListDetailsData> listDetailsData, Context context, ListDataAdapter.OnItemClickListener onItemClickListener) {
        this.listData = listDetailsData;
        this.onItemClickListener = onItemClickListener;
        this.context = context;
    }

    @NonNull
    @Override
    public ListDataAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
        return new ListDataAdapter.ViewHolder(view, onItemClickListener, listData); // Modify this line
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ListDetailsData data = listData.get(position);
        holder.listName.setText(data.getListName());

        holder.listSwitch.setOnCheckedChangeListener(null);
        holder.listSwitch.setChecked(data.isMovieInList());

        holder.listSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                ListDetailsData listData = this.listData.get(pos);
                if (isChecked) {
                    new AddToListThreadTMDb(listData.getMovieId(), listData.getListId(), listData.getMediaType(), context).start();
                } else {
                    new DeleteFromListThreadTMDb(listData.getMovieId(), listData.getListId(), listData.getMediaType(), context, pos, null, null).start();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return listData.size();
    }

    public interface OnItemClickListener {
        void onItemClick(ListDetailsData listData);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView listName;
        MaterialSwitch listSwitch;
        private final ListDataAdapter.OnItemClickListener onItemClickListener;
        private final List<ListDetailsData> listData;

        public ViewHolder(@NonNull View itemView, ListDataAdapter.OnItemClickListener onItemClickListener, List<ListDetailsData> listData) {
            super(itemView);
            listName = itemView.findViewById(R.id.list_name);
            listSwitch = itemView.findViewById(R.id.list_switch);
            this.onItemClickListener = onItemClickListener;
            this.listData = listData;
        }
    }
}