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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContract
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.BaseActivity
import com.wirelessalien.android.moviedb.activity.FilterActivity
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.Optional

class ShowFragment : BaseFragment() {
    private var apiKey: String? = null
    private var mListType: String? = null
    override var mSearchView = false
    private var mSearchQuery: String? = null
    private var filterParameter = ""
    private var filterChanged = false

    private var visibleThreshold = 0
    private var currentPage = 0
    private var currentSearchPage = 0
    private var previousTotal = 0
    private var loading = true
    private var pastVisibleItems = 0
    private var visibleItemCount = 0
    private var totalItemCount = 0
    private var mShowListLoaded = false
    private val dateFormat = android.icu.text.SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    val date = Date()
    private val showIdSet = HashSet<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        apiKey = ConfigHelper.getConfigValue(requireContext().applicationContext, "api_key")
        mListType = if (arguments != null) {
            requireArguments().getString(ARG_LIST_TYPE)
        } else {
            // Movie is the default case.
            SectionsPagerAdapter.MOVIE
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val gridSizePreference = preferences.getInt(GRID_SIZE_PREFERENCE, 3)
        visibleThreshold = gridSizePreference * gridSizePreference

        mShowArrayList = ArrayList()
        mShowGenreList = HashMap()
        mShowAdapter = ShowBaseAdapter(
            mShowArrayList, mShowGenreList,
            preferences.getBoolean(SHOWS_LIST_PREFERENCE, false), false
        )
        (requireActivity() as BaseActivity).checkNetwork()

        // Set filterParameter based on mListType
        filterParameter = if (mListType == SectionsPagerAdapter.MOVIE) {
            "sort_by=popularity.desc&" + BaseActivity.getRegionParameter(requireContext())
        } else {
            "sort_by=popularity.desc&" + BaseActivity.getRegionParameter2(requireContext()) + "&with_watch_monetization_types=flatrate|free|ads|rent|buy"
        }

        // Use persistent filtering if it is enabled.
        if (preferences.getBoolean(PERSISTENT_FILTERING_PREFERENCE, false)) {
            filterShows()
        } else {
            fetchShowList(arrayOf(mListType, "1"))
        }
    }

    override fun doNetworkWork() {
        if (!mGenreListLoaded) {
            fetchGenreList(mListType!!)
        }
        if (!mShowListLoaded) {
            fetchShowList(arrayOf(mListType, "1"))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val fragmentView = inflater.inflate(R.layout.fragment_show, container, false)
        showShowList(fragmentView)
        val fab2 = requireActivity().findViewById<FloatingActionButton>(R.id.fab2)
        fab2.visibility = View.GONE
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.setImageResource(R.drawable.ic_filter_list)
        fab.isEnabled = true
        fab.setOnClickListener {
            // Start the FilterActivity
            filterRequestLauncher.launch(Intent())
        }
        return fragmentView
    }

    override fun onResume() {
        super.onResume()
        val fab2 = requireActivity().findViewById<FloatingActionButton>(R.id.fab2)
        fab2.visibility = View.GONE
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.visibility = View.VISIBLE
        fab.setImageResource(R.drawable.ic_filter_list)
        fab.isEnabled = true
        fab.setOnClickListener {
            // Start the FilterActivity
            filterRequestLauncher.launch(Intent())
        }
    }

    private var filterRequestContract: ActivityResultContract<Intent, Boolean> =
        object : ActivityResultContract<Intent, Boolean>() {
            override fun createIntent(context: Context, input: Intent): Intent {
                return Intent(context, FilterActivity::class.java).putExtra("mode", mListType)
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
                return resultCode == Activity.RESULT_OK
            }
        }

    private var filterRequestLauncher = registerForActivityResult(
        filterRequestContract
    ) { result: Boolean ->
        if (result) {
            filterShows()
        }
    }

    /**
     * Filters the list of shows based on the preferences set in FilterActivity.
     */
    private fun filterShows() {
        // Get the parameters from the filter activity and reload the adapter
        val sharedPreferences = requireActivity().getSharedPreferences(
            FilterActivity.FILTER_PREFERENCES,
            Context.MODE_PRIVATE
        )

        val calendar: android.icu.util.Calendar = android.icu.util.Calendar.getInstance()
        calendar.time = date
        calendar.add(android.icu.util.Calendar.DAY_OF_YEAR, 500)
        val dateAfterYear = calendar.time

        var sortPreference: String?
        if (sharedPreferences.getString(FilterActivity.FILTER_SORT, null)
                .also { sortPreference = it } != null
        ) filterParameter = when (sortPreference) {
            "best_rated" ->
                if (mListType == SectionsPagerAdapter.MOVIE) {
                    BaseActivity.getRegionParameter(requireContext()) + "&sort_by=vote_average.desc"
                } else {
                    "sort_by=vote_average.desc&" + BaseActivity.getRegionParameter2(requireContext())  + "&with_watch_monetization_types=flatrate|free|ads|rent|buy"
                }
            "release_date" ->
                if (mListType == SectionsPagerAdapter.MOVIE) {
                    BaseActivity.getRegionParameter(requireContext()) + "&primary_release_date.lte=" + dateFormat.format(dateAfterYear) + "&sort_by=primary_release_date.desc"
                } else {
                    "sort_by=first_air_date.desc&" + BaseActivity.getRegionParameter2(requireContext()) + "&with_watch_monetization_types=flatrate|free|ads|rent|buy"
                }
            "alphabetic_order" ->
                if (mListType == SectionsPagerAdapter.MOVIE) {
                    "sort_by=title.desc"
                } else {
                    "sort_by=name.desc"
                }
            else ->
                if (mListType == SectionsPagerAdapter.MOVIE) {
                    "sort_by=popularity.desc&" + BaseActivity.getRegionParameter(requireContext())
                } else {
                    "sort_by=popularity.desc&" + BaseActivity.getRegionParameter2(requireContext()) + "&with_watch_monetization_types=flatrate|free|ads|rent|buy"
                }
        }


        // Add the dates as constraints to the new API call.
        var datePreference: String?
        if (sharedPreferences.getString(FilterActivity.FILTER_DATES, null)
                .also { datePreference = it } != null
        ) {
            when (datePreference) {
                "in_theater" -> {
                    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val today = simpleDateFormat.format(Date())
                    val calendar = GregorianCalendar.getInstance()
                    calendar.time = Date()
                    calendar.add(Calendar.DAY_OF_YEAR, -31)
                    val monthAgo = simpleDateFormat.format(calendar.time)
                    filterParameter += ("&primary_release_date.gte=" + monthAgo
                            + "&primary_release_date.lte=" + today)
                }

                "between_dates" -> {
                    var startDate: String
                    if (sharedPreferences.getString(FilterActivity.FILTER_START_DATE, null)
                            .also { startDate = it!! } != null
                    ) {
                        filterParameter += "&primary_release_date.gte=$startDate"
                    }
                    var endDate: String
                    if (sharedPreferences.getString(FilterActivity.FILTER_END_DATE, null)
                            .also { endDate = it!! } != null
                    ) {
                        filterParameter += "&primary_release_date.lte=$endDate"
                    }
                }

                else -> {}
            }
        }

        // Add the genres to be included as constraints to the API call.
        val withGenres = FilterActivity.convertStringToArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_WITH_GENRES,
                null
            ), ", "
        )
        if (withGenres != null && withGenres.isNotEmpty()) {
            filterParameter += "&with_genres="
            for (i in withGenres.indices) {
                filterParameter += withGenres[i]
                if (i + 1 != withGenres.size) {
                    filterParameter += ","
                }
            }
        }

        // Add the genres to be excluded as constraints to the API call.
        val withoutGenres = FilterActivity.convertStringToArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_WITHOUT_GENRES,
                null
            ), ", "
        )
        if (withoutGenres != null && withoutGenres.isNotEmpty()) {
            filterParameter += "&without_genres="
            for (i in withoutGenres.indices) {
                filterParameter += withoutGenres[i]
                if (i + 1 != withoutGenres.size) {
                    filterParameter += ","
                }
            }
        }

        // Add keyword-IDs as the constraints to the API call.
        var withKeywords: String
        if (sharedPreferences.getString(FilterActivity.FILTER_WITH_KEYWORDS, "")
                .also { withKeywords = it!! } != ""
        ) {
            filterParameter += "&with_keywords=$withKeywords"
        }
        var withoutKeywords: String
        if (sharedPreferences.getString(FilterActivity.FILTER_WITHOUT_KEYWORDS, "")
                .also { withoutKeywords = it!! } != ""
        ) {
            filterParameter += "&without_keywords=$withoutKeywords"
        }
        filterChanged = true
        fetchShowList(arrayOf(mListType, "1"))
    }

    /**
     * Visualises the list of shows on the screen.
     *
     * @param fragmentView the view to attach the ListView to.
     */
    override fun showShowList(fragmentView: View) {
        super.showShowList(fragmentView)

        // Dynamically load new pages when user scrolls down.
        mShowView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) { // Check for scroll down and if user is actively scrolling.
                    visibleItemCount = mShowLinearLayoutManager.childCount
                    totalItemCount = mShowLinearLayoutManager.itemCount
                    pastVisibleItems = mShowLinearLayoutManager.findFirstVisibleItemPosition()
                    if (loading) {
                        if (totalItemCount > previousTotal) {
                            loading = false
                            previousTotal = totalItemCount
                        }
                    }
                    var threshold = visibleThreshold
                    if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
                        // It is a grid view, so the threshold should be bigger.
                        val gridSizePreference = preferences.getInt(GRID_SIZE_PREFERENCE, 3)
                        threshold = gridSizePreference * gridSizePreference
                    }

                    // When no new pages are being loaded,
                    // but the user is at the end of the list, load the new page.
                    if (!loading && visibleItemCount + pastVisibleItems + threshold >= totalItemCount) {
                        // Load the next page of the content in the background.
                        if (mSearchView) {
                            searchList(mListType, currentSearchPage, mSearchQuery)
                        } else {
                            // Check if the previous request returned any data
                            if (mShowArrayList.size > 0) {
                                currentPage++
                                fetchShowList(arrayOf(mListType, currentPage.toString()))
                            }
                        }
                        loading = true
                    }
                }
            }
        })
    }

    /**
     * Creates the ShowBaseAdapter with the (still empty) ArrayList.
     * Also starts an AsyncTask to load the items for the empty ArrayList.
     *
     * @param query the query to start the AsyncTask with (and that will be added to the
     * API call as search query).
     */
    fun search(query: String?) {
        // Create a separate adapter for the search results.
        mSearchShowArrayList = ArrayList()
        mSearchShowAdapter = ShowBaseAdapter(
            mSearchShowArrayList, mShowGenreList,
            preferences.getBoolean(SHOWS_LIST_PREFERENCE, false), false
        )

        // Cancel old AsyncTask if it exists.
        currentSearchPage = 1
        mSearchQuery = query
        searchList(mListType, 1, query)
    }

    /**
     * Sets the search variable to false and sets original adapter in the RecyclerView.
     */
    override fun cancelSearch() {
        mSearchView = false
        mShowView.adapter = mShowAdapter
        mShowAdapter.notifyDataSetChanged()
    }

    /**
     * Uses Coroutine to retrieve the list with popular shows.
     */
    private fun fetchShowList(params: Array<String?>) {
        CoroutineScope(Dispatchers.Main).launch {
            val listType: String?
            val page: Int

            try {
                if (!isAdded) {
                    return@launch
                }
                val progressBar = Optional.ofNullable(requireActivity().findViewById<CircularProgressIndicator>(R.id.progressBar))
                progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.VISIBLE }

                listType = params[0]
                page = params[1]!!.toInt()

                val response = withContext(Dispatchers.IO) {
                    try {
                        val api_key = ConfigHelper.getConfigValue(
                            requireContext().applicationContext,
                            "api_read_access_token"
                        )
                        val url = URL("https://api.themoviedb.org/3/discover/" + listType + "?" + filterParameter + "&page=" + page + BaseActivity.getLanguageParameter(context))
                        Log.d("TAG4", url.toString())
                        val client = OkHttpClient()
                        val request = Request.Builder()
                            .url(url)
                            .get()
                            .addHeader("Content-Type", "application/json;charset=utf-8")
                            .addHeader("Authorization", "Bearer $api_key")
                            .build()
                        client.newCall(request).execute().use { response ->
                            response.body()?.string()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        null
                    }
                }

                if (isAdded) {
                    handleResponse(response, page)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (isAdded) {
                    val progressBar = Optional.ofNullable(requireActivity().findViewById<CircularProgressIndicator>(R.id.progressBar))
                    progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.GONE }
                }
            }
        }
    }

    private fun handleResponse(response: String?, page: Int) {
        if (isAdded && !response.isNullOrEmpty()) {
            // Keep the user at the same position in the list.
            val position: Int = try {
                mShowLinearLayoutManager.findFirstVisibleItemPosition()
            } catch (npe: NullPointerException) {
                0
            }

            // If the filter has changed, remove the old items
            if (filterChanged) {
                mShowArrayList.clear()
                showIdSet.clear() // Clear the set of IDs

                // Set the previous total back to zero.
                previousTotal = 0

                // Set filterChanged back to false.
                filterChanged = false
            }

            // Convert the JSON data from the webpage into JSONObjects
            try {
                val reader = JSONObject(response)
                val arrayData = reader.getJSONArray("results")
                for (i in 0 until arrayData.length()) {
                    val websiteData = arrayData.getJSONObject(i)
                    val showId = websiteData.getInt("id")

                    // Check if the ID is already in the set
                    if (!showIdSet.contains(showId)) {
                        if (websiteData.getString("overview").isEmpty()) {
                            websiteData.put("overview", "Overview may not be available in the specified language.")
                        }
                        mShowArrayList.add(websiteData)
                        showIdSet.add(showId) // Add the ID to the set
                    }
                }

                // Reload the adapter (with the new page)
                // and set the user to his old position.
                mShowView.adapter = mShowAdapter
                if (page != 1) {
                    mShowView.scrollToPosition(position)
                }
                mShowListLoaded = true
            } catch (je: JSONException) {
                je.printStackTrace()
            }
        }
        loading = false
    }

    /**
     * Uses Coroutine to retrieve the list with shows that fulfill the search query
     * (and are of the requested type which means that nothing will turn up if you
     * search for a series in the movies tab (and there are no movies with the same name).
     */
    private fun searchList(
        listType: String?,
        page: Int,
        query: String?,
    ) {
        if (query.isNullOrEmpty()) {
            // If the query is empty, show the original show list
            cancelSearch()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            if (isAdded) {
                val progressBar = Optional.ofNullable(requireActivity().findViewById<CircularProgressIndicator>(R.id.progressBar))
                progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.VISIBLE }

                val response = withContext(Dispatchers.IO) {
                    var result: String? = null
                    try {
                        val url = URL(
                            "https://api.themoviedb.org/3/search/" +
                                    listType + "?query=" + query + "&page=" + page +
                                    "&api_key=" + apiKey + BaseActivity.getLanguageParameter(context)
                        )
                        val urlConnection = url.openConnection()
                        val bufferedReader = BufferedReader(InputStreamReader(urlConnection.getInputStream()))
                        val stringBuilder = StringBuilder()
                        var line: String?
                        while (bufferedReader.readLine().also { line = it } != null) {
                            stringBuilder.append(line).append("\n")
                        }
                        bufferedReader.close()
                        result = stringBuilder.toString()
                    } catch (ioe: IOException) {
                        ioe.printStackTrace()
                    }
                    result
                }

                handleResponse(response)
                progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.GONE }
            }
        }
    }

    private fun handleResponse(
        response: String?
    ) {
        requireActivity().runOnUiThread {
            val position: Int = try {
                mShowLinearLayoutManager.findFirstVisibleItemPosition()
            } catch (npe: NullPointerException) {
                0
            }

            if (currentSearchPage <= 0) {
                mSearchShowArrayList.clear()
            }

            if (!response.isNullOrEmpty()) {
                try {
                    val reader = JSONObject(response)
                    val arrayData = reader.getJSONArray("results")
                    for (i in 0 until arrayData.length()) {
                        val websiteData = arrayData.getJSONObject(i)
                        if (websiteData.getString("overview").isEmpty()) {
                            websiteData.put("overview", "Overview may not available in the specified language.")
                        }
                        mSearchShowArrayList.add(websiteData)
                    }

                    mSearchView = true
                    mShowView.adapter = mSearchShowAdapter
                    mShowView.scrollToPosition(position)
                } catch (je: JSONException) {
                    je.printStackTrace()
                }
            }
        }
    }

    companion object {
        const val ARG_LIST_TYPE = "arg_list_type"
        fun newInstance(listType: String?): ShowFragment {
            val fragment = ShowFragment()
            val args = Bundle()
            args.putString(ARG_LIST_TYPE, listType)
            fragment.arguments = args
            return fragment
        }
    }
}
