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
import android.graphics.Color
import android.icu.text.DateFormat
import android.icu.text.DateFormatSymbols
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.EpisodeAdapter.EpisodeViewHolder
import com.wirelessalien.android.moviedb.data.Episode
import com.wirelessalien.android.moviedb.databinding.EpisodeItemBinding
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.account.AddEpisodeRating
import com.wirelessalien.android.moviedb.tmdb.account.DeleteEpisodeRating
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountStateTvSeason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.ParseException
import java.util.Date
import java.util.Locale

class EpisodeAdapter(
    private val context: Context,
    val episodes: List<Episode>,
    var seasonNumber: Int,
    private var tvShowId: Int
) : RecyclerView.Adapter<EpisodeViewHolder?>() {
    private var episodeRatings: Map<Int, Double> = HashMap()

    init {
        CoroutineScope(Dispatchers.Main).launch {
            val getAccountStateTvSeason = GetAccountStateTvSeason(tvShowId, seasonNumber, context, object : GetAccountStateTvSeason.OnDataFetchedListener {
                override fun onDataFetched(episodeRatings: Map<Int, Double>?) {
                    this@EpisodeAdapter.episodeRatings = episodeRatings ?: emptyMap()
                    notifyDataSetChanged()
                }
            })
            getAccountStateTvSeason.fetchAccountState()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = EpisodeItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EpisodeViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
        val imageSize = if (loadHDImage) "w780" else "w500"
        val episode = episodes[position]
        holder.binding.title.text = episode.name
        holder.binding.episodeNumber.text = "(" + episode.episodeNumber + ")"
        holder.binding.description.text = episode.overview
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val parsedDate = inputFormat.parse(episode.airDate)
            if (parsedDate != null) {
                val outputFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                val formattedDate = outputFormat.format(parsedDate)
                holder.binding.date.text = formattedDate
            }
        } catch (e: ParseException) {
            e.printStackTrace()
            holder.binding.date.text = episode.airDate
        }
        holder.binding.runtime.text = context.getString(R.string.runtime_minutes, episode.runtime)
        holder.binding.averageRating.text = context.getString(
            R.string.average_rating, episode.voteAverage, String.format(Locale.getDefault(), "%d", 10)
        )
        Picasso.get()
            .load("https://image.tmdb.org/t/p/" + imageSize + episode.posterPath)
            .placeholder(R.color.md_theme_surface)
            .into(holder.binding.image)
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        val rating = episodeRatings[episode.episodeNumber] ?: 0.0
        if (rating == 0.0) {
            holder.binding.rating.setText(R.string.episode_rating_tmdb_not_set)
        } else {
            holder.binding.rating.text = context.getString(R.string.rating_tmdb) + String.format(
                Locale.getDefault(), "%.1f/" + String.format(Locale.getDefault(), "%d", 10), rating
            )
        }

        val sessionId = defaultSharedPreferences.getString("access_token", null)
        val accountId = defaultSharedPreferences.getString("account_id", null)

        if (sessionId == null || accountId == null) {
            holder.binding.rateBtn.visibility = View.GONE
        } else {
            holder.binding.rateBtn.visibility = View.VISIBLE
        }

        try {
            MovieDatabaseHelper(context).use { db ->
                if (db.isEpisodeInDatabase(tvShowId, seasonNumber, listOf(episode.episodeNumber))) {
                    holder.binding.watched.icon =
                        ContextCompat.getDrawable(context, R.drawable.ic_visibility)
                } else {
                    holder.binding.watched.icon =
                        ContextCompat.getDrawable(context, R.drawable.ic_visibility_off)
                }
                holder.binding.watched.setOnClickListener {
                    // If the episode is in the database, remove it
                    if (db.isEpisodeInDatabase(tvShowId, seasonNumber, listOf(episode.episodeNumber))) {
                        db.removeEpisodeNumber(tvShowId, seasonNumber, listOf(episode.episodeNumber))
                        holder.binding.watched.icon =
                            ContextCompat.getDrawable(context, R.drawable.ic_visibility_off)
                    } else {
                        // If the episode is not in the database, add it
                        db.addEpisodeNumber(tvShowId, seasonNumber, listOf(episode.episodeNumber))

                        holder.binding.watched.icon =
                            ContextCompat.getDrawable(context, R.drawable.ic_visibility)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            MovieDatabaseHelper(context).use { db ->
                val details = db.getEpisodeDetails(tvShowId, seasonNumber, episode.episodeNumber)
                if (details != null) {
                    if (details.rating?.toDouble() != 0.0) {
                        val formattedRating =
                            String.format(Locale.getDefault(), "%.1f/%d", details.rating, 10)
                        holder.binding.episodeDbRating.text =
                            context.getString(R.string.rating_db) + " " + formattedRating
                    }
                    if (details.watchDate != "0000-00-00" && details.watchDate != "00-00-0000") {
                        val originalFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                        try {
                            val date = originalFormat.parse(details.watchDate)
                            val formattedDate = when {
                                details.watchDate.endsWith("-00-00") -> {
                                    val year = details.watchDate.substring(0, 4).toInt()
                                    year.toString()
                                }
                                details.watchDate.endsWith("-00") -> {
                                    val year = details.watchDate.substring(0, 4).toInt()
                                    val month = details.watchDate.substring(5, 7).toInt()
                                    String.format(Locale.getDefault(), "%d-%02d", year, month)
                                }
                                else -> {
                                    DateFormat.getDateInstance(DateFormat.DEFAULT).format(date)
                                }
                            }
                            holder.binding.watchedDate.text =
                                context.getString(R.string.watched_on) + " " + formattedDate
                        } catch (e: ParseException) {
                            e.printStackTrace()
                        }
                    } else {
                        holder.binding.watchedDate.text = context.getString(R.string.watched_on_not_set)
                    }
                    holder.binding.episodeReview.text = details.review
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        holder.binding.editDetails.setOnClickListener {
            val dialog = BottomSheetDialog(context)
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_edit_episode, null)
            dialog.setContentView(dialogView)
            dialog.show()
            val dateTextView = dialogView.findViewById<TextView>(R.id.dateTextView)
            val ratingSlider = dialogView.findViewById<Slider>(R.id.episodeRatingSlider)
            val submitButton = dialogView.findViewById<Button>(R.id.btnSubmit)
            val cancelButton = dialogView.findViewById<Button>(R.id.btnCancel)
            val dateButton = dialogView.findViewById<Button>(R.id.dateButton)
            val episodeTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
            val reviewEditText = dialogView.findViewById<TextInputEditText>(R.id.episodeReview)
            episodeTitle.text = "S:" + seasonNumber + " " + "E:" + episode.episodeNumber + " " + episode.name

            // Fetch episode details from the database
            try {
                MovieDatabaseHelper(context).use { db ->
                    val details = db.getEpisodeDetails(tvShowId, seasonNumber, episode.episodeNumber)
                    if (details != null) {
                        dateTextView.text = details.watchDate
                        if (details.rating?.toDouble() != 0.0 && details.rating != null) {
                            val rating1 = if (details.rating > 10.0) 10.0 else details.rating
                            ratingSlider.value = rating1.toFloat()
                        }
                        reviewEditText.setText(details.review)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            dateButton.setOnClickListener {
                val formatDialog = BottomSheetDialog(context)
                val formatView = inflater.inflate(R.layout.dialog_date_format, null)
                formatDialog.setContentView(formatView)
                formatDialog.show()

                val yearButton = formatView.findViewById<Button>(R.id.btnYear)
                val fullDateButton = formatView.findViewById<Button>(R.id.btnFullDate)

                yearButton.setOnClickListener {
                    showYearMonthPickerDialog(context) { selectedYear, selectedMonth ->
                        if (selectedMonth == null) {
                            dateTextView.text = "$selectedYear-00-00"
                        } else {
                            dateTextView.text = String.format(Locale.ENGLISH, "%d-%02d-00", selectedYear, selectedMonth)
                        }
                    }
                    formatDialog.dismiss()
                }

                fullDateButton.setOnClickListener {
                    val datePicker = MaterialDatePicker.Builder.datePicker()
                        .setTitleText(context.getString(R.string.select_a_date))
                        .build()
                    datePicker.show((context as FragmentActivity).supportFragmentManager, datePicker.toString())
                    datePicker.addOnPositiveButtonClickListener { selection: Long? ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                        val selectedDate = sdf.format(Date(selection!!))
                        dateTextView.text = selectedDate
                    }
                    formatDialog.dismiss()
                }
            }

            submitButton.setOnClickListener {
                val episodeRating = ratingSlider.value
                if (episodeRating > 10.0) {
                    // This should not happen as the slider's max value is 10.0
                } else {
                    val date = dateTextView.text.toString()
                    val review = reviewEditText.text.toString()
                    val adapterPosition = holder.bindingAdapterPosition
                    val episode1 = episodes[adapterPosition]
                    episode1.setWatchDate(date)
                    episode1.setRating(episodeRating)
                    episode1.setReview(review)
                    try {
                        MovieDatabaseHelper(context).use { movieDatabaseHelper ->
                            movieDatabaseHelper.addOrUpdateEpisode(tvShowId, seasonNumber, episode1.episodeNumber, episodeRating, date, review)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    notifyItemChanged(adapterPosition)
                    dialog.dismiss()
                }
            }
            cancelButton.setOnClickListener { dialog.dismiss() }
        }


        holder.binding.rateBtn.setOnClickListener {
            val dialog = BottomSheetDialog(context)
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.rating_dialog, null)
            dialog.setContentView(dialogView)
            dialog.show()
            val ratingSlider = dialogView.findViewById<Slider>(R.id.ratingSlider)
            val submitButton = dialogView.findViewById<Button>(R.id.btnSubmit)
            val cancelButton = dialogView.findViewById<Button>(R.id.btnCancel)
            val deleteButton = dialogView.findViewById<Button>(R.id.btnDelete)
            val episodeTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
            episodeTitle.text = "S:" + seasonNumber + " " + "E:" + episode.episodeNumber + " " + episode.name
            Handler(Looper.getMainLooper())
            submitButton.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    val ratingS = ratingSlider.value.toDouble()
                    val addEpisodeRating = AddEpisodeRating(tvShowId, seasonNumber, episode.episodeNumber, ratingS, context)
                    addEpisodeRating.addRating()
                    if (addEpisodeRating.isSuccessful()) {
                        holder.binding.rating.text = context.getString(R.string.rating_tmdb) + String.format(
                            Locale.getDefault(), "%.1f/" + String.format(Locale.getDefault(), "%d", 10), ratingS
                        )
                    }
                    dialog.dismiss()
                }
            }
            deleteButton.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    val deleteEpisodeRating = DeleteEpisodeRating(tvShowId, seasonNumber, episode.episodeNumber, context)
                    deleteEpisodeRating.deleteEpisodeRating()
                    if (deleteEpisodeRating.isSuccessful()) {
                        holder.binding.rating.setText(R.string.episode_rating_tmdb_not_set)
                    }
                    dialog.dismiss()
                }
            }
            cancelButton.setOnClickListener { dialog.dismiss() }
        }
    }

    private fun showYearMonthPickerDialog(context: Context, onYearMonthSelected: (Int, Int?) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_year_month_picker, null)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.yearPicker)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.monthPicker)
        val monthTitle = dialogView.findViewById<TextView>(R.id.monthTitle)
        val monthLayout = dialogView.findViewById<LinearLayout>(R.id.monthLayout)
        val disableMonthPicker = dialogView.findViewById<MaterialCheckBox>(R.id.disableMonthPicker)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        yearPicker.minValue = 1900
        yearPicker.maxValue = currentYear
        yearPicker.value = currentYear

        val months = DateFormatSymbols.getInstance(Locale.getDefault()).months
        monthPicker.minValue = 0
        monthPicker.maxValue = months.size - 1
        monthPicker.displayedValues = months
        monthPicker.value = Calendar.getInstance().get(Calendar.MONTH)

        disableMonthPicker.setOnCheckedChangeListener { _, isChecked ->
            monthPicker.isEnabled = !isChecked
            monthPicker.visibility = if (isChecked) View.GONE else View.VISIBLE
            monthTitle.visibility = if (isChecked) View.GONE else View.VISIBLE
            monthLayout.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.select_year_and_month))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                val selectedYear = yearPicker.value
                val selectedMonth = if (disableMonthPicker.isChecked) null else monthPicker.value + 1
                onYearMonthSelected(selectedYear, selectedMonth)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    override fun getItemCount(): Int {
        return episodes.size
    }

    class EpisodeViewHolder(var binding: EpisodeItemBinding) : RecyclerView.ViewHolder(
        binding.root
    )

    companion object {
        private const val HD_IMAGE_SIZE = "key_hq_images"
    }
}