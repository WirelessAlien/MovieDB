package com.wirelessalien.android.moviedb.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.data.TVSeason;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.TVSeasonDetailsActivity;

import java.util.List;

public class TVSeasonAdapter extends RecyclerView.Adapter<TVSeasonAdapter.SeasonViewHolder> {

    private final List<TVSeason> seasons;
    private final Context context;
    int tvShowId;
    SharedPreferences preferences;
    public TVSeasonAdapter(Context context, List<TVSeason> seasons) {
        this.context = context;
        this.seasons = seasons;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.tvShowId = preferences.getInt("tvShowId", -1);
    }

    @NonNull
    @Override
    public SeasonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.tv_season_item, parent, false);
        return new SeasonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SeasonViewHolder holder, int position) {
        TVSeason season = seasons.get(position);
        holder.name.setText(season.getName());
        holder.overview.setText(season.getOverview());
        holder.episodeCount.setText(String.valueOf(season.getEpisodeCount()));
        holder.airDate.setText(season.getAirDate());
        holder.voteAverage.setRating((float) season.getVoteAverage() / 2);
        Picasso.get().load(season.getPosterUrl()).into(holder.poster);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TVSeasonDetailsActivity.class);
            intent.putExtra("tvShowId", tvShowId);
            intent.putExtra("seasonNumber", season.getSeasonNumber());
            v.getContext().startActivity(intent);
        });

    }

    @Override
    public int getItemCount() {
        return (seasons != null) ? seasons.size() : 0;
    }

    public static class SeasonViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView overview;
        TextView episodeCount;
        TextView airDate;
        RatingBar voteAverage;
        ImageView poster;

        public SeasonViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.title);
            overview = itemView.findViewById(R.id.description);
            poster = itemView.findViewById(R.id.image);
            episodeCount = itemView.findViewById(R.id.episodeCount);
            airDate = itemView.findViewById(R.id.date);
            voteAverage = itemView.findViewById(R.id.rating);
        }
    }
}