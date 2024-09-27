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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.MyListDetailsActivity
import com.wirelessalien.android.moviedb.adapter.ListAdapter
import com.wirelessalien.android.moviedb.data.ListData
import com.wirelessalien.android.moviedb.databinding.FragmentMyListsBinding
import com.wirelessalien.android.moviedb.tmdb.account.FetchList
import kotlinx.coroutines.launch

class MyListsFragment : BaseFragment() {
    private var listAdapter: ListAdapter? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentMyListsBinding.inflate(inflater, container, false)
        val view: View = binding.root
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.setImageResource(R.drawable.ic_add)
        listAdapter = ListAdapter(ArrayList(), object : ListAdapter.OnItemClickListener {
            override fun onItemClick(listData: ListData?) {
                val intent = Intent(activity, MyListDetailsActivity::class.java)
                intent.putExtra("listId", listData?.id)
                startActivity(intent)
            }
        }, true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = listAdapter
        binding.progressBar.visibility = View.VISIBLE
        val fetcher = FetchList(context, null)
        requireActivity().lifecycleScope.launch {
            val listData = fetcher.fetchLists()
            requireActivity().runOnUiThread {
                listAdapter!!.updateData(listData)
                binding.progressBar.visibility = View.GONE
            }
        }
        fab.setOnClickListener {
            val listBottomSheetDialogFragment =
                ListBottomSheetDialogFragment(0, null, context, false)
            listBottomSheetDialogFragment.show(
                childFragmentManager,
                listBottomSheetDialogFragment.tag
            )
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.visibility = View.VISIBLE
        fab.setOnClickListener {
            val listBottomSheetDialogFragment =
                ListBottomSheetDialogFragment(0, null, context, false)
            listBottomSheetDialogFragment.show(
                childFragmentManager,
                listBottomSheetDialogFragment.tag
            )
        }
    }
}