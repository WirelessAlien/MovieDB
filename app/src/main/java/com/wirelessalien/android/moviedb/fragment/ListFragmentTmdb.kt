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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.ListItemActivityTmdb
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.adapter.ListAdapter
import com.wirelessalien.android.moviedb.data.ListData
import com.wirelessalien.android.moviedb.databinding.ActivityMainBinding
import com.wirelessalien.android.moviedb.databinding.FragmentMyListsBinding
import com.wirelessalien.android.moviedb.tmdb.account.FetchList
import kotlinx.coroutines.launch

class ListFragmentTmdb : BaseFragment(), ListBottomSheetFragment.OnListCreatedListener {
    private var listAdapter: ListAdapter? = null
    private lateinit var activityBinding: ActivityMainBinding
    private lateinit var binding: FragmentMyListsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View {

        binding = FragmentMyListsBinding.inflate(inflater, container, false)
        val view: View = binding.root

        activityBinding = (activity as MainActivity).getBinding()

        activityBinding.fab.setImageResource(R.drawable.ic_add)

        listAdapter = ListAdapter(ArrayList(), object : ListAdapter.OnItemClickListener {
            override fun onItemClick(listData: ListData?) {
                val intent = Intent(activity, ListItemActivityTmdb::class.java)
                intent.putExtra("listId", listData?.id)
                intent.putExtra("listName", listData?.name)
                startActivity(intent)
            }
        }, true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = listAdapter
        binding.progressBar.visibility = View.VISIBLE

        refreshList()

        activityBinding.fab.setOnClickListener {
            val listBottomSheetFragment =
                ListBottomSheetFragment(0, null, context, false, this@ListFragmentTmdb)
            listBottomSheetFragment.show(
                childFragmentManager,
                listBottomSheetFragment.tag
            )
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        activityBinding.fab.visibility = View.VISIBLE
        activityBinding.fab.setOnClickListener {
            val listBottomSheetFragment =
                ListBottomSheetFragment(0, null, context, false, this@ListFragmentTmdb)
            listBottomSheetFragment.show(
                childFragmentManager,
                listBottomSheetFragment.tag
            )
        }

        refreshList()
    }

    override fun onListCreated() {
        refreshList()
    }

    private fun refreshList() {
        binding.progressBar.visibility = View.VISIBLE
        val fetcher = FetchList(context, null)
        viewLifecycleOwner.lifecycleScope.launch {
            val listData = fetcher.fetchLists()
            requireActivity().runOnUiThread {
                if (isAdded) {
                    listAdapter?.updateData(listData)
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }
}