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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.NotificationAdapter
import com.wirelessalien.android.moviedb.helper.NotificationDatabaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationBottomSheet : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private lateinit var dbHelper: NotificationDatabaseHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notification_bottom_sheet, container, false)
        recyclerView = view.findViewById(R.id.notification_recycler_view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbHelper = NotificationDatabaseHelper(requireContext())
        val notifications = dbHelper.getAllNotifications().toMutableList()
        adapter = NotificationAdapter(notifications) { notification ->
            dbHelper.deleteNotification(notification.id)
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Scroll to today's notification
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val position = notifications.indexOfFirst { it.date.startsWith(today) }
        if (position != -1) {
            recyclerView.scrollToPosition(position)
        }
    }
}

