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
import androidx.viewpager.widget.PagerAdapter
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.databinding.ItemImageBinding
import com.wirelessalien.android.moviedb.helper.DirectoryHelper.downloadImage

class ImagePagerAdapter(private val context: Context, private val imageUrls: List<String>, private val filePaths: List<String>) : PagerAdapter() {

    override fun getCount(): Int {
        return imageUrls.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(context), container, false)

        // Show shimmer effect
        binding.shimmerFrameLayout2.visibility = View.VISIBLE
        binding.shimmerFrameLayout2.startShimmer()

        Picasso.get().load(imageUrls[position]).into(binding.imageView, object : com.squareup.picasso.Callback {
            override fun onSuccess() {
                binding.shimmerFrameLayout2.stopShimmer()
                binding.shimmerFrameLayout2.visibility = View.GONE
            }

            override fun onError(e: Exception?) {
                binding.shimmerFrameLayout2.stopShimmer()
                binding.shimmerFrameLayout2.visibility = View.GONE
            }
        })

        binding.downloadBtn.setOnClickListener {
            downloadImage(context, "https://image.tmdb.org/t/p/original${filePaths[position]}", filePaths[position])
        }

        container.addView(binding.root)
        return binding.root
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }
}