package com.wirelessalien.android.moviedb.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.Episode;
import com.wirelessalien.android.moviedb.R;

import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder> {

    private final List<Episode> episodes;
    private final Context context;

    public EpisodeAdapter(Context context, List<Episode> episodes) {
        this.context = context;
        this.episodes = episodes;
    }

    @NonNull
    @Override
    public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.episode_item, parent, false);
        return new EpisodeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        Episode episode = episodes.get(position);
        holder.episodeName.setText(episode.getName());
        holder.episodeOverview.setText(episode.getOverview());
        holder.episodeAirDate.setText(episode.getAirDate());
        holder.episodeRuntime.setText( episode.getRuntime() + " minutes");
        holder.episodeVoteAverage.setRating((float) episode.getVoteAverage() / 2);
        Picasso.get()
                .load(episode.getPosterPath())
                .placeholder(R.drawable.ic_broken_image)
                .into(holder.episodePoster);
    }

    @Override
    public int getItemCount() {
        return (episodes != null) ? episodes.size() : 0;
    }

    public static class EpisodeViewHolder extends RecyclerView.ViewHolder {
        TextView episodeName;
        TextView episodeOverview;
        ImageView episodePoster;
        TextView episodeAirDate;
        TextView episodeRuntime;
        RatingBar episodeVoteAverage;



        public EpisodeViewHolder(@NonNull View itemView) {
            super(itemView);
            episodeName = itemView.findViewById(R.id.title);
            episodeOverview = itemView.findViewById(R.id.description);
            episodePoster = itemView.findViewById(R.id.image);
            episodeAirDate = itemView.findViewById(R.id.date);
            episodeRuntime = itemView.findViewById(R.id.episodeCount);
            episodeVoteAverage = itemView.findViewById(R.id.rating);
        }
    }
}