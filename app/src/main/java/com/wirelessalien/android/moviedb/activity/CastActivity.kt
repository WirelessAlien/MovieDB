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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    private var API_KEY: String? = null

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
        binding = ActivityCastBinding.inflate(
            layoutInflater
        )
        setContentView(binding.root)
        setNavigationDrawer()
        setBackButtons()
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        API_KEY = ConfigHelper.getConfigValue(applicationContext, "api_key")
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

        // Get the actorObject from the intent that contains the necessary
        // data to display the right person and related RecyclerViews.
        // Send the JSONObject to setActorData() so all the data
        // will be displayed on the screen.
        val intent = intent
        try {
            setActorData(JSONObject(intent.getStringExtra("actorObject")))
            actorObject = JSONObject(intent.getStringExtra("actorObject"))

            // Set the adapter with the (still) empty ArrayList.
            castMovieArrayList = ArrayList()
            castMovieAdapter = SimilarMovieBaseAdapter(
                castMovieArrayList,
                applicationContext
            )
            binding.castMovieRecyclerView.adapter = castMovieAdapter

            // Set the adapter with the (still) empty ArrayList.
            crewMovieArrayList = ArrayList()
            crewMovieAdapter = SimilarMovieBaseAdapter(
                crewMovieArrayList,
                applicationContext
            )
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

        // Set a listener to change the visibility when the TextView is clicked.
        setTitleClickListener(
            binding.castMovieTitle,
            binding.castMovieRecyclerView,
            CAST_MOVIE_VIEW_PREFERENCE
        )
        setTitleClickListener(
            binding.crewMovieTitle,
            binding.crewMovieRecyclerView,
            CREW_MOVIE_VIEW_PREFERENCE
        )
        if (preferences.getBoolean(DYNAMIC_COLOR_DETAILS_ACTIVITY, false)) {
            if (actorObject.has("profile_path") && binding.actorImage.drawable == null) {
                val imageUrl: String = try {
                    "https://image.tmdb.org/t/p/h632" + actorObject.getString("profile_path")
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                }
                // Set the loaded bitmap to your ImageView before generating the Palette
                target = object : Target {
                    override fun onBitmapLoaded(bitmap: Bitmap, from: LoadedFrom) {
                        // Set the loaded bitmap to your ImageView before generating the Palette
                        binding.actorImage.setImageBitmap(bitmap)
                        Palette.from(bitmap).generate { palette: Palette? ->
                            darkMutedColor = palette!!.getDarkMutedColor(
                                palette.getMutedColor(
                                    Color.TRANSPARENT
                                )
                            )
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
                        }
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
                        placeHolderDrawable ?: ContextCompat.getColor(context, R.color.md_theme_primary)
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
                Log.d("CastActivity", "Person deleted from database")
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
                Log.d("CastActivity", "Person saved to database")
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Sets the data gotten from the actorObject to the appropriate views.
     *
     * @param actorObject the JSONObject that it takes the data from.
     */
    private fun setActorData(actorObject: JSONObject) {

        // Check if actorObject values differ from the current values,
        // if they do, use the actorObject values (as they are probably
        // more recent).
        try {
            // Set the actorId
            if (actorObject.has("id")) {
                actorId = actorObject.getString("id").toInt()
            }

            // If the name is different in the new dataset, change it.
            if (actorObject.has("name") && actorObject.getString("name") != binding.actorName.text.toString()) {
                binding.actorName.text = actorObject.getString("name")
            }

            // If the place of birth is different in the new dataset, change it.
            if (actorObject.has("place_of_birth") && actorObject.getString("place_of_birth") != binding.actorPlaceOfBirth
                    .text.toString()
            ) {
                binding.actorPlaceOfBirth.text =
                    getString(R.string.place_of_birth) + actorObject.getString("place_of_birth")
            }

            // If the birthday is different in the new dataset, change it.
            if (actorObject.has("birthday") && actorObject.getString("birthday") != binding.actorBirthday
                    .text.toString()
            ) {
                binding.actorBirthday.text =
                    getString(R.string.birthday) + actorObject.getString("birthday")
            }

            // If the biography is different in the new dataset, change it.
            if (actorObject.has("biography") &&
                actorObject.getString("biography") != binding.actorBiography.text.toString()
            ) {
                binding.actorBiography.text = actorObject.getString("biography")
            }
            if (actorObject.getString("biography") == "") {
                fetchActorDetails()
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Coroutine that retrieves the shows that the person is credited
     * for from the API.
     */

    private fun fetchActorMovies() {
        CoroutineScope(Dispatchers.Main).launch {
            val response = withContext(Dispatchers.IO) { doInBackground() }
            onPostExecute(response)
        }
    }

    private fun doInBackground(): String? {
        var line: String?
        val stringBuilder = StringBuilder()

        // Load the webpage with the person's shows.
        try {
            val url = URL(
                "https://api.themoviedb.org/3/person/" +
                        actorId + "/combined_credits?api_key=" + API_KEY + getLanguageParameter(
                    applicationContext
                )
            )
            val urlConnection = url.openConnection()
            try {
                val bufferedReader = BufferedReader(
                    InputStreamReader(
                        urlConnection.getInputStream()
                    )
                )

                // Create one long string of the webpage.
                while (bufferedReader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }

                // Close connection and return the data from the webpage.
                bufferedReader.close()
                return stringBuilder.toString()
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            }
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }

        // Loading the dataset failed, return null.
        return null
    }

    private fun onPostExecute(response: String?) {
        if (!response.isNullOrEmpty()) {
            // Break the JSON dataset down and add the JSONObjects to the array.
            try {
                val reader = JSONObject(response)

                // Add the cast roles to the movieView
                if (reader.getJSONArray("cast").length() <= 0) {
                    // This person has no roles as cast, do not show the
                    // cast related views.
                    val textView = mActivity.findViewById<TextView>(R.id.castMovieTitle)
                    val view = mActivity.findViewById<View>(R.id.secondDivider)
                    textView.visibility = View.GONE
                    view.visibility = View.GONE
                    binding.castMovieRecyclerView.visibility = View.GONE
                } else {
                    val castMovieArray = reader.getJSONArray("cast")
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
                    // This person has no roles as crew, do not show the
                    // crew related views.
                    val textView = mActivity.findViewById<TextView>(R.id.crewMovieTitle)
                    val view = mActivity.findViewById<View>(R.id.thirdDivider)
                    textView.visibility = View.GONE
                    view.visibility = View.GONE
                    binding.crewMovieRecyclerView.visibility = View.GONE
                } else {
                    val crewMovieArray = reader.getJSONArray("crew")
                    for (i in 0 until crewMovieArray.length()) {
                        val crewMovies = crewMovieArray.getJSONObject(i)

                        // TODO: Build a lightweight duplicate checker
                        // (any heavy ones will cause the application to crash).
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
    // Define a single instance of OkHttpClient
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }

    // Use a fixed thread pool for coroutines
    private val coroutineDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

    private fun fetchActorDetails() {
        CoroutineScope(coroutineDispatcher).launch {
            val baseUrl = "https://api.themoviedb.org/3/person/$actorId?api_key=$API_KEY"
            val urlWithLanguage = baseUrl + getLanguageParameter(applicationContext)
            try {
                // First request with language parameter
                var actorData = fetchActorDetails(client, urlWithLanguage)

                // Check if biography is empty
                if (actorData.getString("biography").isEmpty()) {
                    // Second request without language parameter
                    actorData = fetchActorDetails(client, baseUrl)
                }
                withContext(Dispatchers.Main) {
                    onPostExecute(actorData)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class, JSONException::class)
    private fun fetchActorDetails(client: OkHttpClient, url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val responseBody = response.body()!!.string()
            return JSONObject(responseBody)
        }
    }

    private fun onPostExecute(actorData: JSONObject?) {
        actorData?.let { setActorData(it) }
    }

    companion object {
        private const val COLLAPSE_VIEW = "collapseView"
        private const val CAST_MOVIE_VIEW_PREFERENCE = "CastActivity.castMovieView"
        private const val CREW_MOVIE_VIEW_PREFERENCE = "CastActivity.crewMovieView"
        private const val DYNAMIC_COLOR_DETAILS_ACTIVITY = "dynamic_color_details_activity"
    }
}
