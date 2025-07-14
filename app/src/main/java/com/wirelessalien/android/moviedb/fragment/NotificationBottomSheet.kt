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
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.NotificationAdapter
import com.wirelessalien.android.moviedb.databinding.FragmentNotificationBottomSheetBinding
import com.wirelessalien.android.moviedb.helper.NotificationDatabaseHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotificationBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentNotificationBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotificationAdapter
    private lateinit var dbHelper: NotificationDatabaseHelper

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbHelper = NotificationDatabaseHelper(requireContext())
        val allNotifications = dbHelper.getAllNotifications().toMutableList()
        val (past, upcoming) = allNotifications.partition {
            val notificationDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).parse(it.date)
            notificationDate?.before(Date()) ?: false
        }

        val notifications = past.toMutableList()

        if (notifications.isEmpty()) {
            binding.notificationRecyclerView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
        } else {
            binding.notificationRecyclerView.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
        }

        adapter = NotificationAdapter(notifications)
        binding.notificationRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.notificationRecyclerView.adapter = adapter

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val notification = adapter.getNotificationAt(position)
                val notificationDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).parse(notification.date)
                val today = Calendar.getInstance().time

                if (notificationDate != null && notificationDate.after(today)) {
                    Toast.makeText(context,
                        getString(R.string.cannot_delete_upcoming_notifications), Toast.LENGTH_SHORT).show()
                    adapter.notifyItemChanged(position)
                } else {
                    dbHelper.deleteNotification(notification.id)
                    adapter.removeItem(position)
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.notificationRecyclerView)

        // Scroll to today's notification
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val position = notifications.indexOfFirst { it.date.startsWith(today) }
        if (position != -1) {
            binding.notificationRecyclerView.scrollToPosition(position)
        }

        binding.showUpcomingButton.setOnClickListener {
            adapter.showUpcomingNotifications(upcoming.toMutableList())
            binding.showUpcomingButton.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

