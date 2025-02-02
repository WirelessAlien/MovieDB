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
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.PersonActivity
import com.wirelessalien.android.moviedb.adapter.PersonDatabaseAdapter
import com.wirelessalien.android.moviedb.adapter.PersonPagingAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityPersonBinding
import com.wirelessalien.android.moviedb.databinding.FragmentPersonBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.PeopleDatabaseHelper
import com.wirelessalien.android.moviedb.pagingSource.PersonPagingSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

class PersonFragment : BaseFragment() {

    private lateinit var mPersonDatabaseAdapter: PersonDatabaseAdapter
    private lateinit var mPersonAdapter: PersonPagingAdapter
    private var isShowingDatabasePeople = false
    private lateinit var mGridLayoutManager: GridLayoutManager
    private var apiKeyTmdb: String? = null
    private lateinit var binding: FragmentPersonBinding
    private lateinit var sPreferences: SharedPreferences
    private lateinit var activityBinding: ActivityPersonBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiKeyTmdb = ConfigHelper.getConfigValue(requireContext().applicationContext, "api_key")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPersonBinding.inflate(inflater, container, false)
        val fragmentView = binding.root
        activityBinding = (activity as PersonActivity).getBinding()
        sPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        activityBinding.fab.isEnabled = true
        activityBinding.fab.setImageResource(R.drawable.ic_star)
        activityBinding.fab.setOnClickListener {
            isShowingDatabasePeople = if (!isShowingDatabasePeople) {
                showPeopleFromDatabase()
                true
            } else {
                createPersonList()
                false
            }
        }

        mPersonAdapter = PersonPagingAdapter()
        mPersonDatabaseAdapter = PersonDatabaseAdapter(ArrayList())
        showPersonList()
        createPersonList()
        return fragmentView
    }

    private fun showPeopleFromDatabase() {
        val databasePeople = peopleFromDatabase
        mPersonDatabaseAdapter = PersonDatabaseAdapter(databasePeople)
        binding.personRecyclerView.adapter = mPersonDatabaseAdapter
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
        activityBinding.fab.setImageResource(R.drawable.ic_star)
        activityBinding.fab.isEnabled = true
        activityBinding.fab.setOnClickListener {
            isShowingDatabasePeople = if (!isShowingDatabasePeople) {
                showPeopleFromDatabase()
                true
            } else {
                createPersonList()
                false
            }
        }
    }

    private fun createPersonList() {
        mPersonAdapter = PersonPagingAdapter()
        binding.personRecyclerView.adapter = mPersonAdapter

        lifecycleScope.launch {
            Pager(PagingConfig(pageSize = 20)) {
                PersonPagingSource(apiKeyTmdb, requireContext())
            }.flow.collectLatest { pagingData ->
                mPersonAdapter.submitData(pagingData)
            }
        }

        mPersonAdapter.addLoadStateListener { loadState ->
            when (loadState.source.refresh) {
                is LoadState.Loading -> {
                    binding.personRecyclerView.visibility = View.GONE
                    binding.shimmerFrameLayout.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.startShimmer()
                }

                is LoadState.NotLoading -> {
                    binding.personRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                }

                is LoadState.Error -> {
                    binding.personRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_loading_data),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showPersonList() {
        mGridLayoutManager = GridLayoutManager(activity, sPreferences.getInt(GRID_SIZE_PREFERENCE, 3))
        binding.personRecyclerView.layoutManager = mGridLayoutManager
        binding.personRecyclerView.adapter = mPersonAdapter
    }

    companion object {
        private const val GRID_SIZE_PREFERENCE = "key_grid_size_number"

        fun newInstance(): PersonFragment {
            return PersonFragment()
        }
    }
}