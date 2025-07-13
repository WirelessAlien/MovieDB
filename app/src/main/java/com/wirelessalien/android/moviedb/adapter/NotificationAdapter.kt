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

package com.wirelessalien.android.moviedb.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.data.NotificationItem
import com.wirelessalien.android.moviedb.databinding.NotificationItemBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class NotificationAdapter(
    private val notifications: MutableList<NotificationItem>,
    private val onDelete: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = NotificationItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = notifications[position]
        holder.bind(notification)
    }

    override fun getItemCount(): Int = notifications.size

    fun removeItem(position: Int) {
        notifications.removeAt(position)
        notifyItemRemoved(position)
    }

    inner class ViewHolder(private val binding: NotificationItemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: NotificationItem) {
            binding.notificationTitle.text = notification.title
            binding.notificationMessage.text = notification.message
            binding.notificationDate.text = formatDateToRelative(notification.date)
            binding.deleteButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDelete(notifications[position])
                    removeItem(position)
                }
            }
        }
    }

    fun formatDateToRelative(dateString: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = dateFormat.parse(dateString) ?: return "Invalid date"

        val now = Date()
        val diffInMillis = now.time - date.time
        val daysDiff = TimeUnit.MILLISECONDS.toDays(diffInMillis)

        return when {
            daysDiff < 1 -> "Today"
            daysDiff == 1L -> "Yesterday"
            daysDiff < 30 -> "$daysDiff days ago"
            daysDiff < 365 -> "${daysDiff / 30} month(s) ago"
            else -> "${daysDiff / 365} year(s) ago"
        }
    }
}