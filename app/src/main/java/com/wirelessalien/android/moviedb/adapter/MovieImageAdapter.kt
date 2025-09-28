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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexboxLayoutManager
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.MovieImage
import com.wirelessalien.android.moviedb.databinding.DialogImageViewBinding
import com.wirelessalien.android.moviedb.databinding.MovieImageItemBinding
import com.wirelessalien.android.moviedb.helper.DirectoryHelper.downloadImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MovieImageAdapter(
    private val context: Context,
    private var movieImages: List<MovieImage>,
    private var imageType: String
) : RecyclerView.Adapter<MovieImageAdapter.ViewHolder?>() {

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newMovieImages: List<MovieImage>, newImageType: String) {
        this.movieImages = newMovieImages
        this.imageType = newImageType
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = MovieImageItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageSize = if (imageType == "posters") "w185" else "w300"
        val imageUrl = "https://image.tmdb.org/t/p/$imageSize" + movieImages[position].getFilePath()
        Picasso.get()
            .load(imageUrl)
            .placeholder(R.color.md_theme_outline)
            .into(holder.imageView)
        val lp = holder.imageView.layoutParams
        if (lp is FlexboxLayoutManager.LayoutParams) {
            lp.flexGrow = 1.0f
            lp.alignSelf = AlignItems.STRETCH
        }
        holder.imageView.setOnClickListener { v: View? ->
            val position1 = intArrayOf(holder.bindingAdapterPosition)
            if (position1[0] != RecyclerView.NO_POSITION) {
                val binding = DialogImageViewBinding.inflate(LayoutInflater.from(context))
                val popupWindow = PopupWindow(
                    binding.root,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                popupWindow.isOutsideTouchable = true
                popupWindow.isFocusable = true
                val hDImageUrl = "https://image.tmdb.org/t/p/w780" + movieImages[position1[0]].getFilePath()
                val originalImageUrl = arrayOf("https://image.tmdb.org/t/p/original" + movieImages[position1[0]].getFilePath())
                binding.progressBar.visibility = View.VISIBLE
                popupWindow.showAtLocation(v, Gravity.CENTER, 0, 0)
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            Picasso.get().load(hDImageUrl).get()
                        }
                        val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
                        binding.dialogImage.setImageDrawable(drawable)
                        binding.progressBar.visibility = View.GONE
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                binding.rotateBtn.setOnClickListener {
                    binding.dialogImage.rotation = binding.dialogImage.rotation + 90
                }
                binding.dismissBtn.setOnClickListener { popupWindow.dismiss() }
                binding.loadOriginalBtn.setOnClickListener {
                    binding.progressBar.visibility = View.VISIBLE
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val bitmap = withContext(Dispatchers.IO) {
                                Picasso.get().load(originalImageUrl[0]).get()
                            }
                            val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
                            binding.dialogImage.setImageDrawable(drawable)
                            binding.progressBar.visibility = View.GONE
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                binding.zoomInBtn.setOnClickListener {
                    binding.dialogImage.scaleX = binding.dialogImage.scaleX + 0.1f
                    binding.dialogImage.scaleY = binding.dialogImage.scaleY + 0.1f
                }
                binding.zoomOutBtn.setOnClickListener {
                    binding.dialogImage.scaleX = binding.dialogImage.scaleX - 0.1f
                    binding.dialogImage.scaleY = binding.dialogImage.scaleY - 0.1f
                }
                binding.nextBtn.setOnClickListener {
                    if (position1[0] < movieImages.size - 1) {
                        position1[0]++
                        val nextImageUrl = "https://image.tmdb.org/t/p/w780" + movieImages[position1[0]].getFilePath()
                        originalImageUrl[0] = "https://image.tmdb.org/t/p/original" + movieImages[position1[0]].getFilePath()
                        binding.progressBar.visibility = View.VISIBLE
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val bitmap = withContext(Dispatchers.IO) {
                                    Picasso.get().load(nextImageUrl).get()
                                }
                                val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
                                binding.dialogImage.setImageDrawable(drawable)
                                binding.progressBar.visibility = View.GONE
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                binding.prevBtn.setOnClickListener {
                    if (position1[0] > 0) {
                        position1[0]--
                        val prevImageUrl = "https://image.tmdb.org/t/p/w780" + movieImages[position1[0]].getFilePath()
                        originalImageUrl[0] = "https://image.tmdb.org/t/p/original" + movieImages[position1[0]].getFilePath()
                        binding.progressBar.visibility = View.VISIBLE
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val bitmap = withContext(Dispatchers.IO) {
                                    Picasso.get().load(prevImageUrl).get()
                                }
                                val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
                                binding.dialogImage.setImageDrawable(drawable)
                                binding.progressBar.visibility = View.GONE
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                binding.downloadBtn.setOnClickListener {
                    downloadImage(context, originalImageUrl[0], movieImages[position1[0]].getFilePath())
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return movieImages.size
    }

    class ViewHolder(val binding: MovieImageItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val imageView: ImageView = binding.movieImage
    }
}
