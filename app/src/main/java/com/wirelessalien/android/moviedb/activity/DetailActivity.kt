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
import android.content.ActivityNotFoundException
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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import com.squareup.picasso.Target
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.CastBaseAdapter
import com.wirelessalien.android.moviedb.adapter.EpisodePagerAdapter
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter
import com.wirelessalien.android.moviedb.adapter.SimilarMovieBaseAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityDetailBinding
import com.wirelessalien.android.moviedb.fragment.LastEpisodeFragment.Companion.newInstance
import com.wirelessalien.android.moviedb.fragment.ListBottomSheetDialogFragment
import com.wirelessalien.android.moviedb.fragment.ListFragment.Companion.databaseUpdate
import com.wirelessalien.android.moviedb.helper.ConfigHelper.getConfigValue
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.account.AddRating
import com.wirelessalien.android.moviedb.tmdb.account.AddToFavourites
import com.wirelessalien.android.moviedb.tmdb.account.AddToWatchlist
import com.wirelessalien.android.moviedb.tmdb.account.DeleteRating
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountState
import com.wirelessalien.android.moviedb.view.NotifyingScrollView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
import kotlin.math.abs

/**
 * This class provides all the details about the shows.
 * It also manages personal show data.
 */
class DetailActivity : BaseActivity() {
    private var apiKey: String? = null
    private var apiReadAccessToken: String? = null
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
    private var genres: String? = ""
    private var startDate: Date? = null
    private var finishDate: Date? = null
    private lateinit var mActivity: Activity
    private val MAX_RETRY_COUNT = 2
    private var retryCount = 0
    private var added = false
    private var showTitle: SpannableString? = null
    private lateinit var palette: Palette
    private var darkMutedColor = 0
    private var lightMutedColor = 0
    private lateinit var binding: ActivityDetailBinding
    private val mOnScrollChangedListener: NotifyingScrollView.OnScrollChangedListener =
        object : NotifyingScrollView.OnScrollChangedListener {
            var lastScrollY = 0
            val scrollThreshold = 50
            override fun onScrollChanged(t: Int) {
                if (t == 0) {
                    binding.toolbar.title = ""
                } else {
                    if (showTitle != null && abs(t - lastScrollY) > scrollThreshold) {
                        binding.toolbar.title = showTitle
                    }
                }
                if (abs(t - lastScrollY) > scrollThreshold) {
                    if (t > lastScrollY) {
                        binding.fab.hide()
                        binding.fabSave.hide()
                    } else if (t < lastScrollY) {
                        binding.fab.show()
                        binding.fabSave.show()
                    }
                    lastScrollY = t
                }
            }
        }
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
        apiKey = getConfigValue(applicationContext, "api_key")
        apiReadAccessToken = getConfigValue(applicationContext, "api_read_access_token")
        setContentView(binding.root)
        setNavigationDrawer()
        supportActionBar!!.title = ""
        episodeViewPager = findViewById(R.id.episodeViewPager)
        episodePagerAdapter = EpisodePagerAdapter(this)
        // Make the transparency dependent on how far the user scrolled down.
        binding.scrollView.setOnScrollChangedListener(mOnScrollChangedListener)
        binding.scrollView.scrollTo(0, 0)

        // Create a variable with the application context that can be used
        // when data is retrieved.
        mActivity = this
        movieId = 0
        context = this
        seenEpisode = 0

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
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
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
        sessionId = preferences.getString("access_token", null)
        accountId = preferences.getString("account_id", null)
        if (sessionId == null || accountId == null) {
            // Disable the buttons
            binding.watchListButton.isEnabled = false
            binding.favouriteButton.isEnabled = false
            binding.ratingBtn.isEnabled = false
            binding.addToList.isEnabled = false
            //            binding.episodeRateBtn.setEnabled(false);
        } else {
            // Enable the buttons
            binding.watchListButton.isEnabled = true
            binding.favouriteButton.isEnabled = true
            binding.ratingBtn.isEnabled = true
            binding.addToList.isEnabled = true
            //            binding.episodeRateBtn.setEnabled(true);
        }

        // Get the movieObject from the intent that contains the necessary
        // data to display the right movie and related RecyclerViews.
        // Send the JSONObject to setMovieData() so all the data
        // will be displayed on the screen.
        val intent = intent
        isMovie = intent.getBooleanExtra("isMovie", true)
        try {
            setMovieData(JSONObject(intent.getStringExtra("movieObject")))
            jMovieObject = JSONObject(intent.getStringExtra("movieObject"))

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
            similarMovieAdapter = SimilarMovieBaseAdapter(
                similarMovieArrayList,
                applicationContext
            )
            binding.movieRecyclerView.adapter = similarMovieAdapter
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
        try {
            if (cursor.count > 0) {
                // A record has been found
                binding.fabSave.setImageResource(R.drawable.ic_star)
                added = true
            }
        } finally {
            cursor.close()
        }
        val progressBar = binding.progressBar
        progressBar.visibility = View.VISIBLE

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
                        binding.watchListButton.icon = ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_bookmark
                        )
                    } else {
                        binding.watchListButton.icon = ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_bookmark_border
                        )
                    }
                    if (isFavourite) {
                        binding.favouriteButton.icon = ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_favorite
                        )
                    } else {
                        binding.favouriteButton.icon = ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_favorite_border
                        )
                    }
                    if (ratingValue != 0.0) {
                        binding.ratingBtn.icon = ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_thumb_up
                        )
                    } else {
                        binding.ratingBtn.icon = ContextCompat.getDrawable(
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
            progressBar.visibility = View.GONE
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

                            binding.firstDivider.dividerColor = mutedColor
                            binding.secondDivider.dividerColor = mutedColor
                            binding.thirdDivider.dividerColor = mutedColor
                            binding.forthDivider.dividerColor = mutedColor
                            binding.linkRlDivider1.dividerColor = mutedColor
                            binding.sixthDivider.dividerColor = mutedColor
                            binding.seventhDivider.dividerColor = mutedColor

                            binding.allEpisodeBtn.backgroundTintList = colorStateList
                            binding.editIcon.backgroundTintList = colorStateList
                            binding.fabSave.backgroundTintList = colorStateList
                            binding.toolbar.setBackgroundColor(Color.TRANSPARENT)
                            binding.collapsingToolbar.setContentScrimColor(mutedColor)
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

        binding.watchListButton.setOnClickListener {
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
                            binding.watchListButton.icon = ContextCompat.getDrawable(
                                context,
                                R.drawable.ic_bookmark_border
                            )
                            withContext(Dispatchers.IO) {
                                AddToWatchlist(movieId, typeCheck, false, mActivity).addToWatchlist()
                            }
                            binding.watchListButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else {
                            binding.watchListButton.icon = ContextCompat.getDrawable(
                                context,
                                R.drawable.ic_bookmark
                            )
                            withContext(Dispatchers.IO) {
                                AddToWatchlist(movieId, typeCheck, true, mActivity).addToWatchlist()
                            }
                            binding.watchListButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                } else {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.failed_to_retrieve_account_id), Toast.LENGTH_SHORT).show()
                    binding.watchListButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }

        binding.addToList.setOnClickListener {
            val typeCheck = if (isMovie) "movie" else "tv"
            val listBottomSheetDialogFragment =
                ListBottomSheetDialogFragment(movieId, typeCheck, mActivity, true)
            listBottomSheetDialogFragment.show(
                supportFragmentManager,
                listBottomSheetDialogFragment.tag
            )
        }
        binding.favouriteButton.setOnClickListener {
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
                            binding.favouriteButton.icon = ContextCompat.getDrawable(
                                context,
                                R.drawable.ic_favorite_border
                            )
                            withContext(Dispatchers.IO) {
                                AddToFavourites(movieId, typeCheck, false, mActivity).addToFavourites()
                            }
                            binding.favouriteButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else {
                            binding.favouriteButton.icon = ContextCompat.getDrawable(
                                context,
                                R.drawable.ic_favorite
                            )
                            withContext(Dispatchers.IO) {
                                AddToFavourites(movieId, typeCheck, true, mActivity).addToFavourites()
                            }
                            binding.favouriteButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                } else {
                    Toast.makeText(applicationContext,
                        getString(R.string.account_id_fail_try_login_again), Toast.LENGTH_SHORT).show()
                    binding.favouriteButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }

        binding.ratingBtn.setOnClickListener {
            val dialog = BottomSheetDialog(mActivity)
            val inflater = layoutInflater
            val dialogView = inflater.inflate(R.layout.rating_dialog, null)
            dialog.setContentView(dialogView)
            dialog.show()
            val ratingBar = dialogView.findViewById<Slider>(R.id.ratingSlider)
            val submitButton = dialogView.findViewById<Button>(R.id.btnSubmit)
            val cancelButton = dialogView.findViewById<Button>(R.id.btnCancel)
            val deleteButton = dialogView.findViewById<Button>(R.id.btnDelete)
            val movieTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
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
            val iiintent = Intent(applicationContext, TVSeasonDetailsActivity::class.java)
            iiintent.putExtra("tvShowId", movieId)
            iiintent.putExtra("numSeasons", numSeason)
            iiintent.putExtra("tvShowName", showName)
            startActivity(iiintent)
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

        binding.editIcon.setOnClickListener {
            editDetails()
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

        // Load movie details.
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
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()
        editor.putInt("totalEpisodes_$movieId", totalEpisodes)
        editor.commit()
    }

    private suspend fun updateMovieEpisodes() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
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
                val genreIds = movieObject.getString("genre_ids")
                    .substring(
                        1, movieObject.getString("genre_ids")
                            .length - 1
                    )

                // Split the String with the ids and set them into an array.
                val genreArray =
                    genreIds.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                // Get the sharedPreferences
                val sharedPreferences = applicationContext
                    .getSharedPreferences("GenreList", MODE_PRIVATE)

                // Add all the genres in one String.
                val genreNames = StringBuilder()
                val chipGroup = findViewById<ChipGroup>(R.id.genreChipGroup)
                chipGroup.removeAllViews() // Clear previous chips if any

                val inflater = LayoutInflater.from(this)
                for (aGenreArray in genreArray) {
                    val genreName = sharedPreferences.getString(aGenreArray, aGenreArray)
                    genreNames.append(", ").append(genreName)

                    val chip = inflater.inflate(R.layout.genre_chip_item, chipGroup, false) as Chip
                    chip.text = genreName

                    chipGroup.addView(chip)
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
            if (movieObject.has("homepage")) {
                val homepage = movieObject.getString("homepage")
                if (homepage != binding.homepage.text.toString()) {
                    binding.homepage.setOnClickListener {
                        if (homepage.isNotEmpty()) {
                            val builder = CustomTabsIntent.Builder()
                            val customTabsIntent = builder.build()
                            try {
                                customTabsIntent.launchUrl(this, Uri.parse(homepage))
                            } catch (e: ActivityNotFoundException) {
                                // Fallback to open the URL in a regular browser
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(homepage))
                                startActivity(browserIntent)
                            }
                        } else {
                            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                        }
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
        val dialogView = inflater.inflate(R.layout.dialog_date_format, null)
        dialog.setContentView(dialogView)
        dialog.show()

        val fullDateBtn = dialogView.findViewById<Button>(R.id.btnFullDate)
        val yearBtn = dialogView.findViewById<Button>(R.id.btnYear)

        fullDateBtn.setOnClickListener {
            selectDate(view)
            dialog.dismiss()
        }

        yearBtn.setOnClickListener {
            showYearMonthPickerDialog(context) { selectedYear, selectedMonth ->
                // Save the selected year and month to the database
                val movieValues = ContentValues()
                database = databaseHelper.writableDatabase
                databaseHelper.onCreate(database)
                val month = selectedMonth?.toString()?.padStart(2, '0') ?: "00"
                val dateText = "00-$month-$selectedYear"
                if (view.tag == "start_date") {
                    movieValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE, dateText)
                    val button = findViewById<Button>(R.id.startDateButton)
                    button.text = String.format("%02d-%d", month.toInt(), selectedYear)
                    binding.movieStartDate.text = getString(R.string.start_date, formatDateString(dateText))
                } else {
                    movieValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE, dateText)
                    val button = findViewById<Button>(R.id.endDateButton)
                    button.text = String.format("%02d-%d", month.toInt(), selectedYear)
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
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_year_month_picker, null)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.yearPicker)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.monthPicker)
        val monthTitle = dialogView.findViewById<TextView>(R.id.monthTitle)
        val monthLayout = dialogView.findViewById<LinearLayout>(R.id.monthLayout)
        val disableMonthPicker = dialogView.findViewById<MaterialCheckBox>(R.id.disableMonthPicker)

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
            .setTitle(getString(R.string.select_year_and_month))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val selectedYear = yearPicker.value
                val selectedMonth = if (disableMonthPicker.isChecked) null else monthPicker.value + 1
                onYearMonthSelected(selectedYear, selectedMonth)
            }
            .setNegativeButton(getString(R.string.cancel), null)
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
                val button = findViewById<Button>(R.id.startDateButton)
                button.text = dateFormat

                // Convert the date to DateFormat.DEFAULT
                val dateFormatDefault = DateFormat.getDateInstance(DateFormat.DEFAULT)
                val formattedDate = dateFormatDefault.format(calendar.time)
                binding.movieStartDate.text = getString(R.string.start_date, formattedDate)
                startDate = calendar.time
            } else {
                movieValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE, dateFormat)
                val button = findViewById<Button>(R.id.endDateButton)
                button.text = dateFormat

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
                    if (res.body() != null) {
                        response = res.body()!!.string()
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
                if (res.body() != null) {
                    response = res.body()!!.string()
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
            val responseBody = response.body()!!.string()
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
            val imdbId = externalIdsObject.getString("imdb_id")
            if (imdbId == "null") {
                binding.imdbBtn.isEnabled = false
            } else {
                binding.imdbBtn.setOnClickListener {
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
            val keywordsLayout = findViewById<FlexboxLayout>(R.id.keywordsLayout)
            keywordsLayout.removeAllViews()
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
                keywordsLayout.addView(cardView)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            val keywordsLayout = findViewById<FlexboxLayout>(R.id.keywordsLayout)
            keywordsLayout.visibility = View.GONE
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    companion object {
        private const val CAST_VIEW_PREFERENCE = "key_show_cast"
        private const val CREW_VIEW_PREFERENCE = "key_show_crew"
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
