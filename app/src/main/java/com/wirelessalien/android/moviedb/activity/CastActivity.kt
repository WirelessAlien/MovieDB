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
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import com.squareup.picasso.Target
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ImagePagerAdapter
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.adapter.TimelineAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityCastBinding
import com.wirelessalien.android.moviedb.databinding.BottomSheetTimelineBinding
import com.wirelessalien.android.moviedb.databinding.DialogPersonImageBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.PeopleDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * This class displays information about person objects.
 */
class CastActivity : BaseActivity() {
    private lateinit var context: Context
    private lateinit var actorObject: JSONObject
    private lateinit var imagesObject: JSONObject
    private var actorName: String? = null
    private lateinit var binding: ActivityCastBinding
    private var actorId = 0
    private lateinit var target: Target
    private lateinit var palette: Palette
    private var apiKey: String? = null
    private var apiReaT: String? = null
    private lateinit var preferences: SharedPreferences

    private lateinit var mActivity: Activity

    private val mActorDetailsLoaded = false
    private var darkMutedColor = 0
    private var lightMutedColor = 0

    // ActorsCreditFragment related variables
    private lateinit var castMovieAdapter: ShowBaseAdapter
    private lateinit var castMovieArrayList: ArrayList<JSONObject>
    private lateinit var crewMovieAdapter: ShowBaseAdapter
    private lateinit var crewMovieArrayList: ArrayList<JSONObject>
    private val rawCastList = ArrayList<JSONObject>()
    private val rawCrewList = ArrayList<JSONObject>()
    private var sortJob: Job? = null
    private var mShowGenreList: HashMap<String, String?>? = null
    private var mActorMoviesLoaded = false
    private var mActorImagesLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            if (abs(verticalOffset) == appBarLayout.totalScrollRange) {
                // Collapsed
                supportActionBar?.title = if (actorName != null) {
                    actorName!!
                } else {
                    getString(R.string.title_people)
                }
            } else if (verticalOffset == 0) {
                // Expanded
                supportActionBar?.title = ""
            } else {
                // Somewhere in between
                supportActionBar?.title = ""
            }
        }
        setBackButtons()
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        apiKey = ConfigHelper.getConfigValue(applicationContext, "api_key")
        apiReaT = ConfigHelper.getConfigValue(applicationContext, "api_read_access_token")
        context = this
        val dbHelper = PeopleDatabaseHelper(context)

        mActivity = this

        val intent = intent
        try {
            setActorData(JSONObject(intent.getStringExtra("actorObject") ?: "{}"))
            actorObject = JSONObject(intent.getStringExtra("actorObject") ?: "{}")

        } catch (e: JSONException) {
            e.printStackTrace()
        }

        // Initialize ActorsCreditFragment related variables
        mShowGenreList = HashMap()
        castMovieArrayList = ArrayList()
        crewMovieArrayList = ArrayList()

        val isGridView = preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
        castMovieAdapter = ShowBaseAdapter(context, castMovieArrayList, mShowGenreList!!, isGridView)
        crewMovieAdapter = ShowBaseAdapter(context, crewMovieArrayList, mShowGenreList!!, isGridView)

        binding.tabLayout.getTabAt(0)?.select()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateTabVisibility(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                // No action needed
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                // No action needed
            }
        })

        if (isGridView) {
            val gridSpanCount = preferences.getInt(GRID_SIZE_PREFERENCE, 3).coerceAtLeast(1)
            binding.castMovieRecyclerView.layoutManager = GridLayoutManager(context, gridSpanCount)
            binding.crewMovieRecyclerView.layoutManager = GridLayoutManager(context, gridSpanCount)
        } else {
            binding.castMovieRecyclerView.layoutManager = LinearLayoutManager(
                context,
                LinearLayoutManager.VERTICAL, false
            )
            binding.crewMovieRecyclerView.layoutManager = LinearLayoutManager(
                context,
                LinearLayoutManager.VERTICAL, false
            )
        }

        binding.castMovieRecyclerView.setHasFixedSize(true)
        binding.castMovieRecyclerView.setItemViewCacheSize(20)
        binding.castMovieRecyclerView.adapter = castMovieAdapter

        binding.crewMovieRecyclerView.setHasFixedSize(true)
        binding.crewMovieRecyclerView.setItemViewCacheSize(20)
        binding.crewMovieRecyclerView.adapter = crewMovieAdapter

        addMenuProvider()

        if (dbHelper.personExists(actorId)) {
            binding.favoriteFab.setImageResource(R.drawable.ic_star)

        } else {
            binding.favoriteFab.setImageResource(R.drawable.ic_star_border)
        }

        checkNetwork()

        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        val isDarkTheme = uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
        val color: Int = if (isDarkTheme) {
            Color.BLACK
        } else {
            Color.WHITE
        }

        if (preferences.getBoolean(DYNAMIC_COLOR_DETAILS_ACTIVITY, false)) {
            if (actorObject.has("profile_path") && binding.actorImage.drawable == null) {
                val imageUrl: String = try {
                    "https://image.tmdb.org/t/p/h632" + actorObject.optString("profile_path")
                } catch (e: JSONException) {
                    e.printStackTrace()
                    ""
                }
                // Set the loaded bitmap to your ImageView before generating the Palette
                target = object : Target {
                    override fun onBitmapLoaded(bitmap: Bitmap, from: LoadedFrom) {
                        // Set the loaded bitmap to your ImageView before generating the Palette
                        binding.actorImage.setImageBitmap(bitmap)

                        palette = Palette.from(bitmap).generate()

                        darkMutedColor =
                            palette.getDarkMutedColor(palette.getMutedColor(Color.TRANSPARENT))

                        lightMutedColor =
                            palette.getLightMutedColor(palette.getMutedColor(Color.TRANSPARENT))

                        val gradientDrawable: GradientDrawable = if (isDarkTheme) {
                            GradientDrawable(
                                GradientDrawable.Orientation.TL_BR,
                                intArrayOf(darkMutedColor, color)
                            )
                        } else {
                            GradientDrawable(
                                GradientDrawable.Orientation.TL_BR,
                                intArrayOf(lightMutedColor, color)
                            )
                        }

                        binding.root.background = gradientDrawable
                        binding.appBarLayout.setBackgroundColor(Color.TRANSPARENT)

                        val animation = AnimationUtils.loadAnimation(
                            applicationContext, R.anim.fade_in
                        )
                        binding.actorImage.startAnimation(animation)
                    }

                    override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
                        val fallbackDrawable = errorDrawable ?: ContextCompat.getColor(
                            context,
                            R.color.md_theme_surface
                        )

                        binding.actorImage.setBackgroundColor(
                            ContextCompat.getColor(
                                context,
                                R.color.md_theme_surface
                            )
                        )
                        binding.actorImage.setBackgroundColor(fallbackDrawable as Int)
                    }

                    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                        // Ensure placeHolderDrawable is not null
                        placeHolderDrawable ?: ContextCompat.getColor(
                            context,
                            R.color.md_theme_outline
                        )
                        binding.actorImage.setBackgroundColor(
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

            binding.backgroundImage.foreground = ContextCompat.getDrawable(context, R.drawable.bg_gradient)

            if (actorObject.has("profile_path")) {
                if (binding.actorImage.drawable == null) {
                    try {
                        Picasso.get().load(
                            "https://image.tmdb.org/t/p/h632" +
                                    actorObject.optString("profile_path")
                        )
                            .into(binding.actorImage)
                    } catch (e: JSONException) {
                        throw RuntimeException(e)
                    }
                }
            }
        }

        binding.actorBiography.maxLines = 5
        binding.actorBiography.ellipsize = TextUtils.TruncateAt.END

        binding.actorBiography.setOnClickListener {
            if (binding.actorBiography.maxLines == 5) {
                binding.actorBiography.maxLines = Int.MAX_VALUE
                binding.actorBiography.ellipsize = null
            } else {
                binding.actorBiography.maxLines = 5
                binding.actorBiography.ellipsize = TextUtils.TruncateAt.END
            }
        }

        binding.imdb.setOnClickListener {
            if (actorObject.has("imdb_id")) {
                val imdbId = actorObject.optString("imdb_id")
                if (imdbId.isNotEmpty()) {
                    val url = "https://www.imdb.com/name/$imdbId"
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    try {
                        customTabsIntent.launchUrl(this, Uri.parse(url))
                    } catch (e: Exception) {
                        val intentI = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        if (intentI.resolveActivity(packageManager) != null) {
                            startActivity(intentI)
                        } else {
                            Log.e("CastActivity", "No Activity found to handle Intent")
                        }
                    }
                } else {
                    binding.imdb.isEnabled = false
                }
            } else {
                binding.imdb.isEnabled = false
            }
        }

        binding.homepage.setOnClickListener {
            if (actorObject.has("homepage")) {
                val homepage = actorObject.optString("homepage")
                if (!homepage.isNullOrEmpty()) {
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    try {
                        customTabsIntent.launchUrl(this, Uri.parse(homepage))
                    } catch (e: Exception) {
                        val intentI = Intent(Intent.ACTION_VIEW, Uri.parse(homepage))
                        if (intentI.resolveActivity(packageManager) != null) {
                            startActivity(intentI)
                        } else {
                            Log.e("CastActivity", "No Activity found to handle Intent")
                        }
                    }
                } else {
                    binding.homepage.isEnabled = false
                }
            } else {
                binding.homepage.isEnabled = false
            }
        }

        binding.actorImage.setOnClickListener {
            if (!this::imagesObject.isInitialized) return@setOnClickListener
            val dialogBinding = DialogPersonImageBinding.inflate(layoutInflater)
            val viewPager = dialogBinding.viewPager

            val imageUrls = ArrayList<String>()
            val filePaths = ArrayList<String>()
            val profiles = imagesObject.getJSONArray("profiles")
            for (i in 0 until profiles.length()) {
                val imageUrl = "https://image.tmdb.org/t/p/h632" + profiles.getJSONObject(i)
                    .optString("file_path")
                imageUrls.add(imageUrl)

                val filePath = profiles.getJSONObject(i).optString("file_path")
                filePaths.add(filePath)
            }

            val adapter = ImagePagerAdapter(this, imageUrls, filePaths)
            viewPager.adapter = adapter

            val dialog = MaterialAlertDialogBuilder(this, R.style.MaterialDialog)
                .setView(dialogBinding.root)
                .setCancelable(true)
                .create()

            dialog.show()
        }

        binding.favoriteFab.setOnClickListener {
            val actorId = actorObject.optInt("id")
            if (dbHelper.personExists(actorId)) {
                dbHelper.deleteById(actorId)
                binding.favoriteFab.setImageResource(R.drawable.ic_star_border)
            } else {
                // If the person does not exist in the database, insert the person
                val name = actorObject.optString("name")
                val birthday = actorObject.optString("birthday")
                val deathday = actorObject.optString("deathday")
                val biography = actorObject.optString("biography")
                val placeOfBirth = actorObject.optString("place_of_birth")
                val popularity = actorObject.optDouble("popularity")
                val profilePath = actorObject.optString("profile_path")
                val imdbId = actorObject.optString("imdb_id")
                val homepage = actorObject.optString("homepage")
                dbHelper.insert(actorId, name, birthday, deathday, biography, placeOfBirth, popularity, profilePath, imdbId, homepage)
                binding.favoriteFab.setImageResource(R.drawable.ic_star)
            }
        }

        binding.upcoming.setOnClickListener {
            showTimelineBottomSheet()
        }
    }

    private fun addMenuProvider() {
        val menuHost: MenuHost = this
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_cast_sort, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                val criteria = preferences.getString(PREF_CREDITS_SORT_CRITERIA, SORT_DEFAULT) ?: SORT_DEFAULT
                val order = preferences.getString(PREF_CREDITS_SORT_ORDER, ORDER_DESC) ?: ORDER_DESC

                val checkedId = when (criteria) {
                    SORT_DATE -> if (order == ORDER_ASC) R.id.sort_credits_date_asc else R.id.sort_credits_date_desc
                    SORT_NAME -> if (order == ORDER_ASC) R.id.sort_credits_name_asc else R.id.sort_credits_name_desc
                    SORT_RATING -> if (order == ORDER_ASC) R.id.sort_credits_rating_asc else R.id.sort_credits_rating_desc
                    else -> R.id.sort_credits_default
                }
                menu.findItem(checkedId)?.isChecked = true
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.sort_credits_default -> {
                        menuItem.isChecked = true
                        saveAndApplySortPreference(SORT_DEFAULT, ORDER_DESC)
                        true
                    }
                    R.id.sort_credits_date_desc -> {
                        menuItem.isChecked = true
                        saveAndApplySortPreference(SORT_DATE, ORDER_DESC)
                        true
                    }
                    R.id.sort_credits_date_asc -> {
                        menuItem.isChecked = true
                        saveAndApplySortPreference(SORT_DATE, ORDER_ASC)
                        true
                    }
                    R.id.sort_credits_name_asc -> {
                        menuItem.isChecked = true
                        saveAndApplySortPreference(SORT_NAME, ORDER_ASC)
                        true
                    }
                    R.id.sort_credits_name_desc -> {
                        menuItem.isChecked = true
                        saveAndApplySortPreference(SORT_NAME, ORDER_DESC)
                        true
                    }
                    R.id.sort_credits_rating_desc -> {
                        menuItem.isChecked = true
                        saveAndApplySortPreference(SORT_RATING, ORDER_DESC)
                        true
                    }
                    R.id.sort_credits_rating_asc -> {
                        menuItem.isChecked = true
                        saveAndApplySortPreference(SORT_RATING, ORDER_ASC)
                        true
                    }
                    else -> false
                }
            }
        }, this, Lifecycle.State.RESUMED)
    }

    private fun saveAndApplySortPreference(criteria: String, order: String) {
        preferences.edit()
            .putString(PREF_CREDITS_SORT_CRITERIA, criteria)
            .putString(PREF_CREDITS_SORT_ORDER, order)
            .apply()
        sortAndDisplayCredits()
    }

    private fun sortAndDisplayCredits() {
        if (rawCastList.isEmpty() && rawCrewList.isEmpty()) return

        sortJob?.cancel()
        binding.creditsProgressIndicator.visibility = View.VISIBLE

        val criteria = preferences.getString(PREF_CREDITS_SORT_CRITERIA, SORT_DEFAULT) ?: SORT_DEFAULT
        val order = preferences.getString(PREF_CREDITS_SORT_ORDER, ORDER_DESC) ?: ORDER_DESC
        val castSnapshot = ArrayList(rawCastList)
        val crewSnapshot = ArrayList(rawCrewList)

        sortJob = lifecycleScope.launch {
            val (sortedCast, sortedCrew) = withContext(Dispatchers.Default) {
                ensureActive()
                val comparator = buildCreditsComparator(criteria, order)
                if (comparator != null) {
                    castSnapshot.sortWith(comparator)
                    ensureActive()
                    crewSnapshot.sortWith(comparator)
                }
                Pair(castSnapshot, crewSnapshot)
            }

            castMovieAdapter.updateData(sortedCast)
            crewMovieAdapter.updateData(sortedCrew)
            binding.castMovieRecyclerView.scrollToPosition(0)
            binding.crewMovieRecyclerView.scrollToPosition(0)
            updateTabVisibility(binding.tabLayout.selectedTabPosition)
            binding.creditsProgressIndicator.visibility = View.GONE
        }
    }

    private fun buildCreditsComparator(criteria: String, order: String): Comparator<JSONObject>? {
        val isAsc = order == ORDER_ASC
        return when (criteria) {
            SORT_DATE -> Comparator { a, b ->
                val dateA = a.optString(KEY_SORT_DATE, "")
                val dateB = b.optString(KEY_SORT_DATE, "")
                val emptyA = dateA.isEmpty()
                val emptyB = dateB.isEmpty()
                // Always push items without release dates to the bottom
                when {
                    emptyA && emptyB -> a.optInt(KEY_ORIGINAL_INDEX).compareTo(b.optInt(KEY_ORIGINAL_INDEX))
                    emptyA -> 1
                    emptyB -> -1
                    else -> {
                        val cmp = if (isAsc) dateA.compareTo(dateB) else dateB.compareTo(dateA)
                        if (cmp != 0) cmp else a.optString(KEY_SORT_TITLE, "").compareTo(b.optString(KEY_SORT_TITLE, ""))
                    }
                }
            }
            SORT_NAME -> Comparator { a, b ->
                val titleA = a.optString(KEY_SORT_TITLE, "")
                val titleB = b.optString(KEY_SORT_TITLE, "")
                val emptyA = titleA.isEmpty()
                val emptyB = titleB.isEmpty()
                when {
                    emptyA && emptyB -> a.optInt(KEY_ORIGINAL_INDEX).compareTo(b.optInt(KEY_ORIGINAL_INDEX))
                    emptyA -> 1
                    emptyB -> -1
                    else -> {
                        val cmp = if (isAsc) titleA.compareTo(titleB) else titleB.compareTo(titleA)
                        if (cmp != 0) cmp else a.optInt(KEY_ORIGINAL_INDEX).compareTo(b.optInt(KEY_ORIGINAL_INDEX))
                    }
                }
            }
            SORT_RATING -> Comparator { a, b ->
                val ratingA = a.optDouble(KEY_SORT_RATING, 0.0)
                val ratingB = b.optDouble(KEY_SORT_RATING, 0.0)
                val unratedA = ratingA <= 0.0
                val unratedB = ratingB <= 0.0
                // Keep unrated (0.0) items at the bottom for both ascending and descending rating sorts
                when {
                    unratedA && unratedB -> a.optInt(KEY_ORIGINAL_INDEX).compareTo(b.optInt(KEY_ORIGINAL_INDEX))
                    unratedA -> 1
                    unratedB -> -1
                    else -> {
                        val cmp = if (isAsc) ratingA.compareTo(ratingB) else ratingB.compareTo(ratingA)
                        if (cmp != 0) {
                            cmp
                        } else {
                            val votesA = a.optInt("vote_count", 0)
                            val votesB = b.optInt("vote_count", 0)
                            if (isAsc) votesA.compareTo(votesB) else votesB.compareTo(votesA)
                        }
                    }
                }
            }
            else -> null
        }
    }

    private fun updateTabVisibility(selectedTabPosition: Int) {
        when (selectedTabPosition) {
            0 -> {
                binding.crewMovieRecyclerView.visibility = View.GONE
                if (castMovieArrayList.isEmpty()) {
                    binding.castMovieRecyclerView.visibility = View.GONE
                    binding.emptyView.visibility = View.VISIBLE
                } else {
                    binding.castMovieRecyclerView.visibility = View.VISIBLE
                    binding.emptyView.visibility = View.GONE
                }
            }
            1 -> {
                binding.castMovieRecyclerView.visibility = View.GONE
                if (crewMovieArrayList.isEmpty()) {
                    binding.crewMovieRecyclerView.visibility = View.GONE
                    binding.emptyView.visibility = View.VISIBLE
                } else {
                    binding.crewMovieRecyclerView.visibility = View.VISIBLE
                    binding.emptyView.visibility = View.GONE
                }
            }
        }
    }

    private fun showTimelineBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetTimelineBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        val today = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val allCreditsRaw = (rawCastList + rawCrewList)
            .filter {
                val releaseDateStr =
                    it.optString("release_date", "").ifEmpty { it.optString("first_air_date", "") }
                if (releaseDateStr.isEmpty()) {
                    false
                } else {
                    try {
                        val releaseDate = dateFormat.parse(releaseDateStr)
                        releaseDate != null && releaseDate.after(today)
                    } catch (e: Exception) {
                        false
                    }
                }
            }

        val combinedCredits = allCreditsRaw.groupBy { it.optInt("id") }
            .map { (_, credits) ->
                if (credits.size > 1) {
                    val cast = credits.find { !it.has("job") }
                    val crew = credits.find { it.has("job") }
                    if (cast != null && crew != null) {
                        val newCredit = JSONObject(cast.toString()) // clone cast
                        val crewJob = crew.optString("job")
                        newCredit.put("job", "Actor, $crewJob")
                        newCredit
                    } else {
                        credits.first()
                    }
                } else {
                    credits.first()
                }
            }
            .sortedByDescending {
                val releaseDateStr =
                    it.optString("release_date", "").ifEmpty { it.optString("first_air_date", "") }
                try {
                    dateFormat.parse(releaseDateStr)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }

        if (combinedCredits.isEmpty()) {
            bottomSheetBinding.timelineRecyclerView.visibility = View.GONE
            bottomSheetBinding.emptyView.visibility = View.VISIBLE
        } else {
            bottomSheetBinding.timelineRecyclerView.visibility = View.VISIBLE
            bottomSheetBinding.emptyView.visibility = View.GONE
            val adapter = TimelineAdapter(combinedCredits)
            bottomSheetBinding.timelineRecyclerView.adapter = adapter
        }

        bottomSheetDialog.show()
    }

    override fun doNetworkWork() {
        // Load person details
        if (!mActorDetailsLoaded) {
            fetchActorDetails()
        }

        // Load person movies
        if (!mActorMoviesLoaded) {
            fetchActorMovies()
        }

        // Load person images
        if (!mActorImagesLoaded) {
            fetchPersonImages()
        }

    }

    /**
     * Sets the data gotten from the actorObject to the appropriate views.
     *
     * @param actorObject the JSONObject that it takes the data from.
     */
    private fun setActorData(actorObject: JSONObject?) {

        if (actorObject == null) {
            Log.e("CastActivity", "actorObject is null")
            return
        }

        actorId = actorObject.optInt("id", actorId)

        // If the name is different in the new dataset, change it.
        actorObject.optString("name").let { name ->
            if (name != binding.actorName.text.toString()) {
                binding.actorName.text = name
                actorName = name
            }
        }

        actorObject.let { actor ->
            val placeOfBirth = actor.optString("place_of_birth")
            val birthday = actor.optString("birthday")

            if (birthday.isNotEmpty() || placeOfBirth.isNotEmpty()) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val formattedDate = if (birthday.isNotEmpty()) {
                    val date = dateFormat.parse(birthday)
                    DateFormat.getDateInstance(DateFormat.FULL).format(date)
                } else ""

                val combinedText = when {
                    birthday.isNotEmpty() && placeOfBirth.isNotEmpty() ->
                        getString(R.string.born_date_place, formattedDate, placeOfBirth)
                    birthday.isNotEmpty() ->
                        getString(R.string.born_date, formattedDate)
                    placeOfBirth.isNotEmpty() ->
                        getString(R.string.born_place, placeOfBirth)
                    else -> ""
                }

                binding.actorPlaceOfBirth.text = combinedText
            }
        }

        // If the biography is different in the new dataset, change it.
        actorObject.optString("biography").let { biography ->
            if (biography != binding.actorBiography.text.toString()) {
                binding.actorBiography.text = biography
            }
            if (biography.isEmpty()) {
                fetchActorDetails()
            }
        }
    }

    /**
     * Coroutine that retrieves the details of the person from the API.
     */
    private fun fetchActorDetails() {
        lifecycleScope.launch(Dispatchers.IO) {
            val baseUrl = "https://api.themoviedb.org/3/person/$actorId"
            val urlWithLanguage = baseUrl + getLanguageParameter2(applicationContext)
            try {
                var actorData = fetchActorDetails(urlWithLanguage)

                if (actorData.optString("biography").isEmpty()) {
                    actorData = fetchActorDetails(baseUrl)
                }
                withContext(Dispatchers.Main) {
                    actorObject = actorData
                    setActorData(actorObject)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class, JSONException::class)
    private fun fetchActorDetails(url: String): JSONObject {
        val client = com.wirelessalien.android.moviedb.NetworkClient.client
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Content-Type", "application/json;charset=utf-8")
            .addHeader("Authorization", "Bearer $apiReaT")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val responseBody = response.body.string()
            return JSONObject(responseBody)
        }
    }

    private fun fetchActorMovies() {
        lifecycleScope.launch {
            binding.creditsProgressIndicator.visibility = View.VISIBLE

            val response = withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://api.themoviedb.org/3/person/$actorId/combined_credits")
                    val client = com.wirelessalien.android.moviedb.NetworkClient.client
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Content-Type", "application/json;charset=utf-8")
                        .addHeader("Authorization", "Bearer $apiReaT")
                        .build()
                    client.newCall(request).execute().use { res ->
                        res.body.string()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    null
                }
            }

            processCreditsResponse(response)
        }
    }

    private suspend fun processCreditsResponse(response: String?) {
        if (response.isNullOrEmpty()) {
            binding.creditsProgressIndicator.visibility = View.GONE
            return
        }

        val criteria = preferences.getString(PREF_CREDITS_SORT_CRITERIA, SORT_DEFAULT) ?: SORT_DEFAULT
        val order = preferences.getString(PREF_CREDITS_SORT_ORDER, ORDER_DESC) ?: ORDER_DESC

        try {
            val parsedData = withContext(Dispatchers.Default) {
                val reader = JSONObject(response)
                val parsedCast = ArrayList<JSONObject>()
                val parsedCrew = ArrayList<JSONObject>()

                val castMovieArray = reader.optJSONArray("cast")
                if (castMovieArray != null) {
                    val addedCastItems = HashSet<Pair<Int, String>>(castMovieArray.length())
                    for (i in 0 until castMovieArray.length()) {
                        val item = castMovieArray.optJSONObject(i) ?: continue
                        val id = item.optInt("id")
                        val mediaType = item.optString("media_type")
                        if (addedCastItems.add(Pair(id, mediaType))) {
                            enrichSortMetadata(item, parsedCast.size)
                            parsedCast.add(item)
                        }
                    }
                }

                val crewMovieArray = reader.optJSONArray("crew")
                if (crewMovieArray != null) {
                    val addedCrewItems = HashSet<Pair<Int, String>>(crewMovieArray.length())
                    for (i in 0 until crewMovieArray.length()) {
                        val item = crewMovieArray.optJSONObject(i) ?: continue
                        val id = item.optInt("id")
                        val mediaType = item.optString("media_type")
                        if (addedCrewItems.add(Pair(id, mediaType))) {
                            enrichSortMetadata(item, parsedCrew.size)
                            parsedCrew.add(item)
                        }
                    }
                }

                val sortedCast = ArrayList(parsedCast)
                val sortedCrew = ArrayList(parsedCrew)
                val comparator = buildCreditsComparator(criteria, order)
                if (comparator != null) {
                    sortedCast.sortWith(comparator)
                    sortedCrew.sortWith(comparator)
                }

                CreditsResult(parsedCast, parsedCrew, sortedCast, sortedCrew)
            }

            rawCastList.clear()
            rawCastList.addAll(parsedData.rawCast)
            rawCrewList.clear()
            rawCrewList.addAll(parsedData.rawCrew)

            castMovieAdapter.updateData(parsedData.sortedCast)
            crewMovieAdapter.updateData(parsedData.sortedCrew)
            mActorMoviesLoaded = true
            updateTabVisibility(binding.tabLayout.selectedTabPosition)
        } catch (je: JSONException) {
            je.printStackTrace()
        } finally {
            binding.creditsProgressIndicator.visibility = View.GONE
        }
    }

    /**
     * Pre-computes normalized sort keys once on a background thread so subsequent comparisons
     * do not allocate strings or parse JSON repeatedly inside Comparator.compare().
     */
    private fun enrichSortMetadata(item: JSONObject, index: Int) {
        val rawTitle = if (item.has("title")) item.optString("title", "") else item.optString("name", "")
        val normalizedTitle = if (rawTitle == "null") "" else rawTitle.trim().lowercase(Locale.ROOT)

        val rawDate = item.optString("release_date", "").let {
            if (it.isEmpty() || it == "null") item.optString("first_air_date", "") else it
        }
        val normalizedDate = if (rawDate == "null") "" else rawDate.trim()

        val rating = item.optDouble("vote_average", 0.0).let { if (it.isNaN()) 0.0 else it }

        item.put(KEY_SORT_TITLE, normalizedTitle)
        item.put(KEY_SORT_DATE, normalizedDate)
        item.put(KEY_SORT_RATING, rating)
        item.put(KEY_ORIGINAL_INDEX, index)
    }

    private data class CreditsResult(
        val rawCast: ArrayList<JSONObject>,
        val rawCrew: ArrayList<JSONObject>,
        val sortedCast: ArrayList<JSONObject>,
        val sortedCrew: ArrayList<JSONObject>
    )

    private fun fetchPersonImages() {
        lifecycleScope.launch(Dispatchers.IO) {
            val response = try {
                val url = URL("https://api.themoviedb.org/3/person/$actorId/images")
                val client = com.wirelessalien.android.moviedb.NetworkClient.client
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $apiReaT")
                    .build()
                client.newCall(request).execute().use { res ->
                    res.body.string()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }

            withContext(Dispatchers.Main) {
                response?.let {
                    try {
                        val jsonObject = JSONObject(it)
                        val profiles = jsonObject.getJSONArray("profiles")
                        if (profiles.length() > 0) {
                            imagesObject = jsonObject
                            val firstImage = profiles.getJSONObject(0).optString("file_path")
                            val imageUrl = "https://image.tmdb.org/t/p/h632$firstImage"
                            Picasso.get().load(imageUrl).into(binding.backgroundImage)
                        }
                        mActorImagesLoaded = true
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    companion object {
        const val SHOWS_LIST_PREFERENCE = "key_show_shows_grid"
        const val GRID_SIZE_PREFERENCE = "key_grid_size_number"
        private const val DYNAMIC_COLOR_DETAILS_ACTIVITY = "dynamic_color_details_activity"

        const val PREF_CREDITS_SORT_CRITERIA = "person_credits_sort_criteria"
        const val PREF_CREDITS_SORT_ORDER = "person_credits_sort_order"

        const val SORT_DEFAULT = "default"
        const val SORT_DATE = "date"
        const val SORT_NAME = "name"
        const val SORT_RATING = "rating"

        const val ORDER_ASC = "asc"
        const val ORDER_DESC = "desc"

        private const val KEY_SORT_TITLE = "_sort_title"
        private const val KEY_SORT_DATE = "_sort_date"
        private const val KEY_SORT_RATING = "_sort_rating"
        private const val KEY_ORIGINAL_INDEX = "_original_index"
    }
}