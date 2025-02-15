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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.databinding.WatchProviderLayoutBinding

class WatchProviderAdapter(
    private val context: Context,
    private val onProviderClick: (String) -> Unit
) : RecyclerView.Adapter<WatchProviderAdapter.ViewHolder>() {

    private val providers = mutableListOf<ProviderItem>()

    class WatchProviderDiffCallback(
        private val oldList: List<ProviderItem>,
        private val newList: List<ProviderItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
            return oldList[oldPos].name == newList[newPos].name
        }

        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
            return oldList[oldPos] == newList[newPos]
        }
    }

    data class ProviderItem(
        val logoPath: String,
        val name: String,
        val type: String
    )

    inner class ViewHolder(private val binding: WatchProviderLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(provider: ProviderItem) {
            Picasso.get()
                .load("https://image.tmdb.org/t/p/original${provider.logoPath}")
                .fit()
                .into(binding.providerLogo)

            binding.providerName.text = provider.name
            binding.providerType.text = provider.type

            binding.root.setOnClickListener {
                onProviderClick(provider.name)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = WatchProviderLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(providers[position])
    }

    override fun getItemCount() = providers.size

    fun updateProviders(newProviders: List<ProviderItem>) {
        val diffCallback = WatchProviderDiffCallback(providers, newProviders)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        providers.clear()
        providers.addAll(newProviders)

        diffResult.dispatchUpdatesTo(this)
    }
}