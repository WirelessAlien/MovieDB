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
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.data.Episode;
import com.wirelessalien.android.moviedb.databinding.EpisodeItemBinding;

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
        EpisodeItemBinding binding = EpisodeItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new EpisodeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        Episode episode = episodes.get(position);
        holder.binding.title.setText(episode.getName());
        holder.binding.episodeNumber.setText( "(" + episode.getEpisodeNumber() + ")");
        holder.binding.description.setText(episode.getOverview());
        holder.binding.date.setText(episode.getAirDate());
        holder.binding.episodeCount.setText( episode.getRuntime() + " minutes");
        holder.binding.rating.setRating((float) episode.getVoteAverage() / 2);
        Picasso.get()
                .load(episode.getPosterPath())
                .placeholder(R.color.md_theme_surface)
                .into(holder.binding.image);
    }

    @Override
    public int getItemCount() {
        return (episodes != null) ? episodes.size() : 0;
    }

    public static class EpisodeViewHolder extends RecyclerView.ViewHolder {
        EpisodeItemBinding binding;

        public EpisodeViewHolder(@NonNull EpisodeItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}