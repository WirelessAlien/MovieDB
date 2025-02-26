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
import android.content.DialogInterface
import android.content.SharedPreferences
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
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.EpisodeAdapter.EpisodeViewHolder
import com.wirelessalien.android.moviedb.data.Episode
import com.wirelessalien.android.moviedb.databinding.DialogDateFormatBinding
import com.wirelessalien.android.moviedb.databinding.DialogEditEpisodeBinding
import com.wirelessalien.android.moviedb.databinding.DialogYearMonthPickerBinding
import com.wirelessalien.android.moviedb.databinding.EpisodeItemBinding
import com.wirelessalien.android.moviedb.databinding.HistoryDialogTraktBinding
import com.wirelessalien.android.moviedb.databinding.RatingDialogBinding
import com.wirelessalien.android.moviedb.databinding.RatingDialogTraktBinding
import com.wirelessalien.android.moviedb.fragment.ListBottomSheetFragmentTkt
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
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
    var episodes: List<Episode>,
    var seasonNumber: Int,
    private var showTitle: String,
    private var tvShowId: Int,
    private var traktId: Int,
    private var tmdbObject: JSONObject
) : RecyclerView.Adapter<EpisodeViewHolder?>() {

    private var episodeRatings: Map<Int, Double> = HashMap()
    private var watchedEpisodes: Map<Int, Boolean> = HashMap()
    private var mediaObject: JSONObject? = null
    private val tktaccessToken = PreferenceManager.getDefaultSharedPreferences(context).getString("trakt_access_token", null)
    private lateinit var clientId: String
//    private var isInCollection: Boolean = false
    private var isInWatchList: Boolean = false
    private var isInRating: Boolean = false
    private lateinit var defaultSharedPreferences: SharedPreferences

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

            withContext(Dispatchers.IO) {
                TraktDatabaseHelper(context).use { db ->
                    episodes.forEach { episode ->
//                        isInCollection = db.isEpisodeInCollection(tvShowId, seasonNumber, episode.episodeNumber)
                        isInWatchList = db.isEpisodeInWatchlist(tvShowId, seasonNumber, episode.episodeNumber)
                        isInRating = db.isEpisodeInRating(tvShowId, seasonNumber, episode.episodeNumber)
                    }
                }
            }
        }
    }

    class EpisodeDiffCallback(
        private val oldList: List<Episode>,
        private val newList: List<Episode>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].episodeNumber == newList[newItemPosition].episodeNumber
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    fun updateEpisodes(newEpisodes: List<Episode>) {
        val diffCallback = EpisodeDiffCallback(this.episodes, newEpisodes)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.episodes = newEpisodes
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = EpisodeItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        clientId = ConfigHelper.getConfigValue(context, "client_id")?:""
        when (defaultSharedPreferences.getString("sync_provider", "local")) {

            "tmdb" -> {
                binding.syncProviderBtn.text = "TMDB"
                binding.syncProviderBtn.isChecked = true
                binding.btnAddRatingToTmdb.visibility = View.VISIBLE
//                binding.btnAddToTraktCollection.visibility = View.GONE
                binding.btnAddToTraktHistory.visibility = View.GONE
                binding.btnAddToTraktList.visibility = View.GONE
                binding.btnAddToTraktWatchlist.visibility = View.GONE
                binding.btnAddTraktRating.visibility = View.GONE
                if (defaultSharedPreferences.getBoolean("force_local_sync", false)) {
                    binding.btnWatchedToLocalDb.visibility = View.VISIBLE
                    binding.btnAddDetailsToLocalDb.visibility = View.VISIBLE
                } else {
                    binding.btnWatchedToLocalDb.visibility = View.GONE
                    binding.btnAddDetailsToLocalDb.visibility = View.GONE
                }

            }
            "trakt" -> {
                binding.syncProviderBtn.text = "Trakt"
                binding.syncProviderBtn.isChecked = true
                binding.btnAddRatingToTmdb.visibility = View.GONE
//                binding.btnAddToTraktCollection.visibility = View.VISIBLE
                binding.btnAddToTraktHistory.visibility = View.VISIBLE
                binding.btnAddToTraktList.visibility = View.VISIBLE
                binding.btnAddToTraktWatchlist.visibility = View.VISIBLE
                binding.btnAddTraktRating.visibility = View.VISIBLE
                if (defaultSharedPreferences.getBoolean("force_local_sync", false)) {
                    binding.btnWatchedToLocalDb.visibility = View.VISIBLE
                    binding.btnAddDetailsToLocalDb.visibility = View.VISIBLE
                } else {
                    binding.btnWatchedToLocalDb.visibility = View.GONE
                    binding.btnAddDetailsToLocalDb.visibility = View.GONE
                }
            }
            else -> {
                binding.syncProviderBtn.text = "Local"
                binding.syncProviderBtn.isChecked = true
                binding.btnAddDetailsToLocalDb.visibility = View.VISIBLE
                binding.btnWatchedToLocalDb.visibility = View.VISIBLE
                binding.btnAddRatingToTmdb.visibility = View.GONE
    //                binding.btnAddToTraktCollection.visibility = View.GONE
                binding.btnAddToTraktHistory.visibility = View.GONE
                binding.btnAddToTraktList.visibility = View.GONE
                binding.btnAddToTraktWatchlist.visibility = View.GONE
                binding.btnAddTraktRating.visibility = View.GONE
            }
        }

        binding.splitBtn.findViewById<MaterialButton>(R.id.syncProviderChange).setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(context)
            dialog.setTitle(R.string.sync_provider)
            dialog.setSingleChoiceItems(R.array.sync_providers_display, -1) { dialogInterface: DialogInterface, i: Int ->
                val syncProvider = context.resources.getStringArray(R.array.sync_providers)[i]
                val editor = defaultSharedPreferences.edit()
                editor.putString("sync_provider", syncProvider)
                editor.apply()

                when (syncProvider) {
                    "tmdb" -> {
                        binding.btnAddToTraktHistory.visibility = View.GONE
                        binding.btnAddToTraktList.visibility = View.GONE
                        binding.btnAddToTraktWatchlist.visibility = View.GONE
                        binding.btnAddTraktRating.visibility = View.GONE
                        binding.btnAddRatingToTmdb.visibility = View.VISIBLE
                        binding.syncProviderBtn.text = "TMDB"
                        if (defaultSharedPreferences.getBoolean("force_local_sync", false)) {
                            binding.btnWatchedToLocalDb.visibility = View.VISIBLE
                            binding.btnAddDetailsToLocalDb.visibility = View.VISIBLE
                        } else {
                            binding.btnWatchedToLocalDb.visibility = View.GONE
                            binding.btnAddDetailsToLocalDb.visibility = View.GONE
                        }
                    }
                    "trakt" -> {
                        binding.btnAddRatingToTmdb.visibility = View.GONE
                        binding.btnAddToTraktList.visibility = View.VISIBLE
                        binding.btnAddToTraktWatchlist.visibility = View.VISIBLE
                        binding.btnAddTraktRating.visibility = View.VISIBLE
                        binding.btnAddToTraktHistory.visibility = View.VISIBLE
                        binding.syncProviderBtn.text = "Trakt"
                        if (defaultSharedPreferences.getBoolean("force_local_sync", false)) {
                            binding.btnWatchedToLocalDb.visibility = View.VISIBLE
                            binding.btnAddDetailsToLocalDb.visibility = View.VISIBLE
                        } else {
                            binding.btnWatchedToLocalDb.visibility = View.GONE
                            binding.btnAddDetailsToLocalDb.visibility = View.GONE
                        }
                    }
                    else -> {
                        binding.btnAddRatingToTmdb.visibility = View.GONE
                        binding.btnAddToTraktList.visibility = View.GONE
                        binding.btnAddToTraktWatchlist.visibility = View.GONE
                        binding.btnAddTraktRating.visibility = View.GONE
                        binding.btnAddToTraktHistory.visibility = View.GONE
                        binding.btnWatchedToLocalDb.visibility = View.VISIBLE
                        binding.btnAddDetailsToLocalDb.visibility = View.VISIBLE
                        binding.syncProviderBtn.text = "Local"
                    }
                }
                dialogInterface.dismiss()
            }
            dialog.show()
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

        holder.binding.btnAddRatingToTmdb.isEnabled = !(sessionId == null || accountId == null)

        val traktAccessToken = defaultSharedPreferences.getString("trakt_access_token", null)

        if (traktAccessToken == null) {
//            holder.binding.btnAddToTraktCollection.isEnabled = false
            holder.binding.btnAddToTraktHistory.isEnabled = false
            holder.binding.btnAddToTraktList.isEnabled = false
            holder.binding.btnAddToTraktWatchlist.isEnabled = false
            holder.binding.btnAddTraktRating.isEnabled = false
        } else {
//            holder.binding.btnAddToTraktCollection.isEnabled = true
            holder.binding.btnAddToTraktHistory.isEnabled = true
            holder.binding.btnAddToTraktList.isEnabled = true
            holder.binding.btnAddToTraktWatchlist.isEnabled = true
            holder.binding.btnAddTraktRating.isEnabled = true
        }

        updateIconStates(holder)

        // Load current states from database
        CoroutineScope(Dispatchers.Main).launch {
            val newStates = withContext(Dispatchers.IO) {
                TraktDatabaseHelper(context).use { db ->
                    Triple(
                        db.isEpisodeInCollection(tvShowId, seasonNumber, episode.episodeNumber),
                        db.isEpisodeInWatchlist(tvShowId, seasonNumber, episode.episodeNumber),
                        db.isEpisodeInRating(tvShowId, seasonNumber, episode.episodeNumber)
                    )
                }
            }

            // Update states and icons if they changed
            if (isInWatchList != newStates.second ||
//                isInCollection != newStates.first ||
                isInRating != newStates.third) {

//                isInCollection = newStates.first
                isInWatchList = newStates.second
                isInRating = newStates.third
                updateIconStates(holder)
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
                    holder.binding.btnWatchedToLocalDb.icon = AppCompatResources.getDrawable(context, R.drawable.ic_visibility)
                } else {
                    holder.binding.btnWatchedToLocalDb.icon = AppCompatResources.getDrawable(context, R.drawable.ic_visibility_off)
                }
                holder.binding.btnWatchedToLocalDb.setOnClickListener {
                    // If the episode is in the database, remove it
                    if (db.isEpisodeInDatabase(tvShowId, seasonNumber, listOf(episode.episodeNumber))) {
                        db.removeEpisodeNumber(tvShowId, seasonNumber, listOf(episode.episodeNumber))

                        holder.binding.btnWatchedToLocalDb.icon = AppCompatResources.getDrawable(context, R.drawable.ic_visibility_off)
                    } else {
                        // If the episode is not in the database, add it
                        db.addEpisodeNumber(tvShowId, seasonNumber, listOf(episode.episodeNumber))

                        holder.binding.btnWatchedToLocalDb.icon = AppCompatResources.getDrawable(context, R.drawable.ic_visibility)
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

        holder.binding.btnAddDetailsToLocalDb.setOnClickListener {
            val dialog = BottomSheetDialog(context)
            val binding = DialogEditEpisodeBinding.inflate(LayoutInflater.from(context))
            dialog.setContentView(binding.root)
            dialog.show()

            binding.tvTitle.text = context.getString(R.string.episode_title_format, showTitle, seasonNumber, episode.episodeNumber, episode.name)

            // Fetch episode details from the database
            try {
                MovieDatabaseHelper(context).use { db ->
                    val details = db.getEpisodeDetails(tvShowId, seasonNumber, episode.episodeNumber)
                    if (details != null) {
                        binding.dateTextView.text = Editable.Factory.getInstance().newEditable(details.watchDate)
                        if (details.rating?.toDouble() != 0.0 && details.rating != null) {
                            val rating1 = if (details.rating > 10.0) 10.0 else details.rating
                            binding.episodeRatingSlider.value = rating1.toFloat()
                        }
                        binding.episodeReview.setText(details.review)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            binding.dateButton.setOnClickListener {
                val formatDialog = BottomSheetDialog(context)
                val formatBinding = DialogDateFormatBinding.inflate(LayoutInflater.from(context))
                formatDialog.setContentView(formatBinding.root)
                formatDialog.show()

                formatBinding.btnYear.setOnClickListener {
                    showYearMonthPickerDialog(context) { selectedYear, selectedMonth ->
                        if (selectedMonth == null) {
                            binding.dateTextView.text = Editable.Factory.getInstance().newEditable("$selectedYear-00-00")
                        } else {
                            binding.dateTextView.text = Editable.Factory.getInstance().newEditable(String.format(Locale.ENGLISH, "%d-%02d-00", selectedYear, selectedMonth))
                        }
                    }
                    formatDialog.dismiss()
                }

                formatBinding.btnFullDate.setOnClickListener {
                    val datePicker = MaterialDatePicker.Builder.datePicker()
                        .setTitleText(context.getString(R.string.select_a_date))
                        .build()
                    datePicker.show((context as FragmentActivity).supportFragmentManager, datePicker.toString())
                    datePicker.addOnPositiveButtonClickListener { selection: Long? ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                        val selectedDate = sdf.format(Date(selection!!))
                        binding.dateTextView.text = Editable.Factory.getInstance().newEditable(selectedDate)
                    }
                    formatDialog.dismiss()
                }
            }

            binding.btnSubmit.setOnClickListener {
                val episodeRating = binding.episodeRatingSlider.value
                if (episodeRating > 10.0) {
                    // This should not happen as the slider's max value is 10.0
                } else {
                    val date = binding.dateTextView.text.toString()
                    val review = binding.episodeReview.text.toString()
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
            binding.btnCancel.setOnClickListener { dialog.dismiss() }
        }

        holder.binding.btnAddRatingToTmdb.setOnClickListener {
            val dialog = BottomSheetDialog(context)
            val binding = RatingDialogBinding.inflate(LayoutInflater.from(context))
            dialog.setContentView(binding.root)
            dialog.show()

            binding.tvTitle.text = context.getString(
                R.string.season_episode_p,
                seasonNumber,
                episode.episodeNumber,
                episode.name
            )

            Handler(Looper.getMainLooper())
            binding.btnSubmit.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    val ratingS = binding.ratingSlider.value.toDouble()
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
            binding.btnDelete.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    val deleteEpisodeRating = DeleteEpisodeRating(tvShowId, seasonNumber, episode.episodeNumber, context)
                    deleteEpisodeRating.deleteEpisodeRating()
                    if (deleteEpisodeRating.isSuccessful()) {
                        holder.binding.rating.setText(R.string.episode_rating_tmdb_not_set)
                    }
                    dialog.dismiss()
                }
            }
            binding.btnCancel.setOnClickListener { dialog.dismiss() }
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
                    if (isInWatchList) {
                        traktSync("sync/watchlist/remove", episode, 0, holder, null, null, null, null, null, null, null)
                    } else {
                        traktSync("sync/watchlist", episode, 0, holder, null, null, null, null, null, null, null)
                    }
                }
                holder.binding.lProgressBar.visibility = View.GONE
            }
        }

//        holder.binding.btnAddToTraktCollection.setOnClickListener {
//            showCollectionDialog(episode, holder)
//        }

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
                val listBottomSheetFragmentTkt = ListBottomSheetFragmentTkt(tvShowId, context, true, "episode", mediaObject?: JSONObject(), tmdbObject)
                listBottomSheetFragmentTkt.show((context as FragmentActivity).supportFragmentManager, listBottomSheetFragmentTkt.tag)
                holder.binding.lProgressBar.visibility = View.GONE
            }
        }
    }

    private fun updateIconStates(holder: EpisodeViewHolder) {
        holder.binding.apply {
//            btnAddToTraktCollection.icon = AppCompatResources.getDrawable(
//                context,
//                if (isInCollection) R.drawable.ic_collection else R.drawable.ic_collection_border
//            )

            btnAddToTraktWatchlist.icon = AppCompatResources.getDrawable(
                context,
                if (isInWatchList) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border
            )

            btnAddTraktRating.icon = AppCompatResources.getDrawable(
                context,
                if (isInRating) R.drawable.ic_thumb_up else R.drawable.ic_thumb_up_border
            )
        }
    }

    private fun showRatingDialogTrakt(episode: Episode, holder: EpisodeViewHolder) {
        val dialog = BottomSheetDialog(context)
        val binding = RatingDialogTraktBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        dialog.show()

        binding.tvTitle.text = context.getString(R.string.episode_title_format, showTitle, seasonNumber, episode.episodeNumber, episode.name)

        binding.progressIndicator.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val rating = TraktDatabaseHelper(context).use { db ->
                db.getEpisodeRating(tvShowId, seasonNumber, episode.episodeNumber)
            }

            withContext(Dispatchers.Main) {
                binding.progressIndicator.visibility = View.GONE
                binding.ratingSlider.value = rating.toFloat()
            }
        }

        val currentDateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())
        binding.ratedDate.setText(currentDateTime)

        binding.btnSelectDate.setOnClickListener {
            showDatePicker { selectedDate ->
                binding.ratedDate.setText(selectedDate)
            }
        }

        binding.btnSubmit.setOnClickListener {
            binding.progressIndicator.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                val episodeData = fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
                val episodeObject = if (episodeData != null) {
                    val episodeIds = JSONObject().apply {
                        put("trakt", episodeData.getJSONObject("ids").getInt("trakt"))
                        put("tvdb", episodeData.getJSONObject("ids").getInt("tvdb"))
                        put("imdb", episodeData.getJSONObject("ids").getString("imdb"))
                        put("tmdb", episodeData.getJSONObject("ids").getInt("tmdb"))
                    }

                    val rating = binding.ratingSlider.value.toInt()
                    val ratedAtE = binding.ratedDate.text.toString()

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
                        traktSync("sync/ratings", episode, binding.ratingSlider.value.toInt(), holder, null, null, null, null, null, null, null)
                    }
                    binding.progressIndicator.visibility = View.GONE
                    dialog.dismiss()
                }
            }
        }

        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnDelete.setOnClickListener {
            binding.progressIndicator.visibility = View.VISIBLE
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
                        traktSync("sync/ratings/remove", episode, 0, holder, null, null, null, null, null, null, null)
                    }
                    binding.progressIndicator.visibility = View.GONE
                    dialog.dismiss()
                }
            }
        }
    }

    private fun showWatchOptionsDialog(episode: Episode, holder: EpisodeViewHolder) {
        val dialog = BottomSheetDialog(context)
        val binding = HistoryDialogTraktBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        dialog.show()

        binding.tvTitle.text = context.getString(R.string.episode_title_format, showTitle, seasonNumber, episode.episodeNumber, episode.name)

        binding.progressIndicator.visibility = View.VISIBLE

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

            binding.progressIndicator.visibility = View.GONE
            if (watchedData != null) {
                binding.historyCard.visibility = View.VISIBLE
                binding.timePlayed.text = watchedData.first.toString()
                binding.lastWatched.text = context.getString(R.string.last_watched, watchedData.second)
            } else {
                binding.timePlayed.visibility = View.GONE
                binding.lastWatched.visibility = View.GONE
                binding.historyCard.visibility = View.GONE
            }
        }

        binding.removeHistory.setOnClickListener {
            val dialogBuilder = MaterialAlertDialogBuilder(context)
            dialogBuilder.setTitle(context.getString(R.string.remove_from_history))
            dialogBuilder.setMessage(context.getString(R.string.remove_from_history_confirmation))
            dialogBuilder.setPositiveButton(context.getString(R.string.yes)) { _, _ ->
                binding.progressIndicator.visibility = View.VISIBLE
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
                        traktSync("sync/history/remove", episode, 0, holder, null, null, null, null, null, null, null)
                    }
                    binding.progressIndicator.visibility = View.GONE
                    dialog.dismiss()
                }
            }
            dialogBuilder.setNegativeButton(context.getString(R.string.no)) { _, _ -> }
            dialogBuilder.show()
        }

        binding.btnWatchingNow.setOnClickListener {
            binding.progressIndicator.visibility = View.VISIBLE
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
                    traktSync("checkin", episode, 0, holder, null, null, null, null, null, null, null)
                }
                binding.progressIndicator.visibility = View.GONE
                dialog.dismiss()
            }
        }

        binding.btnWatchedAtRelease.setOnClickListener {
            binding.progressIndicator.visibility = View.VISIBLE
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
                    traktSync("sync/history", episode, 0, holder, null, null, null, null, null, null, null)
                }
                binding.progressIndicator.visibility = View.GONE
                dialog.dismiss()
            }
        }

        binding.btnSelectDate.setOnClickListener {
            binding.btnSave.visibility = View.VISIBLE
            showDatePicker { selectedDate ->
                binding.etSelectedDate.setText(selectedDate)
            }
        }

        binding.btnSave.setOnClickListener {
            val selectedDate = binding.etSelectedDate.text.toString()
            if (selectedDate.isNotEmpty()) {
                binding.progressIndicator.visibility = View.VISIBLE
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
                        traktSync("sync/history", episode, 0, holder, selectedDate, null, null, null, null, null, null)
                    }
                    binding.progressIndicator.visibility = View.GONE
                    dialog.dismiss()
                }
            } else {
                binding.etSelectedDate.error = context.getString(R.string.please_select_a_date)
            }
        }
    }

//    private fun showCollectionDialog(episode: Episode, holder: EpisodeViewHolder) {
//        val dialog = BottomSheetDialog(context)
//        val binding = CollectionDialogTraktBinding.inflate(LayoutInflater.from(context))
//        dialog.setContentView(binding.root)
//        dialog.show()
//
//        binding.tvTitle.text = context.getString(R.string.episode_title_format, showTitle, seasonNumber, episode.episodeNumber, episode.name)
//
//        val mediaTypes = context.resources.getStringArray(R.array.media_types)
//        val resolutions = context.resources.getStringArray(R.array.resolutions)
//        val hdrTypes = context.resources.getStringArray(R.array.hdr_types)
//        val audioTypes = context.resources.getStringArray(R.array.audio_types)
//        val audioChannels = context.resources.getStringArray(R.array.audio_channels)
//
//        val mediaTypeAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, mediaTypes)
//        val resolutionAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, resolutions)
//        val hdrAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, hdrTypes)
//        val audioAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, audioTypes)
//        val audioChannelsAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, audioChannels)
//
//        binding.mediaType.setAdapter(mediaTypeAdapter)
//        binding.resolution.setAdapter(resolutionAdapter)
//        binding.hdr.setAdapter(hdrAdapter)
//        binding.audio.setAdapter(audioAdapter)
//        binding.audioChannels.setAdapter(audioChannelsAdapter)
//
//        binding.progressIndicator.visibility = View.VISIBLE
//        CoroutineScope(Dispatchers.IO).launch {
//            val collectionDetails = TraktDatabaseHelper(context).use { db ->
//                db.getEpisodeCollectionDetails(tvShowId, seasonNumber, episode.episodeNumber)
//            }
//
//            withContext(Dispatchers.Main) {
//                binding.progressIndicator.visibility = View.GONE
//                if (collectionDetails != null) {
//                    binding.isCollected.visibility = View.VISIBLE
//                    binding.collectedCard.visibility = View.VISIBLE
//                    binding.etSelectedDate.setText(collectionDetails.collectedAt ?: "")
//                    binding.mediaType.setText(collectionDetails.mediaType, false)
//                    binding.resolution.setText(collectionDetails.resolution, false)
//                    binding.hdr.setText(collectionDetails.hdr, false)
//                    binding.audio.setText(collectionDetails.audio, false)
//                    binding.audioChannels.setText(collectionDetails.audioChannels, false)
//                    binding.switch3D.isChecked = collectionDetails.thd == 1
//                } else {
//                    binding.isCollected.visibility = View.GONE
//                    binding.collectedCard.visibility = View.GONE
//                }
//            }
//        }
//
//        binding.removeCollection.setOnClickListener {
//            val dialogBuilder = MaterialAlertDialogBuilder(context)
//            dialogBuilder.setTitle(context.getString(R.string.remove_from_collection))
//            dialogBuilder.setMessage(context.getString(R.string.remove_from_collection_confirmation))
//            dialogBuilder.setPositiveButton(context.getString(R.string.yes)) { _, _ ->
//                binding.progressIndicator.visibility = View.VISIBLE
//                CoroutineScope(Dispatchers.Main).launch {
//                    val episodeObject = withContext(Dispatchers.IO) {
//                        val episodeData = fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
//                        if (episodeData != null) {
//                            val episodeIds = JSONObject().apply {
//                                put("trakt", episodeData.getJSONObject("ids").getInt("trakt"))
//                                put("tvdb", episodeData.getJSONObject("ids").getInt("tvdb"))
//                                put("imdb", episodeData.getJSONObject("ids").getString("imdb"))
//                                put("tmdb", episodeData.getJSONObject("ids").getInt("tmdb"))
//                            }
//
//                            val episodeDetails = JSONObject().apply {
//                                put("ids", episodeIds)
//                            }
//
//                            JSONObject().apply {
//                                put("episodes", JSONArray().put(episodeDetails))
//                            }
//                        } else {
//                            null
//                        }
//                    }
//
//                    if (episodeObject != null) {
//                        mediaObject = episodeObject
//                        traktSync("sync/collection/remove", episode, 0, holder, null, null, null, null, null, null, null)
//                    }
//                    binding.progressIndicator.visibility = View.GONE
//                    dialog.dismiss()
//                }
//            }
//            dialogBuilder.setNegativeButton(context.getString(R.string.no)) { _, _ -> }
//            dialogBuilder.show()
//        }
//
//        binding.btnSelectDate.setOnClickListener {
//            showDatePicker { selectedDate ->
//                binding.etSelectedDate.setText(selectedDate)
//            }
//        }
//
//        binding.btnSave.setOnClickListener {
//            val selectedDate = binding.etSelectedDate.text.toString()
//            val mediaType = binding.mediaType.text.toString()
//            val resolution = binding.resolution.text.toString()
//            val hdr = binding.hdr.text.toString()
//            val audio = binding.audio.text.toString()
//            val audioChannel = binding.audioChannels.text.toString()
//            val is3D = binding.switch3D.isChecked
//
//            binding.progressIndicator.visibility = View.VISIBLE
//            CoroutineScope(Dispatchers.Main).launch {
//                val episodeObject = withContext(Dispatchers.IO) {
//                    val episodeData = fetchEpisodeData(traktId, seasonNumber, episode.episodeNumber, tktaccessToken!!)
//                    if (episodeData != null) {
//                        val episodeIds = JSONObject().apply {
//                            put("trakt", episodeData.getJSONObject("ids").getInt("trakt"))
//                            put("tvdb", episodeData.getJSONObject("ids").getInt("tvdb"))
//                            put("imdb", episodeData.getJSONObject("ids").getString("imdb"))
//                            put("tmdb", episodeData.getJSONObject("ids").getInt("tmdb"))
//                        }
//
//                        val episodeDetails = JSONObject().apply {
//                            put("collected_at", selectedDate)
//                            put("media_type", mediaType)
//                            put("resolution", resolution)
//                            put("hdr", hdr)
//                            put("audio", audio)
//                            put("audio_channels", audioChannel)
//                            put("3d", is3D)
//                            put("ids", episodeIds)
//                        }
//
//                        JSONObject().apply {
//                            put("episodes", JSONArray().put(episodeDetails))
//                        }
//                    } else {
//                        null
//                    }
//                }
//
//                if (episodeObject != null) {
//                    mediaObject = episodeObject
//                    traktSync("sync/collection", episode, 0, holder, selectedDate, mediaType, resolution, hdr, audio, audioChannel, is3D)
//                }
//                binding.progressIndicator.visibility = View.GONE
//                dialog.dismiss()
//            }
//        }
//    }

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

    private fun traktSync(endpoint: String, episode: Episode, rating: Int, holder: EpisodeViewHolder, collectedAt: String?, mediaType: String?, resolution: String?, hdr: String?, audio: String?, audioChannels: String?, is3D: Boolean?) {
        val traktApiService = TraktSync(tktaccessToken!!)
        val jsonBody = mediaObject ?: JSONObject()
        traktApiService.post(endpoint, jsonBody, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context,
                        context.getString(R.string.failed_to_sync, endpoint), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                Handler(Looper.getMainLooper()).post {
                    val message = if (response.isSuccessful) {
                        CoroutineScope(Dispatchers.Main).launch {
                            updateTraktButtonsUI(endpoint, holder)
                            withContext(Dispatchers.IO) {
                                handleDatabaseUpdate(endpoint, episode, rating, collectedAt, mediaType, resolution, hdr, audio, audioChannels, is3D)
                                addItemtoTmdb()
                                updateBoolean(endpoint)
                            }
                        }
                        context.getString(R.string.success)
                    } else {
                        response.message
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun updateBoolean(endpoint: String) {
        when (endpoint) {
            "sync/watchlist" -> isInWatchList = true
            "sync/watchlist/remove" -> isInWatchList = false
//            "sync/collection" -> isInCollection = true
//            "sync/collection/remove" -> isInCollection = false
            "sync/ratings" -> isInRating = true
            "sync/ratings/remove" -> isInRating = false
        }
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
//            "sync/collection" -> {
//                holder.binding.btnAddToTraktCollection.icon = ContextCompat.getDrawable(
//                    context,
//                    R.drawable.ic_collection
//                )
//            }
//            "sync/collection/remove" -> {
//                holder.binding.btnAddToTraktCollection.icon = ContextCompat.getDrawable(
//                    context,
//                    R.drawable.ic_collection_border
//                )
//            }
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
            "sync/history" -> {
                holder.binding.btnAddToTraktHistory.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_done_2
                )
            }
            "sync/history/remove" -> {
                holder.binding.btnAddToTraktHistory.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_history
                )
            }
        }
    }

    private fun handleDatabaseUpdate(endpoint: String, episode: Episode, rating: Int, collectedAt: String?, mediaType: String?, resolution: String?, hdr: String?, audio: String?, audioChannels: String?, is3D: Boolean?) {
        val dbHelper = TraktDatabaseHelper(context)

        when (endpoint) {
            "sync/watchlist" -> dbHelper.addEpisodeToWatchlist(showTitle, traktId, tvShowId, "episode", seasonNumber, episode.episodeNumber)
            "sync/watchlist/remove" -> dbHelper.removeEpisodeFromWatchlist(tvShowId, seasonNumber, episode.episodeNumber)
//            "sync/collection" -> dbHelper.addEpisodeToCollection(showTitle, traktId, tvShowId, "show", seasonNumber, episode.episodeNumber, collectedAt, mediaType, resolution, hdr, audio, audioChannels, is3D)
//            "sync/collection/remove" -> dbHelper.removeEpisodeFromCollection(tvShowId, seasonNumber, episode.episodeNumber)
            "sync/history" -> {
                dbHelper.addEpisodeToHistory(showTitle, traktId, tvShowId, "episode", seasonNumber, episode.episodeNumber, collectedAt)
                dbHelper.addEpisodeToWatched(showTitle, traktId, tvShowId, seasonNumber, episode.episodeNumber)
                dbHelper.addEpisodeToWatchedTable(tvShowId, traktId, "show", showTitle)
            }
            "sync/history/remove" -> {
                dbHelper.removeEpisodeFromHistory(tvShowId, seasonNumber, episode.episodeNumber)
                dbHelper.removeEpisodeFromWatched(tvShowId, seasonNumber, episode.episodeNumber)
            }
            "sync/ratings" -> dbHelper.addEpisodeRating(showTitle, traktId, tvShowId, "episode" , seasonNumber, episode.episodeNumber, rating)
            "sync/ratings/remove" -> dbHelper.removeEpisodeRating(tvShowId, seasonNumber, episode.episodeNumber)
        }
    }

    private fun addItemtoTmdb() {
        val dbHelper = TmdbDetailsDatabaseHelper(context)
        val tmdbId = tmdbObject.optInt("id")
        val name = tmdbObject.optString("name")
        val backdropPath = tmdbObject.optString("backdrop_path")
        val posterPath = tmdbObject.optString("poster_path")
        val summary = tmdbObject.optString("overview")
        val voteAverage = tmdbObject.optDouble("vote_average")
        val type = "show"
        val releaseDate = tmdbObject.optString("first_air_date")
        val genreIds = tmdbObject.optJSONArray("genres")?.let { genresArray ->
            val ids = (0 until genresArray.length()).joinToString(",") { i ->
                genresArray.getJSONObject(i).getInt("id").toString()
            }
            "[$ids]"
        }
        val seasonEpisodeCount = tmdbObject.optJSONArray("seasons")
        val seasonsEpisodes = StringBuilder()

        for (i in 0 until (seasonEpisodeCount?.length() ?: 0)) {
            val season = seasonEpisodeCount?.getJSONObject(i)
            val seasonNumber = season?.getInt("season_number")

            // Skip specials (season_number == 0)
            if (seasonNumber == 0) continue

            val episodeCount = season?.getInt("episode_count")?: 0
            val episodesList = (1..episodeCount).toList()

            seasonsEpisodes.append("$seasonNumber{${episodesList.joinToString(",")}}")
            if (i < (seasonEpisodeCount?.length() ?: 0) - 1) {
                seasonsEpisodes.append(",")
            }
        }

        dbHelper.addItem(
            tmdbId,
            name,
            backdropPath,
            posterPath,
            summary,
            voteAverage,
            releaseDate,
            genreIds?: "",
            seasonsEpisodes.toString(),
            type
        )
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
        builder.setTitleText(context.getString(R.string.select_a_date))
        val datePicker = builder.build()
        datePicker.show((context as FragmentActivity).supportFragmentManager, datePicker.toString())
        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = selection

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(calendar.get(java.util.Calendar.HOUR_OF_DAY))
                .setMinute(calendar.get(java.util.Calendar.MINUTE))
                .setTitleText(context.getString(R.string.select_a_time))
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
        val binding = DialogYearMonthPickerBinding.inflate(LayoutInflater.from(context))
        val dialogView = binding.root

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        binding.yearPicker.minValue = 1900
        binding.yearPicker.maxValue = currentYear
        binding.yearPicker.value = currentYear

        val months = DateFormatSymbols.getInstance(Locale.getDefault()).months
        binding.monthPicker.minValue = 0
        binding.monthPicker.maxValue = months.size - 1
        binding.monthPicker.displayedValues = months
        binding.monthPicker.value = Calendar.getInstance().get(Calendar.MONTH)

        binding.disableMonthPicker.setOnCheckedChangeListener { _, isChecked ->
            binding.monthPicker.isEnabled = !isChecked
            binding.monthPicker.visibility = if (isChecked) View.GONE else View.VISIBLE
            binding.monthTitle.visibility = if (isChecked) View.GONE else View.VISIBLE
            binding.monthLayout.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.select_year_and_month))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                val selectedYear = binding.yearPicker.value
                val selectedMonth = if (binding.disableMonthPicker.isChecked) null else binding.monthPicker.value + 1
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