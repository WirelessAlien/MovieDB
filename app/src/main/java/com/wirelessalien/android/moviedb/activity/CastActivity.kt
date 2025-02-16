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
import android.view.View
import android.view.animation.AnimationUtils
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import com.squareup.picasso.Target
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ImagePagerAdapter
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityCastBinding
import com.wirelessalien.android.moviedb.databinding.DialogPersonImageBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.PeopleDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
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
//        binding.appBarLayout.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(this)

        val intent = intent
        try {
            setActorData(JSONObject(intent.getStringExtra("actorObject")))
            actorObject = JSONObject(intent.getStringExtra("actorObject"))

        } catch (e: JSONException) {
            e.printStackTrace()
        }

        // Initialize ActorsCreditFragment related variables
        mShowGenreList = HashMap()
        castMovieArrayList = ArrayList()
        crewMovieArrayList = ArrayList()

        binding.tabLayout.getTabAt(0)?.select()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        binding.castMovieRecyclerView.visibility = View.VISIBLE
                        binding.crewMovieRecyclerView.visibility = View.GONE
                    }

                    1 -> {
                        binding.castMovieRecyclerView.visibility = View.GONE
                        binding.crewMovieRecyclerView.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                // No action needed
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                // No action needed
            }
        })

        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
            val mShowGridView = GridLayoutManager(
                context,
                preferences.getInt(GRID_SIZE_PREFERENCE, 3)
            )
            binding.castMovieRecyclerView.layoutManager = mShowGridView
        } else {
            val mShowLinearLayoutManager = LinearLayoutManager(
                context,
                LinearLayoutManager.VERTICAL, false
            )
            binding.castMovieRecyclerView.layoutManager = mShowLinearLayoutManager
        }

        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
            val mShowGridView = GridLayoutManager(
                context,
                preferences.getInt(GRID_SIZE_PREFERENCE, 3)
            )
            binding.crewMovieRecyclerView.layoutManager = mShowGridView
        } else {
            val mShowLinearLayoutManager = LinearLayoutManager(
                context,
                LinearLayoutManager.VERTICAL, false
            )
            binding.crewMovieRecyclerView.layoutManager = mShowLinearLayoutManager
        }

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
                    "https://image.tmdb.org/t/p/h632" + actorObject.getString("profile_path")
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
            if (actorObject.has("profile_path")) {
                if (binding.actorImage.drawable == null) {
                    try {
                        Picasso.get().load(
                            "https://image.tmdb.org/t/p/h632" +
                                    actorObject.getString("profile_path")
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

        binding.images.setOnClickListener {
            val dialogBinding = DialogPersonImageBinding.inflate(layoutInflater)
            val viewPager = dialogBinding.viewPager

            val imageUrls = ArrayList<String>()
            val filePaths = ArrayList<String>()
            val profiles = imagesObject.getJSONArray("profiles")
            for (i in 0 until profiles.length()) {
                val imageUrl = "https://image.tmdb.org/t/p/h632" + profiles.getJSONObject(i)
                    .getString("file_path")
                imageUrls.add(imageUrl)

                val filePath = profiles.getJSONObject(i).getString("file_path")
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

                if (actorData.getString("biography").isEmpty()) {
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
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Content-Type", "application/json;charset=utf-8")
            .addHeader("Authorization", "Bearer $apiReaT")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val responseBody = response.body!!.string()
            return JSONObject(responseBody)
        }
    }

    private fun fetchActorMovies() {
        lifecycleScope.launch(Dispatchers.IO) {
            val response = try {
                val url = URL("https://api.themoviedb.org/3/person/$actorId/combined_credits")
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $apiReaT")
                    .build()
                client.newCall(request).execute().use { res ->
                    res.body?.string()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }

            withContext(Dispatchers.Main) {
                onPostExecute(response)
            }
        }
    }

    private fun onPostExecute(response: String?) {
        if (!response.isNullOrEmpty()) {
            try {
                val reader = JSONObject(response)

                if (reader.getJSONArray("cast").length() <= 0) {
                    binding.castMovieRecyclerView.visibility = View.GONE
                } else {
                    val castMovieArray = reader.getJSONArray("cast")
                    castMovieArrayList.clear()
                    for (i in 0 until castMovieArray.length()) {
                        val actorMovies = castMovieArray.getJSONObject(i)
                        castMovieArrayList.add(actorMovies)
                    }

                    castMovieAdapter = ShowBaseAdapter(castMovieArrayList, mShowGenreList!!, preferences.getBoolean(SHOWS_LIST_PREFERENCE, true))
                    binding.castMovieRecyclerView.adapter = castMovieAdapter
                }

                if (reader.getJSONArray("crew").length() <= 0) {
                    binding.crewMovieRecyclerView.visibility = View.GONE
                } else {
                    val crewMovieArray = reader.getJSONArray("crew")
                    crewMovieArrayList.clear()
                    for (i in 0 until crewMovieArray.length()) {
                        val crewMovies = crewMovieArray.getJSONObject(i)
                        crewMovieArrayList.add(crewMovies)
                    }

                    crewMovieAdapter = ShowBaseAdapter(crewMovieArrayList, mShowGenreList!!, preferences.getBoolean(SHOWS_LIST_PREFERENCE, true))
                    binding.crewMovieRecyclerView.adapter = crewMovieAdapter
                    mActorMoviesLoaded = true
                }
            } catch (je: JSONException) {
                je.printStackTrace()
            }
        }
    }

    private fun fetchPersonImages() {
        lifecycleScope.launch(Dispatchers.IO) {
            val response = try {
                val url = URL("https://api.themoviedb.org/3/person/$actorId/images")
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $apiReaT")
                    .build()
                client.newCall(request).execute().use { res ->
                    res.body?.string()
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
                            val firstImage = profiles.getJSONObject(0).getString("file_path")
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
    }
}