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

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.helper.ConfigHelper
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
     * Uses Thread to retrieve the id to genre mapping.
     */
    internal inner class GenreListThread(
        private val mGenreType: String,
        private val handler: Handler
    ) : Thread() {
        override fun run() {
            if (!isAdded) {
                return
            }
            var line: String?
            val stringBuilder = StringBuilder()

            // Load the genre webpage.
            try {
                val url = URL(
                    "https://api.themoviedb.org/3/genre/"
                            + mGenreType + "/list?api_key=" +
                            API_KEY
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
                    val response = stringBuilder.toString()

                    // Use handler to post the result back to the main thread
                    handler.post { handleResponse(response) }
                } catch (ioe: IOException) {
                    ioe.printStackTrace()
                }
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            }
        }

        private fun handleResponse(response: String?) {
            if (isAdded && !response.isNullOrEmpty()) {
                // Save GenreList to sharedPreferences, this way it can be used anywhere.
                val sharedPreferences = requireContext().applicationContext
                    .getSharedPreferences("GenreList", Context.MODE_PRIVATE)
                val prefsEditor = sharedPreferences.edit()

                // Convert the JSON data from the webpage to JSONObjects in an ArrayList.
                try {
                    val reader = JSONObject(response)
                    val genreArray = reader.getJSONArray("genres")
                    var i = 0
                    while (genreArray.optJSONObject(i) != null) {
                        val websiteData = genreArray.getJSONObject(i)
                        mShowGenreList[websiteData.getString("id")] =
                            websiteData.getString("name")

                        // Temporary fix until I find a way to handle this efficiently.
                        prefsEditor.putString(
                            websiteData.getString("id"),
                            websiteData.getString("name")
                        )
                        prefsEditor.apply()
                        i++
                    }
                    prefsEditor.putString(mGenreType + "GenreJSONArrayList", genreArray.toString())
                    prefsEditor.commit()
                    mShowAdapter.notifyDataSetChanged()
                    mGenreListLoaded = true
                } catch (je: JSONException) {
                    je.printStackTrace()
                }
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
