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
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.CastActivity
import com.wirelessalien.android.moviedb.databinding.CastCardBinding
import org.json.JSONException
import org.json.JSONObject

/**
 * Adapter class for displaying a list of cast members in a RecyclerView.
 */

class CastBaseAdapter(private val castList: ArrayList<JSONObject>, private val context: Context) :
    RecyclerView.Adapter<CastBaseAdapter.CastItemViewHolder>() {

    override fun getItemCount(): Int {
        return castList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CastItemViewHolder {
        val binding = CastCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CastItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CastItemViewHolder, position: Int) {
        val actorData = castList[position]
        try {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "h632" else "w185"
            holder.binding.castName.text = actorData.getString("name")
            holder.binding.characterName.text = actorData.getString("character")

            if (actorData.getString("profile_path") == "null") {
                holder.binding.castImage.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        context.resources,
                        R.drawable.ic_profile_photo,
                        null
                    )
                )
            } else {
                Picasso.get().load("https://image.tmdb.org/t/p/$imageSize${actorData.getString("profile_path")}")
                    .into(holder.binding.castImage)
            }

            val animation = AnimationUtils.loadAnimation(context, R.anim.fade_in_fast)
            holder.binding.castImage.startAnimation(animation)
        } catch (je: JSONException) {
            je.printStackTrace()
        }

        holder.binding.cardView.setBackgroundColor(Color.TRANSPARENT)
        holder.itemView.setOnClickListener { view ->
            val intent = Intent(view.context, CastActivity::class.java)
            intent.putExtra("actorObject", actorData.toString())
            view.context.startActivity(intent)
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    class CastItemViewHolder(val binding: CastCardBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val HD_IMAGE_SIZE = "key_hq_images"
    }
}
