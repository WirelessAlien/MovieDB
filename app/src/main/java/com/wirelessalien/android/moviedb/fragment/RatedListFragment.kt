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
import android.os.Handler
import android.os.Looper
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Optional

class RatedListFragment : BaseFragment() {
    private var mListType: String? = null
    private var visibleThreshold = 0 // Three times the amount of items in a row
    private var currentPage = 0
    private var previousTotal = 0
    private var loading = true
    private var pastVisibleItems = 0
    private var visibleItemCount = 0
    private var totalItemCount = 0

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
            RatedListThread(mListType, 1).start()
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
        RatedListThread(mListType, 1).start()
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
        RatedListThread(mListType, 1).start()
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
                            RatedListThread(mListType, currentPage.toString().toInt()).start()
                        }
                        loading = true
                        currentPage++
                    }
                }
            }
        })
    }

    private inner class RatedListThread(private val listType: String?, private val page: Int) :
        Thread() {
        private val handler: Handler = Handler(Looper.getMainLooper())

        override fun run() {
            isLoadingData = true
            if (!isAdded) {
                return
            }
            handler.post {
                if (isAdded) {
                    val progressBar =
                        Optional.ofNullable(requireActivity().findViewById<ProgressBar>(R.id.progressBar))
                    progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.VISIBLE }
                }
            }

            // Load the webpage with the list of rated movies/series.
            val access_token = preferences.getString("access_token", "")
            val accountId = preferences.getString("account_id", "")
            val url = "https://api.themoviedb.org/4/account/$accountId/$listType/rated?page=$page"
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Content-Type", "application/json;charset=utf-8")
                .addHeader("Authorization", "Bearer $access_token")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    var responseBody: String? = null
                    if (response.body() != null) {
                        responseBody = response.body()!!.string()
                    }
                    handleResponse(responseBody)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                hideProgressBar()
            } finally {
                isLoadingData = false
                hideProgressBar()
            }
        }

        private fun handleResponse(response: String?) {
            handler.post {
                if (isAdded && !response.isNullOrEmpty()) {
                    // Keep the user at the same position in the list.
                    val position: Int = try {
                        mShowLinearLayoutManager.findFirstVisibleItemPosition()
                    } catch (npe: NullPointerException) {
                        0
                    }

                    // Convert the JSON webpage to JSONObjects
                    // Add the JSONObjects to the list with movies/series.
                    try {
                        val reader = JSONObject(response)
                        val arrayData = reader.getJSONArray("results")
                        for (i in 0 until arrayData.length()) {
                            val websiteData = arrayData.getJSONObject(i)
                            mShowArrayList.add(websiteData)
                        }

                        // Reload the adapter (with the new page)
                        // and set the user to his old position.
                        mShowView.adapter = mShowAdapter
                        mShowView.scrollToPosition(position)
                        mShowListLoaded = true
                        hideProgressBar()
                    } catch (je: JSONException) {
                        je.printStackTrace()
                        hideProgressBar()
                    }
                }
                loading = false
            }
        }

        private fun hideProgressBar() {
            handler.post {
                if (isAdded) {
                    val progressBar =
                        Optional.ofNullable(requireActivity().findViewById<ProgressBar>(R.id.progressBar))
                    progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.GONE }
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_MEDIA_TYPE = "key_default_media_type"
    }
}
