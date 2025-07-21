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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.ListDataTmdb
import com.wirelessalien.android.moviedb.databinding.ListListItemBinding
import com.wirelessalien.android.moviedb.fragment.ListTmdbUpdateBottomSheetFragment
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ListAdapterTmdb(
    private var listDatumTmdbs: MutableList<ListDataTmdb>,
    private val onItemClickListener: OnItemClickListener,
    private val onListUpdatedListener: ListTmdbUpdateBottomSheetFragment.OnListUpdatedListener
) : RecyclerView.Adapter<ListAdapterTmdb.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listDatumTmdbs[position])
    }

    override fun getItemCount(): Int {
        return listDatumTmdbs.size
    }

    fun updateData(newData: List<ListDataTmdb>?) {
        if (newData != null) {
            val diffCallback = ListDataDiffCallback(listDatumTmdbs, newData)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            listDatumTmdbs.clear()
            listDatumTmdbs.addAll(newData)
            diffResult.dispatchUpdatesTo(this)
        }
    }

    interface OnItemClickListener {
        fun onItemClick(listDataTmdb: ListDataTmdb?)
    }

    inner class ViewHolder(
        private val binding: ListListItemBinding,
        private val onItemClickListener: OnItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(listDataTmdb: ListDataTmdb) {
            binding.listNameTextView.text = listDataTmdb.name

            if (listDataTmdb.description.isEmpty()) {
                binding.description.setText(R.string.no_description)
            } else {
                binding.description.text = listDataTmdb.description
            }
            binding.itemCount.text = listDataTmdb.itemCount.toString()
            binding.averageRating.rating = listDataTmdb.averageRating.toFloat() / 2
            binding.publicStatus.setImageResource(
                if (listDataTmdb.public) R.drawable.ic_lock_open else R.drawable.ic_lock
            )
            val apiDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
            apiDateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = apiDateFormat.parse(listDataTmdb.updatedAt)

            val dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
            val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
            timeFormatter.timeZone = TimeZone.getDefault()

            val formattedDate = if (date != null) {
                "${dateFormatter.format(date)} ${timeFormatter.format(date)}"
            } else {
                ""
            }

            binding.updatedAt.text = itemView.context.getString(R.string.last_updated, formattedDate)
            itemView.tag = listDataTmdb
            itemView.setOnClickListener { onItemClickListener.onItemClick(itemView.tag as ListDataTmdb) }
            itemView.setOnLongClickListener {
                val bottomSheet = ListTmdbUpdateBottomSheetFragment(listDataTmdb, onListUpdatedListener)
                bottomSheet.show((itemView.context as AppCompatActivity).supportFragmentManager, bottomSheet.tag)
                true
            }
        }
    }

    class ListDataDiffCallback(
        private val oldList: List<ListDataTmdb>,
        private val newList: List<ListDataTmdb>
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