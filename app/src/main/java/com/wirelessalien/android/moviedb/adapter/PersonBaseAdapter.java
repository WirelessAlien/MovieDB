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
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.CastActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/*
* This class provides a list of cards containing the given persons. 
* The card interaction (going to ActorActivity when clicking) can also be found here.
*/
public class PersonBaseAdapter extends RecyclerView.Adapter<PersonBaseAdapter.PersonItemViewHolder> {

    private final ArrayList<JSONObject> mPersonList;
    private static final String HD_IMAGE_SIZE = "key_hq_images";

    /**
     * Create the adapter with the list of persons and the context
     *
     * @param personList the list of people to be displayed.
     */
    public PersonBaseAdapter(ArrayList<JSONObject> personList) {
        mPersonList = personList;
    }

    @Override
    public int getItemCount() {
        // Return the amount of items in the list.
        return mPersonList.size();
    }

    @NonNull
    @Override
    public PersonItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Create a new CardItem when needed.
        View view = LayoutInflater.from(parent.getContext())
                .inflate( R.layout.person_card, parent, false);
        return new PersonItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(PersonItemViewHolder holder, int position) {
        // Fill the views with the needed data.
        final JSONObject personData = mPersonList.get(position);

        Context context = holder.cardView.getContext();

        try {
            SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            boolean loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false);

            String imageSize = loadHDImage ? "w780" : "w342";
            holder.personName.setText(personData.getString("name"));
            if (personData.getString("profile_path") == null) {
                holder.personImage.setImageDrawable(ResourcesCompat.getDrawable(context.getResources(), (R.drawable.ic_broken_image), null));
            } else {
                Picasso.get().load("https://image.tmdb.org/t/p/" + imageSize
                        + personData.getString("profile_path"))
                        .into(holder.personImage);
            }
        } catch (JSONException je) {
            je.printStackTrace();
        }

        // Send the person data and the user to CastActivity when clicking on a card.
        holder.itemView.setOnClickListener( view -> {
            Intent intent = new Intent(view.getContext(), CastActivity.class);
            intent.putExtra("actorObject", personData.toString());
            view.getContext().startActivity(intent);
        } );
    }

    @Override
    public long getItemId(int position) {
        // The id is the same as the position,
        // therefore returning the position is enough.
        return position;
    }

    /**
     * Views that each CardItem will contain.
     */
    public static class PersonItemViewHolder extends RecyclerView.ViewHolder {
        final CardView cardView;
        final TextView personName;
        final ImageView personImage;

        PersonItemViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            personName = itemView.findViewById(R.id.personName);
            personImage = itemView.findViewById(R.id.personImage);
        }
    }

}

