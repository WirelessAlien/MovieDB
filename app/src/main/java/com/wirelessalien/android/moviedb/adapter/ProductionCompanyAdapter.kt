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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.databinding.ProductionCompanyLayoutBinding

class ProductionCompanyAdapter(
    private val context: Context,
) : RecyclerView.Adapter<ProductionCompanyAdapter.ViewHolder>() {

    private val companies = mutableListOf<CompanyItem>()

    class ProductionCompanyDiffCallback(
        private val oldList: List<CompanyItem>,
        private val newList: List<CompanyItem>
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

    data class CompanyItem(
        val logoPath: String,
        val name: String,
        val country: String
    )

    inner class ViewHolder(private val binding: ProductionCompanyLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(company: CompanyItem) {
            if (company.logoPath.isNotEmpty() && company.logoPath != "null") {
                binding.companyLogo.visibility = View.VISIBLE
                Picasso.get()
                    .load("https://image.tmdb.org/t/p/w154${company.logoPath}")
                    .into(binding.companyLogo)
            } else {
                binding.companyLogo.visibility = View.GONE
            }

            binding.companyName.text = company.name
            
            if (company.country.isNotEmpty() && company.country != "null") {
                binding.companyCountry.visibility = View.VISIBLE
                binding.companyCountry.text = company.country
            } else {
                binding.companyCountry.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ProductionCompanyLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(companies[position])
    }

    override fun getItemCount() = companies.size

    fun updateCompanies(newCompanies: List<CompanyItem>) {
        val diffCallback = ProductionCompanyDiffCallback(companies, newCompanies)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        companies.clear()
        companies.addAll(newCompanies)

        diffResult.dispatchUpdatesTo(this)
    }
}