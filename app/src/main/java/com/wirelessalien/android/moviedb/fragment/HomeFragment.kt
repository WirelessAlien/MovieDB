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
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.CarouselSnapHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.BaseActivity
import com.wirelessalien.android.moviedb.activity.PersonActivity
import com.wirelessalien.android.moviedb.adapter.NowPlayingMovieAdapter
import com.wirelessalien.android.moviedb.adapter.TrendingPagerAdapter
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.util.Date
import java.util.Locale
import java.util.Optional

class HomeFragment : BaseFragment() {
    private var mSearchQuery: String? = null
    private var currentSearchPage = 0
    private var loading = true
    private lateinit var tvShowView: RecyclerView
    private lateinit var mUpcomingTVShowView: RecyclerView
    private lateinit var mUpcomingMovieView: RecyclerView
    private var apiKey: String? = null
    private var mShowListLoaded = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    val date = Date()
    private lateinit var mHomeShowAdapter: NowPlayingMovieAdapter
    private lateinit var mTVShowArrayList: ArrayList<JSONObject>
    private lateinit var mHomeShowArrayList: ArrayList<JSONObject>
    private lateinit var mUpcomingTVShowArrayList: ArrayList<JSONObject>
    private lateinit var mUpcomingMovieArrayList: ArrayList<JSONObject>
    private lateinit var mTVShowAdapter: NowPlayingMovieAdapter
    private lateinit var mUpcomingMovieAdapter: NowPlayingMovieAdapter
    private lateinit var mUpcomingTVAdapter: NowPlayingMovieAdapter
    private lateinit var trandingRv: RecyclerView
    private lateinit var searchBar: SearchBar
    private lateinit var searchView: SearchView
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var mHomeSearchShowAdapter: NowPlayingMovieAdapter
    private lateinit var mHomeSearchShowArrayList: ArrayList<JSONObject>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        apiKey = ConfigHelper.getConfigValue(
            requireContext().applicationContext,
            "api_read_access_token"
        )
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        createShowList()
    }

    override fun doNetworkWork() {
        if (!mShowListLoaded) {
            lifecycleScope.launch {
                fetchNowPlayingMovies()
                fetchNowPlayingTVShows()
                fetchTrendingList()
                fetchUpcomingMovies()
                fetchUpcomingTVShows()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val fragmentView = inflater.inflate(R.layout.fragment_home, container, false)
        showMovieList(fragmentView)
        showTVShowList(fragmentView)
        showTrendingList(fragmentView)
        showUpcomingMovieList(fragmentView)
        showUpcomingTVShowList(fragmentView)
        val fab2 = requireActivity().findViewById<FloatingActionButton>(R.id.fab2)
        fab2.visibility = View.GONE
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.visibility = View.GONE
        searchBar = fragmentView.findViewById(R.id.searchbar)
        searchView = requireActivity().findViewById(R.id.search_view)
        searchResultsRecyclerView =
            requireActivity().findViewById(R.id.search_results_recycler_view)
        searchBar.setOnClickListener { searchView.show() }
        searchView.editText.addTextChangedListener(object : TextWatcher {
            private val handler = Handler(Looper.getMainLooper())
            private var workRunnable: Runnable? = null
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (workRunnable != null) {
                    handler.removeCallbacks(workRunnable!!)
                }
                workRunnable = Runnable { performSearch(s.toString()) }
                handler.postDelayed(workRunnable!!, 1000)
            }

            override fun afterTextChanged(s: Editable) {}
        })

        // Setup RecyclerView and Adapter for search results
        mHomeSearchShowArrayList = ArrayList()
        mHomeSearchShowAdapter = NowPlayingMovieAdapter(mHomeSearchShowArrayList)
        val gridLayoutManager = GridLayoutManager(context, 3)
        searchResultsRecyclerView.layoutManager = gridLayoutManager
        searchResultsRecyclerView.adapter = mHomeSearchShowAdapter
        searchResultsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                    val visibleItemCount = gridLayoutManager.childCount
                    val totalItemCount = gridLayoutManager.itemCount
                    val pastVisibleItems = gridLayoutManager.findFirstVisibleItemPosition()
                    if (!loading && visibleItemCount + pastVisibleItems >= totalItemCount) {
                        loading = true
                        currentSearchPage++
                        performSearch(searchView.editText.text.toString())
                    }
                }
            }
        })
        val peopleBtn = fragmentView.findViewById<Button>(R.id.peopleBtn)
        peopleBtn.setOnClickListener {
            val intent = Intent(requireContext(), PersonActivity::class.java)
            startActivity(intent)
        }
        return fragmentView
    }

    private fun showTrendingList(fragmentView: View) {
        trandingRv = fragmentView.findViewById(R.id.trendingViewPager)
        val layoutManager = CarouselLayoutManager()
        trandingRv.layoutManager = layoutManager
        val snapHelper = CarouselSnapHelper()
        snapHelper.attachToRecyclerView(trandingRv)
        val adapter = TrendingPagerAdapter(ArrayList())
        trandingRv.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.visibility = View.GONE
        val fab2 = requireActivity().findViewById<FloatingActionButton>(R.id.fab2)
        fab2.visibility = View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        val item = menu.findItem(R.id.action_search)
        if (item != null) {
            item.setVisible(false)
            item.setEnabled(false)
        }
    }

    private fun performSearch(query: String) {
        if (query != mSearchQuery) {
            currentSearchPage = 1
            mHomeSearchShowArrayList.clear()
        }
        mSearchQuery = query
        searchAsync(query)
    }

    private fun createShowList() {
        mHomeShowArrayList = ArrayList()
        mHomeShowAdapter = NowPlayingMovieAdapter(mHomeShowArrayList)
        mTVShowArrayList = ArrayList()
        mTVShowAdapter = NowPlayingMovieAdapter(mTVShowArrayList)
        mHomeSearchShowArrayList = ArrayList()
        mHomeSearchShowAdapter = NowPlayingMovieAdapter(mHomeSearchShowArrayList)
        mUpcomingTVShowArrayList = ArrayList()
        mUpcomingTVAdapter = NowPlayingMovieAdapter(mUpcomingTVShowArrayList)
        mUpcomingMovieArrayList = ArrayList()
        mUpcomingMovieAdapter = NowPlayingMovieAdapter(mUpcomingMovieArrayList)
        (requireActivity() as BaseActivity).checkNetwork()
    }

    private fun setupRecyclerView(
        recyclerView: RecyclerView?,
        layoutManager: LinearLayoutManager,
        adapter: RecyclerView.Adapter<*>?
    ) {
        recyclerView!!.layoutManager = layoutManager
        recyclerView.adapter = adapter
    }

    private fun showMovieList(fragmentView: View) {
        mShowView = fragmentView.findViewById(R.id.nowPlayingRecyclerView)
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        setupRecyclerView(mShowView, layoutManager, mHomeShowAdapter)
    }

    private fun showTVShowList(fragmentView: View) {
        tvShowView = fragmentView.findViewById(R.id.nowPlayingTVRecyclerView)
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        setupRecyclerView(tvShowView, layoutManager, mTVShowAdapter)
    }

    private fun showUpcomingTVShowList(fragmentView: View) {
        mUpcomingTVShowView = fragmentView.findViewById(R.id.upcomingTVRecyclerView)
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        setupRecyclerView(mUpcomingTVShowView, layoutManager, mUpcomingTVAdapter)
    }

    private fun showUpcomingMovieList(fragmentView: View) {
        mUpcomingMovieView = fragmentView.findViewById(R.id.upcomingMovieRecyclerView)
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        setupRecyclerView(mUpcomingMovieView, layoutManager, mUpcomingMovieAdapter)
    }

    fun search(query: String) {
        mHomeSearchShowArrayList = ArrayList()
        mHomeSearchShowAdapter = NowPlayingMovieAdapter(mHomeSearchShowArrayList)
        currentSearchPage = 1
        mSearchQuery = query
        searchAsync(query)
    }

    override fun cancelSearch() {
        mSearchView = false
        mShowView.adapter = mHomeShowAdapter
    }

    private suspend fun fetchNowPlayingMovies() {
        val response = withContext(Dispatchers.IO) {
            var response: String? = null
            try {
                val url = URL("https://api.themoviedb.org/3/movie/now_playing" + BaseActivity.getLanguageParameter2(requireContext()) + "&page=1" + "&" + BaseActivity.getRegionParameter(requireContext()))
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $apiKey")
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

        if (isAdded && !response.isNullOrEmpty()) {
            handleMovieResponse(response)
        }
    }

    private fun handleMovieResponse(response: String?) {
        if (isAdded && !response.isNullOrEmpty()) {
            try {
                val reader = JSONObject(response)
                val arrayData = reader.getJSONArray("results")
                mHomeShowArrayList.clear()
                for (i in 0 until arrayData.length()) {
                    val websiteData = arrayData.getJSONObject(i)
                    mHomeShowArrayList.add(websiteData)
                }
                mShowView.adapter = mHomeShowAdapter
                mShowListLoaded = true
                hideProgressBar()
            } catch (je: JSONException) {
                je.printStackTrace()
                hideProgressBar()
            }
        }
        loading = false
    }

    private suspend fun fetchNowPlayingTVShows() {
        val response = withContext(Dispatchers.IO) {
            var response: String? = null
            try {
                val url = URL("https://api.themoviedb.org/3/discover/tv" + BaseActivity.getLanguageParameter2(requireContext()) + "&page=1&sort_by=popularity.desc&" + BaseActivity.getRegionParameter2(requireContext())  + "&with_watch_monetization_types=flatrate|free|ads|rent|buy&air_date.lte=" + dateFormat.format(date)+ "&air_date.gte=" + dateFormat.format(date) + "&" + BaseActivity.getTimeZoneParameter(requireContext()))
                Log.d("TAG", url.toString())
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $apiKey")
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

        if (isAdded && !response.isNullOrEmpty()) {
            handleTVResponse(response)
        }
    }

    private fun handleTVResponse(response: String?) {
        if (isAdded && !response.isNullOrEmpty()) {
            try {
                val reader = JSONObject(response)
                val arrayData = reader.getJSONArray("results")
                mTVShowArrayList.clear()
                for (i in 0 until arrayData.length()) {
                    val websiteData = arrayData.getJSONObject(i)
                    mTVShowArrayList.add(websiteData)
                }
                tvShowView.adapter = mTVShowAdapter
                mShowListLoaded = true
                hideProgressBar()
            } catch (je: JSONException) {
                je.printStackTrace()
                hideProgressBar()
            }
        }
        loading = false
    }

    private suspend fun fetchUpcomingTVShows() {
        val calendar: Calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val dateAfterWeek = calendar.time
        val response = withContext(Dispatchers.IO) {
            var response: String? = null
            try {
                val url = URL("https://api.themoviedb.org/3/discover/tv" + BaseActivity.getLanguageParameter2(requireContext()) + "&page=1&sort_by=popularity.desc&" + BaseActivity.getRegionParameter2(requireContext())  + "&with_watch_monetization_types=flatrate|free|ads|rent|buy&&air_date.lte=" + dateFormat.format(dateAfterWeek) + "&air_date.gte=" + dateFormat.format(date) + "&" + BaseActivity.getTimeZoneParameter(requireContext()))
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $apiKey")
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

        if (isAdded && !response.isNullOrEmpty()) {
            handleUpcomingTVResponse(response)
        }
    }

    private fun handleUpcomingTVResponse(response: String?) {
        if (isAdded && !response.isNullOrEmpty()) {
            try {
                val reader = JSONObject(response)
                val arrayData = reader.getJSONArray("results")
                mUpcomingTVShowArrayList.clear()
                for (i in 0 until arrayData.length()) {
                    val websiteData = arrayData.getJSONObject(i)
                    mUpcomingTVShowArrayList.add(websiteData)
                }
                mUpcomingTVShowView.adapter = mUpcomingTVAdapter
                mShowListLoaded = true
                hideProgressBar()
            } catch (je: JSONException) {
                je.printStackTrace()
                hideProgressBar()
            }
        }
        loading = false
    }

    private suspend fun fetchUpcomingMovies() {
        val response = withContext(Dispatchers.IO) {
            var response: String? = null
            try {
                val url = URL("https://api.themoviedb.org/3/movie/upcoming" + BaseActivity.getLanguageParameter2(requireContext()) + "&page=1" + "&" + BaseActivity.getRegionParameter(requireContext()))
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $apiKey")
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

        if (isAdded && !response.isNullOrEmpty()) {
            handleUpcomingMovieResponse(response)
        }
    }

    private fun handleUpcomingMovieResponse(response: String?) {
        if (isAdded && !response.isNullOrEmpty()) {
            try {
                val reader = JSONObject(response)
                val arrayData = reader.getJSONArray("results")
                mUpcomingMovieArrayList.clear()
                for (i in 0 until arrayData.length()) {
                    val websiteData = arrayData.getJSONObject(i)
                    mUpcomingMovieArrayList.add(websiteData)
                }
                mUpcomingMovieView.adapter = mUpcomingMovieAdapter
                mShowListLoaded = true
                hideProgressBar()
            } catch (je: JSONException) {
                je.printStackTrace()
                hideProgressBar()
            }
        }
        loading = false
    }

    private suspend fun fetchTrendingList() {
        val response = withContext(Dispatchers.IO) {
            var response: String? = null
            try {
                val url = URL("https://api.themoviedb.org/3/trending/all/day" + BaseActivity.getLanguageParameter2(requireContext()))
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $apiKey")
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

        if (isAdded && !response.isNullOrEmpty()) {
            handleTrendingResponse(response)
        }
    }

    private fun handleTrendingResponse(response: String?) {
        if (isAdded && !response.isNullOrEmpty()) {
            val trendingArrayList = ArrayList<JSONObject>()
            try {
                val reader = JSONObject(response)
                val arrayData = reader.getJSONArray("results")
                trendingArrayList.clear()
                for (i in 0 until arrayData.length()) {
                    val websiteData = arrayData.getJSONObject(i)
                    val mediaType = websiteData.getString("media_type")
                    if (mediaType == "movie" || mediaType == "tv") {
                        trendingArrayList.add(websiteData)
                    }
                }
                val adapter = trandingRv.adapter as TrendingPagerAdapter?
                if (adapter != null) {
                    adapter.updateData(trendingArrayList)
                    adapter.notifyDataSetChanged()
                }
                hideProgressBar()
            } catch (je: JSONException) {
                je.printStackTrace()
                hideProgressBar()
            }
        }
    }

    private fun searchAsync(query: String) {
        val progressBar = Optional.ofNullable(
            searchView.findViewById<CircularProgressIndicator>(R.id.search_progress_bar)
        )
        progressBar.ifPresent { bar: CircularProgressIndicator -> bar.visibility = View.VISIBLE }
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) { doInBackground(query) }
            if (response != null) {
                onPostExecute(response)
            }
            hideSearchProgressBar()
        }
    }

    private fun doInBackground(query: String): String? {
        var response: String? = null
        try {
            val url = URL(
                "https://api.themoviedb.org/3/search/multi?"
                        + "query=" + query + "&page=" + currentSearchPage
            )
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Content-Type", "application/json;charset=utf-8")
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            try {
                client.newCall(request).execute().use { res ->
                    if (res.body() != null) {
                        response = res.body()!!.string()
                    }
                }
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            }
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }
        return response
    }

    private fun onPostExecute(response: String) {
        try {
            val reader = JSONObject(response)
            val arrayData = reader.getJSONArray("results")
            for (i in 0 until arrayData.length()) {
                val movieData = arrayData.getJSONObject(i)
                if (movieData.getString("media_type") != "person") {
                    mHomeSearchShowArrayList.add(movieData)
                }
            }
            mHomeSearchShowAdapter.notifyDataSetChanged()
            loading = false
        } catch (je: JSONException) {
            je.printStackTrace()
        }
    }

    private fun hideProgressBar() {
        if (isAdded) {
            val progressBar =
                Optional.ofNullable(requireActivity().findViewById<CircularProgressIndicator>(R.id.progressBar))
            progressBar.ifPresent { bar: CircularProgressIndicator -> bar.visibility = View.GONE }
        }
    }

    private fun hideSearchProgressBar() {
        val progressBar = Optional.ofNullable(
            searchView.findViewById<CircularProgressIndicator>(R.id.search_progress_bar)
        )
        progressBar.ifPresent { bar: CircularProgressIndicator -> bar.visibility = View.GONE }
    }

    companion object {
        fun newInstance(): HomeFragment {
            return HomeFragment()
        }
    }
}
