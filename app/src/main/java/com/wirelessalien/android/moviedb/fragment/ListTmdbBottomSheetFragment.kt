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

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.ListDataAdapter
import com.wirelessalien.android.moviedb.data.ListDataTmdb
import com.wirelessalien.android.moviedb.data.ListDetailsData
import com.wirelessalien.android.moviedb.databinding.ListBottomSheetBinding
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.account.CreateList
import com.wirelessalien.android.moviedb.tmdb.account.FetchList
import com.wirelessalien.android.moviedb.tmdb.account.GetAllListData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

class ListTmdbBottomSheetFragment(
    private val movieId: Int,
    private val mediaType: String?,
    private val context: Context?,
    private val fetchList: Boolean,
    private val listener: OnListCreatedListener?
) : BottomSheetDialogFragment() {

    interface OnListCreatedListener {
        fun onListCreated()
    }

    private val listDataAdapter: ListDataAdapter? = null
    private lateinit var binding: ListBottomSheetBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ListBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(getContext())
        binding.recyclerView.adapter = listDataAdapter
        binding.infoBtn.setOnClickListener {
            val builder = MaterialAlertDialogBuilder(
                requireContext()
            )
            builder.setTitle(R.string.lists_state_info_title)
            builder.setMessage(R.string.list_state_info)
            builder.setPositiveButton(getString(R.string.refresh)) { dialog: DialogInterface, _: Int ->
                fetchList()
                dialog.dismiss()
            }
            builder.setNegativeButton(getString(R.string.cancel)) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            builder.show()
        }
        binding.createListBtn.setOnClickListener {
            val listName = binding.newListName.text.toString()
            val description = binding.listDescription.text.toString()
            val isPublic = binding.publicChip.isChecked // true if public chip is checked, false if private chip is checked
            lifecycleScope.launch {
                val success = withContext(Dispatchers.IO) {
                    CreateList(listName, description, isPublic, context).createList()
                }
                if (success) {
                    listener?.onListCreated()
                    dismiss()
                } else {
                    Toast.makeText(context, R.string.failed_to_create_list, Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (!fetchList) {
            binding.previousListText.visibility = View.GONE
        }
        if (fetchList) {
            lifecycleScope.launch {
                val listData: MutableList<ListDetailsData> = withContext(Dispatchers.IO) {
                    val listdatabaseHelper = ListDatabaseHelper(context)
                    val listdb = listdatabaseHelper.readableDatabase
                    val cursor = listdb.query(
                        true,
                        ListDatabaseHelper.TABLE_LISTS,
                        arrayOf(ListDatabaseHelper.COLUMN_LIST_ID, ListDatabaseHelper.COLUMN_LIST_NAME),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                    val listData: MutableList<ListDetailsData> = ArrayList()
                    while (cursor.moveToNext()) {
                        val listId =
                            cursor.getInt(cursor.getColumnIndexOrThrow(ListDatabaseHelper.COLUMN_LIST_ID))
                        val listName =
                            cursor.getString(cursor.getColumnIndexOrThrow(ListDatabaseHelper.COLUMN_LIST_NAME))
                        val isMovieInList = checkIfMovieInList(movieId, listName)
                        listData.add(
                            ListDetailsData(
                                movieId,
                                listId,
                                listName,
                                mediaType!!,
                                isMovieInList
                            )
                        )
                    }
                    cursor.close()
                    listData
                }
                if (context is Activity) {
                    context.runOnUiThread {
                        val adapter = ListDataAdapter(listData, context, object : ListDataAdapter.OnItemClickListener {
                            override fun onItemClick(listData: ListDetailsData?) {
                                // Handle item click
                            }
                        })
                        binding.recyclerView.adapter = adapter
                    }
                }
            }
        }
    }

    private fun checkIfMovieInList(movieId: Int, listName: String): Boolean {
        val selection =
            ListDatabaseHelper.COLUMN_MOVIE_ID + " = ? AND " + ListDatabaseHelper.COLUMN_LIST_NAME + " = ?"
        val selectionArgs = arrayOf(movieId.toString(), listName)
        val listdatabaseHelper = ListDatabaseHelper(context)
        val listdb = listdatabaseHelper.readableDatabase
        val projection = arrayOf(
            ListDatabaseHelper.COLUMN_MOVIE_ID,
            ListDatabaseHelper.COLUMN_LIST_NAME
        )
        val cursor = listdb.query(
            ListDatabaseHelper.TABLE_LIST_DATA,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        )
        val isMovieInList = cursor.count > 0
        cursor.close()
        return isMovieInList
    }

    //fetch list function
    private fun fetchList() {
        val listDatabaseHelper = ListDatabaseHelper(context)
        listDatabaseHelper.deleteAllData()
        val db = listDatabaseHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM " + ListDatabaseHelper.TABLE_LISTS, null)
        Handler(Looper.getMainLooper())
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(R.layout.dialog_progress)
            .setCancelable(false)
            .create()
        progressDialog.show()
        lifecycleScope.launch {
            val fetchList =
                FetchList(context, object : FetchList.OnListFetchListener {
                    override fun onListFetch(listDatumTmdbs: List<ListDataTmdb>?) {
                        if (listDatumTmdbs != null) {
                            for (data in listDatumTmdbs) {
                                listDatabaseHelper.addList(data.id, data.name)
                                lifecycleScope.launch {
                                    val getListDetails = GetAllListData(
                                        data.id,
                                        context,
                                        object :
                                            GetAllListData.OnFetchListDetailsListener {
                                            override fun onFetchListDetails(listDetailsData: ArrayList<JSONObject>?) {
                                                if (listDetailsData != null) {
                                                    for (item in listDetailsData) {
                                                        try {
                                                            val movieId = item.getInt("id")
                                                            val mediaType =
                                                                item.getString("media_type")
                                                            listDatabaseHelper.addListDetails(
                                                                data.id,
                                                                data.name,
                                                                movieId,
                                                                mediaType
                                                            )
                                                        } catch (e: JSONException) {
                                                            e.printStackTrace()
                                                            progressDialog.dismiss()
                                                            Toast.makeText(
                                                                context,
                                                                R.string.error_occurred_in_list_data,
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    getListDetails.fetchAllListData()
                                }
                            }
                        }
                        progressDialog.dismiss()
                    }
                })
            fetchList.fetchLists()
            cursor.close()
        }
    }
}