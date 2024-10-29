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

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.ListData
import com.wirelessalien.android.moviedb.tmdb.account.DeleteList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ListAdapter(
    private val listData: MutableList<ListData>,
    private val onItemClickListener: OnItemClickListener,
    private val showDeleteButton: Boolean
) : RecyclerView.Adapter<ListAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_list_item, parent, false)
        return ViewHolder(view, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listData[position])
    }

    override fun getItemCount(): Int {
        return listData.size
    }

    fun updateData(newData: List<ListData>?) {
        if (newData != null) {
            listData.addAll(newData)
            notifyDataSetChanged()
        }
    }

    interface OnItemClickListener {
        fun onItemClick(listData: ListData?)
    }

    inner class ViewHolder(itemView: View, private val onItemClickListener: OnItemClickListener) :
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

        fun bind(listData: ListData) {
            listNameTextView.text = listData.name

            // Check if description is null or empty
            if (listData.description.isEmpty()) {
                descriptionTextView.setText(R.string.no_description)
            } else {
                descriptionTextView.text = listData.description
            }
            itemCountTextView.text = itemView.context.getString(R.string.items_count, listData.itemCount)
            itemView.tag = listData
            itemView.setOnClickListener { onItemClickListener.onItemClick(itemView.tag as ListData) }
            if (showDeleteButton) {
                deleteButton.visibility = View.VISIBLE
                deleteButton.setOnClickListener {
                    val deleteListThread = DeleteList(listData.id, itemView.context as Activity, object : DeleteList.OnListDeletedListener {
                        override fun onListDeleted() {
                            this@ListAdapter.listData.remove(listData)
                            notifyDataSetChanged()
                        }
                    })
                    CoroutineScope(Dispatchers.Main).launch {
                        deleteListThread.deleteList()
                    }
                }
            } else {
                deleteButton.visibility = View.GONE
            }
        }
    }
}