/*
 *     This file is part of "ShowCase" formerly Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     ShowCase is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ShowCase is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with "ShowCase".  If not, see <https://www.gnu.org/licenses/>.
 */
package com.wirelessalien.android.moviedb.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.CastActivity
import com.wirelessalien.android.moviedb.adapter.CastBaseAdapter.CastItemViewHolder
import org.json.JSONException
import org.json.JSONObject

/*
* This class provides a list of cards containing the given actors. 
* The card interaction (going to ActorActivity when clicking) can also be found here.
*/
class CastBaseAdapter // Create the adapter with the list of actors and the context.
    (private val castList: ArrayList<JSONObject>, private val context: Context) :
    RecyclerView.Adapter<CastItemViewHolder>() {
    override fun getItemCount(): Int {
        // Return the amount of items in the list.
        return castList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CastItemViewHolder {
        // Create a new CardItem when needed.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.cast_card, parent, false)
        return CastItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: CastItemViewHolder, position: Int) {
        // Fill the views with the needed data.
        val actorData = castList[position]
        try {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                context
            )
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "h632" else "w185"
            holder.castName.text = actorData.getString("name")
            holder.characterName.text = actorData.getString("character")

            // Load the image of the person (or an icon showing that it is not available).
            if (actorData.getString("profile_path") == "null") {
                holder.castImage.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        context.resources,
                        R.drawable.ic_profile_photo,
                        null
                    )
                )
            } else {
                Picasso.get().load(
                    "https://image.tmdb.org/t/p/" + imageSize +
                            actorData.getString("profile_path")
                )
                    .into(holder.castImage)
            }

            // Once the image is loaded, make it fade in quickly.
            val animation = AnimationUtils.loadAnimation(
                context,
                R.anim.fade_in_fast
            )
            holder.castImage.startAnimation(animation)
        } catch (je: JSONException) {
            je.printStackTrace()
        }
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        // Send the actor data and the user to CastActivity when clicking on a card.
        holder.itemView.setOnClickListener { view: View ->
            val intent = Intent(view.context, CastActivity::class.java)
            intent.putExtra("actorObject", actorData.toString())
            view.context.startActivity(intent)
        }
    }

    override fun getItemId(position: Int): Long {
        // The id is the same as the position,
        // therefore returning the position is enough.
        return position.toLong()
    }

    // Views that each CardItem will contain.
    class CastItemViewHolder internal constructor(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val cardView: CardView
        val castName: TextView
        val characterName: TextView
        val castImage: ImageView

        init {
            cardView = itemView.findViewById(R.id.cardView)
            castName = itemView.findViewById(R.id.castName)
            characterName = itemView.findViewById(R.id.characterName)
            castImage = itemView.findViewById(R.id.castImage)
        }
    }

    companion object {
        private const val HD_IMAGE_SIZE = "key_hq_images"
    }
}
