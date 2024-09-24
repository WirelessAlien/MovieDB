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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.BaseActivity
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Optional

class FavoriteFragment : BaseFragment() {
    private var mListType: String? = null
    private var visibleThreshold = 0 // Three times the amount of items in a row
    private var currentPage = 0
    private var previousTotal = 0
    private var loading = true
    private var pastVisibleItems = 0
    private var visibleItemCount = 0
    private var totalItemCount = 0
    private val showIdSet = HashSet<Int>()

    @Volatile
    private var isLoadingData = false
    private var mShowListLoaded = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val gridSizePreference = preferences.getInt(GRID_SIZE_PREFERENCE, 3)
        visibleThreshold = gridSizePreference * gridSizePreference
        createShowList(mListType)
    }

    override fun doNetworkWork() {
        if (!mShowListLoaded) {
            loadFavoriteList(mListType, 1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val fragmentView = inflater.inflate(R.layout.fragment_show, container, false)
        // Initialize mListType with "movie" on first load
        mListType = if (preferences.getBoolean(DEFAULT_MEDIA_TYPE, false)) {
            "tv"
        } else {
            "movie"
        }
        loadFavoriteList(mListType, 1)
        showShowList(fragmentView)
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        updateFabIcon(fab, mListType)
        fab.setOnClickListener { toggleListTypeAndLoad() }
        return fragmentView
    }

    private fun toggleListTypeAndLoad() {
        if (isLoadingData) {
            Toast.makeText(context, R.string.loading_in_progress, Toast.LENGTH_SHORT).show()
            return
        }
        mShowArrayList.clear()
        currentPage = 1
        mListType = if ("movie" == mListType) "tv" else "movie"
        loadFavoriteList(mListType, 1)
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        updateFabIcon(fab, mListType)
    }

    override fun onResume() {
        super.onResume()
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.visibility = View.VISIBLE
        updateFabIcon(fab, mListType)
        fab.setOnClickListener { toggleListTypeAndLoad() }
    }

    private fun updateFabIcon(fab: FloatingActionButton, listType: String?) {
        if ("movie" == listType) {
            fab.setImageResource(R.drawable.ic_movie)
        } else {
            fab.setImageResource(R.drawable.ic_tv_show)
        }
    }

    /**
     * Loads a list of shows from the API.
     *
     */
    private fun createShowList(mode: String?) {

        // Create a MovieBaseAdapter and load the first page
        mShowArrayList = ArrayList()
        mShowGenreList = HashMap()
        mShowAdapter = ShowBaseAdapter(
            mShowArrayList, mShowGenreList,
            preferences.getBoolean(SHOWS_LIST_PREFERENCE, false), false
        )
        (requireActivity() as BaseActivity).checkNetwork()
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
                            currentPage++
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
                        if (mShowArrayList.size > 0) {
                            currentPage++
                            loadFavoriteList(mListType, currentPage)
                        }
                        loading = true
                    }
                }
            }
        })
    }

    private fun loadFavoriteList(listType: String?, page: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            isLoadingData = true
            if (!isAdded) {
                return@launch
            }

            val progressBar = Optional.ofNullable(requireActivity().findViewById<ProgressBar>(R.id.progressBar))
            progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.VISIBLE }

            val response = withContext(Dispatchers.IO) {
                val access_token = preferences.getString("access_token", "")
                val accountId = preferences.getString("account_id", "")
                val url = "https://api.themoviedb.org/4/account/$accountId/$listType/favorites?page=$page"
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json;charset=utf-8")
                    .addHeader("Authorization", "Bearer $access_token")
                    .build()
                try {
                    client.newCall(request).execute().use { response ->
                        response.body()?.string()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    null
                }
            }

            handleResponse(response)
            progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.GONE }
            isLoadingData = false
        }
    }

    private fun handleResponse(response: String?) {
        if (isAdded && !response.isNullOrEmpty()) {
            val position: Int = try {
                mShowLinearLayoutManager.findFirstVisibleItemPosition()
            } catch (npe: NullPointerException) {
                0
            }

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

                mShowView.adapter = mShowAdapter
                mShowView.scrollToPosition(position)
                mShowListLoaded = true
            } catch (je: JSONException) {
                je.printStackTrace()
            }
        }
        loading = false
    }

    companion object {
        private const val DEFAULT_MEDIA_TYPE = "key_default_media_type"
    }
}
