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
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.CastActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/*
* This class provides a list of cards containing the given actors. 
* The card interaction (going to ActorActivity when clicking) can also be found here.
*/
public class CastBaseAdapter extends RecyclerView.Adapter<CastBaseAdapter.CastItemViewHolder> {

    private final ArrayList<JSONObject> castList;
    private final Context context;

    // Create the adapter with the list of actors and the context.
    public CastBaseAdapter(ArrayList<JSONObject> castList, Context context) {
        this.castList = castList;
        this.context = context;
    }

    @Override
    public int getItemCount() {
        // Return the amount of items in the list.
        return castList.size();
    }

    @NonNull
    @Override
    public CastItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Create a new CardItem when needed.
        View view = LayoutInflater.from(parent.getContext())
                .inflate( R.layout.cast_card, parent, false);
        return new CastItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(CastItemViewHolder holder, int position) {
        // Fill the views with the needed data.
        final JSONObject actorData = castList.get(position);

        try {
            holder.castName.setText(actorData.getString("name"));
            holder.characterName.setText(actorData.getString("character"));

            // Load the image of the person (or an icon showing that it is not available).
            if (actorData.getString("profile_path").equals("null")) {
                holder.castImage.setImageDrawable( ResourcesCompat.getDrawable(context.getResources(), (R.drawable.ic_profile_photo), null));
            } else {
                Picasso.get().load("https://image.tmdb.org/t/p/w300" +
                        actorData.getString("profile_path"))
                        .into(holder.castImage);
            }

            // Once the image is loaded, make it fade in quickly.
            Animation animation = AnimationUtils.loadAnimation(context,
                    R.anim.fade_in_fast);
            holder.castImage.startAnimation(animation);
        } catch (JSONException je) {
            je.printStackTrace();
        }

        holder.itemView.setBackgroundColor(Color.TRANSPARENT);

        // Send the actor data and the user to CastActivity when clicking on a card.
        holder.itemView.setOnClickListener( view -> {
            Intent intent = new Intent(view.getContext(), CastActivity.class);
            intent.putExtra("actorObject", actorData.toString());
            view.getContext().startActivity(intent);
        } );
    }

    @Override
    public long getItemId(int position) {
        // The id is the same as the position,
        // therefore returning the position is enough.
        return position;
    }

    // Views that each CardItem will contain.
    public static class CastItemViewHolder extends RecyclerView.ViewHolder {
        final CardView cardView;
        final TextView castName;
        final TextView characterName;
        final ImageView castImage;

        CastItemViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            castName = itemView.findViewById(R.id.castName);
            characterName = itemView.findViewById(R.id.characterName);
            castImage = itemView.findViewById(R.id.castImage);
        }
    }

}
