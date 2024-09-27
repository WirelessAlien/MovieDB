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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL

/**
 * This class contains some basic functionality that would
 * otherwise be duplicated in multiple fragments.
 */
open class BaseFragment : Fragment() {
    private var API_KEY: String? = null

    lateinit var mShowView: RecyclerView

    open lateinit var mShowAdapter: ShowBaseAdapter

    protected open lateinit var mSearchShowAdapter: ShowBaseAdapter

    open lateinit var mShowArrayList: ArrayList<JSONObject>

    open lateinit var mSearchShowArrayList: ArrayList<JSONObject>

    protected lateinit var mShowLinearLayoutManager: LinearLayoutManager

    lateinit var mShowGenreList: HashMap<String, String?>
    open var mSearchView = false
    protected open lateinit var preferences: SharedPreferences
    var mGenreListLoaded = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        API_KEY = ConfigHelper.getConfigValue(requireContext().applicationContext, "api_key")
    }

    open fun doNetworkWork() {}

    /**
     * Sets up and displays the grid or list view of shows.
     *
     * @param fragmentView the view of the fragment (that the show view will be placed in).
     */
    open fun showShowList(fragmentView: View) {
        mShowView = fragmentView.findViewById(R.id.showRecyclerView)

        // Set the layout of the RecyclerView.
        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
            // If the user changed from a list layout to a grid layout, reload the ShowBaseAdapter.
            if (mShowView.layoutManager !is GridLayoutManager) {
                mShowAdapter = ShowBaseAdapter(
                    mShowArrayList, mShowGenreList,
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
                mShowAdapter = ShowBaseAdapter(
                    mShowArrayList, mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true), false
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

    /**
     * Sets the search view and adapter back to normal.
     */
    open fun cancelSearch() {
        mSearchView = false
        mShowView.adapter = mShowAdapter
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
            var line: String?
            val stringBuilder = StringBuilder()
            try {
                val url = URL("https://api.themoviedb.org/3/genre/$mGenreType/list?api_key=$API_KEY")
                val urlConnection = url.openConnection()
                try {
                    val bufferedReader = BufferedReader(InputStreamReader(urlConnection.getInputStream()))
                    while (bufferedReader.readLine().also { line = it } != null) {
                        stringBuilder.append(line).append("\n")
                    }
                    bufferedReader.close()
                    stringBuilder.toString()
                } catch (ioe: IOException) {
                    ioe.printStackTrace()
                    null
                }
            } catch (ioe: IOException) {
                ioe.printStackTrace()
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
                var i = 0
                while (genreArray.optJSONObject(i) != null) {
                    val websiteData = genreArray.getJSONObject(i)
                    mShowGenreList[websiteData.getString("id")] = websiteData.getString("name")
                    prefsEditor.putString(websiteData.getString("id"), websiteData.getString("name"))
                    prefsEditor.apply()
                    i++
                }
                prefsEditor.putString(mGenreType+ "GenreJSONArrayList", genreArray.toString())
                prefsEditor.commit()
                mShowAdapter.notifyDataSetChanged()
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
        const val FILTER_REQUEST_CODE = 2
    }
}
