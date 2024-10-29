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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.ListDetailsData
import com.wirelessalien.android.moviedb.tmdb.account.AddToList
import com.wirelessalien.android.moviedb.tmdb.account.DeleteFromList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ListDataAdapter(
    private val listData: List<ListDetailsData>,
    private val context: Context,
    private val onItemClickListener: OnItemClickListener
) : RecyclerView.Adapter<ListDataAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
        return ViewHolder(view, onItemClickListener, listData)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = listData[position]
        holder.listName.text = data.listName
        holder.listSwitch.setOnCheckedChangeListener(null)
        holder.listSwitch.isChecked = data.isMovieInList
        holder.listSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val listData = listData[pos]
                CoroutineScope(Dispatchers.Main).launch {
                    if (isChecked) {
                        AddToList(
                            listData.movieId,
                            listData.listId,
                            listData.mediaType,
                            context
                        ).addToList()
                    } else {
                        DeleteFromList(
                            listData.movieId,
                            listData.listId,
                            listData.mediaType,
                            context,
                            pos,
                            null,
                            null
                        ).deleteFromList()
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return listData.size
    }

    interface OnItemClickListener {
        fun onItemClick(listData: ListDetailsData?)
    }

    class ViewHolder(
        itemView: View,
        private val onItemClickListener: OnItemClickListener,
        private val listData: List<ListDetailsData>
    ) : RecyclerView.ViewHolder(itemView) {
        var listName: TextView
        var listSwitch: MaterialSwitch

        init {
            listName = itemView.findViewById(R.id.list_name)
            listSwitch = itemView.findViewById(R.id.list_switch)
        }
    }
}