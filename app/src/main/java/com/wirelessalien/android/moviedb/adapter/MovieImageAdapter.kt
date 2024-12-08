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
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexboxLayoutManager
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.MovieImage
import com.wirelessalien.android.moviedb.helper.DirectoryHelper.downloadImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MovieImageAdapter(private val context: Context, private val movieImages: List<MovieImage>) :
    RecyclerView.Adapter<MovieImageAdapter.ViewHolder?>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.movie_image_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageUrl = "https://image.tmdb.org/t/p/w300" + movieImages[position].getFilePath()
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
                val popupView =
                    LayoutInflater.from(context).inflate(R.layout.dialog_image_view, null)
                val popupWindow = PopupWindow(
                    popupView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                popupWindow.isOutsideTouchable = true
                popupWindow.isFocusable = true
                val dialogImageView = popupView.findViewById<ImageView>(R.id.dialog_image)
                val rotateButton = popupView.findViewById<Button>(R.id.rotate_btn)
                val loadOriginalButton = popupView.findViewById<Button>(R.id.load_original_btn)
                val progressBar = popupView.findViewById<ProgressBar>(R.id.progress_bar)
                val dismissButton = popupView.findViewById<Button>(R.id.dismiss_btn)
                val nextButton = popupView.findViewById<Button>(R.id.next_btn)
                val prevButton = popupView.findViewById<Button>(R.id.prev_btn)
                val downloadButton = popupView.findViewById<Button>(R.id.download_btn)
                val zoomInButton = popupView.findViewById<Button>(R.id.zoom_in_btn)
                val zoomOutButton = popupView.findViewById<Button>(R.id.zoom_out_btn)
                val hDImageUrl =
                    "https://image.tmdb.org/t/p/w780" + movieImages[position1[0]].getFilePath()
                val originalImageUrl =
                    arrayOf("https://image.tmdb.org/t/p/original" + movieImages[position1[0]].getFilePath())
                progressBar.visibility = View.VISIBLE
                popupWindow.showAtLocation(v, Gravity.CENTER, 0, 0)
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            Picasso.get().load(hDImageUrl).get()
                        }
                        val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
                        dialogImageView.setImageDrawable(drawable)
                        progressBar.visibility = View.GONE
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                rotateButton.setOnClickListener {
                    dialogImageView.rotation = dialogImageView.rotation + 90
                }
                dismissButton.setOnClickListener { popupWindow.dismiss() }
                loadOriginalButton.setOnClickListener {
                    progressBar.visibility = View.VISIBLE
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val bitmap = withContext(Dispatchers.IO) {
                                Picasso.get().load(originalImageUrl[0]).get()
                            }
                            val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
                            dialogImageView.setImageDrawable(drawable)
                            progressBar.visibility = View.GONE
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // zoom in btn
                zoomInButton.setOnClickListener {
                    dialogImageView.scaleX = dialogImageView.scaleX + 0.1f
                    dialogImageView.scaleY = dialogImageView.scaleY + 0.1f
                }

                // zoom out btn
                zoomOutButton.setOnClickListener {
                    dialogImageView.scaleX = dialogImageView.scaleX - 0.1f
                    dialogImageView.scaleY = dialogImageView.scaleY - 0.1f
                }
                nextButton.setOnClickListener {
                    if (position1[0] < movieImages.size - 1) {
                        position1[0]++
                        val nextImageUrl =
                            "https://image.tmdb.org/t/p/w780" + movieImages[position1[0]].getFilePath()
                        originalImageUrl[0] =
                            "https://image.tmdb.org/t/p/original" + movieImages[position1[0]].getFilePath()
                        progressBar.visibility = View.VISIBLE
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val bitmap = withContext(Dispatchers.IO) {
                                    Picasso.get().load(nextImageUrl).get()
                                }
                                val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
                                dialogImageView.setImageDrawable(drawable)
                                progressBar.visibility = View.GONE
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                prevButton.setOnClickListener {
                    if (position1[0] > 0) {
                        position1[0]--
                        val prevImageUrl =
                            "https://image.tmdb.org/t/p/w780" + movieImages[position1[0]].getFilePath()
                        originalImageUrl[0] =
                            "https://image.tmdb.org/t/p/original" + movieImages[position1[0]].getFilePath()
                        progressBar.visibility = View.VISIBLE
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val bitmap = withContext(Dispatchers.IO) {
                                    Picasso.get().load(prevImageUrl).get()
                                }
                                val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
                                dialogImageView.setImageDrawable(drawable)
                                progressBar.visibility = View.GONE
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                downloadButton.setOnClickListener {
                    downloadImage(
                        context, originalImageUrl[0], movieImages[position1[0]].getFilePath()
                    )
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return movieImages.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var imageView: ImageView

        init {
            imageView = itemView.findViewById(R.id.movie_image)
        }
    }
}
