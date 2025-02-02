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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.ListData
import com.wirelessalien.android.moviedb.databinding.ListListItemBinding
import com.wirelessalien.android.moviedb.tmdb.account.DeleteList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ListAdapter(
    private var listData: MutableList<ListData>,
    private val onItemClickListener: OnItemClickListener,
    private val showDeleteButton: Boolean
) : RecyclerView.Adapter<ListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listData[position])
    }

    override fun getItemCount(): Int {
        return listData.size
    }

    fun updateData(newData: List<ListData>?) {
        if (newData != null) {
            val diffCallback = ListDataDiffCallback(listData, newData)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            listData.clear()
            listData.addAll(newData)
            diffResult.dispatchUpdatesTo(this)
        }
    }

    interface OnItemClickListener {
        fun onItemClick(listData: ListData?)
    }

    inner class ViewHolder(
        private val binding: ListListItemBinding,
        private val onItemClickListener: OnItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(listData: ListData) {
            binding.listNameTextView.text = listData.name

            if (listData.description.isEmpty()) {
                binding.description.setText(R.string.no_description)
            } else {
                binding.description.text = listData.description
            }
            binding.itemCount.text = itemView.context.getString(R.string.items_count, listData.itemCount)
            itemView.tag = listData
            itemView.setOnClickListener { onItemClickListener.onItemClick(itemView.tag as ListData) }
            if (showDeleteButton) {
                binding.deleteButton.visibility = View.VISIBLE
                binding.deleteButton.setOnClickListener {
                    val deleteListThread = DeleteList(listData.id, itemView.context as Activity, object : DeleteList.OnListDeletedListener {
                        override fun onListDeleted() {
                            val oldList = ArrayList(this@ListAdapter.listData)
                            this@ListAdapter.listData.remove(listData)
                            val diffCallback = ListDataDiffCallback(oldList, this@ListAdapter.listData)
                            val diffResult = DiffUtil.calculateDiff(diffCallback)
                            diffResult.dispatchUpdatesTo(this@ListAdapter)
                        }
                    })
                    CoroutineScope(Dispatchers.Main).launch {
                        deleteListThread.deleteList()
                    }
                }
            } else {
                binding.deleteButton.visibility = View.GONE
            }
        }
    }

    class ListDataDiffCallback(
        private val oldList: List<ListData>,
        private val newList: List<ListData>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}