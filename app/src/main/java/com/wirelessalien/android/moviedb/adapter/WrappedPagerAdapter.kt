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

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ItemWrappedSlideBinding

class WrappedPagerAdapter(
    private val data: com.wirelessalien.android.moviedb.helper.WrappedData,
    private val onShareClicked: (View) -> Unit,
    private val onSaveClicked: (View) -> Unit,
    private val onPickImageClicked: ((ImageView) -> Unit)? = null,
    private val initialImageUri: Uri? = null
) : RecyclerView.Adapter<WrappedPagerAdapter.WrappedViewHolder>() {

    private val SLIDE_COUNT = 6

    inner class WrappedViewHolder(val binding: ItemWrappedSlideBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WrappedViewHolder {
        val binding = ItemWrappedSlideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WrappedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WrappedViewHolder, position: Int) {
        // Reset visibility
        holder.binding.volumeLayout.visibility = View.GONE
        holder.binding.genresLayout.visibility = View.GONE
        holder.binding.hallOfFameLayout.visibility = View.GONE
        holder.binding.timelineLayout.visibility = View.GONE
        holder.binding.peakLayout.visibility = View.GONE
        holder.binding.summaryLayout.visibility = View.GONE
        
        val context = holder.binding.root.context

        when (position) {
            0 -> {
                holder.binding.slideTitle.text = context.getString(R.string.wrapped_slide1_title)
                holder.binding.volumeLayout.visibility = View.VISIBLE
                holder.binding.tvTotalTitles.text = data.totalTitles.toString()
                holder.binding.tvTotalMovies.text = data.totalMovies.toString()
                holder.binding.tvTotalShows.text = data.totalShows.toString()
                holder.binding.tvTotalEpisodes.text = data.totalEpisodes.toString()
            }
            1 -> {
                holder.binding.slideTitle.text = context.getString(R.string.wrapped_slide2_title)
                holder.binding.genresLayout.visibility = View.VISIBLE
                val genres = data.topGenres
                holder.binding.tvGenre1.text = if (genres.isNotEmpty()) "#1 ${genres[0].first} (${genres[0].second}%)" else "N/A"
                holder.binding.tvGenre2.text = if (genres.size > 1) "#2 ${genres[1].first} (${genres[1].second}%)" else ""
                holder.binding.tvGenre3.text = if (genres.size > 2) "#3 ${genres[2].first} (${genres[2].second}%)" else ""
                holder.binding.tvGenre4.text = if (genres.size > 3) "#4 ${genres[3].first} (${genres[3].second}%)" else ""
                holder.binding.tvGenre5.text = if (genres.size > 4) "#5 ${genres[4].first} (${genres[4].second}%)" else ""
            }
            2 -> {
                holder.binding.slideTitle.text = context.getString(R.string.wrapped_slide3_title)
                holder.binding.hallOfFameLayout.visibility = View.VISIBLE
                holder.binding.tvAverageRating.text = context.getString(R.string.wrapped_avg_rating_format, String.format("%.1f", data.averageRating))
                holder.binding.tvLowestRated.text = data.lowestRatedTitle
                
                var isGrid = false

                fun updateRecyclerView() {
                    if (isGrid) {
                        holder.binding.rvTopRated.layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, 3)
                        holder.binding.rvTopRated.adapter = WrappedPosterAdapter(data.topRatedTitleIds, true)
                        holder.binding.btnToggleLayout.text = "Scroll View"
                    } else {
                        holder.binding.rvTopRated.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
                        holder.binding.rvTopRated.adapter = WrappedPosterAdapter(data.topRatedTitleIds, false)
                        holder.binding.btnToggleLayout.text = "Grid View"
                    }
                }

                updateRecyclerView()

                holder.binding.btnToggleLayout.setOnClickListener {
                    isGrid = !isGrid
                    updateRecyclerView()
                }
            }
            3 -> {
                holder.binding.slideTitle.text = context.getString(R.string.wrapped_slide4_title)
                holder.binding.timelineLayout.visibility = View.VISIBLE
                holder.binding.tvFirstTitle.text = data.firstTitle
                holder.binding.tvFirstDate.text = data.firstDate
                holder.binding.tvLastTitle.text = data.lastTitle
                holder.binding.tvLastDate.text = data.lastDate
            }
            4 -> {
                holder.binding.slideTitle.text = context.getString(R.string.wrapped_slide5_title)
                holder.binding.peakLayout.visibility = View.VISIBLE
                holder.binding.tvPeakMonth.text = context.getString(R.string.wrapped_peak_month_format, data.peakMonth, data.peakMonthCount)
                holder.binding.tvPeakDayOfWeek.text = context.getString(R.string.wrapped_peak_day_format, data.peakDayOfWeek, data.peakDayOfWeekPercentage)
                holder.binding.tvTopBingeDate.text = context.getString(R.string.wrapped_top_binge_format, data.topBingeDate, data.topBingeCount)
            }
            5 -> {
                holder.binding.slideTitle.text = context.getString(R.string.wrapped_slide6_title)
                holder.binding.summaryLayout.visibility = View.VISIBLE
                
                holder.binding.tvSummaryYear.text = context.getString(R.string.wrapped_summary_year, data.year)
                holder.binding.tvPersonaBadge.text = data.personaBadge
                holder.binding.tvPersonaDesc.text = data.personaDescription
                holder.binding.tvSummaryTitles.text = data.totalTitles.toString()
                holder.binding.tvSummaryGenre.text = if (data.topGenres.isNotEmpty()) data.topGenres[0].first else "N/A"
                holder.binding.tvSummaryRating.text = String.format("%.1f", data.averageRating)

                if (initialImageUri != null) {
                    holder.binding.ivProfileImage.setImageURI(initialImageUri)
                }

                holder.binding.ivProfileImage.setOnClickListener {
                    onPickImageClicked?.invoke(holder.binding.ivProfileImage)
                }
                
                holder.binding.btnShare.setOnClickListener { onShareClicked(holder.binding.shareCard) }
                holder.binding.btnSave.setOnClickListener { onSaveClicked(holder.binding.shareCard) }
            }
        }
    }

    override fun getItemCount(): Int = SLIDE_COUNT
}
