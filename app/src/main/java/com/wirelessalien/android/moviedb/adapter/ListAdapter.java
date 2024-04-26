package com.wirelessalien.android.moviedb.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wirelessalien.android.moviedb.data.ListData;
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

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView listNameTextView, descriptionTextView, itemCountTextView;
        private final RatingBar ratingBar;
        private final OnItemClickListener onItemClickListener;

        public ViewHolder(@NonNull View itemView, OnItemClickListener onItemClickListener) {
            super(itemView);
            listNameTextView = itemView.findViewById(R.id.listNameTextView);
            descriptionTextView = itemView.findViewById(R.id.description);
            itemCountTextView = itemView.findViewById(R.id.itemCount);
            ratingBar = itemView.findViewById(R.id.ratingBar);
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
            ratingBar.setRating((float) listData.getAverageRating()/2);
            itemView.setTag(listData);
            itemView.setOnClickListener(v -> onItemClickListener.onItemClick((ListData) itemView.getTag()));
        }
    }
}