package com.wirelessalien.android.moviedb.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.data.ListDetails;

import java.util.List;

public class ListDetailsAdapter extends RecyclerView.Adapter<ListDetailsAdapter.ListDetailsViewHolder> {

    private List<ListDetails> listDetailsData;
    private OnItemClickListener onItemClickListener;

    public ListDetailsAdapter(List<ListDetails> listDetailsData, OnItemClickListener onItemClickListener) {
        this.listDetailsData = listDetailsData;
        this.onItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public ListDetailsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_details_item, parent, false);
        return new ListDetailsViewHolder(view, onItemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ListDetailsViewHolder holder, int position) {
        ListDetails listDetails = listDetailsData.get(position);
        holder.bind(listDetails);
    }

    @Override
    public int getItemCount() {
        return listDetailsData.size();
    }

    public interface OnItemClickListener {
        void onItemClick(ListDetails listDetails);
    }

    static class ListDetailsViewHolder extends RecyclerView.ViewHolder {

        TextView mediaTypeView;
        TextView titleView;

        public ListDetailsViewHolder(@NonNull View itemView, OnItemClickListener onItemClickListener) {
            super(itemView);
            titleView = itemView.findViewById( R.id.title);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onItemClickListener.onItemClick((ListDetails) itemView.getTag());
                }
            });
        }

        public void bind(ListDetails listDetails) {
            titleView.setText(listDetails.getTitle());
            itemView.setTag(listDetails);
        }
    }
}