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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ItemWrappedSlideBinding

class WrappedPagerAdapter(
    private val data: com.wirelessalien.android.moviedb.helper.WrappedData,
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
                holder.binding.slideDesc.text = if (data.totalTitles > 20) context.getString(R.string.wrapped_slide1_desc_high) else context.getString(R.string.wrapped_slide1_desc_low)
                holder.binding.volumeLayout.visibility = View.VISIBLE
                
                holder.binding.tvTotalTitles.text = data.totalTitles.toString()
                holder.binding.tvTotalMovies.text = data.totalMovies.toString()
                holder.binding.tvTotalShows.text = data.totalShows.toString()
                holder.binding.tvTotalEpisodes.text = data.totalEpisodes.toString()
                
                val totalHours = Math.round((data.totalMovies * 2) + (data.totalEpisodes * 0.75)).toInt()
                val totalDays = totalHours / 24.0
                holder.binding.tvVolumeInsight.text = context.getString(R.string.wrapped_insight_format, totalHours, totalDays)
            }
            1 -> {
                holder.binding.slideTitle.text = context.getString(R.string.wrapped_slide2_title)
                holder.binding.slideDesc.text = context.getString(R.string.wrapped_slide2_desc)
                holder.binding.genresLayout.visibility = View.VISIBLE
                val genres = data.topGenres
                
                if (genres.isNotEmpty()) {
                    holder.binding.cvGenre1.visibility = View.VISIBLE
                    holder.binding.tvGenre1Name.text = genres[0].first
                    holder.binding.tvGenre1Percent.text = "${genres[0].second}%"
                    holder.binding.pbGenre1.progress = genres[0].second
                } else {
                    holder.binding.cvGenre1.visibility = View.GONE
                }
                
                fun bindGenreRow(rowBinding: com.wirelessalien.android.moviedb.databinding.ItemGenreRowBinding, index: Int) {
                    if (genres.size > index) {
                        rowBinding.root.visibility = View.VISIBLE
                        rowBinding.tvGenreRank.text = "#${index + 1}"
                        rowBinding.tvGenreName.text = genres[index].first
                        rowBinding.tvGenrePercent.text = "${genres[index].second}%"
                        rowBinding.pbGenre.progress = genres[index].second
                    } else {
                        rowBinding.root.visibility = View.GONE
                    }
                }
                
                bindGenreRow(holder.binding.genre2Row, 1)
                bindGenreRow(holder.binding.genre3Row, 2)
                bindGenreRow(holder.binding.genre4Row, 3)
                bindGenreRow(holder.binding.genre5Row, 4)
            }
            2 -> {
                holder.binding.slideTitle.text = context.getString(R.string.wrapped_slide3_title)
                holder.binding.slideDesc.text = context.getString(R.string.wrapped_slide4_desc)
                holder.binding.hallOfFameLayout.visibility = View.VISIBLE
                
                holder.binding.tvAverageRating.text = String.format("%.1f", data.averageRating)
                holder.binding.tvLowestRated.text = data.lowestRatedTitle
                
                fun bindHofRow(row: com.wirelessalien.android.moviedb.databinding.ItemWrappedHallOfFameRowBinding, index: Int) {
                    if (data.topRatedTitleIds.size > index) {
                        row.root.visibility = View.VISIBLE
                        row.tvRank.text = "#${index + 1}"
                        row.tvTitle.text = data.topRatedTitleIds[index].first
                        row.tvRating.text = String.format("%.1f", data.topRatedTitleIds[index].second)
                    } else {
                        row.root.visibility = View.GONE
                    }
                }
                
                bindHofRow(holder.binding.hofRow1, 0)
                bindHofRow(holder.binding.hofRow2, 1)
                bindHofRow(holder.binding.hofRow3, 2)
                
                holder.binding.hofRow4.root.visibility = View.GONE
                holder.binding.hofRow5.root.visibility = View.GONE
                
                if (data.topRatedTitleIds.size > 3) {
                    holder.binding.btnMoreTopRated.visibility = View.VISIBLE
                    holder.binding.btnMoreTopRated.setOnClickListener {
                        bindHofRow(holder.binding.hofRow4, 3)
                        bindHofRow(holder.binding.hofRow5, 4)
                        holder.binding.btnMoreTopRated.visibility = View.GONE
                    }
                } else {
                    holder.binding.btnMoreTopRated.visibility = View.GONE
                }
            }
            3 -> {
                holder.binding.slideTitle.text = context.getString(R.string.wrapped_slide4_title)
                holder.binding.slideDesc.text = context.getString(R.string.wrapped_slide3_desc)
                holder.binding.timelineLayout.visibility = View.VISIBLE
                
                holder.binding.tvFirstTitle.text = data.firstTitle
                holder.binding.tvFirstDate.text = data.firstDate
                holder.binding.tvLastTitle.text = data.lastTitle
                holder.binding.tvLastDate.text = data.lastDate
            }
            4 -> {
                holder.binding.slideTitle.text = context.getString(R.string.wrapped_slide5_title)
                holder.binding.slideDesc.text = context.getString(R.string.wrapped_slide5_desc)
                holder.binding.peakLayout.visibility = View.VISIBLE
                
                holder.binding.tvPeakMonth.text = data.peakMonth
                holder.binding.tvPeakMonthDesc.text = context.getString(R.string.wrapped_peak_month_format, data.peakMonth, data.peakMonthCount)
                
                holder.binding.tvPeakDayOfWeek.text = data.peakDayOfWeek
                holder.binding.tvPeakDayDesc.text = context.getString(R.string.wrapped_peak_day_format, data.peakDayOfWeek, data.peakDayOfWeekPercentage)
                
                holder.binding.tvTopBingeDate.text = data.topBingeDate
                holder.binding.tvTopBingeDesc.text = context.getString(R.string.wrapped_top_binge_format, data.topBingeDate, data.topBingeCount)
            }
            5 -> {
                holder.binding.slideTitle.text = context.getString(R.string.wrapped_slide6_title)
                holder.binding.slideDesc.text = context.getString(R.string.wrapped_slide6_desc)
                holder.binding.summaryLayout.visibility = View.VISIBLE
                
                holder.binding.tvSummaryYearLabel.text = "${data.year} WRAPPED"
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
            }
        }
    }

    override fun getItemCount(): Int = SLIDE_COUNT
}
