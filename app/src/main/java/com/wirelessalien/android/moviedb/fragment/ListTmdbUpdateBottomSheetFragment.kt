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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.ListDataTmdb
import com.wirelessalien.android.moviedb.databinding.BottomSheetListDetailsBinding
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.account.DeleteList
import com.wirelessalien.android.moviedb.tmdb.account.UpdateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ListTmdbUpdateBottomSheetFragment(
    private val listDataTmdb: ListDataTmdb,
    private val listener: OnListUpdatedListener
) : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetListDetailsBinding
    private lateinit var listDatabaseHelper: ListDatabaseHelper

    interface OnListUpdatedListener {
        fun onListUpdated()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetListDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listDatabaseHelper = ListDatabaseHelper(requireContext())
        binding.listNameEditText.setText(listDataTmdb.name)
        binding.descriptionEditText.setText(listDataTmdb.description)
        binding.publicChip.isChecked = listDataTmdb.public

        binding.updateButton.setOnClickListener {
            val newName = binding.listNameEditText.text.toString()
            val newDescription = binding.descriptionEditText.text.toString()
            val isPublic = binding.publicChip.isChecked
            val updateList = UpdateList(
                listDataTmdb.id,
                newName,
                newDescription,
                isPublic,
                requireContext(),
                object : UpdateList.OnListUpdatedListener {
                    override fun onListUpdated() {
                        listener.onListUpdated()
                        dismiss()
                    }
                })
            CoroutineScope(Dispatchers.Main).launch {
                updateList.updateList()
            }
        }

        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun showDeleteConfirmationDialog() {
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.delete_list))
        builder.setMessage(getString(R.string.delete_list_confirmation))
        builder.setPositiveButton(getString(R.string.delete)) { _, _ ->
            val deleteList = DeleteList(listDataTmdb.id, requireActivity(), object : DeleteList.OnListDeletedListener {
                override fun onListDeleted() {
                    listDatabaseHelper.deleteList(listDataTmdb.id)
                    listener.onListUpdated()
                    dismiss()
                }
            })
            CoroutineScope(Dispatchers.Main).launch {
                deleteList.deleteList()
            }
        }
        builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }
}
