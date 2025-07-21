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
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ListDataAdapterTkt
import com.wirelessalien.android.moviedb.databinding.ListBottomSheetTktBinding
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

class ListBottomSheetFragmentTkt(
    private val movieId: Int,
    private val context: Context?,
    private val fetchList: Boolean,
    private val type: String,
    private val mediaObject: JSONObject,
    private val movieDataObject: JSONObject,
    private val listCreatedListener: OnListCreatedListener?
) : BottomSheetDialogFragment() {
    private var listObject: JSONObject? = null
    private var tktaccessToken: String? = null
    private lateinit var preferences: SharedPreferences
    private lateinit var binding: ListBottomSheetTktBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        tktaccessToken = preferences.getString("trakt_access_token", "")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ListBottomSheetTktBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = ListDataAdapterTkt(requireContext(), movieId, type, tktaccessToken, mediaObject, movieDataObject)

        binding.createListBtn.setOnClickListener {
            val selectedPrivacy = getSelectedPrivacy(binding.listPrivacyChip)
            createListObject(selectedPrivacy)
            traktSync("users/me/lists")
        }

        if (!fetchList) {
            binding.previousListText.visibility = View.GONE
        }

        if (fetchList) {
            loadPreviousLists(binding.recyclerView)
        }
    }

    private fun loadPreviousLists(recyclerView: RecyclerView) {
        val db = TraktDatabaseHelper(requireContext()).readableDatabase
        val cursor: Cursor = db.query(
            TraktDatabaseHelper.USER_LISTS,
            null,
            null,
            null,
            null,
            null,
            null
        )

        val lists: MutableList<ListDataAdapterTkt.ListItem> = mutableListOf()
        while (cursor.moveToNext()) {
            val listId = cursor.getInt(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_TRAKT_ID))
            val listName = cursor.getString(cursor.getColumnIndexOrThrow(TraktDatabaseHelper.COL_NAME))
            lists.add(ListDataAdapterTkt.ListItem(listId, listName, false)) // Set isInList to false initially
        }
        cursor.close()

        val adapter = ListDataAdapterTkt(requireContext(), movieId, type, tktaccessToken, mediaObject, movieDataObject)
        adapter.setLists(lists)
        recyclerView.adapter = adapter
    }

    private fun getSelectedPrivacy(chipGroup: ChipGroup): String {
        return when (chipGroup.checkedChipId) {
            R.id.publicChip -> "public"
            R.id.privateChip -> "private"
            R.id.linkChip -> "link"
            else -> "private" // Default value
        }
    }

    private fun createListObject(privacy: String) {
        val name = binding.newListName.text.toString()
        val description = binding.listDescription.text.toString()
        listObject = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("privacy", privacy)
            put("display_numbers", true)
            put("allow_comments", false)
            put("sort_by", "rank")
            put("sort_how", "asc")
        }
    }

    private fun traktSync(endpoint: String) {
        val traktApiService = TraktSync(tktaccessToken!!, requireContext())
        val jsonBody = listObject ?: JSONObject()
        traktApiService.post(endpoint, jsonBody, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, getString(R.string.failed_to_sync, endpoint), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBodyString = response.body?.string()

                Handler(Looper.getMainLooper()).post {
                    if (response.isSuccessful) {
                        if (!responseBodyString.isNullOrEmpty()) {
                            try {
                                val responseObject = JSONObject(responseBodyString)
                                lifecycleScope.launch(Dispatchers.IO) {
                                    saveListToDatabase(responseObject)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, getString(R.string.success), Toast.LENGTH_SHORT).show()
                                        listCreatedListener?.onListCreated()
                                        dismiss()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("ListTktBottomSheetFragment", "onResponse: ", e)
                                e.printStackTrace()
                            }
                        }
                    } else {
                        Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun saveListToDatabase(responseObject: JSONObject) {
        val db = TraktDatabaseHelper(requireContext()).writableDatabase
        val values = ContentValues().apply {
            put(TraktDatabaseHelper.COL_NAME, responseObject.getString("name"))
            put(TraktDatabaseHelper.COL_DESCRIPTION, responseObject.getString("description"))
            put(TraktDatabaseHelper.COL_PRIVACY, responseObject.getString("privacy"))
            put(TraktDatabaseHelper.COL_SHARE_LINK, responseObject.getString("share_link"))
            put(TraktDatabaseHelper.COL_TYPE, responseObject.getString("type"))
            put(TraktDatabaseHelper.COL_DISPLAY_NUMBERS, responseObject.getBoolean("display_numbers"))
            put(TraktDatabaseHelper.COL_ALLOW_COMMENTS, responseObject.getBoolean("allow_comments"))
            put(TraktDatabaseHelper.COL_SORT_BY, responseObject.getString("sort_by"))
            put(TraktDatabaseHelper.COL_SORT_HOW, responseObject.getString("sort_how"))
            put(TraktDatabaseHelper.COL_CREATED_AT, responseObject.getString("created_at"))
            put(TraktDatabaseHelper.COL_UPDATED_AT, responseObject.getString("updated_at"))
            put(TraktDatabaseHelper.COL_ITEM_COUNT, responseObject.getInt("item_count"))
            put(TraktDatabaseHelper.COL_COMMENT_COUNT, responseObject.getInt("comment_count"))
            put(TraktDatabaseHelper.COL_LIKES, responseObject.getInt("likes"))
            put(TraktDatabaseHelper.COL_TRAKT_ID, responseObject.getJSONObject("ids").getInt("trakt"))
            put(TraktDatabaseHelper.COL_SLUG, responseObject.getJSONObject("ids").getString("slug"))
        }
        db.insert(TraktDatabaseHelper.USER_LISTS, null, values)
    }

    interface OnListCreatedListener {
        fun onListCreated()
    }
}