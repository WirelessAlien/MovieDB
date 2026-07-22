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
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.databinding.ItemKeywordBinding
import org.json.JSONObject

class KeywordAdapter(
    private val onItemClick: (JSONObject) -> Unit
) : PagingDataAdapter<JSONObject, KeywordAdapter.KeywordViewHolder>(diffCallback) {

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<JSONObject>() {
            override fun areItemsTheSame(oldItem: JSONObject, newItem: JSONObject): Boolean {
                return oldItem.optInt("id") == newItem.optInt("id")
            }

            override fun areContentsTheSame(oldItem: JSONObject, newItem: JSONObject): Boolean {
                return oldItem.toString() == newItem.toString()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeywordViewHolder {
        val binding = ItemKeywordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return KeywordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: KeywordViewHolder, position: Int) {
        val keyword = getItem(position)
        if (keyword != null) {
            holder.bind(keyword)
            holder.itemView.setOnClickListener { onItemClick(keyword) }
        }
    }

    class KeywordViewHolder(private val binding: ItemKeywordBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(keyword: JSONObject) {
            binding.keywordText.text = keyword.optString("name")
        }
    }
}