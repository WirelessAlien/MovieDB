package com.wirelessalien.android.moviedb.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wirelessalien.android.moviedb.ListData;
import com.wirelessalien.android.moviedb.R;

import java.util.List;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.ViewHolder> {
    private final List<ListData> listData;
    private final OnItemClickListener onItemClickListener;

    public ListAdapter(List<ListData> listData, OnItemClickListener onItemClickListener) {
        this.listData = listData;
        this.onItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
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

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView listNameTextView;
        private final OnItemClickListener onItemClickListener;

        public ViewHolder(@NonNull View itemView, OnItemClickListener onItemClickListener) {
            super(itemView);
            listNameTextView = itemView.findViewById(R.id.listNameTextView);
            this.onItemClickListener = onItemClickListener;
        }

        public void bind(ListData listData) {
            listNameTextView.setText(listData.getName());
            itemView.setTag(listData);
            itemView.setOnClickListener(v -> onItemClickListener.onItemClick((ListData) itemView.getTag()));
        }
    }
}