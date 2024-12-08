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
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import com.squareup.picasso.Target
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.SimilarMovieBaseAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityCastBinding
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

/**
 * This class displays information about person objects.
 */
class CastActivity : BaseActivity() {
    private lateinit var context: Context
    private lateinit var actorObject: JSONObject
    private lateinit var castMovieAdapter: SimilarMovieBaseAdapter
    private lateinit var castMovieArrayList: ArrayList<JSONObject>
    private lateinit var crewMovieAdapter: SimilarMovieBaseAdapter
    private lateinit var crewMovieArrayList: ArrayList<JSONObject>
    private lateinit var binding: ActivityCastBinding
    private var actorId = 0
    private lateinit var target: Target
    private lateinit var palette: Palette
    private var apiKey: String? = null
    private var apiReaT: String? = null
    private lateinit var preferences: SharedPreferences

    /*
    * This class provides an overview for actors.
	* The data is send via an Intent that comes from the DetailActivity
	* DetailActivity in his turn gets the data from the CastBaseAdapter
	*/
    private lateinit var mActivity: Activity
    private lateinit var collapseViewPreferences: SharedPreferences

    // Indicate whether the network items have loaded.
    private var mActorMoviesLoaded = false
    private val mActorDetailsLoaded = false
    private var darkMutedColor = 0
    private var lightMutedColor = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.title_people)
        setBackButtons()
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        apiKey = ConfigHelper.getConfigValue(applicationContext, "api_key")
        apiReaT = ConfigHelper.getConfigValue(applicationContext, "api_read_access_token")
        context = this

        // Create a variable with the application context that can be used
        // when data is retrieved.
        mActivity = this

        // RecyclerView to display the shows that the person was part of the cast in.
        binding.castMovieRecyclerView.setHasFixedSize(true) // Improves performance (if size is static)

        // RecyclerView to display the movies that the person was part of the crew in.
        binding.crewMovieRecyclerView.setHasFixedSize(true)
        val castLinearLayoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.castMovieRecyclerView.layoutManager = castLinearLayoutManager

        // RecyclerView to display the shows that the person was part of the crew in.;
        binding.crewMovieRecyclerView.setHasFixedSize(true) // Improves performance (if size is static)
        val crewLinearLayoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.crewMovieRecyclerView.layoutManager = crewLinearLayoutManager

        // Make the views invisible if the user collapsed the view.
        collapseViewPreferences = applicationContext
            .getSharedPreferences(COLLAPSE_VIEW, MODE_PRIVATE)
        if (collapseViewPreferences.getBoolean(CAST_MOVIE_VIEW_PREFERENCE, false)) {
            binding.castMovieRecyclerView.visibility = View.GONE
        }
        if (collapseViewPreferences.getBoolean(CREW_MOVIE_VIEW_PREFERENCE, false)) {
            binding.crewMovieRecyclerView.visibility = View.GONE
        }

        val intent = intent
        try {
            setActorData(JSONObject(intent.getStringExtra("actorObject")))
            actorObject = JSONObject(intent.getStringExtra("actorObject"))

            // Set the adapter with the (still) empty ArrayList.
            castMovieArrayList = ArrayList()
            castMovieAdapter = SimilarMovieBaseAdapter(castMovieArrayList, applicationContext)
            binding.castMovieRecyclerView.adapter = castMovieAdapter

            // Set the adapter with the (still) empty ArrayList.
            crewMovieArrayList = ArrayList()
            crewMovieAdapter = SimilarMovieBaseAdapter(crewMovieArrayList, applicationContext)
            binding.crewMovieRecyclerView.adapter = crewMovieAdapter
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        checkNetwork()
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        val isDarkTheme = uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
        val color: Int = if (isDarkTheme) {
            Color.BLACK
        } else {
            Color.WHITE
        }

        setTitleClickListener(binding.castMovieTitle, binding.castMovieRecyclerView, CAST_MOVIE_VIEW_PREFERENCE)

        setTitleClickListener(binding.crewMovieTitle, binding.crewMovieRecyclerView, CREW_MOVIE_VIEW_PREFERENCE)

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

                        darkMutedColor = palette.getDarkMutedColor(palette.getMutedColor(Color.TRANSPARENT))

                        lightMutedColor = palette.getLightMutedColor(palette.getMutedColor(Color.TRANSPARENT))
                        val gradientDrawable: GradientDrawable
                        val mutedColor: Int

                        if (isDarkTheme) {
                            gradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(darkMutedColor, color))
                            mutedColor = darkMutedColor
                        } else {
                            gradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(lightMutedColor, color))
                            mutedColor = lightMutedColor
                        }
                        binding.root.background = gradientDrawable
                        binding.appBarLayout.setBackgroundColor(Color.TRANSPARENT)
                        binding.firstDivider.dividerColor = mutedColor
                        binding.secondDivider.dividerColor = mutedColor
                        binding.thirdDivider.dividerColor = mutedColor

                        val animation = AnimationUtils.loadAnimation(
                            applicationContext, R.anim.fade_in
                        )
                        binding.actorImage.startAnimation(animation)
                    }

                    override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
                        val fallbackDrawable = errorDrawable ?: ContextCompat.getColor(context, R.color.md_theme_surface)

                        binding.actorImage.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_surface))
                        binding.actorImage.setBackgroundColor(fallbackDrawable as Int)
                    }

                    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                        // Ensure placeHolderDrawable is not null
                        placeHolderDrawable ?: ContextCompat.getColor(context, R.color.md_theme_outline)
                        binding.actorImage.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_surface))
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
    }

    override fun doNetworkWork() {
        // Get the shows the person was a part of.
        if (!mActorMoviesLoaded) {
            fetchActorMovies()
        }

        // Load person details
        if (!mActorDetailsLoaded) {
            fetchActorDetails()
        }
    }

    /**
     * Sets an OnClickListener on the title above the RecyclerView.
     * collapseViewEditor needs to be initialised.
     *
     * @param title      the title that the OnClickListener will be set on.
     * @param view       the view that will be expanded/collapsed.
     * @param preference the preferences that needs to be edited to remember the choice.
     */
    private fun setTitleClickListener(title: TextView, view: RecyclerView, preference: String) {
        title.setOnClickListener {
            val collapseViewEditor = collapseViewPreferences.edit()
            if (collapseViewPreferences.getBoolean(preference, false)) {
                // The view needs to expand.
                view.visibility = View.VISIBLE

                // The preference needs to change.
                collapseViewEditor.putBoolean(preference, false)
            } else {
                // The view needs to collapse.
                view.visibility = View.GONE

                // The preference needs to change.
                collapseViewEditor.putBoolean(preference, true)
            }
            // Set the changes.
            collapseViewEditor.apply()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.save_menu, menu)
        val saveItem = menu.findItem(R.id.action_save)
        val dbHelper = PeopleDatabaseHelper(context)
        if (dbHelper.personExists(actorId)) {
            saveItem.setIcon(R.drawable.ic_star)
        } else {
            saveItem.setIcon(R.drawable.ic_star_border)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_save) {
            val dbHelper = PeopleDatabaseHelper(context)
            val actorId = actorObject.optInt("id")
            if (dbHelper.personExists(actorId)) {
                dbHelper.deleteById(actorId)
                item.setIcon(R.drawable.ic_star_border)
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
                item.setIcon(R.drawable.ic_star)
            }
        }
        return super.onOptionsItemSelected(item)
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
            }
        }

        // If the place of birth is different in the new dataset, change it.
        actorObject.optString("place_of_birth").let { placeOfBirth ->
        if (placeOfBirth != binding.actorPlaceOfBirth.text.toString()) {
            binding.actorPlaceOfBirth.text = getString(R.string.place_of_birth, placeOfBirth)
        }
        }

        // If the birthday is different in the new dataset, change it.
        actorObject.optString("birthday").let { birthday ->
            if (birthday != binding.actorBirthday.text.toString()) {
                binding.actorBirthday.text = getString(R.string.birthday, birthday)
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
     * Coroutine that retrieves the shows that the person is credited
     * for from the API.
     */

    private fun fetchActorMovies() {
        lifecycleScope.launch(Dispatchers.IO) {
            val response = try {
                val url = URL("https://api.themoviedb.org/3/person/$actorId/combined_credits" + getLanguageParameter2(applicationContext))
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $apiReaT")
                    .build()
                client.newCall(request).execute().use { res ->
                    res.body()?.string()
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

                // Add the cast roles to the movieView
                if (reader.getJSONArray("cast").length() <= 0) {
                    binding.castMovieTitle.visibility = View.GONE
                    binding.secondDivider.visibility = View.GONE
                    binding.castMovieRecyclerView.visibility = View.GONE
                } else {
                    val castMovieArray = reader.getJSONArray("cast")
                    castMovieArrayList.clear()
                    for (i in 0 until castMovieArray.length()) {
                        val actorMovies = castMovieArray.getJSONObject(i)
                        castMovieArrayList.add(actorMovies)
                    }

                    // Set a new adapter so the RecyclerView
                    // shows the new items.
                    castMovieAdapter = SimilarMovieBaseAdapter(
                        castMovieArrayList, applicationContext
                    )
                    binding.castMovieRecyclerView.adapter = castMovieAdapter
                }

                // Add the crew roles to the crewMovieView
                if (reader.getJSONArray("crew").length() <= 0) {
                    binding.crewMovieTitle.visibility = View.GONE
                    binding.thirdDivider.visibility = View.GONE
                    binding.crewMovieRecyclerView.visibility = View.GONE
                } else {
                    val crewMovieArray = reader.getJSONArray("crew")
                    crewMovieArrayList.clear()
                    for (i in 0 until crewMovieArray.length()) {
                        val crewMovies = crewMovieArray.getJSONObject(i)
                        crewMovieArrayList.add(crewMovies)
                    }

                    // Set a new adapter so the RecyclerView
                    // shows the new items.
                    crewMovieAdapter = SimilarMovieBaseAdapter(
                        crewMovieArrayList, applicationContext
                    )
                    binding.crewMovieRecyclerView.adapter = crewMovieAdapter
                    mActorMoviesLoaded = true
                }
            } catch (je: JSONException) {
                je.printStackTrace()
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
            val responseBody = response.body()!!.string()
            return JSONObject(responseBody)
        }
    }

    companion object {
        private const val COLLAPSE_VIEW = "collapseView"
        private const val CAST_MOVIE_VIEW_PREFERENCE = "CastActivity.castMovieView"
        private const val CREW_MOVIE_VIEW_PREFERENCE = "CastActivity.crewMovieView"
        private const val DYNAMIC_COLOR_DETAILS_ACTIVITY = "dynamic_color_details_activity"
    }
}
