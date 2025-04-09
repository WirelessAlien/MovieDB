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

import android.icu.text.SimpleDateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ReviewItemBinding
import org.json.JSONObject
import java.util.Locale

class ReviewAdapter :
    PagingDataAdapter<JSONObject, ReviewAdapter.ReviewViewHolder>(ReviewDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ReviewItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = getItem(position)
        review?.let { holder.bind(it) }
    }

    class ReviewViewHolder(private val binding: ReviewItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var isContentExpanded = false

        fun bind(review: JSONObject) {
            val authorDetails = review.getJSONObject("author_details")
            val avatarPath = authorDetails.optString("avatar_path", "")
            val rating = authorDetails.optDouble("rating", -1.0)

            binding.textViewAuthor.text = review.optString("author")

            val dateString = review.optString("created_at")
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                binding.textViewCreatedAt.text = outputFormat.format(date)
            } catch (e: Exception) {
                // If parsing fails, show the original string
                binding.textViewCreatedAt.text = dateString
            }

            binding.textViewRating.text = if (rating != -1.0) "$rating/10" else "N/A"

            // Set content with max lines and click listener
            val content = review.optString("content")
            binding.textViewContent.text = content
            binding.textViewContent.post {
                if (binding.textViewContent.lineCount > 3) {
                    binding.textViewContent.maxLines = 3
                    isContentExpanded = false
                } else {
                    isContentExpanded = true
                }
            }

            binding.textViewContent.setOnClickListener {
                if (binding.textViewContent.lineCount > 3) {
                    isContentExpanded = !isContentExpanded
                    binding.textViewContent.maxLines = if (isContentExpanded) Int.MAX_VALUE else 3
                }
            }

            if (avatarPath.isNotEmpty() && avatarPath.startsWith("/")) {
                Picasso.get()
                    .load("https://image.tmdb.org/t/p/w500$avatarPath")
                    .into(binding.imageViewAvatar)
            } else {
                binding.imageViewAvatar.setImageResource(R.drawable.ic_profile_photo)
            }
        }
    }

    class ReviewDiffCallback : DiffUtil.ItemCallback<JSONObject>() {
        override fun areItemsTheSame(oldItem: JSONObject, newItem: JSONObject): Boolean {
            return oldItem.getString("id") == newItem.getString("id")
        }

        override fun areContentsTheSame(oldItem: JSONObject, newItem: JSONObject): Boolean {
            return oldItem.toString() == newItem.toString()
        }
    }
}