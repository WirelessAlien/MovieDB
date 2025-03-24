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
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ListAdapterTkt
import com.wirelessalien.android.moviedb.databinding.FragmentMyListsBinding
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ListFragmentTkt : BaseFragment() {

    private lateinit var adapter: ListAdapterTkt
    private val listData = ArrayList<JSONObject>()
    private lateinit var dbHelper: TraktDatabaseHelper
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var binding: FragmentMyListsBinding
    private var accessToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = TraktDatabaseHelper(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        preferences.getString("trakt_access_token", null)?.let {
            accessToken = it
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMyListsBinding.inflate(inflater, container, false)
        val view: View = binding.root
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.visibility = View.VISIBLE
        fab.setImageResource(R.drawable.ic_add)
        adapter = ListAdapterTkt(listData, accessToken!!)

        linearLayoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)
        binding.recyclerView.layoutManager = linearLayoutManager

        binding.recyclerView.adapter = adapter

        loadListData()
        fab.setOnClickListener {
            val listBottomSheetFragment =
                ListBottomSheetFragmentTkt(0, context, false,  "", JSONObject(), JSONObject())
            listBottomSheetFragment.show(
                childFragmentManager,
                listBottomSheetFragment.tag
            )
        }
        return view
    }

    private fun loadListData() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val newList = withContext(Dispatchers.IO) {
                val db = dbHelper.readableDatabase
                val cursor = db.query(
                    TraktDatabaseHelper.USER_LISTS, null, null, null, null, null, null)

                val tempList = ArrayList<JSONObject>()
                if (cursor.moveToFirst()) {
                    do {
                        val jsonObject = JSONObject().apply {
                            put("name", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_NAME)))
                            put("trakt_list_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TRAKT_ID)))
                            put("number_of_items", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_ITEM_COUNT)))
                            put("description", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_DESCRIPTION)))
                            put("created_at", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_CREATED_AT)))
                        }
                        tempList.add(jsonObject)
                    } while (cursor.moveToNext())
                }
                cursor.close()
                tempList
            }
            val sortedList = applySorting(newList)
            adapter.updateList(sortedList)
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun applySorting(list: ArrayList<JSONObject>): ArrayList<JSONObject> {
        val criteria = preferences.getString("tkt_sort_criteria", "name")
        val order = preferences.getString("tkt_sort_order", "asc")

        val comparator = when (criteria) {
            "name" -> compareBy<JSONObject> { it.optString("name", "") }
            "date" -> compareBy { it.optString("created_at", "") }
            else -> compareBy { it.optString("name", "") }
        }

        if (order == "desc") {
            list.sortWith(comparator.reversed())
        } else {
            list.sortWith(comparator)
        }

        return list
    }
}