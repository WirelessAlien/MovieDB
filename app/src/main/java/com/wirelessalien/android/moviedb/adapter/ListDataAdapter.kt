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
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.data.ListDetailsData
import com.wirelessalien.android.moviedb.databinding.ListItemBinding
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
        val binding = ListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = listData[position]
        holder.bind(data)
    }

    override fun getItemCount(): Int {
        return listData.size
    }

    interface OnItemClickListener {
        fun onItemClick(listData: ListDetailsData?)
    }

    class ViewHolder(
        private val binding: ListItemBinding,
        private val onItemClickListener: OnItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(data: ListDetailsData) {
            binding.listName.text = data.listName
            binding.listSwitch.setOnCheckedChangeListener(null)
            binding.listSwitch.isChecked = data.isMovieInList
            binding.listSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    CoroutineScope(Dispatchers.Main).launch {
                        if (isChecked) {
                            AddToList(
                                data.movieId,
                                data.listId,
                                data.mediaType,
                                binding.root.context
                            ).addToList()
                        } else {
                            DeleteFromList(
                                data.movieId,
                                data.listId,
                                data.mediaType,
                                binding.root.context,
                                pos,
                                null,
                                null
                            ).deleteFromList()
                        }
                    }
                }
            }
            itemView.setOnClickListener { onItemClickListener.onItemClick(data) }
        }
    }
}