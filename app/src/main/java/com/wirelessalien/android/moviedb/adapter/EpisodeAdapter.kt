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
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.EpisodeAdapter.EpisodeViewHolder
import com.wirelessalien.android.moviedb.data.Episode
import com.wirelessalien.android.moviedb.databinding.EpisodeItemBinding
import com.wirelessalien.android.moviedb.fragment.ListBottomSheetFragmentTkt
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.account.AddEpisodeRating
import com.wirelessalien.android.moviedb.tmdb.account.DeleteEpisodeRating
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountStateTvSeason
import com.wirelessalien.android.moviedb.trakt.GetShowProgressTkt
import com.wirelessalien.android.moviedb.trakt.TraktSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.ParseException
import java.util.Date
import java.util.Locale

class EpisodeAdapter(
    private val context: Context,
    val episodes: List<Episode>,
    var seasonNumber: Int,
    private var showTitle: String,
    private var tvShowId: Int,
    private var traktId: Int
) : RecyclerView.Adapter<EpisodeViewHolder?>() {

    private var episodeRatings: Map<Int, Double> = HashMap()
    private var watchedEpisodes: Map<Int, Boolean> = HashMap()
    private var mediaObject: JSONObject? = null
    private val tktaccessToken = PreferenceManager.getDefaultSharedPreferences(context).getString("trakt_access_token", null)
    private lateinit var clientId: String
    init {
        CoroutineScope(Dispatchers.Main).launch {
            val getAccountStateTvSeason = GetAccountStateTvSeason(tvShowId, seasonNumber, context, object : GetAccountStateTvSeason.OnDataFetchedListener {
                override fun onDataFetched(episodeRatings: Map<Int, Double>?) {
                    this@EpisodeAdapter.episodeRatings = episodeRatings ?: emptyMap()
                    notifyDataSetChanged()
                }
            })
            getAccountStateTvSeason.fetchAccountState()

            val getShowProgressTkt = GetShowProgressTkt(traktId, seasonNumber, context, object : GetShowProgressTkt.OnDataFetchedListener {
                override fun onDataFetched(watchedEpisodes: Map<Int, Boolean>?) {
                    this@EpisodeAdapter.watchedEpisodes = watchedEpisodes ?: emptyMap()
                    notifyDataSetChanged()
                }
            })
            getShowProgressTkt.fetchShowProgress()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = EpisodeItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        clientId = ConfigHelper.getConfigValue(context, "client_id")?:""
        when (defaultSharedPreferences.getString("selected_episode_edit_button", "LOCAL")) {
            "LOCAL" -> {
                binding.toggleButtonGroup.check(R.id.btnLocal)
                binding.btnAddDetailsToLocalDb.visibility = View.VISIBLE
                binding.btnWatchedToLocalDb.visibility = View.VISIBLE
                binding.btnAddRatingToTmdb.visibility = View.GONE
                binding.btnAddToTraktCollection.visibility = View.GONE
                binding.btnAddToTraktHistory.visibility = View.GONE
                binding.btnAddToTraktList.visibility = View.GONE
                binding.btnAddToTraktWatchlist.visibility = View.GONE
                binding.btnAddTraktRating.visibility = View.GONE
            }
            "TMDB" -> {
                binding.toggleButtonGroup.check(R.id.btnTmdb)
                binding.btnAddDetailsToLocalDb.visibility = View.GONE
                binding.btnWatchedToLocalDb.visibility = View.GONE
                binding.btnAddRatingToTmdb.visibility = View.VISIBLE
                binding.btnAddToTraktCollection.visibility = View.GONE
                binding.btnAddToTraktHistory.visibility = View.GONE
                binding.btnAddToTraktList.visibility = View.GONE
                binding.btnAddToTraktWatchlist.visibility = View.GONE
                binding.btnAddTraktRating.visibility = View.GONE

            }
            "TRAKT" -> {
                binding.toggleButtonGroup.check(R.id.btnTrakt)
                binding.btnAddDetailsToLocalDb.visibility = View.GONE
                binding.btnWatchedToLocalDb.visibility = View.GONE
                binding.btnAddRatingToTmdb.visibility = View.GONE
                binding.btnAddToTraktCollection.visibility = View.VISIBLE
                binding.btnAddToTraktHistory.visibility = View.VISIBLE
                binding.btnAddToTraktList.visibility = View.VISIBLE
                binding.btnAddToTraktWatchlist.visibility = View.VISIBLE
                binding.btnAddTraktRating.visibility = View.VISIBLE
            }
        }

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

//        if (sessionId == null || accountId == null) {
//            holder.binding.rateBtn.visibility = View.GONE
//        } else {
//            holder.binding.rateBtn.visibility = View.VISIBLE
//        }

        CoroutineScope(Dispatchers.IO).launch {
            val isInCollection = TraktDatabaseHelper(context).use { db ->
                db.isEpisodeInCollection(tvShowId, seasonNumber, episode.episodeNumber)
            }

            val isInWatchList = TraktDatabaseHelper(context).use { db ->
                db.isEpisodeInWatchlist(tvShowId, seasonNumber, episode.episodeNumber)
            }

            val isInRating = TraktDatabaseHelper(context).use { db ->
                db.isEpisodeInRating(tvShowId, seasonNumber, episode.episodeNumber)
            }

            withContext(Dispatchers.Main) {
                if (isInCollection) {
                    holder.binding.btnAddToTraktCollection.icon = AppCompatResources.getDrawable(context, R.drawable.ic_collection)
                } else {
                    holder.binding.btnAddToTraktCollection.icon = AppCompatResources.getDrawable(context, R.drawable.ic_collection_border)
                }

                if (isInWatchList) {
                    holder.binding.btnAddToTraktWatchlist.icon = AppCompatResources.getDrawable(context, R.drawable.ic_bookmark)
                } else {
                    holder.binding.btnAddToTraktWatchlist.icon = AppCompatResources.getDrawable(context, R.drawable.ic_bookmark_border)
                }

                if (isInRating) {
                    holder.binding.btnAddTraktRating.icon = AppCompatResources.getDrawable(context, R.drawable.ic_thumb_up)
                } else {
                    holder.binding.btnAddTraktRating.icon = AppCompatResources.getDrawable(context, R.drawable.ic_thumb_up_border)
                }
            }
        }

        val isWatched = watchedEpisodes[episode.episodeNumber] ?: false
        if (isWatched) {
            holder.binding.btnAddToTraktHistory.icon = AppCompatResources.getDrawable(context, R.drawable.ic_done_2)
            holder.binding.btnAddToTraktHistory.text = context.getText(R.string.history)
        } else {
            holder.binding.btnAddToTraktHistory.icon = AppCompatResources.getDrawable(context, R.drawable.ic_history)
            holder.binding.btnAddToTraktHistory.text = context.getText(R.string.history)
        }

        try {
            MovieDatabaseHelper(context).use { db ->
                if (db.isEpisodeInDatabase(tvShowId, seasonNumber, listOf(episode.episodeNumber))) {
                    holder.binding.btnWatchedToLocalDb.text = context.getString(R.string.watched)
                } else {
                    holder.binding.btnWatchedToLocalDb.text = context.getString(R.string.not_watched)
                }
                holder.binding.btnWatchedToLocalDb.setOnClickListener {
                    // If the episode is in the database, remove it
                    if (db.isEpisodeInDatabase(tvShowId, seasonNumber, listOf(episode.episodeNumber))) {
                        db.removeEpisodeNumber(tvShowId, seasonNumber, listOf(episode.episodeNumber))

                        holder.binding.btnWatchedToLocalDb.text = context.getString(R.string.not_watched)

                    } else {
                        // If the episode is not in the database, add it
                        db.addEpisodeNumber(tvShowId, seasonNumber, listOf(episode.episodeNumber))

                        holder.binding.btnWatchedToLocalDb.text = context.getString(R.string.watched)

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
                                details.watchDate?.endsWith("-00-00") == true -> {
                                    val year = details.watchDate.substring(0, 4).toInt()
                                    year.toString()
                                }
                                details.watchDate?.endsWith("-00") == true -> {
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

        holder.binding.toggleButtonGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnLocal -> {
                        holder.binding.btnAddDetailsToLocalDb.visibility = View.VISIBLE
                        holder.binding.btnWatchedToLocalDb.visibility = View.VISIBLE
                        holder.binding.btnAddRatingToTmdb.visibility = View.GONE
                        holder.binding.btnAddToTraktCollection.visibility = View.GONE
                        holder.binding.btnAddToTraktHistory.visibility = View.GONE
                        holder.binding.btnAddToTraktList.visibility = View.GONE
                        holder.binding.btnAddToTraktWatchlist.visibility = View.GONE
                        holder.binding.btnAddTraktRating.visibility = View.GONE
                    }
                    R.id.btnTmdb -> {
                        holder.binding.btnAddDetailsToLocalDb.visibility = View.GONE
                        holder.binding.btnAddRatingToTmdb.visibility = View.VISIBLE
                        holder.binding.btnAddToTraktCollection.visibility = View.GONE
                        holder.binding.btnAddToTraktHistory.visibility = View.GONE
                        holder.binding.btnAddToTraktList.visibility = View.GONE
                        holder.binding.btnWatchedToLocalDb.visibility = View.GONE
                        holder.binding.btnAddToTraktWatchlist.visibility = View.GONE
                        holder.binding.btnAddTraktRating.visibility = View.GONE

                    }
                    R.id.btnTrakt -> {
                        holder.binding.btnAddDetailsToLocalDb.visibility = View.GONE
                        holder.binding.btnAddRatingToTmdb.visibility = View.GONE
                        holder.binding.btnAddToTraktCollection.visibility = View.VISIBLE
                        holder.binding.btnAddToTraktHistory.visibility = View.VISIBLE
                        holder.binding.btnAddToTraktList.visibility = View.VISIBLE
                        holder.binding.btnWatchedToLocalDb.visibility = View.GONE
                        holder.binding.btnAddToTraktWatchlist.visibility = View.VISIBLE
                        holder.binding.btnAddTraktRating.visibility = View.VISIBLE
                    }
                }
            }
        }

        holder.binding.btnAddDetailsToLocalDb.setOnClickListener {
            val dialog = BottomSheetDialog(context)
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_edit_episode, null)
            dialog.setContentView(dialogView)
            dialog.show()
            val dateTextView = dialogView.findViewById<TextInputEditText>(R.id.dateTextView)
            val ratingSlider = dialogView.findViewById<Slider>(R.id.episodeRatingSlider)
            val submitButton = dialogView.findViewById<Button>(R.id.btnSubmit)
            val cancelButton = dialogView.findViewById<Button>(R.id.btnCancel)
            val dateButton = dialogView.findViewById<Button>(R.id.dateButton)
            val episodeTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
            val reviewEditText = dialogView.findViewById<TextInputEditText>(R.id.episodeReview)
            episodeTitle.text = context.getString(R.string.episode_title_format, showTitle, seasonNumber, episode.episodeNumber, episode.name)

            // Fetch episode details from the database
            try {
                MovieDatabaseHelper(context).use { db ->
                    val details = db.getEpisodeDetails(tvShowId, seasonNumber, episode.episodeNumber)
                    if (details != null) {
                        dateTextView.text = Editable.Factory.getInstance().newEditable(details.watchDate)
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
                            dateTextView.text = Editable.Factory.getInstance().newEditable("$selectedYear-00-00")
                        } else {
                            dateTextView.text = Editable.Factory.getInstance().newEditable(String.format(Locale.ENGLISH, "%d-%02d-00", selectedYear, selectedMonth))
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
                        dateTextView.text = Editable.Factory.getInstance().newEditable(selectedDate)
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

        holder.binding.btnAddRatingToTmdb.setOnClickListener {
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

        holder.binding.btnAddToTraktWatchlist.setOnClickListener {
            holder.binding.lProgressBar.visibility = View.VISIBLE

            CoroutineScope(Dispatchers.Main).launch {
                val episodeData = withContext(Dispatchers.IO) {
                    fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
                }
                val episodeObject = if (episodeData != null) {
                    createTraktEpisodeObject(
                        seasonNumber,
                        episode.episodeNumber,
                        episodeData.getString("title"),
                        episodeData.getJSONObject("ids").getInt("tmdb"),
                        episodeData.getJSONObject("ids").getInt("trakt"),
                        episodeData.getJSONObject("ids").getInt("tvdb"),
                        episodeData.getJSONObject("ids").getString("imdb")
                    )
                } else {
                    null
                }
                mediaObject = episodeObject
                withContext(Dispatchers.IO) {
                    traktSync("sync/watchlist", episode, 0, holder)
                }
                holder.binding.lProgressBar.visibility = View.GONE
            }
        }

        holder.binding.btnAddToTraktCollection.setOnClickListener {
            showCollectionDialog(episode, holder)
        }

        holder.binding.btnAddToTraktHistory.setOnClickListener {
            showWatchOptionsDialog(episode, holder)
        }

        holder.binding.btnAddTraktRating.setOnClickListener {
            showRatingDialogTrakt(episode, holder)
        }

        holder.binding.btnAddToTraktList.setOnClickListener {
            holder.binding.lProgressBar.visibility = View.VISIBLE

            CoroutineScope(Dispatchers.Main).launch {
                val episodeData = withContext(Dispatchers.IO) {
                    fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
                }
                val episodeObject = if (episodeData != null) {
                    createTraktEpisodeObject(
                        seasonNumber,
                        episode.episodeNumber,
                        episodeData.getString("title"),
                        episodeData.getJSONObject("ids").getInt("tmdb"),
                        episodeData.getJSONObject("ids").getInt("trakt"),
                        episodeData.getJSONObject("ids").getInt("tvdb"),
                        episodeData.getJSONObject("ids").getString("imdb")
                    )
                } else {
                    null
                }
                mediaObject = episodeObject
                val listBottomSheetFragmentTkt = ListBottomSheetFragmentTkt(tvShowId, context, true, "episode", mediaObject?: JSONObject())
                listBottomSheetFragmentTkt.show((context as FragmentActivity).supportFragmentManager, listBottomSheetFragmentTkt.tag)
                holder.binding.lProgressBar.visibility = View.GONE
            }
        }
    }

    private fun showRatingDialogTrakt(episode: Episode, holder: EpisodeViewHolder) {
        val dialog = BottomSheetDialog(context)
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.rating_dialog_trakt, null)
        dialog.setContentView(dialogView)
        dialog.show()

        val ratingSlider = dialogView.findViewById<Slider>(R.id.ratingSlider)
        val submitButton = dialogView.findViewById<Button>(R.id.btnSubmit)
        val cancelButton = dialogView.findViewById<Button>(R.id.btnCancel)
        val deleteButton = dialogView.findViewById<Button>(R.id.btnDelete)
        val episodeTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val ratedAt = dialogView.findViewById<TextInputEditText>(R.id.ratedDate)
        val selectDateButton = dialogView.findViewById<Button>(R.id.btnSelectDate)
        val progressIndicator = dialogView.findViewById<LinearProgressIndicator>(R.id.progressIndicator)

        episodeTitle.text = context.getString(R.string.episode_title_format, showTitle, seasonNumber, episode.episodeNumber, episode.name)

        progressIndicator.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val rating = TraktDatabaseHelper(context).use { db ->
                db.getEpisodeRating(tvShowId, seasonNumber, episode.episodeNumber)
            }

            withContext(Dispatchers.Main) {
                progressIndicator.visibility = View.GONE
                ratingSlider.value = rating.toFloat()
            }
        }

        val currentDateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())
        ratedAt.setText(currentDateTime)

        selectDateButton.setOnClickListener {
            showDatePicker { selectedDate ->
                ratedAt.setText(selectedDate)
            }
        }

        submitButton.setOnClickListener {
            progressIndicator.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                val episodeData = fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
                val episodeObject = if (episodeData != null) {
                    val episodeIds = JSONObject().apply {
                        put("trakt", episodeData.getJSONObject("ids").getInt("trakt"))
                        put("tvdb", episodeData.getJSONObject("ids").getInt("tvdb"))
                        put("imdb", episodeData.getJSONObject("ids").getString("imdb"))
                        put("tmdb", episodeData.getJSONObject("ids").getInt("tmdb"))
                    }

                    val rating = ratingSlider.value.toInt()
                    val ratedAtE = ratedAt.text.toString()

                    val episodeDetails = JSONObject().apply {
                        put("rated_at", ratedAtE)
                        put("rating", rating)
                        put("ids", episodeIds)
                    }

                    JSONObject().apply {
                        put("episodes", JSONArray().put(episodeDetails))
                    }
                } else {
                    null
                }

                withContext(Dispatchers.Main) {
                    if (episodeObject != null) {
                        mediaObject = episodeObject
                        traktSync("sync/ratings", episode, ratingSlider.value.toInt(), holder)
                    }
                    progressIndicator.visibility = View.GONE
                    dialog.dismiss()
                }
            }
        }

        cancelButton.setOnClickListener { dialog.dismiss() }
        deleteButton.setOnClickListener {
            progressIndicator.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                val episodeData = fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
                val episodeObject = if (episodeData != null) {
                    createTraktEpisodeObject(
                        seasonNumber,
                        episode.episodeNumber,
                        episodeData.getString("title"),
                        episodeData.getJSONObject("ids").getInt("tmdb"),
                        episodeData.getJSONObject("ids").getInt("trakt"),
                        episodeData.getJSONObject("ids").getInt("tvdb"),
                        episodeData.getJSONObject("ids").getString("imdb")
                    )
                } else {
                    null
                }

                withContext(Dispatchers.Main) {
                    if (episodeObject != null) {
                        mediaObject = episodeObject
                        traktSync("sync/ratings/remove", episode, 0, holder)
                    }
                    progressIndicator.visibility = View.GONE
                    dialog.dismiss()
                }
            }
        }
    }

    private fun showWatchOptionsDialog(episode: Episode, holder: EpisodeViewHolder) {
        val dialog = BottomSheetDialog(context)
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.history_dialog_trakt, null)
        dialog.setContentView(dialogView)
        dialog.show()

        val movieTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val watchingNowButton = dialogView.findViewById<Button>(R.id.btnWatchingNow)
        val watchedAtReleaseButton = dialogView.findViewById<Button>(R.id.btnWatchedAtRelease)
        val selectDateButton = dialogView.findViewById<Button>(R.id.btnSelectDate)
        val selectedDateEditText = dialogView.findViewById<TextInputEditText>(R.id.etSelectedDate)
        val updateButton = dialogView.findViewById<Button>(R.id.btnSave)
        val timesPlayed = dialogView.findViewById<TextView>(R.id.timePlayed)
        val lastWatched = dialogView.findViewById<TextView>(R.id.lastWatched)
        val historyCard = dialogView.findViewById<MaterialCardView>(R.id.historyCard)
        val removeHistory = dialogView.findViewById<ImageView>(R.id.removeHistory)

        val progressBar = dialogView.findViewById<LinearProgressIndicator>(R.id.progressIndicator)

        movieTitle.text = context.getString(R.string.episode_title_format, showTitle, seasonNumber, episode.episodeNumber, episode.name)

        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.Main).launch {
            val watchedData = withContext(Dispatchers.IO) {
                val dbHelper = TraktDatabaseHelper(context)
                val timesPlayedD = dbHelper.getEpisodeTimesPlayed(tvShowId, seasonNumber, episode.episodeNumber)
                val lastWatchedD = dbHelper.getEpisodeLastWatched(tvShowId, seasonNumber, episode.episodeNumber)
                if (lastWatchedD != null) {
                    val dateFormat = java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        Locale.getDefault()
                    )
                    val date = dateFormat.parse(lastWatchedD)
                    val formattedDate = DateFormat.getDateInstance(DateFormat.DEFAULT).format(date)
                    Pair(timesPlayedD, formattedDate)
                } else {
                    null
                }
            }

            progressBar.visibility = View.GONE
            if (watchedData != null) {
                historyCard.visibility = View.VISIBLE
                timesPlayed.text = watchedData.first.toString()
                lastWatched.text = context.getString(R.string.last_watched, watchedData.second)
            } else {
                timesPlayed.visibility = View.GONE
                lastWatched.visibility = View.GONE
                historyCard.visibility = View.GONE
            }
        }

        removeHistory.setOnClickListener {
            val dialogBuilder = MaterialAlertDialogBuilder(context)
            dialogBuilder.setTitle("Remove from history")
            dialogBuilder.setMessage("Are you sure you want to remove this item from your history?")
            dialogBuilder.setPositiveButton("Yes") { _, _ ->
                progressBar.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    val episodeObject = withContext(Dispatchers.IO) {
                        val episodeData = fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
                        if (episodeData != null) {
                            val episodeIds = JSONObject().apply {
                                put("trakt", episodeData.getJSONObject("ids").getInt("trakt"))
                                put("tvdb", episodeData.getJSONObject("ids").getInt("tvdb"))
                                put("imdb", episodeData.getJSONObject("ids").getString("imdb"))
                                put("tmdb", episodeData.getJSONObject("ids").getInt("tmdb"))
                            }

                            val episodeDetails = JSONObject().apply {
                                put("ids", episodeIds)
                            }

                            JSONObject().apply {
                                put("episodes", JSONArray().put(episodeDetails))
                            }
                        } else {
                            null
                        }
                    }

                    if (episodeObject != null) {
                        mediaObject = episodeObject
                        traktSync("sync/history/remove", episode, 0, holder)
                    }
                    progressBar.visibility = View.GONE
                    dialog.dismiss()
                }
            }
            dialogBuilder.setNegativeButton("No") { _, _ -> }
            dialogBuilder.show()
        }

        watchingNowButton.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.Main).launch {
                val episodeObject = withContext(Dispatchers.IO) {
                    val episodeData = fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
                    if (episodeData != null) {
                        val episodeIds = JSONObject().apply {
                            put("trakt", episodeData.getJSONObject("ids").getInt("trakt"))
                            put("tvdb", episodeData.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", episodeData.getJSONObject("ids").getString("imdb"))
                            put("tmdb", episodeData.getJSONObject("ids").getInt("tmdb"))
                        }

                        val episodeDetails = JSONObject().apply {
                            put("ids", episodeIds)
                        }

                        JSONObject().apply {
                            put("episodes", JSONArray().put(episodeDetails))
                        }
                    } else {
                        null
                    }
                }

                if (episodeObject != null) {
                    mediaObject = episodeObject
                    traktSync("checkin", episode, 0, holder)
                }
                progressBar.visibility = View.GONE
                dialog.dismiss()
            }
        }

        watchedAtReleaseButton.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.Main).launch {
                val episodeObject = withContext(Dispatchers.IO) {
                    val episodeData = fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
                    if (episodeData != null) {
                        val episodeIds = JSONObject().apply {
                            put("trakt", episodeData.getJSONObject("ids").getInt("trakt"))
                            put("tvdb", episodeData.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", episodeData.getJSONObject("ids").getString("imdb"))
                            put("tmdb", episodeData.getJSONObject("ids").getInt("tmdb"))
                        }

                        val episodeDetails = JSONObject().apply {
                            put("watched_at", "released")
                            put("ids", episodeIds)
                        }

                        JSONObject().apply {
                            put("episodes", JSONArray().put(episodeDetails))
                        }
                    } else {
                        null
                    }
                }

                if (episodeObject != null) {
                    mediaObject = episodeObject
                    traktSync("sync/history", episode, 0, holder)
                }
                progressBar.visibility = View.GONE
                dialog.dismiss()
            }
        }

        selectDateButton.setOnClickListener {
            updateButton.visibility = View.VISIBLE
            showDatePicker { selectedDate ->
                selectedDateEditText.setText(selectedDate)
            }
        }

        updateButton.setOnClickListener {
            val selectedDate = selectedDateEditText.text.toString()
            if (selectedDate.isNotEmpty()) {
                progressBar.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    val episodeObject = withContext(Dispatchers.IO) {
                        val episodeData = fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
                        if (episodeData != null) {
                            val episodeIds = JSONObject().apply {
                                put("trakt", episodeData.getJSONObject("ids").getInt("trakt"))
                                put("tvdb", episodeData.getJSONObject("ids").getInt("tvdb"))
                                put("imdb", episodeData.getJSONObject("ids").getString("imdb"))
                                put("tmdb", episodeData.getJSONObject("ids").getInt("tmdb"))
                            }

                            val episodeDetails = JSONObject().apply {
                                put("watched_at", selectedDate)
                                put("ids", episodeIds)
                            }

                            JSONObject().apply {
                                put("episodes", JSONArray().put(episodeDetails))
                            }
                        } else {
                            null
                        }
                    }

                    if (episodeObject != null) {
                        mediaObject = episodeObject
                        traktSync("sync/history", episode, 0, holder)
                    }
                    progressBar.visibility = View.GONE
                    dialog.dismiss()
                }
            } else {
                selectedDateEditText.error = "Please select a date"
            }
        }
    }

    private fun showCollectionDialog(episode: Episode, holder: EpisodeViewHolder) {
        val dialog = BottomSheetDialog(context)
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.collection_dialog_trakt, null)
        dialog.setContentView(dialogView)
        dialog.show()

        val movieTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val selectDateButton = dialogView.findViewById<Button>(R.id.btnSelectDate)
        val selectedDateEditText = dialogView.findViewById<TextInputEditText>(R.id.etSelectedDate)
        val mediaTypeView = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.mediaType)
        val resolutionView = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.resolution)
        val hdrView = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.hdr)
        val audioView = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.audio)
        val audioChannelsView = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.audioChannels)
        val switch3D = dialogView.findViewById<MaterialSwitch>(R.id.switch3D)
        val progressBar = dialogView.findViewById<LinearProgressIndicator>(R.id.progressIndicator)
        val removeCollection = dialogView.findViewById<ImageView>(R.id.removeCollection)
        val saveBtn = dialogView.findViewById<Button>(R.id.btnSave)

        val mediaTypes = context.resources.getStringArray(R.array.media_types)
        val resolutions = context.resources.getStringArray(R.array.resolutions)
        val hdrTypes = context.resources.getStringArray(R.array.hdr_types)
        val audioTypes = context.resources.getStringArray(R.array.audio_types)
        val audioChannels = context.resources.getStringArray(R.array.audio_channels)

        val mediaTypeAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, mediaTypes)
        val resolutionAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, resolutions)
        val hdrAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, hdrTypes)
        val audioAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, audioTypes)
        val audioChannelsAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, audioChannels)

        mediaTypeView.setAdapter(mediaTypeAdapter)
        resolutionView.setAdapter(resolutionAdapter)
        hdrView.setAdapter(hdrAdapter)
        audioView.setAdapter(audioAdapter)
        audioChannelsView.setAdapter(audioChannelsAdapter)

        movieTitle.text = context.getString(R.string.episode_title_format, showTitle, seasonNumber, episode.episodeNumber, episode.name)

        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val isInCollection = TraktDatabaseHelper(context).use { db ->
                db.isEpisodeInCollection(tvShowId, seasonNumber, episode.episodeNumber)
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                val isCollectedTextView = dialogView.findViewById<TextView>(R.id.isCollected)
                val collectedCard = dialogView.findViewById<MaterialCardView>(R.id.collectedCard)
                if (isInCollection) {
                    isCollectedTextView.visibility = View.VISIBLE
                    collectedCard.visibility = View.VISIBLE
                } else {
                    isCollectedTextView.visibility = View.GONE
                    collectedCard.visibility = View.GONE
                }
            }
        }

        removeCollection.setOnClickListener {
            val dialogBuilder = MaterialAlertDialogBuilder(context)
            dialogBuilder.setTitle("Remove from collection")
            dialogBuilder.setMessage("Are you sure you want to remove this item from your collection?")
            dialogBuilder.setPositiveButton("Yes") { _, _ ->
                progressBar.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    val episodeObject = withContext(Dispatchers.IO) {
                        val episodeData = fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
                        if (episodeData != null) {
                            val episodeIds = JSONObject().apply {
                                put("trakt", episodeData.getJSONObject("ids").getInt("trakt"))
                                put("tvdb", episodeData.getJSONObject("ids").getInt("tvdb"))
                                put("imdb", episodeData.getJSONObject("ids").getString("imdb"))
                                put("tmdb", episodeData.getJSONObject("ids").getInt("tmdb"))
                            }

                            val episodeDetails = JSONObject().apply {
                                put("ids", episodeIds)
                            }

                            JSONObject().apply {
                                put("episodes", JSONArray().put(episodeDetails))
                            }
                        } else {
                            null
                        }
                    }

                    if (episodeObject != null) {
                        mediaObject = episodeObject
                        traktSync("sync/collection/remove", episode, 0, holder)
                    }
                    progressBar.visibility = View.GONE
                    dialog.dismiss()
                }
            }
            dialogBuilder.setNegativeButton("No") { _, _ -> }
            dialogBuilder.show()
        }

        selectDateButton.setOnClickListener {
            showDatePicker { selectedDate ->
                selectedDateEditText.setText(selectedDate)
            }
        }

        saveBtn.setOnClickListener {
            val selectedDate = selectedDateEditText.text.toString()
            val mediaType = mediaTypeView.text.toString()
            val resolution = resolutionView.text.toString()
            val hdr = hdrView.text.toString()
            val audio = audioView.text.toString()
            val audioChannel = audioChannelsView.text.toString()
            val is3D = switch3D.isChecked

            progressBar.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.Main).launch {
                val episodeObject = withContext(Dispatchers.IO) {
                    val episodeData = fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
                    if (episodeData != null) {
                        val episodeIds = JSONObject().apply {
                            put("trakt", episodeData.getJSONObject("ids").getInt("trakt"))
                            put("tvdb", episodeData.getJSONObject("ids").getInt("tvdb"))
                            put("imdb", episodeData.getJSONObject("ids").getString("imdb"))
                            put("tmdb", episodeData.getJSONObject("ids").getInt("tmdb"))
                        }

                        val episodeDetails = JSONObject().apply {
                            put("collected_at", selectedDate)
                            put("media_type", mediaType)
                            put("resolution", resolution)
                            put("hdr", hdr)
                            put("audio", audio)
                            put("audio_channels", audioChannel)
                            put("3d", is3D)
                            put("ids", episodeIds)
                        }

                        JSONObject().apply {
                            put("episodes", JSONArray().put(episodeDetails))
                        }
                    } else {
                        null
                    }
                }

                if (episodeObject != null) {
                    mediaObject = episodeObject
                    traktSync("sync/collection", episode, 0, holder)
                }
                progressBar.visibility = View.GONE
                dialog.dismiss()
            }
        }
    }

    private suspend fun fetchEpisodeData(traktId: Int, seasonNumber: Int, episodeNumber: Int, accessToken: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.trakt.tv/shows/$traktId/seasons/$seasonNumber/episodes/$episodeNumber")
                .get()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("trakt-api-version", "2")
                .addHeader("trakt-api-key", clientId)
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                    null
                } else {
                    val jsonResponse = JSONObject(responseBody)
                    JSONObject().apply {
                        put("season", jsonResponse.getInt("season"))
                        put("number", jsonResponse.getInt("number"))
                        put("title", jsonResponse.getString("title"))
                        put("ids", jsonResponse.getJSONObject("ids"))
                        put("traktid", jsonResponse.getJSONObject("ids").getInt("trakt"))
                        put("tvdbid", jsonResponse.getJSONObject("ids").getInt("tvdb"))
                        put("imdbid", jsonResponse.getJSONObject("ids").getString("imdb"))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun traktSync(endpoint: String, episode: Episode, rating: Int, holder: EpisodeViewHolder) {
        val traktApiService = TraktSync(tktaccessToken!!)
        val jsonBody = mediaObject ?: JSONObject()
        traktApiService.post(endpoint, jsonBody, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Failed to sync $endpoint", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                Handler(Looper.getMainLooper()).post {
                    val message = if (response.isSuccessful) {
                        CoroutineScope(Dispatchers.Main).launch {
                            updateTraktButtonsUI(endpoint, holder)
                            withContext(Dispatchers.IO) {
                                handleDatabaseUpdate(endpoint, episode, rating)
                            }
                        }
                        "Success"
                    } else {
                        response.message
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun updateTraktButtonsUI(endpoint: String, holder: EpisodeViewHolder) {
        when (endpoint) {
            "sync/watchlist" -> {
                holder.binding.btnAddToTraktWatchlist.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_bookmark
                )
            }
            "sync/watchlist/remove" -> {
                holder.binding.btnAddToTraktWatchlist.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_bookmark_border
                )
            }
            "sync/collection" -> {
                holder.binding.btnAddToTraktCollection.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_collection
                )
            }
            "sync/collection/remove" -> {
                holder.binding.btnAddToTraktCollection.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_collection_border
                )
            }
            "sync/ratings" -> {
                holder.binding.btnAddTraktRating.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_thumb_up
                )
            }
            "sync/ratings/remove" -> {
                holder.binding.btnAddTraktRating.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_thumb_up_border
                )
            }
        }
    }

    private fun handleDatabaseUpdate(endpoint: String, episode: Episode, rating: Int) {
        val dbHelper = TraktDatabaseHelper(context)

        when (endpoint) {
            "sync/watchlist" -> dbHelper.addEpisodeToWatchlist(showTitle, traktId, tvShowId, "episode", seasonNumber, episode.episodeNumber)
            "sync/watchlist/remove" -> dbHelper.removeEpisodeFromWatchlist(tvShowId, seasonNumber, episode.episodeNumber)
            "sync/collection" -> dbHelper.addEpisodeToCollection(showTitle, traktId, tvShowId, "show", seasonNumber, episode.episodeNumber)
            "sync/collection/remove" -> dbHelper.removeEpisodeFromCollection(tvShowId, seasonNumber, episode.episodeNumber)
            "sync/history" -> {
                dbHelper.addEpisodeToHistory(showTitle, traktId, tvShowId, "episode", seasonNumber, episode.episodeNumber)
                dbHelper.addEpisodeToWatched(showTitle, traktId, tvShowId, "show", seasonNumber, episode.episodeNumber)
            }
            "sync/history/remove" -> {
                dbHelper.removeEpisodeFromHistory(tvShowId, seasonNumber, episode.episodeNumber)
                dbHelper.removeEpisodeFromWatched(tvShowId, seasonNumber, episode.episodeNumber)
            }
            "sync/ratings" -> dbHelper.addEpisodeRating(showTitle, traktId, tvShowId, "episode" , seasonNumber, episode.episodeNumber, rating)
            "sync/ratings/remove" -> dbHelper.removeEpisodeRating(tvShowId, seasonNumber, episode.episodeNumber)
        }
    }

    private fun createTraktEpisodeObject(
        episodeSeason: Int,
        episodeNumber: Int,
        episodeTitle: String,
        episodeTmdbId: Int,
        episodeTraktId: Int,
        episodeTvdbId: Int,
        episodeImdbId: String
    ): JSONObject {
        val episodeIds = JSONObject().apply {
            put("trakt", episodeTraktId)
            put("tvdb", episodeTvdbId)
            put("imdb", episodeImdbId)
            put("tmdb", episodeTmdbId)
        }

        val episodeObject = JSONObject().apply {
            put("season", episodeSeason)
            put("number", episodeNumber)
            put("title", episodeTitle)
            put("ids", episodeIds)
        }

        return JSONObject().apply {
            put("episodes", JSONArray().put(episodeObject))
        }
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val builder = MaterialDatePicker.Builder.datePicker()
        builder.setTitleText("Select a date")
        val datePicker = builder.build()
        datePicker.show((context as FragmentActivity).supportFragmentManager, datePicker.toString())
        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = selection

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(calendar.get(java.util.Calendar.HOUR_OF_DAY))
                .setMinute(calendar.get(java.util.Calendar.MINUTE))
                .setTitleText("Select a time")
                .build()
            timePicker.show(context.supportFragmentManager, timePicker.toString())

            timePicker.addOnPositiveButtonClickListener {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(java.util.Calendar.MINUTE, timePicker.minute)

                val sdf =
                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val selectedDateTime = sdf.format(calendar.time)
                onDateSelected(selectedDateTime)
            }
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