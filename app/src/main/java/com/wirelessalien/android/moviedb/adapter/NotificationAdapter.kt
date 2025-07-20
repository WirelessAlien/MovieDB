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
import com.wirelessalien.android.moviedb.helper.NotificationDateUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class NotificationAdapter(
    private val notifications: MutableList<NotificationItem>
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

    inner class ViewHolder(private val binding: NotificationItemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: NotificationItem) {
            binding.notificationTitle.text = notification.title
            binding.notificationMessage.text = notification.message
            binding.notificationDate.text = formatDateToRelative(notification.date)
        }
    }

    fun getNotificationAt(position: Int): NotificationItem {
        return notifications[position]
    }

    fun removeItem(position: Int) {
        notifications.removeAt(position)
        notifyItemRemoved(position)
    }

    fun showUpcomingNotifications(upcomingNotifications: List<NotificationItem>) {
        val currentSize = notifications.size
        notifications.addAll(upcomingNotifications)
        notifyItemRangeInserted(currentSize, upcomingNotifications.size)
    }

    fun formatDateToRelative(dateString: String): String {
        val date = NotificationDateUtil.parseDate(dateString) ?: return "Invalid date"

        val now = Calendar.getInstance()
        val notificationDate = Calendar.getInstance()
        notificationDate.time = date

        val diffInMillis = notificationDate.timeInMillis - now.timeInMillis
        val daysDiff = TimeUnit.MILLISECONDS.toDays(diffInMillis)

        return if (diffInMillis > 0) {
            // Upcoming dates
            when {
                isSameDay(now, notificationDate) -> "Today"
                isTomorrow(now, notificationDate) -> "Tomorrow"
                daysDiff in 2..6 -> "In $daysDiff days"
                daysDiff in 7..13 -> "In a week"
                daysDiff in 14..30 -> "In ${daysDiff / 7} weeks"
                daysDiff > 30 -> "In a month"
                else -> "In the future"
            }
        } else {
            // Past dates
            val pastDiffInMillis = -diffInMillis
            val pastDaysDiff = TimeUnit.MILLISECONDS.toDays(pastDiffInMillis)
            when {
                pastDaysDiff == 0L -> "Today"
                pastDaysDiff == 1L -> "Yesterday"
                pastDaysDiff in 2..6 -> "$pastDaysDiff days ago"
                pastDaysDiff in 7..13 -> "A week ago"
                pastDaysDiff in 14..30 -> "${pastDaysDiff / 7} weeks ago"
                pastDaysDiff > 30 -> "${pastDaysDiff / 30} months ago"
                else -> "In the past"
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isTomorrow(now: Calendar, notificationDate: Calendar): Boolean {
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return isSameDay(tomorrow, notificationDate)
    }
}