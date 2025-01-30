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

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.CastActivity
import org.json.JSONException
import org.json.JSONObject

class PersonDatabaseAdapter(private val personList: List<JSONObject>) :
    RecyclerView.Adapter<PersonDatabaseAdapter.PersonItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.person_card, parent, false)
        return PersonItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: PersonItemViewHolder, position: Int) {
        val personData = personList[position]
        val context = holder.cardView.context
        try {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "w780" else "w342"
            holder.personName.text = personData.getString("name")
            Picasso.get().load(
                "https://image.tmdb.org/t/p/$imageSize${personData.getString("profile_path")}"
            ).into(holder.personImage)
        } catch (je: JSONException) {
            je.printStackTrace()
        }

        holder.itemView.setOnClickListener { view: View ->
            val intent = Intent(view.context, CastActivity::class.java)
            intent.putExtra("actorObject", personData.toString())
            view.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return personList.size
    }

    class PersonItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.cardView)
        val personName: TextView = itemView.findViewById(R.id.personName)
        val personImage: ImageView = itemView.findViewById(R.id.personImage)
    }

    companion object {
        private const val HD_IMAGE_SIZE = "key_hq_images"
    }
}