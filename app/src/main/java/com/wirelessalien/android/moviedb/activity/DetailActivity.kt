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
package com.wirelessalien.android.moviedb.activity

import android.app.Activity
import android.app.UiModeManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.icu.text.DateFormat
import android.icu.text.DateFormatSymbols
import android.icu.text.NumberFormat
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.util.Log
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import com.squareup.picasso.Target
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.CastBaseAdapter
import com.wirelessalien.android.moviedb.adapter.EpisodePagerAdapter
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter
import com.wirelessalien.android.moviedb.adapter.SimilarMovieBaseAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityDetailBinding
import com.wirelessalien.android.moviedb.databinding.CollectionDialogTraktBinding
import com.wirelessalien.android.moviedb.databinding.DialogDateFormatBinding
import com.wirelessalien.android.moviedb.databinding.DialogYearMonthPickerBinding
import com.wirelessalien.android.moviedb.databinding.HistoryDialogTraktBinding
import com.wirelessalien.android.moviedb.databinding.RatingDialogBinding
import com.wirelessalien.android.moviedb.databinding.RatingDialogTraktBinding
import com.wirelessalien.android.moviedb.fragment.LastEpisodeFragment.Companion.newInstance
import com.wirelessalien.android.moviedb.fragment.ListBottomSheetFragment
import com.wirelessalien.android.moviedb.fragment.ListBottomSheetFragmentTkt
import com.wirelessalien.android.moviedb.fragment.ListFragment.Companion.databaseUpdate
import com.wirelessalien.android.moviedb.helper.ConfigHelper.getConfigValue
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TmdbDetailsDatabaseHelper
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.account.AddRating
import com.wirelessalien.android.moviedb.tmdb.account.AddToFavourites
import com.wirelessalien.android.moviedb.tmdb.account.AddToWatchlist
import com.wirelessalien.android.moviedb.tmdb.account.DeleteRating
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountState
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
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * This class provides all the details about the shows.
 * It also manages personal show data.
 */
class DetailActivity : BaseActivity() {
    private var apiKey: String? = null
    private var tktaccessToken: String? = null
    private var apiReadAccessToken: String? = null
    private var tktApiKey: String? = null
    private lateinit var castAdapter: CastBaseAdapter
    private lateinit var crewAdapter: CastBaseAdapter
    private lateinit var castArrayList: ArrayList<JSONObject>
    private lateinit var crewArrayList: ArrayList<JSONObject>
    private lateinit var similarMovieAdapter: SimilarMovieBaseAdapter
    private lateinit var episodeViewPager: ViewPager2
    private lateinit var episodePagerAdapter: EpisodePagerAdapter
    private lateinit var similarMovieArrayList: ArrayList<JSONObject>
    private var sessionId: String? = null
    private var accountId: String? = null
    private lateinit var database: SQLiteDatabase
    private lateinit var databaseHelper: MovieDatabaseHelper
    private var movieId = 0
    private var seasons: JSONArray? = null
    private var lastEpisode: JSONObject? = null
    private var nextEpisode: JSONObject? = null
    private lateinit var target: Target
    private val voteAverage: String? = null
    private var numSeason = 0
    private var showName: String? = null
    private var totalEpisodes: Int? = null
    private var seenEpisode = 0
    private var isMovie = true
    private var context: Context = this
    private lateinit var jMovieObject: JSONObject
    private lateinit var movieDataObject: JSONObject
    private var imdbId: String? = null
    private var traktCheckingObject: JSONObject? = null
    private var traktMediaObject: JSONObject? = null
    private var genres: String? = ""
    private var startDate: Date? = null
    private var finishDate: Date? = null
    private lateinit var mActivity: Activity
    private val MAX_RETRY_COUNT = 2
    private var retryCount = 0
    private var added = false
    private var showTitle: SpannableString? = null
    private var movieTitle: String? = null
    private var movieYear: String? = null
    private lateinit var palette: Palette
    private var darkMutedColor = 0
    private var lightMutedColor = 0
    private var isInCollection = false
    private var isInWatchlist = false
    private var isInHistory = false
    private var isInFavourite = false
    private var isInWatched = false
    private var isInRating = false
    private lateinit var binding: ActivityDetailBinding

    private lateinit var preferences: SharedPreferences

    // Indicate whether network items have loaded.
    private var mMovieDetailsLoaded = false
    private var mSimilarMoviesLoaded = false
    private var mCastAndCrewLoaded = false
    private var mVideosLoaded = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(
            layoutInflater
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        apiKey = getConfigValue(applicationContext, "api_key")
        apiReadAccessToken = getConfigValue(applicationContext, "api_read_access_token")
        tktApiKey = getConfigValue(applicationContext, "client_id")
        tktaccessToken = preferences.getString("trakt_access_token", null)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            if (abs(verticalOffset) == appBarLayout.totalScrollRange) {
                // Collapsed
                supportActionBar?.title = showTitle
            } else if (verticalOffset == 0) {
                // Expanded
                supportActionBar?.title = ""
            } else {
                // Somewhere in between
                supportActionBar?.title = ""
            }
        }
        episodeViewPager = binding.episodeViewPager
        episodePagerAdapter = EpisodePagerAdapter(this)

        // Create a variable with the application context that can be used
        // when data is retrieved.
        mActivity = this
        movieId = 0
        context = this
        seenEpisode = 0
        movieDataObject = JSONObject()

        // RecyclerView to display the cast of the show.
        binding.castRecyclerView.setHasFixedSize(true) // Improves performance (if size is static)
        val castLinearLayoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL, false
        )
        binding.castRecyclerView.layoutManager = castLinearLayoutManager

        // RecyclerView to display the crew of the show.
        binding.crewRecyclerView.setHasFixedSize(true) // Improves performance (if size is static)
        val crewLinearLayoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL, false
        )
        binding.crewRecyclerView.layoutManager = crewLinearLayoutManager

        // RecyclerView to display similar shows to this one.
        binding.movieRecyclerView.setHasFixedSize(true) // Improves performance (if size is static)
        val movieLinearLayoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL, false
        )
        binding.movieRecyclerView.layoutManager = movieLinearLayoutManager

        val adapter = ArrayAdapter.createFromResource(this, R.array.categories, android.R.layout.simple_dropdown_item_1line)
        binding.categories.setAdapter(adapter)

        // Make the views invisible if the user collapsed the view.
        if (!preferences.getBoolean(CAST_VIEW_PREFERENCE, false)) {
            binding.castRecyclerView.visibility = View.GONE
            binding.castTitle.visibility = View.GONE
            binding.secondDivider.visibility = View.GONE
        }
        if (!preferences.getBoolean(CREW_VIEW_PREFERENCE, false)) {
            binding.crewRecyclerView.visibility = View.GONE
            binding.crewTitle.visibility = View.GONE
            binding.thirdDivider.visibility = View.GONE
        }
        if (!preferences.getBoolean(RECOMMENDATION_VIEW_PREFERENCE, false)) {
            binding.movieRecyclerView.visibility = View.GONE
            val similarMovieTitle = binding.similarMovieTitle
            similarMovieTitle.visibility = View.GONE
        }

        when (preferences.getString("sync_provider", "local")) {
            "tmdb" -> {
                binding.toggleButtonGroup.check(R.id.btnTmdb)
                binding.btnAddToTraktWatchlist.visibility = View.GONE
                binding.btnAddToTraktFavorite.visibility = View.GONE
                binding.btnAddToTraktCollection.visibility = View.GONE
                binding.btnAddToTraktHistory.visibility = View.GONE
                binding.btnAddToTraktList.visibility = View.GONE
                binding.btnAddTraktRating.visibility = View.GONE
                binding.ratingBtnTmdb.visibility = View.VISIBLE
                binding.favouriteButtonTmdb.visibility = View.VISIBLE
                binding.addToListTmdb.visibility = View.VISIBLE
                binding.watchListButtonTmdb.visibility = View.VISIBLE

            }
            "trakt" -> {
                binding.toggleButtonGroup.check(R.id.btnTrakt)
                binding.btnAddToTraktWatchlist.visibility = View.VISIBLE
                binding.btnAddToTraktFavorite.visibility = View.VISIBLE
                binding.btnAddToTraktCollection.visibility = View.VISIBLE
                binding.btnAddToTraktHistory.visibility = View.VISIBLE
                binding.btnAddToTraktList.visibility = View.VISIBLE
                binding.btnAddTraktRating.visibility = View.VISIBLE
                binding.ratingBtnTmdb.visibility = View.GONE
                binding.favouriteButtonTmdb.visibility = View.GONE
                binding.addToListTmdb.visibility = View.GONE
                binding.watchListButtonTmdb.visibility = View.GONE

            }
        }

        binding.toggleButtonGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnTmdb -> {
                        binding.btnAddToTraktWatchlist.visibility = View.GONE
                        binding.btnAddToTraktFavorite.visibility = View.GONE
                        binding.btnAddToTraktCollection.visibility = View.GONE
                        binding.btnAddToTraktHistory.visibility = View.GONE
                        binding.btnAddToTraktList.visibility = View.GONE
                        binding.btnAddTraktRating.visibility = View.GONE
                        binding.ratingBtnTmdb.visibility = View.VISIBLE
                        binding.favouriteButtonTmdb.visibility = View.VISIBLE
                        binding.addToListTmdb.visibility = View.VISIBLE
                        binding.watchListButtonTmdb.visibility = View.VISIBLE
                    }
                    R.id.btnTrakt -> {
                        binding.btnAddToTraktWatchlist.visibility = View.VISIBLE
                        binding.btnAddToTraktFavorite.visibility = View.VISIBLE
                        binding.btnAddToTraktCollection.visibility = View.VISIBLE
                        binding.btnAddToTraktHistory.visibility = View.VISIBLE
                        binding.btnAddToTraktList.visibility = View.VISIBLE
                        binding.btnAddTraktRating.visibility = View.VISIBLE
                        binding.ratingBtnTmdb.visibility = View.GONE
                        binding.favouriteButtonTmdb.visibility = View.GONE
                        binding.addToListTmdb.visibility = View.GONE
                        binding.watchListButtonTmdb.visibility = View.GONE
                    }
                }
            }
        }

        sessionId = preferences.getString("access_token", null)
        accountId = preferences.getString("account_id", null)
        if (sessionId == null || accountId == null) {
            // Disable the buttons
            binding.watchListButtonTmdb.isEnabled = false
            binding.favouriteButtonTmdb.isEnabled = false
            binding.ratingBtnTmdb.isEnabled = false
            binding.addToListTmdb.isEnabled = false
            //            binding.episodeRateBtn.setEnabled(false);
        } else {
            // Enable the buttons
            binding.watchListButtonTmdb.isEnabled = true
            binding.favouriteButtonTmdb.isEnabled = true
            binding.ratingBtnTmdb.isEnabled = true
            binding.addToListTmdb.isEnabled = true
            //            binding.episodeRateBtn.setEnabled(true);
        }

        // Get the movieObject from the intent that contains the necessary
        // data to display the right movie and related RecyclerViews.
        // Send the JSONObject to setMovieData() so all the data
        // will be displayed on the screen.
        val intent = intent
        isMovie = intent.getBooleanExtra("isMovie", true)
        try {
            val movieObjectString = intent.getStringExtra("movieObject")
            if (movieObjectString != null) {
                setMovieData(JSONObject(movieObjectString))
                jMovieObject = JSONObject(movieObjectString)

                // Set the adapter with the (still) empty ArrayList.
                castArrayList = ArrayList()
                castAdapter = CastBaseAdapter(castArrayList, applicationContext)
                binding.castRecyclerView.adapter = castAdapter

                // Set the adapter with the (still) empty ArrayList.
                crewArrayList = ArrayList()
                crewAdapter = CastBaseAdapter(crewArrayList, applicationContext)
                binding.crewRecyclerView.adapter = crewAdapter

                // Set the adapter with the (still) empty ArrayList.
                similarMovieArrayList = ArrayList()
                similarMovieAdapter = SimilarMovieBaseAdapter(similarMovieArrayList, applicationContext)
                binding.movieRecyclerView.adapter = similarMovieAdapter
            } else {
                Log.e("DetailActivity", "movieObjectString is null")
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        checkNetwork()
        databaseHelper = MovieDatabaseHelper(applicationContext)
        database = databaseHelper.writableDatabase
        databaseHelper.onCreate(database)

        // Check if the show is already in the database.
        val cursor = database.rawQuery(
            "SELECT * FROM " +
                    MovieDatabaseHelper.TABLE_MOVIES +
                    " WHERE " + MovieDatabaseHelper.COLUMN_MOVIES_ID +
                    "=" + movieId + " LIMIT 1", null
        )
        cursor.use { cursor1 ->
            if (cursor1.count > 0) {
                // A record has been found
                binding.fabSave.setImageResource(R.drawable.ic_star)
                added = true
            }
        }

        binding.shimmerFrameLayout1.visibility = View.VISIBLE
        binding.shimmerFrameLayout1.startShimmer()

        lifecycleScope.launch {
            if (accountId != null && sessionId != null) {
                val typeCheck = if (isMovie) "movie" else "tv"
                val getAccountState = GetAccountState(movieId, typeCheck, mActivity)

                withContext(Dispatchers.IO) {
                    getAccountState.fetchAccountState()
                }

                val isInWatchlist = getAccountState.isInWatchlist
                val isFavourite = getAccountState.isInFavourites
                val ratingValue = getAccountState.rating

                withContext(Dispatchers.Main) {
                    if (isInWatchlist) {
                        binding.watchListButtonTmdb.icon = ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_bookmark
                        )
                    } else {
                        binding.watchListButtonTmdb.icon = ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_bookmark_border
                        )
                    }
                    if (isFavourite) {
                        binding.favouriteButtonTmdb.icon = ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_favorite
                        )
                    } else {
                        binding.favouriteButtonTmdb.icon = ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_favorite_border
                        )
                    }
                    if (ratingValue != 0.0) {
                        binding.ratingBtnTmdb.icon = ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_thumb_up
                        )
                    } else {
                        binding.ratingBtnTmdb.icon = ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_thumb_up_border
                        )
                    }
                }
            } else {
                val isToastShown = preferences.getBoolean("isToastShown", false)
                if (!isToastShown) {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            R.string.login_is_required_to_use_tmdb_based_sync,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    val editor = preferences.edit()
                    editor.putBoolean("isToastShown", true)
                    editor.apply()
                }
            }
            binding.shimmerFrameLayout1.visibility = View.GONE
            binding.shimmerFrameLayout1.stopShimmer()
        }


        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        val isDarkTheme = uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
        val color: Int = if (isDarkTheme) {
            Color.BLACK
        } else {
            Color.WHITE
        }
        if (preferences.getBoolean(DYNAMIC_COLOR_DETAILS_ACTIVITY, false)) {
            if (jMovieObject.has("backdrop_path") && binding.movieImage.drawable == null) {
                val loadHDImage = preferences.getBoolean(HD_IMAGE_SIZE, false)
                val imageSize = if (loadHDImage) "w1280" else "w780"
                val imageUrl: String = try {
                    "https://image.tmdb.org/t/p/" + imageSize + jMovieObject.getString("backdrop_path")
                } catch (e: JSONException) {
                    e.printStackTrace()
                    ""
                }

                // Set the loaded bitmap to your ImageView before generating the Palette
                target = object : Target {
                    override fun onBitmapLoaded(bitmap: Bitmap, from: LoadedFrom) {
                        // Set the loaded bitmap to your ImageView before generating the Palette
                        binding.movieImage.setImageBitmap(bitmap)
                        palette = Palette.from(bitmap).generate()
                        darkMutedColor =
                            palette.getDarkMutedColor(palette.getMutedColor(Color.TRANSPARENT))
                        lightMutedColor =
                            palette.getLightMutedColor(palette.getMutedColor(Color.TRANSPARENT))
                        val gradientDrawable: GradientDrawable
                        val mutedColor: Int
                        if (isDarkTheme) {
                            gradientDrawable = GradientDrawable(
                                GradientDrawable.Orientation.TL_BR,
                                intArrayOf(darkMutedColor, color)
                            )
                            mutedColor = darkMutedColor
                        } else {
                            gradientDrawable = GradientDrawable(
                                GradientDrawable.Orientation.TL_BR,
                                intArrayOf(lightMutedColor, color)
                            )
                            mutedColor = lightMutedColor
                        }
                        binding.root.background = gradientDrawable
                        binding.appBarLayout.setBackgroundColor(Color.TRANSPARENT)

                        val foregroundGradientDrawable = GradientDrawable(
                            GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(mutedColor, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT)
                        )
                        binding.movieImage.foreground = foregroundGradientDrawable

                        val colorStateList = ColorStateList.valueOf(mutedColor)
                        if (mutedColor != Color.TRANSPARENT) {
                            binding.fab.backgroundTintList = colorStateList

                            binding.secondDivider.dividerColor = mutedColor
                            binding.thirdDivider.dividerColor = mutedColor
                            binding.forthDivider.dividerColor = mutedColor
                            binding.sixthDivider.dividerColor = mutedColor
                            binding.seventhDivider.dividerColor = mutedColor

                            binding.allEpisodeBtn.backgroundTintList = colorStateList
                            binding.editIcon.backgroundTintList = colorStateList
                            binding.fabSave.backgroundTintList = colorStateList
                            binding.toolbar.setBackgroundColor(Color.TRANSPARENT)
                            binding.collapsingToolbar.setContentScrimColor(mutedColor)
                            binding.showRating.backgroundTintList = colorStateList

                            binding.ratingCard.setCardBackgroundColor(Color.TRANSPARENT)

                            preferences.getString("sync_provider", "local")?.let {
                                if (it == "tmdb") {
                                    binding.btnTmdb.backgroundTintList = colorStateList
                                } else {
                                    binding.btnTrakt.backgroundTintList = colorStateList
                                }
                            }
                            binding.toggleButtonGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
                                if (isChecked) {
                                    val checkedButton = group.findViewById<MaterialButton>(checkedId)
                                    checkedButton.backgroundTintList = colorStateList
                                } else {
                                    val uncheckedButton = group.findViewById<MaterialButton>(checkedId)
                                    uncheckedButton.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                                }
                            }

                            binding.btnAddToTraktWatchlist.backgroundTintList = colorStateList
                            binding.btnAddToTraktFavorite.backgroundTintList = colorStateList
                            binding.btnAddToTraktCollection.backgroundTintList = colorStateList
                            binding.btnAddToTraktHistory.backgroundTintList = colorStateList
                            binding.btnAddToTraktList.backgroundTintList = colorStateList
                            binding.btnAddTraktRating.backgroundTintList = colorStateList
                            binding.ratingBtnTmdb.backgroundTintList = colorStateList
                            binding.favouriteButtonTmdb.backgroundTintList = colorStateList
                            binding.addToListTmdb.backgroundTintList = colorStateList
                            binding.watchListButtonTmdb.backgroundTintList = colorStateList

                        }
                        val animation = AnimationUtils.loadAnimation(
                            applicationContext, R.anim.slide_in_right
                        )
                        binding.movieImage.startAnimation(animation)
                    }

                    override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
                        if (retryCount < MAX_RETRY_COUNT) {
                            Picasso.get().load(imageUrl).into(this)
                            retryCount++
                        } else {
                            val fallbackDrawable = errorDrawable ?: ContextCompat.getColor(context, R.color.md_theme_surface)
                            binding.movieImage.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_surface))
                            binding.movieImage.setBackgroundColor(fallbackDrawable as Int)

                        }
                    }

                    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                        // Ensure placeHolderDrawable is not null
                        placeHolderDrawable ?: ContextCompat.getColor(context, R.color.md_theme_outline)
                        binding.movieImage.setBackgroundColor(
                            ContextCompat.getColor(
                                context,
                                R.color.md_theme_surface
                            )
                        )
                    }
                }
                Picasso.get().load(imageUrl).into(target)
            }
        } else {
            val foregroundGradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(ContextCompat.getColor(context, R.color.md_theme_surface), Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT)
            )
            binding.movieImage.foreground = foregroundGradientDrawable

            if (jMovieObject.has("backdrop_path") && binding.movieImage.drawable == null) {
                val loadHDImage = preferences.getBoolean(HD_IMAGE_SIZE, false)
                val imageSize = if (loadHDImage) "w1280" else "w780"
                try {
                    Picasso.get().load(
                        "https://image.tmdb.org/t/p/" + imageSize +
                                jMovieObject.getString("backdrop_path")
                    )
                        .into(binding.movieImage)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
                val animation = AnimationUtils.loadAnimation(
                    applicationContext, R.anim.fade_in
                )
                binding.movieImage.startAnimation(animation)
            }
        }

        binding.watchListButtonTmdb.setOnClickListener {
            lifecycleScope.launch {
                if (accountId != null) {
                    val typeCheck = if (isMovie) "movie" else "tv"
                    val getAccountState = GetAccountState(movieId, typeCheck, mActivity)

                    withContext(Dispatchers.IO) {
                        getAccountState.fetchAccountState()
                    }

                    val isInWatchlist = getAccountState.isInWatchlist

                    withContext(Dispatchers.Main) {
                        if (isInWatchlist) {
                            binding.watchListButtonTmdb.icon = ContextCompat.getDrawable(
                                context,
                                R.drawable.ic_bookmark_border
                            )
                            withContext(Dispatchers.IO) {
                                AddToWatchlist(movieId, typeCheck, false, mActivity).addToWatchlist()
                            }
                            binding.watchListButtonTmdb.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else {
                            binding.watchListButtonTmdb.icon = ContextCompat.getDrawable(
                                context,
                                R.drawable.ic_bookmark
                            )
                            withContext(Dispatchers.IO) {
                                AddToWatchlist(movieId, typeCheck, true, mActivity).addToWatchlist()
                            }
                            binding.watchListButtonTmdb.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                } else {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.failed_to_retrieve_account_id), Toast.LENGTH_SHORT).show()
                    binding.watchListButtonTmdb.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }

        binding.addToListTmdb.setOnClickListener {
            val typeCheck = if (isMovie) "movie" else "tv"
            val listBottomSheetFragment =
                ListBottomSheetFragment(movieId, typeCheck, mActivity, true)
            listBottomSheetFragment.show(
                supportFragmentManager,
                listBottomSheetFragment.tag
            )
        }
        binding.favouriteButtonTmdb.setOnClickListener {
            lifecycleScope.launch {
                if (accountId != null) {
                    val typeCheck = if (isMovie) "movie" else "tv"
                    val getAccountState = GetAccountState(movieId, typeCheck, mActivity)

                    withContext(Dispatchers.IO) {
                        getAccountState.fetchAccountState()
                    }

                    val isInFavourites = getAccountState.isInFavourites

                    withContext(Dispatchers.Main) {
                        if (isInFavourites) {
                            binding.favouriteButtonTmdb.icon = ContextCompat.getDrawable(
                                context,
                                R.drawable.ic_favorite_border
                            )
                            withContext(Dispatchers.IO) {
                                AddToFavourites(movieId, typeCheck, false, mActivity).addToFavourites()
                            }
                            binding.favouriteButtonTmdb.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else {
                            binding.favouriteButtonTmdb.icon = ContextCompat.getDrawable(
                                context,
                                R.drawable.ic_favorite
                            )
                            withContext(Dispatchers.IO) {
                                AddToFavourites(movieId, typeCheck, true, mActivity).addToFavourites()
                            }
                            binding.favouriteButtonTmdb.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                } else {
                    Toast.makeText(applicationContext,
                        getString(R.string.account_id_fail_try_login_again), Toast.LENGTH_SHORT).show()
                    binding.favouriteButtonTmdb.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }

        binding.ratingBtnTmdb.setOnClickListener {
            val dialog = BottomSheetDialog(mActivity)
            val dialogViewBinding = RatingDialogBinding.inflate(layoutInflater)
            dialog.setContentView(dialogViewBinding.root)
            dialog.show()

            val ratingBar = dialogViewBinding.ratingSlider
            val submitButton = dialogViewBinding.btnSubmit
            val cancelButton = dialogViewBinding.btnCancel
            val deleteButton = dialogViewBinding.btnDelete
            val movieTitle = dialogViewBinding.tvTitle
            movieTitle.text = showTitle
            lifecycleScope.launch {
                val typeCheck = if (isMovie) "movie" else "tv"
                val getAccountState = GetAccountState(movieId, typeCheck, mActivity)

                withContext(Dispatchers.IO) {
                    getAccountState.fetchAccountState()
                }

                val previousRating = getAccountState.rating
                ratingBar.value = previousRating.toFloat()

                submitButton.setOnClickListener {
                    lifecycleScope.launch {
                        val type = if (isMovie) "movie" else "tv"
                        val rating = ratingBar.value.toDouble()
                        withContext(Dispatchers.IO) {
                            AddRating(movieId, rating, type, mActivity).addRating()
                        }
                        submitButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        dialog.dismiss()
                    }
                }

                deleteButton.setOnClickListener {
                    lifecycleScope.launch {
                        val type = if (isMovie) "movie" else "tv"
                        withContext(Dispatchers.IO) {
                            DeleteRating(movieId, type, mActivity).deleteRating()
                        }
                        deleteButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        dialog.dismiss()
                    }
                }

                cancelButton.setOnClickListener { dialog.dismiss() }
            }
        }
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.setPackage("com.android.chrome")
        CustomTabsClient.bindCustomTabsService(
            mActivity,
            "com.android.chrome",
            object : CustomTabsServiceConnection() {
                override fun onCustomTabsServiceConnected(
                    componentName: ComponentName,
                    customTabsClient: CustomTabsClient
                ) {
                    customTabsClient.warmup(0L)
                }

                override fun onServiceDisconnected(name: ComponentName) {}
            })
        binding.fab.setOnClickListener(object : View.OnClickListener {
            val typeCheck = if (isMovie) "movie" else "tv"
            override fun onClick(view: View) {
                val tmdbLink = "https://www.themoviedb.org/$typeCheck/$movieId"
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.setType("text/plain")
                shareIntent.putExtra(Intent.EXTRA_TEXT, tmdbLink)
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_link_using)))
            }
        })
        if (!isMovie) {
            binding.revenueText.visibility = View.GONE
            binding.revenueDataText.visibility = View.GONE
        }

        if (isMovie) {
            binding.episodeViewPager.visibility = View.GONE
            binding.allEpisodeBtn.visibility = View.GONE
            binding.episodeText.visibility = View.GONE
        }
        binding.allEpisodeBtn.setOnClickListener {
            binding.shimmerFrameLayout1.visibility = View.VISIBLE
            binding.shimmerFrameLayout1.startShimmer()

            lifecycleScope.launch {
                val traktId = fetchTraktId(movieId, imdbId)
                binding.shimmerFrameLayout1.visibility = View.GONE
                binding.shimmerFrameLayout1.stopShimmer()

                if (traktId == null) {
                    Toast.makeText(applicationContext, "Trakt ID not found", Toast.LENGTH_SHORT).show()
                }
                val iIntent = Intent(applicationContext, TVSeasonDetailsActivity::class.java)
                iIntent.putExtra("tvShowId", movieId)
                iIntent.putExtra("numSeasons", numSeason)
                iIntent.putExtra("tvShowName", showName)
                iIntent.putExtra("traktId", traktId)
                iIntent.putExtra("tmdbObject", movieDataObject.toString())
                startActivity(iIntent)
            }
        }
        binding.moreImageBtn.setOnClickListener {
            val imageintent = Intent(applicationContext, MovieImageActivity::class.java)
            imageintent.putExtra("movieId", movieId)
            imageintent.putExtra("isMovie", isMovie)
            startActivity(imageintent)
        }
        binding.watchProvider.setOnClickListener {
            val typeCheck = if (isMovie) "movie" else "tv"
            val url =
                "https://www.themoviedb.org/" + typeCheck + "/" + movieId + "/watch?locale=" + Locale.getDefault().country
            val builder = CustomTabsIntent.Builder()
            val customTabIntent = builder.build()
            if (customTabIntent.intent.resolveActivity(packageManager) != null) {
                customTabIntent.launchUrl(this, Uri.parse(url))
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            }
        }

        binding.fabSave.setOnClickListener {
            if (added) {

                // Remove the show from the database.
                database.delete(
                    MovieDatabaseHelper.TABLE_MOVIES,
                    MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null
                )

                // Remove all episodes related to the show from the database.
                database.delete(
                    MovieDatabaseHelper.TABLE_EPISODES,
                    MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null
                )

                added = false
                binding.fabSave.setImageResource(R.drawable.ic_star_border)
                databaseUpdate()
                binding.fabSave.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                finish()
            } else {
                val showValues = ContentValues()

                // Only show the dialog if the user specified so in the preferences.
                if (preferences.getBoolean(SHOW_SAVE_DIALOG_PREFERENCE, false)) {

                    // Ask in which category the show should be placed.
                    val categoriesDialog = MaterialAlertDialogBuilder(this)
                    categoriesDialog.setTitle(getString(R.string.category_picker))
                    categoriesDialog.setItems(R.array.categories) { _: DialogInterface?, which: Int ->
                        showValues.put(
                            MovieDatabaseHelper.COLUMN_CATEGORIES,
                            getCategoryNumber(which)
                        )

                        // Add the show to the database
                        addMovieToDatabase(showValues)

                        // If the selected category is CATEGORY_WATCHED, add seasons and episodes to the database
                        if (getCategoryNumber(which) == MovieDatabaseHelper.CATEGORY_WATCHED) {
                            addSeasonsAndEpisodesToDatabase()
                            lifecycleScope.launch {
                                if (!isMovie) {
                                    updateEpisodeFragments()
                                }
                            }
                        }
                        binding.fabSave.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        binding.editIcon.visibility = View.VISIBLE
                    }
                    categoriesDialog.show()
                } else {
                    // If no dialog is shown, add the show to the default category.
                    showValues.put(
                        MovieDatabaseHelper.COLUMN_CATEGORIES,
                        MovieDatabaseHelper.CATEGORY_WATCHING
                    )
                    addMovieToDatabase(showValues)
                    binding.fabSave.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    binding.editIcon.visibility = View.VISIBLE
                }
            }
        }
        val searchEngineUrl =
            preferences.getString(SEARCH_ENGINE_PREFERENCE, "https://www.google.com/search?q=")
        binding.searchBtn.setOnClickListener {
            val title = if (jMovieObject.has("title")) "title" else "name"
            val url = searchEngineUrl + jMovieObject.optString(title)
            val builder = CustomTabsIntent.Builder()
            val customTabIntent = builder.build()
            if (customTabIntent.intent.resolveActivity(packageManager) != null) {
                customTabIntent.launchUrl(this, Uri.parse(url))
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            }
        }
        OnBackPressedDispatcher().addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val editShowDetails = binding.editShowDetails
                if (editShowDetails.visibility != View.GONE) {
                    // Clear the focus (in case it has the focus)
                    // so the content will be saved when the user leaves.
                    binding.categories.clearFocus()
                    binding.timesWatched.clearFocus()
                    binding.showRating.clearFocus()
                }
                setResult(RESULT_CANCELED)
                finish()
            }
        })

        isInCollection = TraktDatabaseHelper(context).use { db ->
            db.isMovieInCollection(movieId)
        }

        isInWatchlist = TraktDatabaseHelper(context).use { db ->
            db.isMovieInWatchlist(movieId)
        }

        isInFavourite = TraktDatabaseHelper(context).use { db ->
            db.isMovieInFavorite(movieId)
        }

        isInWatched = TraktDatabaseHelper(context).use { db ->
            db.isMovieInWatched(movieId)
        }

        isInRating = TraktDatabaseHelper(context).use { db ->
            db.isMovieInRating(movieId)
        }

        val rating = TraktDatabaseHelper(context).use { db ->
            db.getMovieRating(movieId)
        }

        binding.btnAddToTraktWatchlist.icon = if (isInWatchlist) {
            ContextCompat.getDrawable(context, R.drawable.ic_bookmark)
        } else {
            ContextCompat.getDrawable(context, R.drawable.ic_bookmark_border)
        }

        binding.btnAddToTraktFavorite.icon = if (isInFavourite) {
            ContextCompat.getDrawable(context, R.drawable.ic_favorite)
        } else {
            ContextCompat.getDrawable(context, R.drawable.ic_favorite_border)
        }

        binding.btnAddToTraktCollection.icon = if (isInCollection) {
            ContextCompat.getDrawable(context, R.drawable.ic_collection)
        } else {
            ContextCompat.getDrawable(context, R.drawable.ic_collection_border)
        }

        binding.btnAddTraktRating.icon = if (isInRating) {
            ContextCompat.getDrawable(context, R.drawable.ic_thumb_up)
        } else {
            ContextCompat.getDrawable(context, R.drawable.ic_thumb_up_border)
        }

        if (isInWatched) {
            binding.btnAddToTraktHistory.icon = ContextCompat.getDrawable(context, R.drawable.ic_done_2)
            binding.btnAddToTraktHistory.text = getString(R.string.history)
        } else {
            binding.btnAddToTraktHistory.icon = ContextCompat.getDrawable(context, R.drawable.ic_history)
            binding.btnAddToTraktHistory.text = getString(R.string.history)

        }

        binding.editIcon.setOnClickListener {
            editDetails()
        }

        binding.btnAddToTraktHistory.setOnClickListener {
            showWatchOptionsDialog()
        }

        binding.btnAddToTraktCollection.setOnClickListener {
            showCollectionDialog()
        }

        binding.btnAddToTraktWatchlist.setOnClickListener {
            if (isInWatchlist) {
                syncTraktData("sync/watchlist/remove", 0, "", null, null, null, null, null, null, null)
                syncTraktData("sync/watchlist/remove", 0, "", null, null, null, null, null, null, null)
            } else {
                syncTraktData("sync/watchlist", 0, "", null, null, null, null, null, null, null)
            }
        }

        binding.btnAddToTraktFavorite.setOnClickListener {
            if (isInFavourite) {
                syncTraktData("sync/favorites/remove", 0, "", null, null, null, null, null, null, null)
            } else {
                syncTraktData("sync/favorites", 0, "", null, null, null, null, null, null, null)
            }
        }

        binding.btnAddTraktRating.setOnClickListener {
            showRateOptionDialog()
        }

        binding.btnAddToTraktList.setOnClickListener {
            val typeCheck = if (isMovie) "movie" else "tv"
            val jsonBody = JSONObject().apply {
                if (isMovie) {
                    put("movies", JSONArray().apply { put(traktMediaObject) })
                } else {
                    put("shows", JSONArray().apply { put(traktMediaObject) })
                }
            }
            val listBottomSheetFragmentTkt = ListBottomSheetFragmentTkt(movieId, mActivity, true, typeCheck, jsonBody, movieDataObject)
            listBottomSheetFragmentTkt.show(supportFragmentManager, listBottomSheetFragmentTkt.tag)
        }

        val rottenTomatoesUrl = "https://www.rottentomatoes.com/search?search=$movieTitle $movieYear"
        val metacriticUrl = "https://www.metacritic.com/search/all/$movieTitle $movieYear/results"

        binding.rottenTomatoesRatingChip.setOnClickListener {
            launchUrl(context, rottenTomatoesUrl)
        }

        binding.metacriticRatingChip.setOnClickListener {
            launchUrl(context, metacriticUrl)
        }
    }

    private fun launchUrl(context: Context, url: String) {
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        if (customTabsIntent.intent.resolveActivity(context.packageManager) != null) {
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } else {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (browserIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(browserIntent)
            } else {
                Toast.makeText(context, R.string.no_browser_available, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCollectionDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogViewBinding = CollectionDialogTraktBinding.inflate(layoutInflater)
        dialog.setContentView(dialogViewBinding.root)
        dialog.show()

        val mediaTypes = resources.getStringArray(R.array.media_types)
        val resolutions = resources.getStringArray(R.array.resolutions)
        val hdrTypes = resources.getStringArray(R.array.hdr_types)
        val audioTypes = resources.getStringArray(R.array.audio_types)
        val audioChannels = resources.getStringArray(R.array.audio_channels)

        val mediaTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mediaTypes)
        val resolutionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, resolutions)
        val hdrAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, hdrTypes)
        val audioAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, audioTypes)
        val audioChannelsAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, audioChannels)

        dialogViewBinding.mediaType.setAdapter(mediaTypeAdapter)
        dialogViewBinding.resolution.setAdapter(resolutionAdapter)
        dialogViewBinding.hdr.setAdapter(hdrAdapter)
        dialogViewBinding.audio.setAdapter(audioAdapter)
        dialogViewBinding.audioChannels.setAdapter(audioChannelsAdapter)

        dialogViewBinding.tvTitle.text = showTitle
        dialogViewBinding.progressIndicator.visibility = View.VISIBLE
        lifecycleScope.launch {
            val collectionDetails = TraktDatabaseHelper(context).use { db ->
                db.getMovieCollectionDetails(movieId)
            }

            withContext(Dispatchers.Main) {
                dialogViewBinding.progressIndicator.visibility = View.GONE
                if (collectionDetails != null) {
                    dialogViewBinding.isCollected.visibility = View.VISIBLE
                    dialogViewBinding.collectedCard.visibility = View.VISIBLE
                    dialogViewBinding.etSelectedDate.setText(collectionDetails.collectedAt)
                    dialogViewBinding.mediaType.setText(collectionDetails.mediaType, false)
                    dialogViewBinding.resolution.setText(collectionDetails.resolution, false)
                    dialogViewBinding.hdr.setText(collectionDetails.hdr, false)
                    dialogViewBinding.audio.setText(collectionDetails.audio, false)
                    dialogViewBinding.audioChannels.setText(collectionDetails.audioChannels, false)
                    dialogViewBinding.switch3D.isChecked = collectionDetails.thd == 1
                } else {
                    dialogViewBinding.isCollected.visibility = View.GONE
                    dialogViewBinding.collectedCard.visibility = View.GONE
                }
            }
        }

        dialogViewBinding.removeCollection.setOnClickListener {
            val dialogBuilder = MaterialAlertDialogBuilder(this)
            dialogBuilder.setTitle(getString(R.string.remove_from_collection))
            dialogBuilder.setMessage(getString(R.string.remove_from_collection_confirmation))
            dialogBuilder.setPositiveButton(getString(R.string.yes)) { _, _ ->
                syncTraktData("sync/collection/remove", 0, "", null, null, null, null, null, null, null)
                dialog.dismiss()
            }
            dialogBuilder.setNegativeButton(getString(R.string.no)) { _, _ -> }
            dialogBuilder.show()
        }

        dialogViewBinding.btnSelectDate.setOnClickListener {
            showDatePicker { selectedDate ->
                dialogViewBinding.etSelectedDate.setText(selectedDate)
            }
        }

        dialogViewBinding.btnSave.setOnClickListener {
            val selectedDate = dialogViewBinding.etSelectedDate.text.toString()
            val mediaType = dialogViewBinding.mediaType.text.toString()
            val resolution = dialogViewBinding.resolution.text.toString()
            val hdr = dialogViewBinding.hdr.text.toString()
            val audio =dialogViewBinding.audio.text.toString()
            val audioChannel = dialogViewBinding.audioChannels.text.toString()
            val is3D = dialogViewBinding.switch3D.isChecked

            updateMediaObjectWithMetadata(selectedDate, mediaType, resolution, hdr, audio, audioChannel, is3D)
            dialog.dismiss()
        }
    }

    private fun updateMediaObjectWithMetadata(selectedDate: String?, mediaType: String?, resolution: String?, hdr: String?, audio: String?, audioChannels: String?, is3D: Boolean) {
        selectedDate?.takeIf { it.isNotEmpty() }?.let { traktMediaObject?.put("collected_at", it) }
        mediaType?.takeIf { it.isNotEmpty() }?.let { traktMediaObject?.put("media_type", it) }
        resolution?.takeIf { it.isNotEmpty() }?.let { traktMediaObject?.put("resolution", it) }
        hdr?.takeIf { it.isNotEmpty() }?.let { traktMediaObject?.put("hdr", it) }
        audio?.takeIf { it.isNotEmpty() }?.let { traktMediaObject?.put("audio", it) }
        audioChannels?.takeIf { it.isNotEmpty() }?.let { traktMediaObject?.put("audio_channels", it) }
        traktMediaObject?.put("3d", is3D)
        syncTraktData("sync/collection", 0, "", selectedDate, mediaType, resolution, hdr, audio, audioChannels, is3D)
    }

    private fun showWatchOptionsDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogViewBinding = HistoryDialogTraktBinding.inflate(layoutInflater)
        dialog.setContentView(dialogViewBinding.root)
        dialog.show()

        val movieTitle = dialogViewBinding.tvTitle
        val watchingNowButton = dialogViewBinding.btnWatchingNow
        val watchedAtReleaseButton = dialogViewBinding.btnWatchedAtRelease
        val selectDateButton = dialogViewBinding.btnSelectDate
        val selectedDateEditText = dialogViewBinding.etSelectedDate
        val updateButton = dialogViewBinding.btnSave
        val timesPlayed = dialogViewBinding.timePlayed
        val lastWatched = dialogViewBinding.lastWatched
        val historyCard = dialogViewBinding.historyCard
        val removeHistory = dialogViewBinding.removeHistory
        val progressBar = dialogViewBinding.progressIndicator

        movieTitle.text = showTitle

        if (!isMovie) {
            watchingNowButton.isEnabled = false
        }

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val watchedData = withContext(Dispatchers.IO) {
                val dbHelper = TraktDatabaseHelper(context)
                val timesPlayedD = dbHelper.getTimesPlayed(movieId)
                val lastWatchedD = dbHelper.getLastWatched(movieId)
                if (lastWatchedD != null) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
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
                lastWatched.text = getString(R.string.last_watched, watchedData.second)
            } else {
                timesPlayed.visibility = View.GONE
                lastWatched.visibility = View.GONE
                historyCard.visibility = View.GONE
            }
        }

        removeHistory.setOnClickListener {
            val dialogBuilder = MaterialAlertDialogBuilder(this)
            dialogBuilder.setTitle(getString(R.string.remove_from_history))
            dialogBuilder.setMessage(getString(R.string.remove_from_history_confirmation))
            dialogBuilder.setPositiveButton(getString(R.string.yes)) { _, _ ->
                syncTraktData("sync/history/remove", 0, "", null, null, null, null, null, null, null)
                dialog.dismiss()
            }
            dialogBuilder.setNegativeButton(getString(R.string.no)) { _, _ -> }
            dialogBuilder.show()
        }

        watchingNowButton.setOnClickListener {
            traktCheckin("/checkin")
            dialog.dismiss()
        }

        watchedAtReleaseButton.setOnClickListener {
            updateMediaObjectWithWatchedAt("released")
            dialog.dismiss()
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
                updateMediaObjectWithWatchedAt(selectedDate)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please select a date and time first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRateOptionDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogViewBinding = RatingDialogTraktBinding.inflate(layoutInflater)
        dialog.setContentView(dialogViewBinding.root)
        dialog.show()

        val ratingSlider = dialogViewBinding.ratingSlider
        val submitButton = dialogViewBinding.btnSubmit
        val cancelButton = dialogViewBinding.btnCancel
        val deleteButton = dialogViewBinding.btnDelete
        val movieTitle = dialogViewBinding.tvTitle
        val ratedAt = dialogViewBinding.ratedDate
        val progressIndicator = dialogViewBinding.progressIndicator
        val selectDateButton = dialogViewBinding.btnSelectDate

        movieTitle.text = showTitle
        progressIndicator.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            val rating = TraktDatabaseHelper(context).use { db ->
                db.getMovieRating(movieId)
            }

            withContext(Dispatchers.Main) {
                progressIndicator.visibility = View.GONE
                ratingSlider.value = rating.toFloat()
            }
        }

        val currentDateTime = android.icu.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.getDefault()
        ).format(Date())
        ratedAt.setText(currentDateTime)

        selectDateButton.setOnClickListener {
            showDatePicker { selectedDate ->
                ratedAt.setText(selectedDate)
            }
        }

        submitButton.setOnClickListener {
            val rating = ratingSlider.value.toInt()
            val selectedDate = ratedAt.text.toString()
            if (selectedDate.isNotEmpty()) {
                updateMediaObjectWithRating(rating, selectedDate)
                dialog.dismiss()
            } else {
                syncTraktData("sync/watchlist/remove", 0, "", null, null, null, null, null, null, null)
                Toast.makeText(this, getString(R.string.please_select_a_date), Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        deleteButton.setOnClickListener {
            syncTraktData("sync/ratings/remove", 0, "", null, null, null, null, null, null, null)
            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val builder = MaterialDatePicker.Builder.datePicker()
        builder.setTitleText(getString(R.string.select_a_date))
        val datePicker = builder.build()
        datePicker.show(supportFragmentManager, datePicker.toString())
        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selection

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(calendar.get(Calendar.HOUR_OF_DAY))
                .setMinute(calendar.get(Calendar.MINUTE))
                .setTitleText(getString(R.string.select_time))
                .build()
            timePicker.show(supportFragmentManager, timePicker.toString())

            timePicker.addOnPositiveButtonClickListener {
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(Calendar.MINUTE, timePicker.minute)

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val selectedDateTime = sdf.format(calendar.time)
                onDateSelected(selectedDateTime)
            }
        }
    }

    private fun updateMediaObjectWithWatchedAt(watchedAt: String) {
        traktMediaObject?.put("watched_at", watchedAt)
        syncTraktData("sync/history", 0, watchedAt, null, null, null, null, null, null, null)
    }

    private fun updateMediaObjectWithRating(rating: Int, ratedAt: String) {
        traktMediaObject?.put("rated_at", ratedAt)
        traktMediaObject?.put("rating", rating)
        syncTraktData("sync/ratings", rating, "", null, null, null, null, null, null, null)
    }
    private fun syncTraktData(endpoint: String, rating: Int, watchedAt: String, collectedAt: String?, mediaType: String?, resolution: String?, hdr: String?, audio: String?, audioChannels: String?, is3D: Boolean?) {
        val traktApiService = TraktSync(tktaccessToken!!)
        val jsonBody = JSONObject().apply {
            if (isMovie) {
                put("movies", JSONArray().apply { put(traktMediaObject) })
            } else {
                put("shows", JSONArray().apply { put(traktMediaObject) })
            }
        }
        traktApiService.post(endpoint, jsonBody, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@DetailActivity, getString(R.string.failed_to_sync, endpoint), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    val message = if (response.isSuccessful) {
                        updateTraktButtonsUI(endpoint)
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                handleDatabaseUpdate(endpoint, rating, watchedAt, collectedAt, mediaType, resolution, hdr, audio, audioChannels, is3D)
                                addItemtoTmdb()
                                updateBoolean(endpoint)
                            }
                        }
                        getString(R.string.success)
                    } else {
                        response.message
                    }
                    Toast.makeText(this@DetailActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun updateBoolean(endpoint: String) {
        when (endpoint) {
            "sync/watchlist" -> isInWatchlist = true
            "sync/watchlist/remove" -> isInWatchlist = false
            "sync/favorites" -> isInFavourite = true
            "sync/favorites/remove" -> isInFavourite = false
            "sync/collection" -> isInCollection = true
            "sync/collection/remove" -> isInCollection = false
            "sync/ratings" -> isInRating = true
            "sync/ratings/remove" -> isInRating = false
            "sync/history" -> isInWatched = true
            "sync/history/remove" -> isInWatched = false
        }
    }

    private fun addItemtoTmdb() {
        val dbHelper = TmdbDetailsDatabaseHelper(context)
        val tmdbId = movieDataObject.optInt("id")
        val name = if (isMovie) movieDataObject.optString("title") else movieDataObject.optString("name")
        val backdropPath = movieDataObject.optString("backdrop_path")
        val posterPath = movieDataObject.optString("poster_path")
        val summary = movieDataObject.optString("overview")
        val voteAverage = movieDataObject.optDouble("vote_average")
        val type = if (isMovie) "movie" else "show"
        val releaseDate = if (isMovie) movieDataObject.optString("release_date") else movieDataObject.optString("first_air_date")
        val genreIds = movieDataObject.optJSONArray("genres")?.let { genresArray ->
            val ids = (0 until genresArray.length()).joinToString(",") { i ->
                genresArray.getJSONObject(i).getInt("id").toString()
            }
            "[$ids]"
        }
        val seasonEpisodeCount = movieDataObject.optJSONArray("seasons")
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

    private fun updateTraktButtonsUI(endpoint: String) {
        when (endpoint) {
            "sync/watchlist" -> {
                binding.btnAddToTraktWatchlist.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_bookmark
                )
            }
            "sync/watchlist/remove" -> {
                binding.btnAddToTraktWatchlist.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_bookmark_border
                )
            }
            "sync/favorites" -> {
                binding.btnAddToTraktFavorite.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_favorite
                )
            }
            "sync/favorites/remove" -> {
                binding.btnAddToTraktFavorite.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_favorite_border
                )
            }
            "sync/collection" -> {
                binding.btnAddToTraktCollection.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_collection
                )
            }
            "sync/collection/remove" -> {
                binding.btnAddToTraktCollection.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_collection_border
                )
            }
            "sync/ratings" -> {
                binding.btnAddTraktRating.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_thumb_up
                )
            }
            "sync/ratings/remove" -> {
                binding.btnAddTraktRating.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_thumb_up_border
                )
            }
            "sync/history" -> {
                binding.btnAddToTraktHistory.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_done_2
                )
                binding.btnAddToTraktHistory.text = getString(R.string.history)
            }
            "sync/history/remove" -> {
                binding.btnAddToTraktHistory.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_history
                )
                binding.btnAddToTraktHistory.text = getString(R.string.history)
            }
        }
    }

    private fun handleDatabaseUpdate(endpoint: String, rating: Int, watchedAt: String, collectedAt: String?, mediaType: String?, resolution: String?, hdr: String?, audio: String?, audioChannels: String?, is3D: Boolean?) {
        val dbHelper = TraktDatabaseHelper(context)
        val movieTitle = if (isMovie) movieDataObject.optString("title") else movieDataObject.optString("name")
        val tmdbId = movieDataObject.optInt("id")
        val type = if (isMovie) "movie" else "show"
        val watchedAtN = if (watchedAt == "released") {
            if (isMovie) {
                movieDataObject.optString("release_date")
            } else {
                movieDataObject.optString("first_air_date")
            }
        } else {
            watchedAt
        }

        when (endpoint) {
            "sync/watchlist" -> dbHelper.addMovieToWatchlist(movieTitle, type, tmdbId)
            "sync/watchlist/remove" -> dbHelper.removeMovieFromWatchlist(tmdbId)
            "sync/favorites" -> dbHelper.addMovieToFavorites(movieTitle, type, tmdbId)
            "sync/favorites/remove" -> dbHelper.removeMovieFromFavorites(tmdbId)
            "sync/collection" -> dbHelper.addMovieToCollection(movieTitle, type, tmdbId, collectedAt, mediaType, resolution, hdr, audio, audioChannels, is3D)
            "sync/collection/remove" -> dbHelper.removeFromCollection(tmdbId)
            "sync/history" -> {
                dbHelper.addMovieToHistory(movieTitle, type, tmdbId)
                dbHelper.addMovieToWatched(movieTitle, type, tmdbId, watchedAtN)
            }
            "sync/history/remove" -> {
                dbHelper.removeMovieFromHistory(tmdbId)
                dbHelper.removeMovieFromWatched(tmdbId)
            }
            "sync/ratings" -> dbHelper.addMovieRating(movieTitle, type, tmdbId, rating)
            "sync/ratings/remove" -> dbHelper.removeMovieRating(tmdbId)
        }
    }
    private fun traktCheckin(endpoint: String) {
        val traktApiService = TraktSync(tktaccessToken!!)
        val jsonBody = traktCheckingObject ?: JSONObject()
        traktApiService.post(endpoint, jsonBody, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@DetailActivity, getString(R.string.failed_to_sync, endpoint), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    val message = if (response.isSuccessful) {
                        updateTraktButtonsUI("sync/history")
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                handleDatabaseUpdate("sync/history", 0, "", null, null, null, null, null, null, null)
                            }
                        }
                        getString(R.string.success)
                    } else {
                        response.message
                    }
                    Toast.makeText(this@DetailActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private suspend fun fetchTraktId(tmdbId: Int, imdbId: String?): Int? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()

            var url = "https://api.trakt.tv/search/tmdb/$tmdbId?type=show"
            var request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("trakt-api-version", "2")
                .addHeader("trakt-api-key", tktApiKey ?: "")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonArray = JSONArray(response.body!!.string())
                    if (jsonArray.length() > 0) {
                        return@withContext jsonArray.getJSONObject(0).getJSONObject("show").getJSONObject("ids").getInt("trakt")
                    }
                }
            }

            if (!imdbId.isNullOrEmpty()) {
                url = "https://api.trakt.tv/search/imdb/$imdbId?type=show"
                request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("trakt-api-version", "2")
                    .addHeader("trakt-api-key", tktApiKey ?: "")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonArray = JSONArray(response.body!!.string())
                        if (jsonArray.length() > 0) {
                            return@withContext jsonArray.getJSONObject(0).getJSONObject("show").getJSONObject("ids").getInt("trakt")
                        }
                    }
                }
            }

            return@withContext null
        }
    }


    private suspend fun fetchMovieDetailsByExternalId(externalId: String, type: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val url = "https://api.themoviedb.org/3/find/$externalId?external_source=$type"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer $apiReadAccessToken")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body!!.string()
                    val jsonObject = JSONObject(responseBody)
                    Log.d("DetailActivity", jsonObject.toString())
                    val results = if (type == "imdb_id") jsonObject.getJSONArray("movie_results") else jsonObject.getJSONArray("tv_results")
                    if (results.length() > 0) {
                        return@withContext results.getJSONObject(0)
                    }
                }
            }
            return@withContext null
        }
    }

    private suspend fun fetchMovieDetailsByTitleAndYear(type: String, title: String, year: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val url = "https://api.themoviedb.org/3/search/$type?query=$title&year=$year"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer $apiReadAccessToken")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body!!.string()
                    val jsonObject = JSONObject(responseBody)
                    val results = jsonObject.getJSONArray("results")
                    if (results.length() > 0) {
                        return@withContext results.getJSONObject(0)
                    }
                }
            }
            return@withContext null
        }
    }

    override fun doNetworkWork() {
        // Get the cast and crew for the CastListAdapter and get the movies for the MovieListAdapter.
        if (!mCastAndCrewLoaded) {
            lifecycleScope.launch {
                fetchCastList()
            }
        }
        if (!mSimilarMoviesLoaded) {
            startSimilarMovieList()
        }

        if (!mMovieDetailsLoaded) {
            fetchMovieDetailsCoroutine()
        }
        if (!mVideosLoaded) {
            lifecycleScope.launch {
                fetchVideos()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (!isMovie) {
                updateMovieEpisodes()
            }
            updateEpisodeFragments()
        }
    }

    private fun saveTotalEpisodes(totalEpisodes: Int) {
        val sharedPreferences = getSharedPreferences("totalEpisodes", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("totalEpisodes_$movieId", totalEpisodes)
        editor.commit()
    }

    private suspend fun updateMovieEpisodes() {
        val sharedPreferences = getSharedPreferences("totalEpisodes", Context.MODE_PRIVATE)
        withContext(Dispatchers.IO) {
            seenEpisode = databaseHelper.getSeenEpisodesCount(movieId)
            totalEpisodes = sharedPreferences.getInt("totalEpisodes_$movieId", 0)
        }

        withContext(Dispatchers.Main) {
            if (seenEpisode != 0) {
                binding.movieEpisodes.text = getString(R.string.episodes_seen, seenEpisode, totalEpisodes ?: 0)
                binding.movieEpisodes.visibility = View.VISIBLE
            }
        }
    }


    private suspend fun updateEpisodeFragments() {
        withContext(Dispatchers.Main) {
            if (!isMovie) {
                // Clear the adapter's fragments before adding new ones or it duplicates them.
                episodePagerAdapter.clearFragments()
                val lastEpisodeLocal = lastEpisode
                if (lastEpisodeLocal is JSONObject) {
                    episodePagerAdapter.addFragment(newInstance(lastEpisodeLocal, "Latest Episode"), 0)
                }
                val nextEpisodeLocal = nextEpisode
                if (nextEpisodeLocal is JSONObject) {
                    episodePagerAdapter.addFragment(newInstance(nextEpisodeLocal, "Next Episode"), 1)
                }
                episodeViewPager.adapter = episodePagerAdapter
            }
        }
    }

    private fun addSeasonsAndEpisodesToDatabase() {
        if (!isMovie && seasons != null) {
            try {
                val firstSeasonNumber = seasons!!.getJSONObject(0).getInt("season_number")
                val startSeasonIndex = if (firstSeasonNumber == 0) 1 else 0

                for (i in startSeasonIndex until seasons!!.length()) {
                    val season = seasons!!.getJSONObject(i)
                    val seasonNumber = season.getInt("season_number")
                    val episodeCount = season.getInt("episode_count")
                    for (j in 1..episodeCount) {
                        val cursor = database.rawQuery(
                            "SELECT * FROM ${MovieDatabaseHelper.TABLE_EPISODES} WHERE " +
                                    "${MovieDatabaseHelper.COLUMN_MOVIES_ID} = ? AND " +
                                    "${MovieDatabaseHelper.COLUMN_SEASON_NUMBER} = ? AND " +
                                    "${MovieDatabaseHelper.COLUMN_EPISODE_NUMBER} = ?",
                            arrayOf(movieId.toString(), seasonNumber.toString(), j.toString())
                        )
                        if (cursor.count == 0) {
                            val values = ContentValues().apply {
                                put(MovieDatabaseHelper.COLUMN_MOVIES_ID, movieId)
                                put(MovieDatabaseHelper.COLUMN_SEASON_NUMBER, seasonNumber)
                                put(MovieDatabaseHelper.COLUMN_EPISODE_NUMBER, j)
                            }
                            val newRowId = database.insert(MovieDatabaseHelper.TABLE_EPISODES, null, values)
                            if (newRowId == -1L) {
                                Toast.makeText(
                                    this,
                                    R.string.error_adding_episode_to_database,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        cursor.close()
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Adds all necessary values to the ContentValues
     * and then inserts it into the database.
     *
     * @param showValues the ContentValuese with the already specified values.
     */
    private fun addMovieToDatabase(showValues: ContentValues) {
        // Add the show to the database.
        try {
            // Put the necessary values into ContentValues object.
            showValues.put(MovieDatabaseHelper.COLUMN_MOVIES_ID, jMovieObject.getString("id").toInt())
            showValues.put(MovieDatabaseHelper.COLUMN_IMAGE, jMovieObject.getString("backdrop_path"))
            showValues.put(MovieDatabaseHelper.COLUMN_ICON, jMovieObject.getString("poster_path"))
            val title = if (isMovie) "title" else "name"
            showValues.put(MovieDatabaseHelper.COLUMN_TITLE, jMovieObject.getString(title))
            showValues.put(MovieDatabaseHelper.COLUMN_SUMMARY, jMovieObject.getString("overview"))
            showValues.put(MovieDatabaseHelper.COLUMN_GENRES, genres)
            showValues.put(MovieDatabaseHelper.COLUMN_GENRES_IDS, jMovieObject.getString("genre_ids"))
            showValues.put(MovieDatabaseHelper.COLUMN_MOVIE, isMovie)
            showValues.put(MovieDatabaseHelper.COLUMN_RATING, jMovieObject.getString("vote_average"))
            val releaseDate = if (isMovie) "release_date" else "first_air_date"
            showValues.put(MovieDatabaseHelper.COLUMN_RELEASE_DATE, jMovieObject.getString(releaseDate))

            // Insert the show into the database.
            database.insert(MovieDatabaseHelper.TABLE_MOVIES, null, showValues)

            // Inform the user of the addition to the database
            // and change the boolean in order to change the MenuItem's behaviour.
            added = true
            binding.fabSave.setImageResource(R.drawable.ic_star)
            if (isMovie) {
                Toast.makeText(applicationContext, resources.getString(R.string.movie_added), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, resources.getString(R.string.series_added), Toast.LENGTH_SHORT).show()
            }
        } catch (je: JSONException) {
            je.printStackTrace()
            Toast.makeText(this, resources.getString(R.string.show_added_error), Toast.LENGTH_SHORT).show()
        }

        // Update the ListFragment to include the newly added show.
        databaseUpdate()
    }

    /**
     * Checks if the current data is different than the data from the
     * specified JSONObject. If that's the case, replace the data with the
     * data from the specified JSONObject.
     *
     * @param movieObject JSONObject with the information about the show.
     */
    private fun setMovieData(movieObject: JSONObject) {

        lifecycleScope.launch {
            if (!movieObject.has("id")) {
                val externalId = movieObject.optString("imdb")
                var fetchedMovieObject = fetchMovieDetailsByExternalId(externalId, "imdb_id")

                if (fetchedMovieObject == null && !isMovie) {
                    val tvdbId = movieObject.optString("tvdb")
                    fetchedMovieObject = fetchMovieDetailsByExternalId(tvdbId, "tvdb_id")
                }

                if (fetchedMovieObject == null) {
                    val type = if (isMovie) "movie" else "tv"
                    val title = movieObject.optString("title")
                    val year = movieObject.optString("year")
                    fetchedMovieObject = fetchMovieDetailsByTitleAndYear(type, title, year)
                }

                if (fetchedMovieObject != null) {
                    if (fetchedMovieObject.has("id")) {
                        movieId = fetchedMovieObject.getInt("id")

                        if (!mCastAndCrewLoaded) {
                            lifecycleScope.launch {
                                fetchCastList()
                            }
                        }
                        if (!mSimilarMoviesLoaded) {
                            startSimilarMovieList()
                        }

                        if (!mMovieDetailsLoaded) {
                            fetchMovieDetailsCoroutine()
                        }
                        if (!mVideosLoaded) {
                            lifecycleScope.launch {
                                fetchVideos()
                            }
                        }
                    }
                    return@launch
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@DetailActivity,
                            getString(R.string.media_details_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
            }
        }

        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
        val imageSize = if (loadHDImage) "w780" else "w500"

        // Check if movieObject values differ from the current values,
        // if they do, use the movieObject values.
        try {
            // Set the movieId
            movieId = movieObject.getString("id").toInt()

            // Due to the difficulty of comparing images (or rather,
            // this can be a really slow process) the id of the image is
            // saved as class variable for easy comparison.
            if (movieObject.has("poster_path") && binding.moviePoster.drawable == null) {
                Picasso.get().load(
                    "https://image.tmdb.org/t/p/" + imageSize +
                            movieObject.getString("poster_path")
                )
                    .into(binding.moviePoster)

                // Set the old posterId to the new one.
                movieObject.getString("poster_path")
            }

            // Check if it is a movie or a TV series.
            val title = if (movieObject.has("title")) "title" else "name"
            movieTitle = if (isMovie) movieObject.getString("title") else movieObject.getString("name")
            movieYear = if (movieObject.has("release_date")) movieObject.getString("release_date").substring(0, 4) else movieObject.getString("first_air_date").substring(0, 4)
            showName = movieObject.optString("name")
            if (movieObject.has(title) &&
                movieObject.getString(title) != binding.movieTitle
                    .text.toString()
            ) {
                binding.movieTitle.text = movieObject.getString(title)

                // Initialise global variables (will become visible when scrolling down).
                showTitle = SpannableString(movieObject.getString(title))
            }

            // The rating also uses a class variable for the same reason
            // as the image.
            databaseHelper = MovieDatabaseHelper(applicationContext)
            database = databaseHelper.writableDatabase
            databaseHelper.onCreate(database)

            // Retrieve and present saved data of the show.
            val cursor = database.rawQuery(
                "SELECT * FROM " +
                        MovieDatabaseHelper.TABLE_MOVIES +
                        " WHERE " + MovieDatabaseHelper.COLUMN_MOVIES_ID +
                        "=" + movieId + " LIMIT 1", null
            )
            if (cursor.count > 0) {
                cursor.moveToFirst()
                // Set the rating to the personal rating of the user.
                val localizedTen = String.format(Locale.getDefault(), "%d", 10)
                binding.movieRating.text = getString(R.string.rating_format, cursor.getFloat(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING)), localizedTen)
                binding.movieRating.visibility = View.VISIBLE

                var dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                // If the database has a start date, use it, otherwise print unknown.
                val dateString = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE))
                if (dateString != null) {
                    dbDateFormat = if (dateString.indexOf('-') == 4) {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    } else {
                        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                    }
                    // Use dbDateFormat as needed
                } else {
                    // Handle the null case, e.g., set a default value or log a message
                    Log.e("DetailActivity", "dateString is null")
                }

                // Start Date
                if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE))
                    && cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)) != ""
                ) {
                    val startDateString =
                        cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE))
                    try {
                        val formattedStartDate = when {
                            startDateString.startsWith("00-00-") -> {
                                val year = startDateString.substring(6)
                                year
                            }
                            startDateString.startsWith("00-") -> {
                                val monthYear = startDateString.substring(3)
                                SimpleDateFormat("MM-yyyy", Locale.getDefault()).parse(monthYear)?.let {
                                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(it)
                                }
                            }
                            else -> {
                                dbDateFormat.parse(startDateString)?.let {
                                    DateFormat.getDateInstance(DateFormat.DEFAULT).format(it)
                                }
                            }
                        }
                        val startDateText = if (formattedStartDate != null) {
                            getString(R.string.start_date, formattedStartDate)
                        } else {
                            getString(R.string.start_date_unknown)
                        }
                        binding.movieStartDate.text = startDateText
                    } catch (e: ParseException) {
                        e.printStackTrace()
                        binding.movieStartDate.text = getString(R.string.start_date_unknown)
                    }
                } else {
                    binding.movieStartDate.text = getString(R.string.start_date_unknown)
                }

                // Finish Date
                if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE))
                    && cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)) != ""
                ) {
                    val finishDateString =
                        cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE))
                    try {
                        val formattedFinishDate = when {
                            finishDateString.startsWith("00-00-") -> {
                                val year = finishDateString.substring(6)
                                year
                            }
                            finishDateString.startsWith("00-") -> {
                                val monthYear = finishDateString.substring(3)
                                SimpleDateFormat("MM-yyyy", Locale.getDefault()).parse(monthYear)?.let {
                                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(it)
                                }
                            }
                            else -> {
                                dbDateFormat.parse(finishDateString)?.let {
                                    DateFormat.getDateInstance(DateFormat.DEFAULT).format(it)
                                }
                            }
                        }
                        val finishDateText = if (formattedFinishDate != null) {
                            getString(R.string.finish_date, formattedFinishDate)
                        } else {
                            getString(R.string.finish_date_unknown)
                        }
                        binding.movieFinishDate.text = finishDateText
                    } catch (e: ParseException) {
                        e.printStackTrace()
                        binding.movieFinishDate.text = getString(R.string.finish_date_unknown)
                    }
                } else {
                    binding.movieFinishDate.text = getString(R.string.finish_date_unknown)
                }

                // If the database has a rewatched count, use it, otherwise it is 0.
                var watched = 0
                if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED))
                    && cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED
                        )
                    ) != ""
                ) {
                    watched =
                        cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED))
                } else if (cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            MovieDatabaseHelper.COLUMN_CATEGORIES
                        )
                    ) == 1
                ) {
                    watched = 1
                }
                binding.movieRewatched.text = getString(R.string.times_watched, watched)
                if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE_REVIEW))) {
                    binding.movieReviewText.text = getString(R.string.reviews, cursor.getString(
                        cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE_REVIEW)))
                    binding.movieReviewText.visibility = View.VISIBLE
                } else {
                    binding.movieReviewText.text = getString(R.string.no_reviews)
                }

                if (!isMovie) {
                    // Get the total amount of episodes.
                    if (movieObject.has("number_of_episodes")) {
                        totalEpisodes = movieObject.getInt("number_of_episodes")
                    } else if (movieObject.has("seasons")) {
                        val seasonArray = movieObject.getJSONArray("seasons")
                        var episodeCount = 0
                        // Iterate through all seasons, including season 0.
                        for (i in 0 until seasonArray.length()) {
                            if (seasonArray[i] != null) {
                                episodeCount += (seasonArray[i] as JSONObject).getInt("episode_count")
                            }
                        }
                        totalEpisodes = episodeCount
                    }
                    seenEpisode = databaseHelper.getSeenEpisodesCount(movieId)
                    binding.movieEpisodes.text = getString(R.string.episodes_seen, seenEpisode, totalEpisodes)
                    binding.movieEpisodes.visibility = View.VISIBLE
                }

                // Make all the views visible (if the show is in the database).
                binding.movieStartDate.visibility = View.VISIBLE
                binding.movieFinishDate.visibility = View.VISIBLE
                binding.movieRewatched.visibility = View.VISIBLE
                binding.movieReviewText.visibility = View.VISIBLE

                // Make it possible to change the values.
                binding.editIcon.visibility = View.VISIBLE
            } else if (movieObject.has("vote_average") &&
                movieObject.getString("vote_average") != voteAverage
            ) {
                val voteAverage = movieObject.getString("vote_average").toFloat()
                val localizedTen = String.format(Locale.getDefault(), "%d", 10)
                binding.rating.text =
                    String.format(Locale.getDefault(), "%.2f/%s", voteAverage, localizedTen)
            }

            // If the overview (summary) is different in the new dataset, change it.
            if (movieObject.has("overview") &&
                movieObject.getString("overview") != binding.movieDescription
                    .text.toString() && movieObject.getString("overview") != "" && movieObject.getString(
                    "overview"
                ) != "Overview not available"
            ) {
                binding.movieDescription.text = movieObject.getString("overview")
            }

            // Set the genres
            if (movieObject.has("genre_ids")) {
                // This works a bit different,
                // the ids will be converted to genres first after that
                // the new text with genres will be compared to the old one.

                // Remove the [ and ] from the String
                val genreIds = movieObject.getString("genre_ids").substring(1, movieObject.getString("genre_ids").length - 1)

                // Split the String with the ids and set them into an array.
                val genreArray =
                    genreIds.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                // Get the sharedPreferences
                val sharedPreferences = applicationContext
                    .getSharedPreferences("GenreList", MODE_PRIVATE)

                // Add all the genres in one String.
                val genreNames = StringBuilder()
                binding.genreChipGroup.removeAllViews() // Clear previous chips if any

                val inflater = LayoutInflater.from(this)
                for (aGenreArray in genreArray) {
                    val genreName = sharedPreferences.getString(aGenreArray, aGenreArray)
                    genreNames.append(", ").append(genreName)

                    val chip = inflater.inflate(R.layout.genre_chip_item,  binding.genreChipGroup, false) as Chip
                    chip.text = genreName

                    binding.genreChipGroup.addView(chip)
                }
            }

            if (movieObject.has("popularity")) {
                val popularity = movieObject.getString("popularity").toDouble()
                val formattedPopularity = NumberFormat.getNumberInstance(Locale.getDefault()).format(popularity)
                binding.popularityText.text = formattedPopularity
            }

            if (movieObject.has("spoken_languages")) {
                val spokenLanguagesArray = movieObject.getJSONArray("spoken_languages")
                val languagesList = mutableListOf<String>()

                for (i in 0 until spokenLanguagesArray.length()) {
                    val languageObject = spokenLanguagesArray.getJSONObject(i)
                    val languageName = languageObject.getString("name")
                    languagesList.add(languageName)
                }

                val languagesText = languagesList.joinToString(", ")
                binding.languageText.text = languagesText
            }

            if (!isMovie) {
                val lastEpisode = movieObject.optJSONObject("last_episode_to_air")
                val nextEpisode = movieObject.optJSONObject("next_episode_to_air")

                // Clear the adapter's fragments before adding new ones or it duplicates them.
                episodePagerAdapter.clearFragments()
                if (lastEpisode != null) {
                    episodePagerAdapter.addFragment(newInstance(lastEpisode, "Latest Episode"), 0)
                }
                if (nextEpisode != null) {
                    episodePagerAdapter.addFragment(newInstance(nextEpisode, "Next Episode"), 1)
                }
                episodeViewPager.adapter = episodePagerAdapter
            }

            if (movieObject.has("tagline")) {
                val tagline = movieObject.getString("tagline")
                if (tagline != binding.tagline.text.toString()) {
                    binding.tagline.text = tagline
                    binding.tagline.visibility = View.VISIBLE
                }
            } else {
                binding.tagline.visibility = View.GONE
            }
            if (!isMovie) {
                if (movieObject.has("first_air_date")) {
                    val firstAirDateStr = movieObject.getString("first_air_date")
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val firstAirDate = sdf.parse(firstAirDateStr)
                        if (firstAirDate != null) {
                            val dateFormat =
                                DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                            val formattedDate = dateFormat.format(firstAirDate)
                            if (formattedDate != binding.releaseDate.text.toString()) {
                                binding.releaseDate.text = formattedDate
                            }
                        }
                    } catch (e: ParseException) {
                        e.printStackTrace()
                    }
                }
            }

            if (isMovie) {
                if (movieObject.has("runtime") && movieObject.getString("runtime") != binding.runtime.text.toString()) {
                    val totalMinutes = movieObject.getString("runtime").toInt()
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    val formattedRuntime =
                        String.format(Locale.getDefault(), "%dh %dm", hours, minutes)
                    binding.runtime.text = formattedRuntime
                }
            } else {
                if (movieObject.has("episode_run_time")) {
                    val episodeRuntimes = movieObject.getJSONArray("episode_run_time")
                    if (episodeRuntimes.length() > 0) {
                        val episodeRuntimeMinutes = episodeRuntimes.getInt(0)
                        val hours = episodeRuntimeMinutes / 60
                        val minutes = episodeRuntimeMinutes % 60
                        val formattedRuntime =
                            String.format(Locale.getDefault(), "%dh %dm", hours, minutes)
                        binding.runtime.text = formattedRuntime
                    } else {
                        binding.runtime.setText(R.string.unknown)
                    }
                } else {
                    binding.runtime.setText(R.string.unknown)
                }
            }
            if (movieObject.has("status") && movieObject.getString("status") != binding.statusDataText.text.toString()) {
                binding.statusDataText.text = movieObject.getString("status")
            }
            if (movieObject.has("production_countries")) {
                val productionCountries = movieObject.getJSONArray("production_countries")
                val countries = StringBuilder()
                for (i in 0 until productionCountries.length()) {
                    val country = productionCountries.getJSONObject(i)
                    countries.append(country.getString("name"))
                    if (i < productionCountries.length() - 1) {
                        countries.append(", ")
                    }
                }
                binding.countryDataText.text = countries.toString()
            }
            val formattedRevenue: String = if (movieObject.has("revenue")) {
                val revenue = movieObject.getString("revenue").toLong()
                formatCurrency(revenue)
            } else {
                getString(R.string.unknown)
            }
            val formattedBudget: String = if (movieObject.has("budget")) {
                val budget = movieObject.getString("budget").toLong()
                formatCurrency(budget)
            } else {
                getString(R.string.unknown)
            }
            val combinedText = "$formattedBudget / $formattedRevenue"
            binding.revenueDataText.text = combinedText
            cursor.close()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun formatCurrency(value: Long): String {
        return if (value >= 1000000000) {
            String.format(Locale.US, "$%.1fB", value / 1000000000.0)
        } else if (value >= 1000000) {
            String.format(Locale.US, "$%.1fM", value / 1000000.0)
        } else if (value >= 1000) {
            String.format(Locale.US, "$%.1fK", value / 1000.0)
        } else {
            String.format(Locale.US, "$%d", value)
        }
    }

    private suspend fun fetchVideos() {
        withContext(Dispatchers.IO) {
            try {
                val type = if (isMovie) SectionsPagerAdapter.MOVIE else SectionsPagerAdapter.TV
                val url = URL("https://api.themoviedb.org/3/$type/$movieId/videos?api_key=$apiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val stringBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line)
                }
                reader.close()
                val response = stringBuilder.toString()
                withContext(Dispatchers.Main) {
                    onVideoPostExecute(response)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun onVideoPostExecute(response: String) {
        try {
            val jsonObject = JSONObject(response)
            val results = jsonObject.getJSONArray("results")
            for (i in 0 until results.length()) {
                val video = results.getJSONObject(i)
                val type = video.getString("type")
                val site = video.getString("site")
                if (type == "Trailer") {
                    val key = video.getString("key")
                    val url: String = if (site == "YouTube") {
                        "https://www.youtube.com/watch?v=$key"
                    } else {
                        "https://www." + site.lowercase(Locale.getDefault()) + ".com/watch?v=" + key
                    }
                    binding.trailer.setOnClickListener {
                        if (url.isEmpty()) {
                            Toast.makeText(context,
                                getString(R.string.no_trailer_available), Toast.LENGTH_SHORT).show()
                        } else if (url.contains("youtube")) {
                            // Extract the video key from the URL if it's a YouTube video
                            val videoKey = url.substring(url.lastIndexOf("=") + 1)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoKey"))
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                // YouTube app is not installed, open the video in a custom Chrome tab
                                val builder = CustomTabsIntent.Builder()
                                val customTabsIntent = builder.build()
                                customTabsIntent.launchUrl(context, Uri.parse(url))
                            }
                        } else {
                            // If it's not a YouTube video, open it in a custom Chrome tab
                            val builder = CustomTabsIntent.Builder()
                            val customTabsIntent = builder.build()
                            customTabsIntent.launchUrl(context, Uri.parse(url))
                        }
                    }
                }
            }
            mVideosLoaded = true
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Makes the showDetails layout invisible and the editShowDetails visible
     * (or the other way around).
     */
    private fun editDetails() {

        if (binding.editShowDetails.visibility == View.GONE) {
            fadeOutAndHideAnimation(binding.showDetails)
            fadeInAndShowAnimation(binding.editShowDetails)

            // Set the adapter for categoriesView before calling updateEditShowDetails
            val categoriesView = binding.categories
            val adapter = ArrayAdapter.createFromResource(
                this,
                R.array.categories, android.R.layout.simple_dropdown_item_1line
            )
            categoriesView.setAdapter(adapter)

            // Disable text input
            categoriesView.inputType = InputType.TYPE_NULL
            categoriesView.keyListener = null

            // Show dropdown on click
            categoriesView.setOnClickListener { categoriesView.showDropDown() }
            categoriesView.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                // Save the category to the database
                val showValues = ContentValues()
                database = databaseHelper.writableDatabase
                val cursor = database.rawQuery("SELECT * FROM " + MovieDatabaseHelper.TABLE_MOVIES + " WHERE " + MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId + " LIMIT 1", null)
                cursor.moveToFirst()

                // Check if the show is already watched and if the user changed the category.
                if (getCategoryNumber(position) == 1 && cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_CATEGORIES)) != getCategoryNumber(position)) {

                    // If the user hasn't set their own watched value, automatically set it.
                    val timesWatchedCount = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED))
                    if (timesWatchedCount == 0) { showValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED, 1)
                        binding.timesWatched.setText("1")
                    }

                    // Fetch seasons data and add to database if category is changed to "watched"
                    lifecycleScope.launch {
                        fetchMovieDetailsCoroutine()
                        addSeasonsAndEpisodesToDatabase()
                    }
                }
                showValues.put(MovieDatabaseHelper.COLUMN_CATEGORIES, getCategoryNumber(position))
                database.update(MovieDatabaseHelper.TABLE_MOVIES, showValues, MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null)
                database.close()
            }

            // Listen to changes to the EditText.
            binding.timesWatched.onFocusChangeListener = OnFocusChangeListener { _: View?, hasFocus: Boolean ->
                if (!hasFocus && binding.timesWatched.text.toString().isNotEmpty()) {
                    // Save the number to the database
                    val showValues = ContentValues()
                    val timesWatched = binding.timesWatched.text.toString().toInt()
                    showValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED, timesWatched)
                    database = databaseHelper.writableDatabase
                    database.update(MovieDatabaseHelper.TABLE_MOVIES, showValues, MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null)
                    database.close()

                    // Update the view
                    binding.movieRewatched.text = getString(R.string.times_watched, timesWatched)
                    binding.movieRewatched.visibility = View.VISIBLE
                }
            }

            // Listen to changes to the ShowRating Slider.
            binding.showRating.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    // Save the number to the database
                    val showValues = ContentValues()
                    showValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_RATING, value)
                    database = databaseHelper.writableDatabase
                    database.update(MovieDatabaseHelper.TABLE_MOVIES, showValues, MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null)
                    database.close()

                    // Update the view
                    val localizedTen = String.format(Locale.getDefault(), "%.1f", 10.0f)
                    binding.movieRating.text =
                        getString(R.string.rating_format, value, localizedTen)
                    binding.movieRating.visibility = View.VISIBLE
                }
            }

            // Listen to changes to the MovieReview EditText.
            binding.movieReview.onFocusChangeListener = OnFocusChangeListener { _: View?, hasFocus: Boolean ->
                if (!hasFocus) {
                    // Save the review to the database
                    val showValues = ContentValues()
                    val review = binding.movieReview.text.toString()
                    showValues.put(MovieDatabaseHelper.COLUMN_MOVIE_REVIEW, review)
                    database = databaseHelper.writableDatabase
                    database.update(MovieDatabaseHelper.TABLE_MOVIES, showValues, MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null)
                    database.close()

                    if (review.isNotEmpty()) {
                        binding.movieReviewText.text = getString(R.string.reviews, review)
                        binding.movieReviewText.visibility = View.VISIBLE
                    } else {
                        binding.movieReviewText.text = getString(R.string.no_reviews)
                        binding.movieReviewText.visibility = View.VISIBLE
                    }
                }
            }

            updateEditShowDetails()
            binding.showDetails.visibility = View.GONE
            binding.editShowDetails.visibility = View.VISIBLE

            binding.editIcon.icon = ContextCompat.getDrawable(this, R.drawable.ic_check)
            binding.editIcon.setText(R.string.done)
        } else {

            binding.categories.clearFocus()
            binding.timesWatched.clearFocus()
            binding.movieReview.clearFocus()

            fadeOutAndHideAnimation(binding.editShowDetails)
            fadeInAndShowAnimation(binding.showDetails)
            updateEditShowDetails()
            binding.showDetails.visibility = View.VISIBLE
            binding.editShowDetails.visibility = View.GONE

            binding.editIcon.icon = ContextCompat.getDrawable(this, R.drawable.ic_edit)
            binding.editIcon.setText(R.string.edit)

        }
    }

    private fun updateEditShowDetails() {
        database = databaseHelper.writableDatabase
        val cursor = database.rawQuery("SELECT * FROM " + MovieDatabaseHelper.TABLE_MOVIES + " WHERE " + MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId + " LIMIT 1", null)
        if (cursor.count <= 0) {
            cursor.close()
        } else {
            cursor.moveToFirst()
            when (cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_CATEGORIES))) {
                MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH -> binding.categories.setText(
                    binding.categories.adapter.getItem(1).toString(), false
                )
                MovieDatabaseHelper.CATEGORY_WATCHED -> binding.categories.setText(
                    binding.categories.adapter.getItem(2).toString(), false
                )
                MovieDatabaseHelper.CATEGORY_ON_HOLD -> binding.categories.setText(
                    binding.categories.adapter.getItem(3).toString(), false
                )
                MovieDatabaseHelper.CATEGORY_DROPPED -> binding.categories.setText(
                    binding.categories.adapter.getItem(4).toString(), false
                )
                else -> binding.categories.setText(
                    binding.categories.adapter.getItem(0).toString(), false
                )
            }
            if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE))
                && cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)) != ""
            ) {
                val startDateString = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE))
                if (startDateString.startsWith("00-")) {
                    val parts = startDateString.split("-")
                    val month = parts[1]
                    val year = parts[2]
                    binding.startDateButton.text = getString(R.string.month_year_format1, month, year)
                } else {
                    try {
                        binding.startDateButton.text = startDateString
                    } catch (e: ParseException) {
                        e.printStackTrace()
                    }
                }
            } else {
                binding.startDateButton.setText(R.string.change_start_date_2)
            }
            if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE))
                && cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)) != ""
            ) {
                val finishDateString = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE))
                if (finishDateString.startsWith("00-")) {
                    val parts = finishDateString.split("-")
                    val month = parts[1]
                    val year = parts[2]
                    binding.endDateButton.text = getString(R.string.month_year_format1, month, year)
                } else {
                    try {
                        binding.endDateButton.text = finishDateString
                    } catch (e: ParseException) {
                        e.printStackTrace()
                    }
                }
            } else {
                binding.endDateButton.setText(R.string.change_finish_date_2)
            }
            if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED))
                && cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED)) != "") {
                binding.timesWatched.setText(cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED)))
            } else {
                if (cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_CATEGORIES)) == 1) {
                    binding.timesWatched.setText("1")
                } else {
                    binding.timesWatched.setText("0")
                }
            }
            if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING)) && cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING)) != "") {
                binding.showRating.value = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_RATING)).toFloat()
            }
            if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE_REVIEW)) && cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE_REVIEW)) != "") {
                binding.movieReview.setText(cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_MOVIE_REVIEW)))
            }
        }
        cursor.close()
        databaseUpdate()
    }

    fun showDateSelectionDialog(view: View) {
        val context = view.context
        val dialog = BottomSheetDialog(context)
        val inflater = LayoutInflater.from(context)
        val dialogView = DialogDateFormatBinding.inflate(inflater)
        dialog.setContentView(dialogView.root)
        dialog.show()

        dialogView.btnFullDate.setOnClickListener {
            selectDate(view)
            dialog.dismiss()
        }

        dialogView.btnYear.setOnClickListener {
            showYearMonthPickerDialog(context) { selectedYear, selectedMonth ->
                // Save the selected year and month to the database
                val movieValues = ContentValues()
                database = databaseHelper.writableDatabase
                databaseHelper.onCreate(database)
                val month = selectedMonth?.toString()?.padStart(2, '0') ?: "00"
                val dateText = "00-$month-$selectedYear"
                if (view.tag == "start_date") {
                    movieValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE, dateText)
                    binding.startDateButton.text = String.format("%02d-%d", month.toInt(), selectedYear)
                    binding.movieStartDate.text = getString(R.string.start_date, formatDateString(dateText))
                } else {
                    movieValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE, dateText)
                    binding.endDateButton.text = String.format("%02d-%d", month.toInt(), selectedYear)
                    binding.movieFinishDate.text = getString(R.string.finish_date, formatDateString(dateText))
                }
                database.update(MovieDatabaseHelper.TABLE_MOVIES, movieValues, "${MovieDatabaseHelper.COLUMN_MOVIES_ID}=$movieId", null)
                // Update the UI component immediately
                when (view.tag) {
                    "start_date" -> {
                        binding.startDateButton.text = String.format("%02d-%d", month.toInt(), selectedYear)
                        binding.movieStartDate.text = getString(R.string.start_date, formatDateString(dateText))
                        binding.movieStartDate.visibility = View.VISIBLE
                    }
                    "end_date" -> {
                        binding.endDateButton.text = String.format("%02d-%d", month.toInt(), selectedYear)
                        binding.movieFinishDate.text = getString(R.string.finish_date, formatDateString(dateText))
                        binding.movieFinishDate.visibility = View.VISIBLE
                    }
                    else -> {
                        binding.endDateButton.text = String.format("%02d-%d", month.toInt(), selectedYear)
                        binding.movieFinishDate.text = getString(R.string.start_date_unknown)
                    }
                }
                dialog.dismiss()
            }
        }
    }

    private fun formatDateString(dateString: String): String {
        return when {
            dateString.startsWith("00-00-") -> {
                val year = dateString.substring(6)
                year
            }
            dateString.startsWith("00-") -> {
                val monthYear = dateString.substring(3)
                SimpleDateFormat("MM-yyyy", Locale.getDefault()).parse(monthYear)?.let {
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(it)
                } ?: dateString
            }
            else -> {
                val dbDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                dbDateFormat.parse(dateString)?.let {
                    DateFormat.getDateInstance(DateFormat.DEFAULT).format(it)
                } ?: dateString
            }
        }
    }

    private fun showYearMonthPickerDialog(
        context: Context,
        onYearMonthSelected: (Int, Int?) -> Unit
    ) {
        val dialogView = DialogYearMonthPickerBinding.inflate(LayoutInflater.from(context))
        val yearPicker = dialogView.yearPicker
        val monthPicker = dialogView.monthPicker
        val monthTitle = dialogView.monthTitle
        val monthLayout = dialogView.monthLayout
        val disableMonthPicker = dialogView.disableMonthPicker

        val currentYear = android.icu.util.Calendar.getInstance().get(android.icu.util.Calendar.YEAR)
        yearPicker.minValue = 1900
        yearPicker.maxValue = currentYear
        yearPicker.value = currentYear

        val months = DateFormatSymbols.getInstance(Locale.getDefault()).months
        monthPicker.minValue = 0
        monthPicker.maxValue = months.size - 1
        monthPicker.displayedValues = months
        monthPicker.value = android.icu.util.Calendar.getInstance().get(android.icu.util.Calendar.MONTH)

        disableMonthPicker.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                monthLayout.visibility = View.GONE
                monthTitle.visibility = View.GONE
            } else {
                monthLayout.visibility = View.VISIBLE
                monthTitle.visibility = View.VISIBLE
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.select_year_and_month))
            .setView(dialogView.root)
            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                val selectedYear = yearPicker.value
                val selectedMonth = if (disableMonthPicker.isChecked) null else monthPicker.value + 1
                onYearMonthSelected(selectedYear, selectedMonth)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    fun selectDate(view: View) {
        val builder = MaterialDatePicker.Builder.datePicker()
        builder.setTitleText(getString(R.string.select_a_date))
        val datePicker = builder.build()
        datePicker.show(supportFragmentManager, datePicker.toString())
        datePicker.addOnPositiveButtonClickListener { selection: Long? ->
            // Get the date from the MaterialDatePicker.
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selection!!
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
            val dateFormat = sdf.format(calendar.time)

            // Save the date to the database and update the view
            val movieValues = ContentValues()
            if (view.tag == "start_date") {
                movieValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE, dateFormat)
                binding.startDateButton.text = dateFormat

                // Convert the date to DateFormat.DEFAULT
                val dateFormatDefault = DateFormat.getDateInstance(DateFormat.DEFAULT)
                val formattedDate = dateFormatDefault.format(calendar.time)
                binding.movieStartDate.text = getString(R.string.start_date, formattedDate)
                startDate = calendar.time
            } else {
                movieValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE, dateFormat)
                binding.endDateButton.text = dateFormat

                // Convert the date to DateFormat.DEFAULT
                val dateFormatDefault = DateFormat.getDateInstance(DateFormat.DEFAULT)
                val formattedDate = dateFormatDefault.format(calendar.time)
                binding.movieFinishDate.text = getString(R.string.finish_date, formattedDate)
                finishDate = calendar.time
            }
            database = databaseHelper.writableDatabase
            databaseHelper.onCreate(database)
            database.update(
                MovieDatabaseHelper.TABLE_MOVIES,
                movieValues,
                MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId,
                null
            )
        }
    }

    private fun fadeOutAndHideAnimation(view: View) {
        val fadeOut: Animation = AlphaAnimation(1f, 0f)
        fadeOut.interpolator = AccelerateInterpolator()
        fadeOut.duration = 300
        view.startAnimation(fadeOut)
    }

    private fun fadeInAndShowAnimation(view: View) {
        val fadeIn: Animation = AlphaAnimation(0f, 1f)
        fadeIn.interpolator = AccelerateInterpolator()
        fadeIn.duration = 300
        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationEnd(animation: Animation) {
                view.visibility = View.VISIBLE
            }

            override fun onAnimationRepeat(animation: Animation) {}
            override fun onAnimationStart(animation: Animation) {}
        })
        view.startAnimation(fadeIn)
    }

    private fun hideEmptyRecyclerView(recyclerView: RecyclerView, titleView: View) {
        if (recyclerView.adapter!!.itemCount == 0) {
            recyclerView.visibility = View.GONE
            titleView.visibility = View.GONE
        }
    }

    // Load the list of actors.
    private suspend fun fetchCastList() {
        val response = withContext(Dispatchers.IO) {
            var response: String? = null
            try {
                val movie = if (isMovie) SectionsPagerAdapter.MOVIE else SectionsPagerAdapter.TV
                val url = URL("https://api.themoviedb.org/3/$movie/$movieId/credits" + getLanguageParameter2(applicationContext))
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $apiReadAccessToken")
                    .build()
                client.newCall(request).execute().use { res ->
                    if (res.body != null) {
                        response = res.body!!.string()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            response
        }

        if (!response.isNullOrEmpty()) {
            onCastPostExecute(response)
        }
    }

    private fun onCastPostExecute(response: String?) {
        if (!response.isNullOrEmpty()) {
            try {
                val reader = JSONObject(response)

                // Add the cast to the castView
                if (reader.getJSONArray("cast").length() <= 0) {
                    binding.castTitle.visibility = View.GONE
                    binding.secondDivider.visibility = View.GONE
                    binding.castRecyclerView.visibility = View.GONE
                } else {
                    val castArray = reader.getJSONArray("cast")
                    castArrayList.clear()
                    for (i in 0 until castArray.length()) {
                        val castData = castArray.getJSONObject(i)
                        castArrayList.add(castData)
                    }
                    castAdapter = CastBaseAdapter(castArrayList, applicationContext)
                    binding.castRecyclerView.adapter = castAdapter
                }

                // Add the crew to the crewView
                if (reader.getJSONArray("crew").length() <= 0) {
                    binding.crewTitle.visibility = View.GONE
                    binding.thirdDivider.visibility = View.GONE
                    binding.crewRecyclerView.visibility = View.GONE
                } else {
                    val crewArray = reader.getJSONArray("crew")
                    crewArrayList.clear()
                    for (i in 0 until crewArray.length()) {
                        val crewData = crewArray.getJSONObject(i)
                        crewData.put("character", crewData.getString("job"))
                        crewArrayList.add(crewData)
                    }

                    // Sort the crewArrayList to show "director" first, then "producer", then others
                    crewArrayList.sortWith(compareBy({ it.getString("job") != "Director" }, { it.getString("job") != "Producer" }))

                    crewAdapter = CastBaseAdapter(crewArrayList, applicationContext)
                    binding.crewRecyclerView.adapter = crewAdapter
                }
                mCastAndCrewLoaded = true
            } catch (je: JSONException) {
                je.printStackTrace()
            }
        }
        hideEmptyRecyclerView(binding.castRecyclerView, binding.castTitle)
        hideEmptyRecyclerView(binding.crewRecyclerView, binding.crewTitle)
    }

    private fun startSimilarMovieList() {
        lifecycleScope.launch {
            val response = fetchSimilarMovies()
            onPostExecuteSimilarMovies(response)
        }
    }

    private suspend fun fetchSimilarMovies(): String? = withContext(Dispatchers.IO) {
        var response: String? = null
        val movie = if (isMovie) SectionsPagerAdapter.MOVIE else SectionsPagerAdapter.TV
        val url = "https://api.themoviedb.org/3/$movie/$movieId/recommendations" + getLanguageParameter2(applicationContext)
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Content-Type", "application/json;charset=utf-8")
            .addHeader("Authorization", "Bearer $apiReadAccessToken")
            .build()
        try {
            client.newCall(request).execute().use { res ->
                if (res.body != null) {
                    response = res.body!!.string()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        response
    }

    private fun onPostExecuteSimilarMovies(response: String?) {
        if (!response.isNullOrEmpty()) {
            try {
                val reader = JSONObject(response)
                val similarMovieArray = reader.getJSONArray("results")
                similarMovieArrayList.clear()
                for (i in 0 until similarMovieArray.length()) {
                    val movieData = similarMovieArray.getJSONObject(i)
                    similarMovieArrayList.add(movieData)
                }
                similarMovieAdapter = SimilarMovieBaseAdapter(
                    similarMovieArrayList, applicationContext
                )
                binding.movieRecyclerView.adapter = similarMovieAdapter
                mSimilarMoviesLoaded = false
            } catch (je: JSONException) {
                je.printStackTrace()
            }
        }
        hideEmptyRecyclerView(binding.movieRecyclerView, binding.similarMovieTitle)
    }

    // Load the movie details.
    private fun fetchMovieDetails(url: String): JSONObject {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("accept", "application/json")
            .addHeader("Authorization", "Bearer $apiReadAccessToken")
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body!!.string()
            return JSONObject(responseBody)
        }
    }

    private fun onPostExecute(movieData: JSONObject?) {
        if (movieData != null) {
            try {
                setMovieData(movieData)
                showKeywords(movieData)
                showExternalIds(movieData)
                if (movieData.has("number_of_seasons")) {
                    numSeason = movieData.getInt("number_of_seasons")
                }
                if (movieData.has("number_of_episodes")) {
                    totalEpisodes = movieData.getInt("number_of_episodes")
                    saveTotalEpisodes(totalEpisodes!!)
                }
                if (movieData.has("seasons")) {
                    seasons = movieData.getJSONArray("seasons")
                }
                if (movieData.has("last_episode_to_air")) {
                    lastEpisode = movieData.getJSONObject("last_episode_to_air")
                }
                if (movieData.has("next_episode_to_air")) {
                    nextEpisode = movieData.getJSONObject("next_episode_to_air")
                }
                if (isMovie) {
                    val releaseDatesObject = movieData.getJSONObject("release_dates")
                    val resultsArray = releaseDatesObject.getJSONArray("results")
                    var defaultResult: JSONObject? = null
                    for (i in 0 until resultsArray.length()) {
                        val result = resultsArray.getJSONObject(i)
                        val isoCountry = result.getString("iso_3166_1")
                        if (isoCountry == Locale.getDefault().country) {
                            processReleaseDates(result)
                            return
                        }
                        if (isoCountry == "US") {
                            defaultResult = result
                        }
                    }
                    defaultResult?.let { processReleaseDates(it) }
                } else {
                    val contentRatingsObject = movieData.getJSONObject("content_ratings")
                    val contentRrArray = contentRatingsObject.getJSONArray("results")
                    var isLocaleRatingFound = false
                    var usRating: String? = null
                    for (i in 0 until contentRrArray.length()) {
                        val result = contentRrArray.getJSONObject(i)
                        val isoCountry = result.getString("iso_3166_1")
                        val rating = result.getString("rating")
                        if (isoCountry.equals(Locale.getDefault().country, ignoreCase = true)) {
                            binding.certification.text = getString(R.string.certification_with_country, rating, isoCountry)
                            isLocaleRatingFound = true
                            break
                        }
                        if (isoCountry.equals("US", ignoreCase = true)) {
                            usRating = rating
                        }
                    }
                    if (!isLocaleRatingFound && usRating != null) {
                        binding.certification.text = "$usRating (US)"
                    }
                }
                mMovieDetailsLoaded = true
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private fun showExternalIds(movieData: JSONObject) {
        try {
            val externalIdsObject = movieData.getJSONObject("external_ids")
            imdbId = externalIdsObject.getString("imdb_id")
            if (imdbId == "null") {
                binding.imdbRatingChip.isEnabled = false
            } else {
                binding.imdbRatingChip.setOnClickListener {
                    val url = "https://www.imdb.com/title/$imdbId"
                    val builder = CustomTabsIntent.Builder()
                    val customTabsIntent = builder.build()
                    if (customTabsIntent.intent.resolveActivity(packageManager) != null) {
                        customTabsIntent.launchUrl(context, Uri.parse(url))
                    } else {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        if (browserIntent.resolveActivity(packageManager) != null) {
                            startActivity(browserIntent)
                        } else {
                            Toast.makeText(context, R.string.no_browser_available, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            mMovieDetailsLoaded = true
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun showKeywords(movieData: JSONObject) {
        try {
            val keywordsObject = movieData.getJSONObject("keywords")
            val keywordsArray = if (isMovie) keywordsObject.getJSONArray("keywords") else keywordsObject.getJSONArray("results")
            binding.keywordsLayout.removeAllViews()
            for (i in 0 until keywordsArray.length()) {
                val keyword = keywordsArray.getJSONObject(i)
                val keywordName = keyword.getString("name")
                val cardView = MaterialCardView(context)
                cardView.radius = 5f
                cardView.setCardBackgroundColor(Color.TRANSPARENT)
                cardView.strokeWidth = 2
                cardView.setContentPadding(5, 5, 5, 5)
                val keywordTextView = TextView(context)
                keywordTextView.text = keywordName
                keywordTextView.setPadding(8, 4, 8, 4)
                keywordTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                cardView.addView(keywordTextView)
                val params = FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    FlexboxLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(8, 8, 8, 8)
                cardView.layoutParams = params
                binding.keywordsLayout.addView(cardView)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            binding.keywordsLayout.visibility = View.GONE
        }
    }

    private fun fetchMovieDetailsCoroutine() {
        lifecycleScope.launch {
            try {
                OkHttpClient()
                val type = if (isMovie) SectionsPagerAdapter.MOVIE else SectionsPagerAdapter.TV
                val additionalEndpoint = if (isMovie) "release_dates,external_ids,keywords" else "content_ratings,external_ids,keywords"
                val baseUrl = "https://api.themoviedb.org/3/$type/$movieId?append_to_response=$additionalEndpoint"
                val urlWithLanguage = baseUrl + getLanguageParameter(applicationContext)

                var movieData: JSONObject = withContext(Dispatchers.IO) {
                    fetchMovieDetails(urlWithLanguage)
                }

                if (movieData.getString("overview").isEmpty()) {
                    movieData = withContext(Dispatchers.IO) {
                        fetchMovieDetails(baseUrl)
                    }
                }

                withContext(Dispatchers.Main) {
                    onPostExecute(movieData)
                }

                withContext(Dispatchers.IO) {
                    movieDataObject = movieData
                    traktMediaObject = createTraktMediaObject(movieData)
                    traktCheckingObject = createTraktCheckinObject(movieData)
                    val imdbId = movieData.getJSONObject("external_ids").getString("imdb_id")
                    val omdbType = if (isMovie) "movie" else "series"
                    val omdbResponse = fetchMovieRatings(imdbId, movieTitle, movieYear, omdbType, preferences.getString(OMDB_API_KEY, "")!!)
                    omdbResponse?.let {
                        val ratings = parseRatings(it)
                        withContext(Dispatchers.Main) {
                            displayRatings(ratings)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchMovieRatings(imdbId: String?, title: String?, year: String?, type: String, apiKey: String): JSONObject? {
        val client = OkHttpClient()
        val url = if (imdbId != null) {
            "http://www.omdbapi.com/?i=$imdbId&plot=short&apikey=$apiKey"
        } else {
            "http://www.omdbapi.com/?t=$title&y=$year&type=$type&plot=short&apikey=$apiKey"
        }

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val responseBody = response.body?.string()
            return if (responseBody != null) JSONObject(responseBody) else null
        }
    }

    @Throws(JSONException::class)
    private fun processReleaseDates(result: JSONObject) {
        val releaseDates = result.getJSONArray("release_dates")
        var date = "Unknown"
        var certification = "Unknown"
        for (j in 0 until releaseDates.length()) {
            val releaseDate = releaseDates.getJSONObject(j)
            if (releaseDate.getString("type") == "3") { // Theatrical release
                date = releaseDate.getString("release_date")
                certification = releaseDate.getString("certification")
                break
            } else if (releaseDate.getString("type") == "4") { // Digital release
                date = releaseDate.getString("release_date")
                certification = releaseDate.getString("certification")
            }
        }

        // Parse the date string and format it to only include the date
        if (isMovie) {
            try {
                val inputFormat =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                val parsedDate = inputFormat.parse(date)
                if (parsedDate != null) {
                    val outputFormat =
                        DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                    val formattedDate = outputFormat.format(parsedDate)
                    binding.releaseDate.text = formattedDate
                }
            } catch (e: ParseException) {
                e.printStackTrace()
            }
        }
        if (certification.isNotEmpty()) {
            binding.certification.text = certification
        } else binding.certification.setText(R.string.not_rated)
    }

    private fun parseRatings(response: JSONObject): Map<String, String> {
        val ratings = mutableMapOf<String, String>()
        val ratingsArray = response.getJSONArray("Ratings")

        for (i in 0 until ratingsArray.length()) {
            val rating = ratingsArray.getJSONObject(i)
            when (rating.getString("Source")) {
                "Internet Movie Database" -> ratings["IMDb"] = rating.getString("Value")
                "Rotten Tomatoes" -> ratings["Rotten Tomatoes"] = rating.getString("Value")
                "Metacritic" -> ratings["Metacritic"] = rating.getString("Value")
            }
        }

        return ratings
    }

    private fun displayRatings(ratings: Map<String, String>) {
        val imdbRating = ratings["IMDb"] ?: "0.0/10"
        val rottenTomatoesRating = ratings["Rotten Tomatoes"] ?: "00%"
        val metacriticRating = ratings["Metacritic"] ?: "00/100"

        if (preferences.getString(OMDB_API_KEY, "") == "") {
            binding.imdbRatingChip.text = "IMDb"
            binding.rottenTomatoesRatingChip.visibility = View.GONE
            binding.metacriticRatingChip.visibility = View.GONE
        } else {
            binding.imdbRatingChip.text = getString(R.string.imdb, imdbRating)
            binding.rottenTomatoesRatingChip.text = getString(R.string.r_tomatoes, rottenTomatoesRating)
            binding.metacriticRatingChip.text = getString(R.string.metacritic, metacriticRating)
        }
    }

    private fun createTraktMediaObject(tmdbMovieData: JSONObject): JSONObject? {
        return try {
            val tmdbId = tmdbMovieData.getInt("id")
            val imdbId = tmdbMovieData.optString("imdb_id")
            val title = tmdbMovieData.getString(if (isMovie) "title" else "name")
            val year = tmdbMovieData.getString(if (isMovie) "release_date" else "first_air_date").substring(0, 4).toInt()

            val ids = JSONObject().apply {
                put("tmdb", tmdbId)
                put("imdb", imdbId)
            }

            val traktMediaObject = JSONObject().apply {
                put("title", title)
                put("year", year)
                put("ids", ids)
            }

            traktMediaObject

        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    private fun createTraktCheckinObject(tmdbMovieData: JSONObject): JSONObject? {
        return try {
            val tmdbId = tmdbMovieData.getInt("id")
            val imdbId = tmdbMovieData.optString("imdb_id")
            val title = tmdbMovieData.getString(if (isMovie) "title" else "name")
            val year = tmdbMovieData.getString(if (isMovie) "release_date" else "first_air_date").substring(0, 4).toInt()

            val ids = JSONObject().apply {
                put("trakt", 0)
                put("slug", "")
                put("tmdb", tmdbId)
                put("imdb", imdbId)
            }

            val traktCheckinObject = JSONObject().apply {
                put("title", title)
                put("year", year)
                put("ids", ids)
            }

            JSONObject().apply {
                if (isMovie) {
                    put("movie", traktCheckinObject)
                } else {
                    put("show", traktCheckinObject)
                }
            }

        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val CAST_VIEW_PREFERENCE = "key_show_cast"
        private const val CREW_VIEW_PREFERENCE = "key_show_crew"
        private const val OMDB_API_KEY = "omdb_api_key"
        private const val RECOMMENDATION_VIEW_PREFERENCE = "key_show_similar_movies"
        private const val SHOW_SAVE_DIALOG_PREFERENCE = "key_show_save_dialog"
        private const val DYNAMIC_COLOR_DETAILS_ACTIVITY = "dynamic_color_details_activity"
        private const val HD_IMAGE_SIZE = "key_hq_images"
        private const val SEARCH_ENGINE_PREFERENCE = "key_search_engine"

        /**
         * Returns the category number when supplied with
         * the category string array index.
         *
         * @param index the position in the string array.
         * @return the category number of the specified position.
         */
        private fun getCategoryNumber(index: Int): Int {
            return when (index) {
                0 -> MovieDatabaseHelper.CATEGORY_WATCHING
                1 -> MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH
                2 -> MovieDatabaseHelper.CATEGORY_WATCHED
                3 -> MovieDatabaseHelper.CATEGORY_ON_HOLD
                4 -> MovieDatabaseHelper.CATEGORY_DROPPED
                else -> MovieDatabaseHelper.CATEGORY_WATCHING
            }
        }

        /**
         * Returns the category number when supplied with
         * the category string.
         *
         * @param category the name of the category in snake case style.
         * @return the category number of the specified category string.
         */
        fun getCategoryNumber(category: String?): Int {
            return when (category) {
                "watching" -> MovieDatabaseHelper.CATEGORY_WATCHING
                "plan_to_watch" -> MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH
                "watched" -> MovieDatabaseHelper.CATEGORY_WATCHED
                "on_hold" -> MovieDatabaseHelper.CATEGORY_ON_HOLD
                "dropped" -> MovieDatabaseHelper.CATEGORY_DROPPED
                else -> MovieDatabaseHelper.CATEGORY_WATCHING
            }
        }
    }
}
