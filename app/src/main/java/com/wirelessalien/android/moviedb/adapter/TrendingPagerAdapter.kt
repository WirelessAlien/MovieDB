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
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.animation.AnimationUtils
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import com.squareup.picasso.Target
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.databinding.TrendingCardBinding
import com.wirelessalien.android.moviedb.fragment.ShowDetailsBottomSheet
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class TrendingPagerAdapter(private val mShowArrayList: ArrayList<JSONObject>?) : RecyclerView.Adapter<TrendingPagerAdapter.ShowItemViewHolder>() {
    fun updateData(newTrendingList: ArrayList<JSONObject>?) {
        mShowArrayList!!.clear()
        mShowArrayList.addAll(newTrendingList!!)
    }

    override fun getItemCount(): Int {
        return mShowArrayList!!.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShowItemViewHolder {
        val binding = TrendingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShowItemViewHolder(binding)
    }

    @SuppressLint("RestrictedApi")
    override fun onBindViewHolder(holder: ShowItemViewHolder, position: Int) {
        val showData = mShowArrayList?.get(position % mShowArrayList.size) ?: return
        val context = holder.binding.root.context

        try {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "w780" else "w500"
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val isDarkTheme = uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
            try {
                if (showData.getString(KEY_IMAGE) == "null") {
                    holder.binding.movieImage.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.md_theme_outline, null))
                } else {
                    val imageUrl = "https://image.tmdb.org/t/p/$imageSize" + showData.getString(KEY_IMAGE)
                    holder.target = object : Target {
                        override fun onBitmapLoaded(bitmap: Bitmap, from: LoadedFrom) {
                            holder.binding.movieImage.setImageBitmap(bitmap)
                            val palette = Palette.from(bitmap).generate()
                            val darkMutedColor = palette.getDarkMutedColor(palette.getMutedColor(Color.BLACK))
                            val lightMutedColor = palette.getLightMutedColor(palette.getMutedColor(Color.WHITE))
                            val foregroundGradientDrawable: GradientDrawable = if (isDarkTheme) {
                                GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(darkMutedColor, darkMutedColor, darkMutedColor, Color.TRANSPARENT))
                            } else {
                                GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(lightMutedColor, lightMutedColor, lightMutedColor, Color.TRANSPARENT))
                            }
                            holder.binding.movieInfo.background = foregroundGradientDrawable
                        }

                        override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
                            val fallbackDrawable = errorDrawable ?: ContextCompat.getColor(context, R.color.md_theme_surface)
                            holder.binding.movieImage.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_surface))
                            holder.binding.movieImage.setBackgroundColor(fallbackDrawable as Int)
                        }

                        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                            placeHolderDrawable ?: ContextCompat.getColor(context, R.color.md_theme_outline)
                            holder.binding.movieImage.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_surface))
                        }
                    }
                    Picasso.get().load(imageUrl).into(holder.target)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            val name = if (showData.has(KEY_TITLE)) showData.getString(KEY_TITLE) else showData.getString(KEY_NAME)
            holder.binding.movieTitle.text = name

            holder.binding.typeText.visibility - View.VISIBLE
            holder.binding.typeText.text = if (showData.has(KEY_TITLE)) context.getString(R.string.movie) else context.getString(R.string.show)

            var dateString = if (showData.has(KEY_DATE_MOVIE)) showData.getString(KEY_DATE_MOVIE) else showData.getString(KEY_DATE_SERIES)
            val originalFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            try {
                val date = originalFormat.parse(dateString)
                val localFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                dateString = localFormat.format(date)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            holder.binding.date.text = dateString
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        holder.binding.root.setOnClickListener { view: View ->
            val intent = Intent(view.context, DetailActivity::class.java)
            intent.putExtra("movieObject", showData.toString())
            if (showData.has(KEY_NAME)) {
                intent.putExtra("isMovie", false)
            }
            view.context.startActivity(intent)
        }

        holder.binding.root.setOnLongClickListener { view: View ->
            val isMovie = !showData.has(KEY_NAME) // If KEY_NAME exists, it's a TV show
            val bottomSheet = ShowDetailsBottomSheet.newInstance(showData.toString(), isMovie)
            val activity = view.context as? FragmentActivity
            activity?.supportFragmentManager?.let { fragmentManager ->
                bottomSheet.show(fragmentManager, ShowDetailsBottomSheet.TAG)
            }
            true
        }

        holder.binding.root.setOnMaskChangedListener { maskRect: RectF ->
            holder.binding.movieTitle.translationX = maskRect.left
            holder.binding.date.translationX = maskRect.left
            holder.binding.trendingText.translationX = maskRect.left
            holder.binding.typeText.translationX = maskRect.left
            holder.binding.movieTitle.alpha = AnimationUtils.lerp(1f, 0f, 0f, 80f, maskRect.left)
            holder.binding.date.alpha = AnimationUtils.lerp(1f, 0f, 0f, 80f, maskRect.left)
            holder.binding.trendingText.alpha = AnimationUtils.lerp(1f, 0f, 0f, 80f, maskRect.left)
            holder.binding.typeText.alpha = AnimationUtils.lerp(1f, 0f, 0f, 80f, maskRect.left)
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    class ShowItemViewHolder(val binding: TrendingCardBinding) : RecyclerView.ViewHolder(binding.root) {
        lateinit var target: Target
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