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
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.ListItemActivityTkt
import org.json.JSONObject

class ListAdapterTkt(
    private val listData: ArrayList<JSONObject>,
) : RecyclerView.Adapter<ListAdapterTkt.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listData[position])
    }

    override fun getItemCount(): Int {
        return listData.size
    }

    fun updateData(newData: List<JSONObject>?) {
        if (newData != null) {
            listData.addAll(newData)
            notifyDataSetChanged()
        }
    }

    inner class ViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        private val listNameTextView: TextView
        private val descriptionTextView: TextView
        private val itemCountTextView: TextView
        private val deleteButton: Button

        init {
            listNameTextView = itemView.findViewById(R.id.listNameTextView)
            descriptionTextView = itemView.findViewById(R.id.description)
            itemCountTextView = itemView.findViewById(R.id.itemCount)
            deleteButton = itemView.findViewById(R.id.deleteButton)
        }

        fun bind(jsonObject: JSONObject) {
            listNameTextView.text = jsonObject.getString("name")

            // Check if description is null or empty
            val description = jsonObject.getString("description")
            if (description.isEmpty()) {
                descriptionTextView.setText(R.string.no_description)
            } else {
                descriptionTextView.text = description
            }
            itemCountTextView.text = itemView.context.getString(R.string.items_count, jsonObject.getInt("number_of_items"))
            itemView.tag = jsonObject
            itemView.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, ListItemActivityTkt::class.java).apply {
                    putExtra("trakt_list_id", jsonObject.getInt("trakt_list_id"))
                    putExtra("trakt_list_name", jsonObject.getString("name"))
                }
                context.startActivity(intent)
            }
        }
    }
}