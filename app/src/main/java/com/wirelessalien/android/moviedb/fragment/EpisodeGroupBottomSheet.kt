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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.TVSeasonDetailsActivity
import com.wirelessalien.android.moviedb.adapter.EpisodeGroupAdapter
import com.wirelessalien.android.moviedb.data.EpisodeGroup
import org.json.JSONArray

class EpisodeGroupBottomSheet : BottomSheetDialogFragment() {
    private var tvShowId = 0
    private var traktId = -1
    private var tvShowName = ""
    private var episodeGroupsJson = "[]"
    private var tmdbObjectString = "{}"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_episode_groups, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        tvShowId = requireArguments().getInt("tvShowId", 0)
        traktId = requireArguments().getInt("traktId", -1)
        tvShowName = requireArguments().getString("tvShowName", "")
        episodeGroupsJson = requireArguments().getString("episodeGroupsJson", "[]")
        tmdbObjectString = requireArguments().getString("tmdbObject", "{}")

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val emptyDataLayout = view.findViewById<View>(R.id.emptyDataLayout)

        val groupsArray = JSONArray(episodeGroupsJson)
        val groups = mutableListOf<EpisodeGroup>()

        for (i in 0 until groupsArray.length()) {
            val obj = groupsArray.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            val description = obj.optString("description")
            val episodeCount = obj.optInt("episode_count")
            val groupCount = obj.optInt("group_count")
            val network = obj.optJSONObject("network")?.optString("name")
            groups.add(EpisodeGroup(id, name, description, episodeCount, groupCount, network))
        }

        if (groups.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyDataLayout.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyDataLayout.visibility = View.GONE
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = EpisodeGroupAdapter(groups, object : EpisodeGroupAdapter.OnItemClickListener {
                override fun onItemClick(group: EpisodeGroup) {
                    val intent = Intent(requireContext(), TVSeasonDetailsActivity::class.java).apply {
                        putExtra("tvShowId", tvShowId)
                        putExtra("tvShowName", tvShowName)
                        putExtra("traktId", traktId)
                        putExtra("tmdbObject", tmdbObjectString)
                        putExtra("isGroup", true)
                        putExtra("groupId", group.id)
                        putExtra("groupName", group.name)
                    }
                    startActivity(intent)
                    dismiss()
                }
            })
        }
    }
}
