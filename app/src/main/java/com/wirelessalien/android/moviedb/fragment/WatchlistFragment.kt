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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
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

class WatchlistFragment : BaseFragment() {
    private var mListType: String? = null
    private var visibleThreshold = 0
    private var currentPage = 0
    private var previousTotal = 0
    private var loading = true
    private var pastVisibleItems = 0
    private var visibleItemCount = 0
    private var totalItemCount = 0
    private var totalPages = 0

    @Volatile
    private var isLoadingData = false
    private var mShowListLoaded = false
    private val showIdSet = HashSet<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val gridSizePreference = preferences.getInt(GRID_SIZE_PREFERENCE, 3)
        visibleThreshold = gridSizePreference * gridSizePreference
        createShowList(mListType)
    }

    override fun doNetworkWork() {
        if (!mShowListLoaded) {
            loadWatchList(mListType, 1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val fragmentView = inflater.inflate(R.layout.fragment_show, container, false)
        mListType = if (preferences.getBoolean(DEFAULT_MEDIA_TYPE, false)) "tv" else "movie"
        loadWatchList(mListType, 1)
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
        showIdSet.clear()
        mShowAdapter.notifyDataSetChanged()
        currentPage = 1
        totalPages = 0
        mListType = if ("movie" == mListType) "tv" else "movie"
        loadWatchList(mListType, 1)
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
        fab.setImageResource(if ("movie" == listType) R.drawable.ic_movie else R.drawable.ic_tv_show)
    }

    private fun createShowList(mode: String?) {
        mShowArrayList = ArrayList()
        mShowGenreList = HashMap()
        mShowAdapter = ShowBaseAdapter(
            mShowArrayList, mShowGenreList,
            preferences.getBoolean(SHOWS_LIST_PREFERENCE, false), false
        )
        (requireActivity() as BaseActivity).checkNetwork()
    }

    override fun showShowList(fragmentView: View) {
        super.showShowList(fragmentView)
        mShowView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                    visibleItemCount = mShowLinearLayoutManager.childCount
                    totalItemCount = mShowLinearLayoutManager.itemCount
                    pastVisibleItems = mShowLinearLayoutManager.findFirstVisibleItemPosition()
                    if (loading && totalItemCount > previousTotal) {
                        loading = false
                        previousTotal = totalItemCount
                        currentPage++
                    }
                    val threshold = if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
                        val gridSizePreference = preferences.getInt(GRID_SIZE_PREFERENCE, 3)
                        gridSizePreference * gridSizePreference
                    } else {
                        visibleThreshold
                    }
                    if (!loading && visibleItemCount + pastVisibleItems + threshold >= totalItemCount) {
                        if (mShowArrayList.isNotEmpty() && hasMoreItemsToLoad()) {
                            currentPage++
                            loadWatchList(mListType, currentPage)
                        }
                        loading = true
                    }
                }
            }
        })
    }

    private fun loadWatchList(listType: String?, page: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            isLoadingData = true
            if (!isAdded) return@launch

            val progressBar = Optional.ofNullable(requireActivity().findViewById<CircularProgressIndicator>(R.id.progressBar))
            progressBar.ifPresent { it.visibility = View.VISIBLE }

            val response = withContext(Dispatchers.IO) {
                fetchWatchListFromApi(listType, page)
            }

            handleResponse(response)
            progressBar.ifPresent { it.visibility = View.GONE }
            isLoadingData = false
        }
    }

    private fun fetchWatchListFromApi(listType: String?, page: Int): String? {
        val accessToken = preferences.getString("access_token", "")
        val accountId = preferences.getString("account_id", "")
        val url = "https://api.themoviedb.org/4/account/$accountId/$listType/watchlist?page=$page"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Content-Type", "application/json;charset=utf-8")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                response.body()?.string()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun handleResponse(response: String?) {
        if (isAdded && !response.isNullOrEmpty()) {
            val position = try {
                mShowLinearLayoutManager.findFirstVisibleItemPosition()
            } catch (npe: NullPointerException) {
                0
            }

            try {
                val reader = JSONObject(response)
                totalPages = reader.getInt("total_pages")
                val arrayData = reader.getJSONArray("results")
                val newItems = mutableListOf<JSONObject>()
                for (i in 0 until arrayData.length()) {
                    val websiteData = arrayData.getJSONObject(i)
                    val showId = websiteData.getInt("id")
                    if (!showIdSet.contains(showId)) {
                        if (websiteData.getString("overview").isEmpty()) {
                            websiteData.put("overview", "Overview may not be available in the specified language.")
                        }
                        newItems.add(websiteData)
                        showIdSet.add(showId)
                    }
                }
                mShowArrayList.addAll(newItems)
                mShowAdapter.notifyItemRangeInserted(mShowArrayList.size - newItems.size, newItems.size)
                mShowView.adapter = mShowAdapter
                mShowView.scrollToPosition(position)
                mShowListLoaded = true
            } catch (je: JSONException) {
                je.printStackTrace()
            }
        }
        loading = false
    }

    private fun hasMoreItemsToLoad(): Boolean {
        return currentPage < totalPages
    }

    companion object {
        private const val DEFAULT_MEDIA_TYPE = "key_default_media_type"
    }
}