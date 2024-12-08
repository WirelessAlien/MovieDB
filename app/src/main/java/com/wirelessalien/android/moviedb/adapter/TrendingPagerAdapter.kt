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
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.icu.text.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.animation.AnimationUtils
import com.google.android.material.carousel.MaskableFrameLayout
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import com.squareup.picasso.Target
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class TrendingPagerAdapter (private val mShowArrayList: ArrayList<JSONObject>?) : RecyclerView.Adapter<TrendingPagerAdapter.ShowItemViewHolder>() {
    fun updateData(newTrendingList: ArrayList<JSONObject>?) {
        mShowArrayList!!.clear()
        mShowArrayList.addAll(newTrendingList!!)
    }

    override fun getItemCount(): Int {
        // Return the amount of items in the list.
        return mShowArrayList!!.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShowItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.trending_card, parent, false)
        return ShowItemViewHolder(view)
    }

    @SuppressLint("RestrictedApi")
    override fun onBindViewHolder(holder: ShowItemViewHolder, position: Int) {
        val showData =
            (if (!mShowArrayList.isNullOrEmpty()) mShowArrayList[position % mShowArrayList.size] else null) ?: return
        val context = holder.showView.context

        // Fills the views with show details.
        try {
            // Load the thumbnail with Picasso.
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "w780" else "w500"
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val isDarkTheme = uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
            try {
                if (showData.getString(KEY_IMAGE) == "null") {
                    holder.showImage.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.md_theme_outline, null))
                } else {
                    val imageUrl = "https://image.tmdb.org/t/p/$imageSize" + showData.getString(
                        KEY_IMAGE
                    )
                    holder.target = object : Target {
                        override fun onBitmapLoaded(bitmap: Bitmap, from: LoadedFrom) {
                            holder.showImage.setImageBitmap(bitmap)
                            val palette = Palette.from(bitmap).generate()
                            val darkMutedColor = palette.getDarkMutedColor(
                                palette.getMutedColor(Color.BLACK))

                            val lightMutedColor = palette.getLightMutedColor(
                                palette.getMutedColor(Color.WHITE))

                            val foregroundGradientDrawable: GradientDrawable = if (isDarkTheme) {
                                GradientDrawable(
                                    GradientDrawable.Orientation.BOTTOM_TOP,
                                    intArrayOf(darkMutedColor, darkMutedColor, darkMutedColor, Color.TRANSPARENT))
                            } else {
                                GradientDrawable(
                                    GradientDrawable.Orientation.BOTTOM_TOP,
                                    intArrayOf(lightMutedColor, lightMutedColor, lightMutedColor, Color.TRANSPARENT))
                            }
                            holder.movieInfo.background = foregroundGradientDrawable
                        }

                        override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
                            val fallbackDrawable = errorDrawable ?: ContextCompat.getColor(context, R.color.md_theme_surface)
                            holder.showImage.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_surface))
                            holder.showImage.setBackgroundColor(fallbackDrawable as Int)
                        }

                        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                            // Ensure placeHolderDrawable is not null
                            placeHolderDrawable ?: ContextCompat.getColor(context, R.color.md_theme_outline)
                            holder.showImage.setBackgroundColor(
                                ContextCompat.getColor(context, R.color.md_theme_surface)
                            )
                        }
                    }
                    Picasso.get().load(imageUrl).into(holder.target)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            // Check if the object has "title" if not,
            // it is a series and "name" is used.
            val name =
                if (showData.has(KEY_TITLE)) showData.getString(KEY_TITLE) else showData.getString(
                    KEY_NAME
                )

            // Set the title and description.
            holder.showTitle.text = name

            // Check if the object has "title" if not,
            // it is a series and "name" is used.
            var dateString =
                if (showData.has(KEY_DATE_MOVIE)) showData.getString(KEY_DATE_MOVIE) else showData.getString(
                    KEY_DATE_SERIES
                )

            // Convert date to locale.
            val originalFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            try {
                val date = originalFormat.parse(dateString)
                val localFormat =
                    DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                dateString = localFormat.format(date)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            holder.showDate.text = dateString
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        // Send the movie data and the user to DetailActivity when clicking on a card.
        holder.itemView.setOnClickListener { view: View ->
            val intent = Intent(view.context, DetailActivity::class.java)
            intent.putExtra("movieObject", showData.toString())
            if (showData.has(KEY_NAME)) {
                intent.putExtra("isMovie", false)
            }
            view.context.startActivity(intent)
        }
        if (holder.itemView is MaskableFrameLayout) {
            (holder.itemView as MaskableFrameLayout).setOnMaskChangedListener { maskRect: RectF ->
                // Any custom motion to run when mask size changes
                holder.showTitle.translationX = maskRect.left
                holder.showDate.translationX = maskRect.left
                holder.trendingText.translationX = maskRect.left
                holder.showTitle.alpha = AnimationUtils.lerp(1f, 0f, 0f, 80f, maskRect.left)
                holder.showDate.alpha = AnimationUtils.lerp(1f, 0f, 0f, 80f, maskRect.left)
                holder.trendingText.alpha = AnimationUtils.lerp(1f, 0f, 0f, 80f, maskRect.left)
            }
        }
    }

    override fun getItemId(position: Int): Long {
        // The id is the same as the position,
        // therefore returning the position is enough.
        return position.toLong()
    }

    /**
     * The View of every item that is displayed in the list.
     */
    class ShowItemViewHolder internal constructor(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val showView: View
        val trendingText: TextView
        val showTitle: TextView
        val showImage: ImageView
        val showDate: TextView
        var movieInfo: RelativeLayout
        lateinit var target: Target

        init {
            showView = itemView.findViewById(R.id.cardView2)
            trendingText = itemView.findViewById(R.id.trendingText)
            showTitle = itemView.findViewById(R.id.movieTitle)
            showImage = itemView.findViewById(R.id.movieImage)
            movieInfo = itemView.findViewById(R.id.movieInfo)

            // Only used if presented in a list.
            showDate = itemView.findViewById(R.id.date)
        }
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_IMAGE = "backdrop_path"
        const val KEY_POSTER = "poster_path"
        const val KEY_TITLE = "title"
        const val KEY_NAME = "name"
        const val KEY_DESCRIPTION = "overview"
        const val KEY_RATING = "vote_average"
        const val KEY_DATE_MOVIE = "release_date"
        const val KEY_DATE_SERIES = "first_air_date"
        private const val HD_IMAGE_SIZE = "key_hq_images"
    }
}
