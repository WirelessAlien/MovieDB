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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.ListItemActivityTkt
import com.wirelessalien.android.moviedb.databinding.ListListItemBinding
import com.wirelessalien.android.moviedb.helper.TraktDatabaseHelper
import com.wirelessalien.android.moviedb.trakt.TraktSync
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class ListAdapterTkt(
    private val listData: ArrayList<JSONObject>,
    private val accessToken: String
) : RecyclerView.Adapter<ListAdapterTkt.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listData[position])
    }

    override fun getItemCount(): Int {
        return listData.size
    }

    class ListDiffCallback(
        private val oldList: List<JSONObject>,
        private val newList: List<JSONObject>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].getInt("trakt_list_id") == newList[newItemPosition].getInt("trakt_list_id")
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].toString() == newList[newItemPosition].toString()
        }
    }

    fun updateList(newList: List<JSONObject>) {
        val diffCallback = ListDiffCallback(listData, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        listData.clear()
        listData.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    inner class ViewHolder(private val binding: ListListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(jsonObject: JSONObject) {
            binding.listNameTextView.text = jsonObject.getString("name")

            val description = jsonObject.getString("description")
            if (description.isEmpty()) {
                binding.description.setText(R.string.no_description)
            } else {
                binding.description.text = description
            }
            binding.itemCount.text = itemView.context.getString(R.string.items_count, jsonObject.getInt("number_of_items"))
            binding.root.tag = jsonObject
            binding.root.setOnClickListener {
                val context = binding.root.context
                val intent = Intent(context, ListItemActivityTkt::class.java).apply {
                    putExtra("trakt_list_id", jsonObject.getInt("trakt_list_id"))
                    putExtra("trakt_list_name", jsonObject.getString("name"))
                }
                context.startActivity(intent)
            }

            binding.deleteButton.setOnClickListener {
                val context = binding.root.context
                val listId = jsonObject.getInt("trakt_list_id")
                deleteList(context, listId, bindingAdapterPosition)
            }
        }

    }

    private fun deleteList(context: Context, listId: Int, position: Int) {
        val traktSync = TraktSync(accessToken, context)
        val endpoint = "users/me/lists/$listId"

        traktSync.delete(endpoint, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, context.getString(R.string.failed_to_delete_list), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                (context as? Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        listData.removeAt(position)
                        notifyItemRemoved(position)

                        // Delete from local database
                        val db = TraktDatabaseHelper(context).writableDatabase
                        db.delete(
                            TraktDatabaseHelper.USER_LISTS,
                            "${TraktDatabaseHelper.COL_TRAKT_ID} = ?",
                            arrayOf(listId.toString())
                        )

                        Toast.makeText(context, context.getString(R.string.list_delete_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.failed_to_delete_list), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}