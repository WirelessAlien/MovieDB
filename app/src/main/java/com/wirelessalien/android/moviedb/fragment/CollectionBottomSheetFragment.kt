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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.CollectionAdapter
import com.wirelessalien.android.moviedb.databinding.FragmentCollectionBottomSheetBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class CollectionBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentCollectionBottomSheetBinding
    private lateinit var collectionAdapter: CollectionAdapter
    private var collectionId: Int = 0
    private var apiReadAccessToken: String? = null
    private lateinit var preferences: SharedPreferences
    private lateinit var genreList: HashMap<String, String?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        loadGenreList()
        arguments?.let {
            collectionId = it.getInt(COLLECTION_ID)
        }
        apiReadAccessToken = ConfigHelper.getConfigValue(requireContext(), "api_read_access_token")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCollectionBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchCollectionDetails()
    }

    private fun loadGenreList() {
        val sharedPreferences = requireContext().applicationContext
            .getSharedPreferences("GenreList", Context.MODE_PRIVATE)

        genreList = HashMap()
        val allEntries = sharedPreferences.all
        for ((key, value) in allEntries) {
            if (value is String) {
                genreList[key] = value
            }
        }

        val genreJSONArray = sharedPreferences.getString("movieGenreJSONArrayList", null)
        genreJSONArray?.let {
            val genreArray = JSONArray(it)
            for (i in 0 until genreArray.length()) {
                val genreObject = genreArray.getJSONObject(i)
                genreList[genreObject.getString("id")] = genreObject.getString("name")
            }
        }
    }

    private fun fetchCollectionDetails() {
        binding.shimmerFrameLayout1.startShimmer()
        binding.shimmerFrameLayout1.visibility = View.VISIBLE

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.themoviedb.org/3/collection/$collectionId")
            .header("Authorization", "Bearer $apiReadAccessToken")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    binding.shimmerFrameLayout1.stopShimmer()
                    binding.shimmerFrameLayout1.visibility = View.GONE
                    Toast.makeText(requireContext(), getString(R.string.failed_to_fetch_collection_details, e.message), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread {
                    binding.shimmerFrameLayout1.stopShimmer()
                    binding.shimmerFrameLayout1.visibility = View.GONE
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        val jsonObject = JSONObject(responseData.toString())
                        val parts = jsonObject.getJSONArray("parts")

                        val movies = mutableListOf<JSONObject>()
                        for (i in 0 until parts.length()) {
                            movies.add(parts.getJSONObject(i))
                        }

                        binding.collectionName.text = jsonObject.getString("name")
                        binding.collectionOverview.apply {
                            text = jsonObject.getString("overview")
                            maxLines = 3
                            setOnClickListener {
                                maxLines = if (maxLines == 3) Integer.MAX_VALUE else 3
                            }
                        }
                        setupRecyclerView(movies, preferences.getBoolean(BaseFragment.SHOWS_LIST_PREFERENCE, false))
                    } else {
                        Log.e(TAG, "Failed to fetch collection details: ${response.message}")
                        Toast.makeText(requireContext(), getString(R.string.failed_to_fetch_collection_details, response.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun setupRecyclerView(movies: List<JSONObject>, gridView: Boolean) {
        collectionAdapter = CollectionAdapter(requireContext(), movies, genreList, gridView)
        binding.collectionRecyclerView.apply {
            adapter = collectionAdapter
            layoutManager = if (gridView) {
                GridLayoutManager(context, preferences.getInt(BaseFragment.GRID_SIZE_PREFERENCE, 3))
            } else {
                LinearLayoutManager(context)
            }
        }
    }

    companion object {
        const val TAG = "CollectionBottomSheetFragment"
        private const val COLLECTION_ID = "collection_id"

        fun newInstance(collectionId: Int): CollectionBottomSheetFragment {
            val args = Bundle()
            args.putInt(COLLECTION_ID, collectionId)
            val fragment = CollectionBottomSheetFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
