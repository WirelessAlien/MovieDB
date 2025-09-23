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

import android.content.ContentValues
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ListAdapterTkt
import com.wirelessalien.android.moviedb.databinding.FragmentMyListsBinding
import com.wirelessalien.android.moviedb.databinding.ListOptionsBottomSheetTktBinding
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.trakt.TraktSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class ListFragmentTkt : BaseFragment(), ListBottomSheetFragmentTkt.OnListCreatedListener {

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
        adapter = ListAdapterTkt(listData, accessToken ?: "") { jsonObject, position ->
            showEditBottomSheet(jsonObject, position)
        }

        linearLayoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)
        binding.recyclerView.layoutManager = linearLayoutManager

        binding.recyclerView.adapter = adapter

        loadListData()
        fab.setOnClickListener {
            val listBottomSheetFragment =
                ListBottomSheetFragmentTkt(0, context, false, "", JSONObject(), JSONObject(), this)
            listBottomSheetFragment.show(
                childFragmentManager,
                listBottomSheetFragment.tag
            )
        }
        return view
    }

    private fun showEditBottomSheet(jsonObject: JSONObject, position: Int) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val bottomSheetBinding = ListOptionsBottomSheetTktBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        bottomSheetBinding.listNameEditText.setText(jsonObject.getString("name"))
        bottomSheetBinding.listDescriptionEditText.setText(jsonObject.getString("description"))

        val privacyOptions = resources.getStringArray(R.array.privacy_options)
        val currentPrivacy = jsonObject.getString("privacy")

        for (option in privacyOptions) {
            val chip = Chip(requireContext())
            chip.text = option
            chip.isCheckable = true
            if (option == currentPrivacy) {
                chip.isChecked = true
            }
            bottomSheetBinding.privacyChipGroup.addView(chip)
        }

        bottomSheetBinding.updateButton.setOnClickListener {
            val newName = bottomSheetBinding.listNameEditText.text.toString()
            val newDescription = bottomSheetBinding.listDescriptionEditText.text.toString()
            val checkedChipId = bottomSheetBinding.privacyChipGroup.checkedChipId
            val checkedChip = bottomSheetDialog.findViewById<Chip>(checkedChipId)
            val newPrivacy = checkedChip?.text.toString()
            updateList(jsonObject.getInt("trakt_list_id"), newName, newDescription, newPrivacy)
            bottomSheetDialog.dismiss()
        }

        bottomSheetBinding.deleteButton.setOnClickListener {
            deleteList(jsonObject.getInt("trakt_list_id"), position)
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun updateList(listId: Int, name: String, description: String, privacy: String) {
        val traktSync = TraktSync(accessToken ?: "", requireContext())
        val endpoint = "users/me/lists/$listId"
        val json = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("privacy", privacy)
        }

        traktSync.post(endpoint, json, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, getString(R.string.failed_to_update_list), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        val values = ContentValues().apply {
                            put(TraktDatabaseHelper.COL_NAME, name)
                            put(TraktDatabaseHelper.COL_DESCRIPTION, description)
                            put(TraktDatabaseHelper.COL_PRIVACY, privacy)
                        }
                        dbHelper.writableDatabase.update(
                            TraktDatabaseHelper.USER_LISTS,
                            values,
                            "${TraktDatabaseHelper.COL_TRAKT_ID} = ?",
                            arrayOf(listId.toString())
                        )
                        Toast.makeText(context, getString(R.string.list_update_success), Toast.LENGTH_SHORT).show()
                        loadListData()
                    } else {
                        Toast.makeText(context, getString(R.string.failed_to_update_list), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun deleteList(listId: Int, position: Int) {
        val traktSync = TraktSync(accessToken ?: "", requireContext())
        val endpoint = "users/me/lists/$listId"

        traktSync.delete(endpoint, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, getString(R.string.failed_to_delete_list), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        dbHelper.writableDatabase.delete(
                            TraktDatabaseHelper.USER_LISTS,
                            "${TraktDatabaseHelper.COL_TRAKT_ID} = ?",
                            arrayOf(listId.toString())
                        )
                        listData.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        Toast.makeText(context, getString(R.string.list_delete_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, getString(R.string.failed_to_delete_list), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }


    private fun loadListData() {
        lifecycleScope.launch {
            binding.shimmerFrameLayout1.visibility = View.VISIBLE
            binding.shimmerFrameLayout1.startShimmer()
            val newList = withContext(Dispatchers.IO) {
                val db = dbHelper.readableDatabase
                val cursor = db.query(
                    TraktDatabaseHelper.USER_LISTS, null, null, null, null, null, null
                )

                val tempList = ArrayList<JSONObject>()
                if (cursor.moveToFirst()) {
                    do {
                        val jsonObject = JSONObject().apply {
                            put("name", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_NAME)))
                            put("trakt_list_id", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TRAKT_ID)))
                            put("number_of_items", cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_ITEM_COUNT)))
                            put("description", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_DESCRIPTION)))
                            put("created_at", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_CREATED_AT)))
                            put("updated_at", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_UPDATED_AT)))
                            put("privacy", cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_PRIVACY)))
                        }
                        tempList.add(jsonObject)
                    } while (cursor.moveToNext())
                }
                cursor.close()
                tempList
            }
            val sortedList = applySorting(newList)
            adapter.updateList(sortedList)
            binding.shimmerFrameLayout1.stopShimmer()
            binding.shimmerFrameLayout1.visibility = View.GONE
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

    override fun onListCreated() {
        loadListData()
    }
}