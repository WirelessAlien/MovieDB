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

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.BaseActivity
import com.wirelessalien.android.moviedb.adapter.PersonBaseAdapter
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.PeopleDatabaseHelper
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

class PersonFragment : BaseFragment() {
    private lateinit var mPersonGridView: RecyclerView
    private lateinit var mPersonAdapter: PersonBaseAdapter
    private lateinit var mSearchPersonAdapter: PersonBaseAdapter
    private var mPersonArrayList: ArrayList<JSONObject>? = null
    private var mSearchPersonArrayList: ArrayList<JSONObject>? = null
    private lateinit var mGridLayoutManager: GridLayoutManager
    private var isShowingDatabasePeople = false
    private var API_KEY: String? = null
    private val mSearchQuery: String? = null
    override var mSearchView = false

    private var visibleThreshold = 9
    private var currentPage = 0
    private var currentSearchPage = 0
    private var previousTotal = 0
    private var loading = true
    private var pastVisibleItems = 0
    private var visibleItemCount = 0
    private var totalItemCount = 0
    private val showIdSet = HashSet<Int>()

    private lateinit var sPreferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        API_KEY = ConfigHelper.getConfigValue(requireContext().applicationContext, "api_key")
        createPersonList()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        sPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        // Inflate the layout for this fragment
        val fragmentView = inflater.inflate(R.layout.fragment_person, container, false)
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.isEnabled = true
        fab.setImageResource(R.drawable.ic_star)
        fab.setOnClickListener {
            isShowingDatabasePeople = if (!isShowingDatabasePeople) {
                // Show people from the database
                showPeopleFromDatabase()
                true
            } else {
                // Show all people from the API
                createPersonList()
                false
            }
        }
        showPersonList(fragmentView)
        return fragmentView
    }

    private fun showPeopleFromDatabase() {
        // Get people from the database
        val databasePeople = peopleFromDatabase

        // Set the adapter with the database people
        mPersonAdapter = PersonBaseAdapter(databasePeople)
        mPersonGridView.adapter = mPersonAdapter
        showIdSet.clear()
        mPersonAdapter.notifyDataSetChanged()
    }

    private val peopleFromDatabase: ArrayList<JSONObject>
        get() {
            val databasePeople = ArrayList<JSONObject>()
            val dbHelper = PeopleDatabaseHelper(requireActivity())
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(PeopleDatabaseHelper.SELECT_ALL_SORTED_BY_NAME, null)
            if (cursor.moveToFirst()) {
                do {
                    val person = JSONObject()
                    try {
                        person.put("id", cursor.getInt(cursor.getColumnIndexOrThrow(PeopleDatabaseHelper.COLUMN_ID)))
                        person.put("name", cursor.getString(cursor.getColumnIndexOrThrow(PeopleDatabaseHelper.COLUMN_NAME)))
                        person.put("profile_path", cursor.getString(cursor.getColumnIndexOrThrow(PeopleDatabaseHelper.COLUMN_PROFILE_PATH)))
                        databasePeople.add(person)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
            db.close()
            return databasePeople
        }

    override fun onResume() {
        super.onResume()
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.setImageResource(R.drawable.ic_star)
        fab.isEnabled = true
        fab.setOnClickListener {
            isShowingDatabasePeople = if (!isShowingDatabasePeople) {
                // Show people from the database
                showPeopleFromDatabase()
                true
            } else {
                // Show all people from the API
                createPersonList()
                false
            }
        }
    }

    /**
     * Creates the PersonBaseAdapter with the (still empty) ArrayList.
     * Also starts an AsyncTask to load the items for the empty ArrayList.
     */
    private fun createPersonList() {
        mPersonArrayList = ArrayList()

        // Create the adapter
        mPersonAdapter = PersonBaseAdapter(mPersonArrayList!!)
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        visibleThreshold *= preferences.getInt(GRID_SIZE_PREFERENCE, 3)
        showIdSet.clear()
        // Get the persons
        fetchPersonList(1)
    }

    /**
     * Sets up and displays the grid view of people.
     *
     * @param fragmentView the view of the fragment (that the person view will be placed in).
     */
    private fun showPersonList(fragmentView: View) {
        // RecyclerView to display all the popular persons in a grid.
        mPersonGridView = fragmentView.findViewById(R.id.personRecyclerView)

        // Use a GridLayoutManager
        mGridLayoutManager = GridLayoutManager(
            activity,
            sPreferences.getInt(GRID_SIZE_PREFERENCE, 3)
        ) // For now three items in a row seems good, might be changed later on.
        mPersonGridView.layoutManager = mGridLayoutManager
        mPersonGridView.adapter = mPersonAdapter
        mPersonGridView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) { // Check for scroll down.
                    visibleItemCount = mGridLayoutManager.childCount
                    totalItemCount = mGridLayoutManager.itemCount
                    pastVisibleItems = mGridLayoutManager.findFirstVisibleItemPosition()
                    if (loading) {
                        if (totalItemCount > previousTotal) {
                            loading = false
                            previousTotal = totalItemCount
                            if (mSearchView) {
                                currentSearchPage++
                            } else {
                                currentPage++
                            }
                        }
                    }

                    // When no new pages are being loaded,
                    // but the user is at the end of the list, load the new page.
                    if (!loading && visibleItemCount + pastVisibleItems + visibleThreshold >= totalItemCount) {
                        // Load the next page of the content in the background.
                        if (mSearchView) {
                            search(mSearchQuery, (currentSearchPage + 1))
                        } else {
                            fetchPersonList((currentPage + 1))
                        }
                        loading = true
                    }
                }
            }
        })
    }

    /**
     * Creates an empty ArrayList and an adapter based on it.
     * Cancels the previous search task if and starts a new one based on the new query.
     *
     * @param query the name of the person to search for.
     */
    fun search(query: String?) {
        // Create a PersonBaseAdapter for the search results and load those results.
        mSearchPersonArrayList = ArrayList()
        mSearchPersonAdapter = PersonBaseAdapter(mSearchPersonArrayList!!)

        // Cancel old AsyncTask if exists.
        currentSearchPage = 1
        search(query, 1)
    }

    /**
     * Sets search boolean to false and sets original adapter in the RecyclerView.
     */
    override fun cancelSearch() {
        // Replace the current list with the personList.
        mSearchView = false
        mPersonGridView.adapter = mPersonAdapter
    }

    /**
     * Uses Coroutine to retrieve the list with popular people.
     */
    private fun fetchPersonList(page: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            requireActivity().runOnUiThread {
                val progressBar = requireActivity().findViewById<CircularProgressIndicator>(R.id.progressBar)
                progressBar.visibility = View.VISIBLE
            }

            val response = withContext(Dispatchers.IO) {
                var line: String?
                val stringBuilder = StringBuilder()

                try {
                    val url = URL(
                        "https://api.themoviedb.org/3/person/popular?page=$page&api_key=$API_KEY${BaseActivity.getLanguageParameter(context)}"
                    )
                    val urlConnection = url.openConnection()
                    try {
                        val bufferedReader = BufferedReader(InputStreamReader(urlConnection.getInputStream()))
                        while (bufferedReader.readLine().also { line = it } != null) {
                            stringBuilder.append(line).append("\n")
                        }
                        bufferedReader.close()
                    } catch (ioe: IOException) {
                        ioe.printStackTrace()
                    }
                } catch (ioe: IOException) {
                    ioe.printStackTrace()
                }
                stringBuilder.toString()
            }

            if (response.isNotEmpty()) {
                try {
                    val reader = JSONObject(response)
                    val arrayData = reader.getJSONArray("results")
                    val newItems = mutableListOf<JSONObject>()
                    for (i in 0 until arrayData.length()) {
                        val websiteData = arrayData.getJSONObject(i)
                        val personId = websiteData.getInt("id")
                        if (!showIdSet.contains(personId)) {
                            newItems.add(websiteData)
                            showIdSet.add(personId)
                        }
                    }
                    mPersonArrayList!!.addAll(newItems)
                    if (page == 1) {
                        mPersonGridView.adapter = mPersonAdapter
                    } else {
                        mPersonAdapter.notifyDataSetChanged()
                    }
                } catch (je: JSONException) {
                    je.printStackTrace()
                }
            }

            requireActivity().runOnUiThread {
                val progressBar = requireActivity().findViewById<CircularProgressIndicator>(R.id.progressBar)
                progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * Load a list of persons that fulfill the search query.
     */
    private fun search(query: String?, page: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val response = withContext(Dispatchers.IO) {
                doInBackground(query, page)
            }
            if (response != null) {
                onPostExecute(response)
            }
        }
    }

    private fun doInBackground(query: String?, page: Int): String? {
        var line: String?
        val stringBuilder = StringBuilder()

        // Load the webpage with the popular persons
        try {
            val url = URL(
                "https://api.themoviedb.org/3/search/person?"
                        + "query=" + query + "&page=" + page
                        + "&api_key=" + API_KEY + BaseActivity.getLanguageParameter(context)
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

                // Close the connection and return the data from the webpage.
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
        // Keep the user at the same position in the list.
        val position: Int = try {
            mGridLayoutManager.findFirstVisibleItemPosition()
        } catch (npe: NullPointerException) {
            0
        }

        // Clear the ArrayList before adding new movies to it.
        if (currentSearchPage <= 0) {
            // Only if the list doesn't already contain search results.
            mSearchPersonArrayList!!.clear()
        }

        // Convert the JSON webpage to JSONObjects.
        // Add the JSONObjects to the list with persons.
        if (!(response == null || response == "")) {
            try {
                val reader = JSONObject(response)
                val arrayData = reader.getJSONArray("results")
                for (i in 0 until arrayData.length()) {
                    val websiteData = arrayData.getJSONObject(i)
                    mSearchPersonArrayList!!.add(websiteData)
                }

                // Reload the adapter (with the new page)
                // and set the user to his old position.
                mSearchView = true
                mPersonGridView.adapter = mSearchPersonAdapter
                mPersonGridView.scrollToPosition(position)
                val progressBar = requireActivity().findViewById<CircularProgressIndicator>(R.id.progressBar)
                progressBar.visibility = View.GONE
            } catch (je: JSONException) {
                je.printStackTrace()
                val progressBar = requireActivity().findViewById<CircularProgressIndicator>(R.id.progressBar)
                progressBar.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val GRID_SIZE_PREFERENCE = "key_grid_size_number"

        fun newInstance(): PersonFragment {
            return PersonFragment()
        }
    }
}