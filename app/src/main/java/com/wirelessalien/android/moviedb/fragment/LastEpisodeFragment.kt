/*
 *     This file is part of Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     Movie DB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movie DB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movie DB.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.wirelessalien.android.moviedb.fragment

import android.graphics.Color
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RatingBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.FragmentLastEpisodeBinding
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.account.AddEpisodeRating
import com.wirelessalien.android.moviedb.tmdb.account.DeleteEpisodeRating
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.util.Locale

class LastEpisodeFragment : Fragment() {
    private var binding: FragmentLastEpisodeBinding? = null
    private var databaseHelper: MovieDatabaseHelper? = null
    private var movieId = 0
    private var seasonNumber = 0
    private var episodeNumber = 0
    private var episodeName: String? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLastEpisodeBinding.inflate(inflater, container, false)
        val view: View = binding!!.root
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getBoolean(DYNAMIC_COLOR_DETAILS_ACTIVITY, false)) {
            binding!!.lastEpisodeCard.strokeWidth = 5
            binding!!.lastEpisodeCard.setCardBackgroundColor(Color.TRANSPARENT)
        }
        val sessionId = preferences.getString("access_token", null)
        val accountId = preferences.getString("account_id", null)
        binding!!.episodeRateBtn.isEnabled = sessionId != null && accountId != null
        if (arguments != null) {
            try {
                val episodeDetails = JSONObject(requireArguments().getString(ARG_EPISODE_DETAILS))
                movieId = episodeDetails.getInt("show_id")
                seasonNumber = episodeDetails.getInt("season_number")
                episodeNumber = episodeDetails.getInt("episode_number")
                episodeName = episodeDetails.getString("name")
                val labelText = requireArguments().getString(ARG_LABEL_TEXT)
                binding!!.episodeRecencyText.text = labelText
                binding!!.seasonNo.text = "S: $seasonNumber"
                binding!!.episodeNo.text = "E: $episodeNumber"
                binding!!.episodeName.text = episodeName
                binding!!.ratingAverage.text = String.format(
                    Locale.getDefault(),
                    "%.2f/%s",
                    episodeDetails.getDouble("vote_average"),
                    String.format(
                        Locale.getDefault(), "%d", 10
                    )
                )
                val overview = episodeDetails.getString("overview")
                if (overview.isEmpty()) {
                    binding!!.episodeOverview.setText(R.string.overview_not_available)
                } else {
                    binding!!.episodeOverview.text = overview
                }
                val episodeAirDateStr = episodeDetails.getString("air_date")
                val originalFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = originalFormat.parse(episodeAirDateStr)
                val dateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                val formattedDate = dateFormat.format(date)
                binding!!.episodeAirDate.text = formattedDate
                databaseHelper = MovieDatabaseHelper(context)
                if (databaseHelper!!.isEpisodeInDatabase(
                        movieId,
                        seasonNumber,
                        listOf(episodeNumber)
                    )
                ) {
                    binding!!.episodeWathchBtn.icon =
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_visibility_fill)
                } else {
                    binding!!.episodeWathchBtn.icon =
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_visibility)
                }
                binding!!.episodeRateBtn.setOnClickListener { v: View? ->
                    val dialog = BottomSheetDialog(requireContext())
                    val dialogInflater = LayoutInflater.from(context)
                    val dialogView = dialogInflater.inflate(R.layout.rating_dialog, null)
                    dialog.setContentView(dialogView)
                    dialog.show()
                    val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)
                    val submitButton = dialogView.findViewById<Button>(R.id.btnSubmit)
                    val cancelButton = dialogView.findViewById<Button>(R.id.btnCancel)
                    val deleteButton = dialogView.findViewById<Button>(R.id.btnDelete)
                    val episodeTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
                    episodeTitle.text = "S:$seasonNumber E:$episodeNumber $episodeName"
                    submitButton.setOnClickListener {
                        CoroutineScope(Dispatchers.Main).launch {
                            val rating = ratingBar.rating.toDouble()
                            AddEpisodeRating(
                                movieId,
                                seasonNumber,
                                episodeNumber,
                                rating,
                                context
                            ).addRating()
                            dialog.dismiss()
                        }
                    }

                    deleteButton.setOnClickListener {
                        CoroutineScope(Dispatchers.Main).launch {
                            DeleteEpisodeRating(
                                movieId,
                                seasonNumber,
                                episodeNumber,
                                context
                            ).deleteEpisodeRating()
                            dialog.dismiss()
                        }
                    }
                    cancelButton.setOnClickListener { dialog.dismiss() }
                }
                binding!!.episodeWathchBtn.setOnClickListener {
                    if (databaseHelper == null) {
                        databaseHelper = MovieDatabaseHelper(context)
                    }
                    if (databaseHelper!!.isEpisodeInDatabase(
                            movieId,
                            seasonNumber,
                            listOf(episodeNumber)
                        )
                    ) {
                        databaseHelper!!.removeEpisodeNumber(
                            movieId,
                            seasonNumber,
                            listOf(episodeNumber)
                        )
                        binding!!.episodeWathchBtn.icon =
                            ContextCompat.getDrawable(requireContext(), R.drawable.ic_visibility)
                    } else {
                        databaseHelper!!.addEpisodeNumber(
                            movieId,
                            seasonNumber,
                            listOf(episodeNumber)
                        )
                        binding!!.episodeWathchBtn.icon = ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_visibility_fill
                        )
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            } catch (e: ParseException) {
                e.printStackTrace()
            }
        }
        return view
    }

    companion object {
        private const val ARG_EPISODE_DETAILS = "episode_details"
        private const val ARG_LABEL_TEXT = "label_text"
        private const val DYNAMIC_COLOR_DETAILS_ACTIVITY = "dynamic_color_details_activity"
        @JvmStatic
        fun newInstance(episodeDetails: JSONObject, labelText: String?): LastEpisodeFragment {
            val fragment = LastEpisodeFragment()
            val args = Bundle()
            args.putString(ARG_EPISODE_DETAILS, episodeDetails.toString())
            args.putString(ARG_LABEL_TEXT, labelText)
            fragment.arguments = args
            return fragment
        }
    }
}