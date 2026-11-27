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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ItemGenreRowBinding
import com.wirelessalien.android.moviedb.databinding.ItemWrappedHallOfFameRowBinding
import com.wirelessalien.android.moviedb.databinding.ItemWrappedSlideBinding
import com.wirelessalien.android.moviedb.helper.WrappedData
import java.util.Locale

class WrappedPagerAdapter(
    private val data: WrappedData,
    private val onPickImageClicked: ((ImageView) -> Unit)? = null,
    private var currentImageUri: Uri? = null,
    private val onUserInteracted: (() -> Unit)? = null
) : RecyclerView.Adapter<WrappedPagerAdapter.WrappedViewHolder>() {

    companion object {
        private const val SLIDE_COUNT = 6
        const val PREF_WRAPPED_DISPLAY_NAME = "wrapped_display_name"
        private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w185"
    }

    inner class WrappedViewHolder(val binding: ItemWrappedSlideBinding) : RecyclerView.ViewHolder(binding.root) {
        var displayNameWatcher: TextWatcher? = null
    }

    fun updateImageUri(uri: Uri?) {
        currentImageUri = uri
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WrappedViewHolder {
        val binding = ItemWrappedSlideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WrappedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WrappedViewHolder, position: Int) {
        val b = holder.binding
        val context = b.root.context

        // Reset visibility
        b.volumeLayout.visibility = View.GONE
        b.genresLayout.visibility = View.GONE
        b.hallOfFameLayout.visibility = View.GONE
        b.timelineLayout.visibility = View.GONE
        b.peakLayout.visibility = View.GONE
        b.summaryLayout.visibility = View.GONE

        applySlideThemeBackground(b.root, position)

        when (position) {
            0 -> {
                b.slideTitle.text = context.getString(R.string.wrapped_slide1_title)
                b.slideDesc.text = if (data.totalTitles > 20) {
                    context.getString(R.string.wrapped_slide1_desc_high)
                } else {
                    context.getString(R.string.wrapped_slide1_desc_low)
                }
                b.volumeLayout.visibility = View.VISIBLE

                b.tvTotalTitles.text = data.totalTitles.toString()
                b.tvTotalMovies.text = data.totalMovies.toString()
                b.tvTotalShows.text = data.totalShows.toString()
                b.tvTotalEpisodes.text = data.totalEpisodes.toString()

                val totalHours = Math.round((data.totalMovies * 2) + (data.totalEpisodes * 0.75)).toInt()
                val totalDays = totalHours / 24.0
                b.tvVolumeInsight.text = context.getString(R.string.wrapped_insight_format, totalHours, totalDays)
            }
            1 -> {
                b.slideTitle.text = context.getString(R.string.wrapped_slide2_title)
                b.slideDesc.text = context.getString(R.string.wrapped_slide2_desc)
                b.genresLayout.visibility = View.VISIBLE
                val genres = data.topGenres

                if (genres.isNotEmpty()) {
                    b.cvGenre1.visibility = View.VISIBLE
                    b.tvGenre1Name.text = genres[0].first
                    b.tvGenre1Percent.text = "${genres[0].second}%"
                    b.pbGenre1.progress = genres[0].second
                } else {
                    b.cvGenre1.visibility = View.GONE
                }

                fun bindGenreRow(rowBinding: ItemGenreRowBinding, index: Int) {
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

                bindGenreRow(b.genre2Row, 1)
                bindGenreRow(b.genre3Row, 2)
                bindGenreRow(b.genre4Row, 3)
                bindGenreRow(b.genre5Row, 4)

                b.tvGenreInsight.text = when {
                    genres.size >= 2 -> context.getString(
                        R.string.wrapped_genre_dynamic_multi,
                        genres[0].first,
                        genres[1].first,
                        genres[0].second
                    )
                    genres.size == 1 -> context.getString(
                        R.string.wrapped_genre_dynamic_single,
                        genres[0].first,
                        genres[0].second
                    )
                    else -> context.getString(R.string.wrapped_slide2_insight)
                }
            }
            2 -> {
                b.slideTitle.text = context.getString(R.string.wrapped_slide3_title)
                b.slideDesc.text = context.getString(R.string.wrapped_slide4_desc)
                b.hallOfFameLayout.visibility = View.VISIBLE

                b.tvAverageRating.text = if (data.ratedCount > 0) {
                    String.format(Locale.getDefault(), "%.1f", data.averageRating)
                } else {
                    "—"
                }

                if (data.ratedCount == 0) {
                    b.tvNoRatingsHint.visibility = View.VISIBLE
                    b.tvTopRatedHeader.visibility = View.GONE
                    b.llTopRatedList.visibility = View.GONE
                    b.cvGuiltyPleasure.visibility = View.GONE
                } else {
                    b.tvNoRatingsHint.visibility = View.GONE
                    b.tvTopRatedHeader.visibility = View.VISIBLE
                    b.llTopRatedList.visibility = View.VISIBLE

                    fun bindHofRow(row: ItemWrappedHallOfFameRowBinding, index: Int) {
                        if (data.topRatedTitleIds.size > index) {
                            val item = data.topRatedTitleIds[index]
                            row.root.visibility = View.VISIBLE
                            row.tvRank.text = "#${index + 1}"
                            row.tvTitle.text = item.title
                            row.tvRating.text = String.format(Locale.getDefault(), "%.1f", item.rating)
                            loadPoster(row.ivPoster, item.posterPath)
                        } else {
                            row.root.visibility = View.GONE
                        }
                    }

                    bindHofRow(b.hofRow1, 0)
                    bindHofRow(b.hofRow2, 1)
                    bindHofRow(b.hofRow3, 2)

                    b.hofRow4.root.visibility = View.GONE
                    b.hofRow5.root.visibility = View.GONE

                    if (data.topRatedTitleIds.size > 3) {
                        b.btnMoreTopRated.visibility = View.VISIBLE
                        b.btnMoreTopRated.setOnClickListener {
                            onUserInteracted?.invoke()
                            bindHofRow(b.hofRow4, 3)
                            bindHofRow(b.hofRow5, 4)
                            b.btnMoreTopRated.visibility = View.GONE
                        }
                    } else {
                        b.btnMoreTopRated.visibility = View.GONE
                    }

                    if (data.lowestRatedTitle != null) {
                        b.cvGuiltyPleasure.visibility = View.VISIBLE
                        b.tvLowestRated.text = data.lowestRatedTitle
                    } else {
                        b.cvGuiltyPleasure.visibility = View.GONE
                    }
                }
            }
            3 -> {
                b.slideTitle.text = context.getString(R.string.wrapped_slide4_title)
                b.slideDesc.text = context.getString(R.string.wrapped_slide3_desc)
                b.timelineLayout.visibility = View.VISIBLE

                b.tvFirstTitle.text = data.firstTitle
                b.tvFirstDate.text = data.firstDate
                loadPoster(b.ivFirstPoster, data.firstPosterPath)

                b.tvLastTitle.text = data.lastTitle
                b.tvLastDate.text = data.lastDate
                loadPoster(b.ivLastPoster, data.lastPosterPath)
            }
            4 -> {
                b.slideTitle.text = context.getString(R.string.wrapped_slide5_title)
                b.slideDesc.text = context.getString(R.string.wrapped_slide5_desc)
                b.peakLayout.visibility = View.VISIBLE

                b.tvPeakMonth.text = data.peakMonth
                b.tvPeakMonthDesc.text = context.getString(R.string.wrapped_peak_month_format, data.peakMonth, data.peakMonthCount)
                populateMiniBarChart(
                    container = b.llMonthlyChart,
                    values = data.monthlyCounts,
                    labels = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"),
                    highlightIndex = data.peakMonthIndex,
                    highlightAttr = androidx.appcompat.R.attr.colorPrimary
                )

                b.tvPeakDayOfWeek.text = data.peakDayOfWeek
                b.tvPeakDayDesc.text = context.getString(R.string.wrapped_peak_day_format, data.peakDayOfWeek, data.peakDayOfWeekPercentage)
                populateMiniBarChart(
                    container = b.llWeeklyChart,
                    values = data.dayOfWeekCounts,
                    labels = listOf("S", "M", "T", "W", "T", "F", "S"),
                    highlightIndex = data.peakDayOfWeekIndex,
                    highlightAttr = com.google.android.material.R.attr.colorSecondary
                )

                b.tvTopBingeDate.text = data.topBingeDate
                b.tvTopBingeDesc.text = context.getString(R.string.wrapped_top_binge_format, data.topBingeDate, data.topBingeCount)
            }
            5 -> {
                b.slideTitle.text = context.getString(R.string.wrapped_slide6_title)
                b.slideDesc.text = context.getString(R.string.wrapped_slide6_desc)
                b.summaryLayout.visibility = View.VISIBLE

                b.tvSummaryYearLabel.text = context.getString(R.string.wrapped_summary_year, data.year).uppercase(Locale.getDefault())
                b.tvPersonaBadge.text = data.personaBadge
                b.tvPersonaDesc.text = data.personaDescription
                b.tvSummaryTitles.text = data.totalTitles.toString()
                b.tvSummaryGenre.text = if (data.topGenres.isNotEmpty()) data.topGenres[0].first else "N/A"
                b.tvSummaryRating.text = if (data.ratedCount > 0) {
                    String.format(Locale.getDefault(), "%.1f", data.averageRating)
                } else {
                    "—"
                }

                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val savedName = prefs.getString(PREF_WRAPPED_DISPLAY_NAME, "") ?: ""
                holder.displayNameWatcher?.let { b.etDisplayName.removeTextChangedListener(it) }
                if (b.etDisplayName.text?.toString() != savedName) {
                    b.etDisplayName.setText(savedName)
                }
                holder.displayNameWatcher = b.etDisplayName.doAfterTextChanged { editable ->
                    prefs.edit().putString(PREF_WRAPPED_DISPLAY_NAME, editable?.toString() ?: "").apply()
                }
                b.etDisplayName.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) onUserInteracted?.invoke()
                }

                if (currentImageUri != null) {
                    b.ivProfileImage.setImageURI(currentImageUri)
                } else {
                    b.ivProfileImage.setImageResource(R.drawable.ic_profile_photo)
                }

                val pickListener = View.OnClickListener {
                    onUserInteracted?.invoke()
                    onPickImageClicked?.invoke(b.ivProfileImage)
                }
                b.ivProfileImage.setOnClickListener(pickListener)
                b.flProfileImageContainer.setOnClickListener(pickListener)
            }
        }
    }

    override fun getItemCount(): Int = SLIDE_COUNT

    fun playSlideAnimations(slideView: View, position: Int) {
        val contentContainer = slideView.findViewById<ViewGroup>(R.id.slideContentContainer) ?: return
        contentContainer.alpha = 0f
        contentContainer.translationY = 24f
        contentContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        when (position) {
            0 -> {
                val tvTotal = slideView.findViewById<TextView>(R.id.tvTotalTitles) ?: return
                animateIntCount(tvTotal, data.totalTitles)
            }
            1 -> {
                animateProgressBar(slideView.findViewById(R.id.pbGenre1), data.topGenres.getOrNull(0)?.second ?: 0)
                animateProgressBar(slideView.findViewById<View>(R.id.genre2Row)?.findViewById(R.id.pbGenre), data.topGenres.getOrNull(1)?.second ?: 0)
                animateProgressBar(slideView.findViewById<View>(R.id.genre3Row)?.findViewById(R.id.pbGenre), data.topGenres.getOrNull(2)?.second ?: 0)
                animateProgressBar(slideView.findViewById<View>(R.id.genre4Row)?.findViewById(R.id.pbGenre), data.topGenres.getOrNull(3)?.second ?: 0)
                animateProgressBar(slideView.findViewById<View>(R.id.genre5Row)?.findViewById(R.id.pbGenre), data.topGenres.getOrNull(4)?.second ?: 0)
            }
            2 -> {
                if (data.ratedCount > 0) {
                    val tvAvg = slideView.findViewById<TextView>(R.id.tvAverageRating) ?: return
                    ValueAnimator.ofFloat(0f, data.averageRating).apply {
                        duration = 550L
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { anim ->
                            tvAvg.text = String.format(Locale.getDefault(), "%.1f", anim.animatedValue as Float)
                        }
                        start()
                    }
                }
            }
        }
    }

    private fun animateIntCount(textView: TextView, target: Int) {
        if (target <= 0) {
            textView.text = target.toString()
            return
        }
        ValueAnimator.ofInt(0, target).apply {
            duration = 600L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                textView.text = (anim.animatedValue as Int).toString()
            }
            start()
        }
    }

    private fun animateProgressBar(progressBar: ProgressBar?, target: Int) {
        if (progressBar == null || target <= 0) return
        progressBar.progress = 0
        ValueAnimator.ofInt(0, target).apply {
            duration = 550L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                progressBar.progress = anim.animatedValue as Int
            }
            start()
        }
    }

    private fun loadPoster(imageView: ImageView, posterPath: String) {
        if (posterPath.isBlank()) {
            imageView.visibility = View.GONE
            return
        }
        imageView.visibility = View.VISIBLE
        Picasso.get()
            .load("$TMDB_POSTER_BASE$posterPath")
            .fit()
            .centerCrop()
            .into(imageView)
    }

    private fun populateMiniBarChart(
        container: LinearLayout,
        values: List<Int>,
        labels: List<String>,
        highlightIndex: Int,
        highlightAttr: Int
    ) {
        container.removeAllViews()
        val context = container.context
        val maxVal = (values.maxOrNull() ?: 1).coerceAtLeast(1)
        val highlightColor = resolveThemeColor(context, highlightAttr)
        val mutedColor = resolveThemeColor(context, com.google.android.material.R.attr.colorOutlineVariant)
        val labelColor = resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val density = context.resources.displayMetrics.density
        val maxBarHeightPx = (24 * density).toInt()
        val minBarHeightPx = (4 * density).toInt()

        values.forEachIndexed { index, count ->
            val col = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }

            val barHeight = if (count == 0) {
                minBarHeightPx
            } else {
                (minBarHeightPx + ((count.toFloat() / maxVal) * (maxBarHeightPx - minBarHeightPx))).toInt()
            }

            val bar = View(context).apply {
                layoutParams = LinearLayout.LayoutParams((8 * density).toInt(), barHeight)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 4 * density
                    setColor(if (index == highlightIndex && count > 0) highlightColor else mutedColor)
                }
            }

            val label = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (2 * density).toInt()
                }
                text = labels.getOrElse(index) { "" }
                textSize = 9f
                setTextColor(if (index == highlightIndex && count > 0) highlightColor else labelColor)
                if (index == highlightIndex && count > 0) {
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
            }

            col.addView(bar)
            col.addView(label)
            container.addView(col)
        }
    }

    private fun applySlideThemeBackground(root: View, position: Int) {
        val context = root.context
        val surface = resolveThemeColor(context, com.google.android.material.R.attr.colorSurface)
        val accentAttr = when (position % 3) {
            0 -> com.google.android.material.R.attr.colorPrimaryContainer
            1 -> com.google.android.material.R.attr.colorSecondaryContainer
            else -> com.google.android.material.R.attr.colorTertiaryContainer
        }
        val accent = resolveThemeColor(context, accentAttr)
        val topTint = ColorUtils.blendARGB(surface, accent, 0.22f)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topTint, surface)
        )
    }

    private fun resolveThemeColor(context: Context, attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
