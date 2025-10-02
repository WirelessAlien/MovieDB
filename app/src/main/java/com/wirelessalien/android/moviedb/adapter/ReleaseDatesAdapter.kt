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

import android.content.Context
import android.icu.text.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.databinding.ItemReleaseDateBinding
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ReleaseDatesAdapter(private val context: Context, private var releaseDates: List<JSONObject>) :
    RecyclerView.Adapter<ReleaseDatesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReleaseDateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val releaseDate = releaseDates[position]
        holder.bind(releaseDate)
    }

    override fun getItemCount(): Int = releaseDates.size

    fun updateData(newReleaseDates: List<JSONObject>) {
        releaseDates = newReleaseDates
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemReleaseDateBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(releaseDate: JSONObject) {
            val releaseDateStr = releaseDate.optString("release_date")
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                val parsedDate = inputFormat.parse(releaseDateStr)
                if (parsedDate != null) {
                    val outputFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                    val formattedDate = outputFormat.format(parsedDate)
                    binding.releaseDateText.text = formattedDate
                }
            } catch (e: ParseException) {
                e.printStackTrace()
                binding.releaseDateText.text = releaseDateStr.substringBefore("T")
            }
            val certification = releaseDate.optString("certification")
            if (certification.isNotEmpty()) {
                binding.certificationText.text = certification
                binding.certificationText.visibility = View.VISIBLE
            } else {
                binding.certificationText.visibility = View.GONE
            }

            binding.typeText.text = getReleaseTypeString(releaseDate.optInt("type"))

            val note = releaseDate.optString("note")
            if (note.isNotEmpty()) {
                binding.noteText.text = note
                binding.noteText.visibility = View.VISIBLE
            } else {
                binding.noteText.visibility = View.GONE
            }
        }
    }

    private fun getReleaseTypeString(type: Int): String {
        return when (type) {
            1 -> "Premiere"
            2 -> "Theatrical (limited)"
            3 -> "Theatrical"
            4 -> "Digital"
            5 -> "Physical"
            6 -> "TV"
            else -> "Unknown"
        }
    }
}