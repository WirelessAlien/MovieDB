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

package com.wirelessalien.android.moviedb.fragment

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.databinding.BottomSheetShowDetailsBinding
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ShowDetailsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetShowDetailsBinding
    private var showJSONObject: JSONObject? = null
    private var isMovie: Boolean = true // Default to movie, will be set from arguments

    private lateinit var preferences: SharedPreferences
    private lateinit var movieDatabaseHelper: MovieDatabaseHelper
    private lateinit var traktDatabaseHelper: TraktDatabaseHelper
    private lateinit var tmdbDetailsDatabaseHelper: TmdbDetailsDatabaseHelper
    private var isSaved: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val showString = it.getString(ARG_SHOW_JSON)
            if (showString != null) {
                showJSONObject = JSONObject(showString)
            }
            isMovie = it.getBoolean(ARG_IS_MOVIE, true)
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        movieDatabaseHelper = MovieDatabaseHelper(requireContext())
        traktDatabaseHelper = TraktDatabaseHelper(requireContext())
        tmdbDetailsDatabaseHelper = TmdbDetailsDatabaseHelper(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetShowDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (showJSONObject == null) {
            Toast.makeText(context, "Show data not available.", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        populateBasicShowData()
        setupDetailsButton()
        loadProviderSpecificData()
        removeFromDbLocal()

        binding.bottomSheetTmdbActions.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.these_actions_cannot_be_modified_tooltip), Toast.LENGTH_SHORT).show()
        }

        binding.bottomSheetTraktActions.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.these_actions_cannot_be_modified_tooltip), Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeFromDbLocal() {
        val movieId = showJSONObject?.optInt("id") ?: 0
        isSaved = movieDatabaseHelper.isShowInDatabase(movieId)
        val database = movieDatabaseHelper.writableDatabase
        binding.bottomSheetSavedIcon.setOnClickListener {
            if (isSaved) {
                val alertDialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.confirm_deletion))
                    .setMessage(getString(R.string.confirm_delete_message))
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        // Perform deletion
                        database.delete(
                            MovieDatabaseHelper.TABLE_MOVIES,
                            MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null
                        )

                        database.delete(
                            MovieDatabaseHelper.TABLE_EPISODES,
                            MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null
                        )

                        ListFragment.databaseUpdate()
                        binding.bottomSheetSavedIcon.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        dialog?.dismiss()
                    }
                    .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()

                alertDialog.show()
            }
        }
    }

    private fun populateBasicShowData() {
        showJSONObject?.let {
            val title = if (isMovie) it.optString("title") else it.optString("name")
            val overview = it.optString("overview")
            val posterPath = it.optString("poster_path")

            binding.bottomSheetTitleText.text = title
            binding.bottomSheetOverviewText.text = overview

            if (!posterPath.isNullOrEmpty() && posterPath != "null") {
                Picasso.get()
                    .load("https://image.tmdb.org/t/p/w342$posterPath")
                    .placeholder(R.color.md_theme_onPrimary)
                    .error(R.drawable.ic_broken_image)
                    .into(binding.bottomSheetPosterImage)
            } else {
                binding.bottomSheetPosterImage.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_broken_image)
                )
            }

            // Check saved status (local DB)
            val showId = it.optInt("id")
            isSaved = movieDatabaseHelper.isShowInDatabase(showId)
            if (isSaved) {
                binding.bottomSheetSavedIcon.visibility = View.VISIBLE
                binding.bottomSheetSavedIcon.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_star)
                binding.bottomSheetSavedIcon.text = getString(R.string.title_saved)

            } else {
                binding.bottomSheetSavedIcon.visibility = View.GONE
            }
        }
    }

    private fun setupDetailsButton() {
        binding.bottomSheetDetailsButton.setOnClickListener {
            showJSONObject?.let {
                val intent = Intent(activity, DetailActivity::class.java)
                intent.putExtra("movieObject", it.toString())
                intent.putExtra("isMovie", isMovie)
                activity?.startActivity(intent)
                dismiss()
            }
        }
    }

    private fun loadProviderSpecificData() {
        val syncProvider = preferences.getString("sync_provider", "local")
        val showId = showJSONObject?.optInt("id") ?: 0
        val tmdbId = showJSONObject?.optInt("id") ?: 0

        when (syncProvider) {
            "tmdb" -> {
                binding.bottomSheetTmdbActions.visibility = View.VISIBLE
                binding.bottomSheetTraktActions.visibility = View.GONE
                loadTmdbData(showId)
            }
            "trakt" -> {
                binding.bottomSheetTraktActions.visibility = View.VISIBLE
                binding.bottomSheetTmdbActions.visibility = View.GONE
                loadTraktData(tmdbId) // tmdbId is used to lookup trakt info
            }
            else -> { // local or other
                binding.bottomSheetTmdbActions.visibility = View.GONE
                binding.bottomSheetTraktActions.visibility = View.GONE
                // "Saved" status is already handled in populateBasicShowData
            }
        }
    }

    private fun loadTmdbData(showId: Int) {
        val typeCheck = if (isMovie) "movie" else "tv"
        val sessionId = preferences.getString("access_token", null)
        val accountId = preferences.getString("account_id", null)

        if (sessionId == null || accountId == null) {
            // Not logged into TMDB, show default states
            binding.bottomSheetTmdbWatchlistIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_bookmark_border))
            binding.bottomSheetTmdbFavoriteIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_border))
            binding.bottomSheetTmdbRatingIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_thumb_up_border))
            return
        }

        lifecycleScope.launch {
            val getAccountState = GetAccountState(showId, typeCheck, requireActivity())
            withContext(Dispatchers.IO) {
                getAccountState.fetchAccountState()
            }

            withContext(Dispatchers.Main) {
                if (getAccountState.isInWatchlist) {
                    binding.bottomSheetTmdbWatchlistIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_bookmark))
                } else {
                    binding.bottomSheetTmdbWatchlistIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_bookmark_border))
                }

                if (getAccountState.isInFavourites) {
                    binding.bottomSheetTmdbFavoriteIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite))
                } else {
                    binding.bottomSheetTmdbFavoriteIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_border))
                }

                val ratingValue = getAccountState.rating
                if (ratingValue != 0) {
                    binding.bottomSheetTmdbRatingIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_thumb_up))
                    binding.bottomSheetTmdbRatingText.text = getString(R.string.number, ratingValue)
                } else {
                    binding.bottomSheetTmdbRatingIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_thumb_up_border))
                }
            }
        }
    }

    private fun loadTraktData(tmdbId: Int) {
        // Trakt data is primarily stored locally after sync
        val isInTraktWatchlist = traktDatabaseHelper.isMovieInWatchlist(tmdbId)
        val isInTraktCollection = traktDatabaseHelper.isMovieInCollection(tmdbId)
        val isTraktWatched = traktDatabaseHelper.isMovieInWatched(tmdbId)
        val traktRating = traktDatabaseHelper.getMovieRating(tmdbId) // Returns 0 if not rated

        if (isTraktWatched) {
            binding.bottomSheetTraktWatchedIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_done_2))
        } else {
            binding.bottomSheetTraktWatchedIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_history))
        }

        if (isInTraktWatchlist) {
            binding.bottomSheetTraktWatchlistIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_bookmark))
        } else {
            binding.bottomSheetTraktWatchlistIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_bookmark_border))
        }

        if (isInTraktCollection) {
            binding.bottomSheetTraktCollectionIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_collection))
        } else {
            binding.bottomSheetTraktCollectionIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_collection_border))
        }

        if (traktRating > 0) {
            binding.bottomSheetTraktRatingIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_thumb_up))
            binding.bottomSheetTraktRatingText.text = getString(R.string.number, traktRating)
        } else {
            binding.bottomSheetTraktRatingIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_thumb_up_border))
        }
    }

    override fun onDestroyView() {
        movieDatabaseHelper.close()
        traktDatabaseHelper.close()
        super.onDestroyView()
    }

    companion object {
        const val TAG = "ShowDetailsBottomSheet"
        private const val ARG_SHOW_JSON = "arg_show_json"
        private const val ARG_IS_MOVIE = "arg_is_movie"

        fun newInstance(showJson: String, isMovie: Boolean): ShowDetailsBottomSheet {
            val args = Bundle()
            args.putString(ARG_SHOW_JSON, showJson)
            args.putBoolean(ARG_IS_MOVIE, isMovie)
            val fragment = ShowDetailsBottomSheet()
            fragment.arguments = args
            return fragment
        }
    }
}

fun MovieDatabaseHelper.isShowInDatabase(movieId: Int): Boolean {
    val db = this.readableDatabase
    var cursor: android.database.Cursor? = null
    try {
        cursor = db.rawQuery(
            "SELECT * FROM ${MovieDatabaseHelper.TABLE_MOVIES} WHERE ${MovieDatabaseHelper.COLUMN_MOVIES_ID}=? LIMIT 1",
            arrayOf(movieId.toString())
        )
        return (cursor?.count ?: 0) > 0
    } finally {
        cursor?.close()
    }
}
