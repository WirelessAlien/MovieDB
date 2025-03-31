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

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.adapter.ShowPagingAdapter
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * This class contains some basic functionality that would
 * otherwise be duplicated in multiple fragments.
 */
open class BaseFragment : Fragment() {

    private var apiKey: String? = null
    lateinit var mShowView: RecyclerView
    lateinit var mShowAdapter: ShowBaseAdapter
    private lateinit var mShowPagingAdapter: ShowPagingAdapter
    open lateinit var mShowArrayList: ArrayList<JSONObject>
    open lateinit var mUpcomingArrayList: ArrayList<JSONObject>
    open lateinit var mSearchShowAdapter: ShowBaseAdapter
    open lateinit var mSearchShowArrayList: ArrayList<JSONObject>
    open lateinit var mShowLinearLayoutManager: LinearLayoutManager
    lateinit var mShowGenreList: HashMap<String, String?>
    open var mSearchView = false
    protected open lateinit var preferences: SharedPreferences
    private var mGenreListLoaded = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        apiKey = ConfigHelper.getConfigValue(requireContext().applicationContext, "api_key")
    }

    open fun doNetworkWork() {}

    open fun showShowList(fragmentView: View) {
        mShowView = fragmentView.findViewById(R.id.showRecyclerView)

        // Set the layout of the RecyclerView.
        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
            // If the user changed from a list layout to a grid layout, reload the ShowBaseAdapter.
            if (mShowView.layoutManager !is GridLayoutManager) {
                mShowAdapter = ShowBaseAdapter(requireContext(),
                    mShowArrayList, mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
                )
            }
            val mShowGridView = GridLayoutManager(
                activity,
                preferences.getInt(GRID_SIZE_PREFERENCE, 3)
            )
            mShowView.layoutManager = mShowGridView
            mShowLinearLayoutManager = mShowGridView
        } else {
            // If the user changed from a list layout to a grid layout, reload the ShowBaseAdapter.
            if (mShowView.layoutManager !is LinearLayoutManager) {
                mShowAdapter = ShowBaseAdapter(requireContext(),
                    mShowArrayList, mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)
                )
            }
            mShowLinearLayoutManager = LinearLayoutManager(
                activity,
                LinearLayoutManager.VERTICAL, false
            )
            mShowView.layoutManager = mShowLinearLayoutManager
        }
        mShowView.adapter = mShowAdapter
    }

    open fun showPagingList(fragmentView: View) {
        mShowView = fragmentView.findViewById(R.id.showRecyclerView)
        // Set the layout of the RecyclerView.
        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
            // If the user changed from a list layout to a grid layout, reload the ShowBaseAdapter.
            if (mShowView.layoutManager !is GridLayoutManager) {
                mShowPagingAdapter = ShowPagingAdapter(mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true), false
                )
            }
            val mShowGridView = GridLayoutManager(
                activity,
                preferences.getInt(GRID_SIZE_PREFERENCE, 3)
            )
            mShowView.layoutManager = mShowGridView
            mShowLinearLayoutManager = mShowGridView
        } else {
            // If the user changed from a list layout to a grid layout, reload the ShowBaseAdapter.
            if (mShowView.layoutManager !is LinearLayoutManager) {
                mShowPagingAdapter = ShowPagingAdapter(mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true), false
                )
            }
            mShowLinearLayoutManager = LinearLayoutManager(
                activity,
                LinearLayoutManager.VERTICAL, false
            )
            mShowView.layoutManager = mShowLinearLayoutManager
        }
        mShowView.adapter = mShowPagingAdapter
    }

    /**
     * Uses Coroutine to retrieve the id to genre mapping.
     */
    fun fetchGenreList(mGenreType: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val response = fetchGenreListFromNetwork(mGenreType)
            handleResponse(response, mGenreType)
        }
    }

    private suspend fun fetchGenreListFromNetwork(mGenreType: String): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.themoviedb.org/3/genre/$mGenreType/list?api_key=$apiKey")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun handleResponse(response: String?, mGenreType: String) {
        if (isAdded && !response.isNullOrEmpty()) {
            val sharedPreferences = requireContext().applicationContext
                .getSharedPreferences("GenreList", Context.MODE_PRIVATE)
            val prefsEditor = sharedPreferences.edit()
            try {
                val reader = JSONObject(response)
                val genreArray = reader.getJSONArray("genres")
                for (i in 0 until genreArray.length()) {
                    val websiteData = genreArray.getJSONObject(i)
                    mShowGenreList[websiteData.getString("id")] = websiteData.getString("name")
                    prefsEditor.putString(websiteData.getString("id"), websiteData.getString("name"))
                }
                prefsEditor.putString("${mGenreType}GenreJSONArrayList", genreArray.toString())
                prefsEditor.apply()

                if (::mShowAdapter.isInitialized) {
                    mShowAdapter.notifyDataSetChanged()
                }

                if (::mShowPagingAdapter.isInitialized) {
                    mShowPagingAdapter.notifyDataSetChanged()
                }
                mGenreListLoaded = true
            } catch (je: JSONException) {
                je.printStackTrace()
            }
        }
    }

    companion object {
        const val SHOWS_LIST_PREFERENCE = "key_show_shows_grid"
        const val GRID_SIZE_PREFERENCE = "key_grid_size_number"
        const val PERSISTENT_FILTERING_PREFERENCE = "key_persistent_filtering"
    }
}
