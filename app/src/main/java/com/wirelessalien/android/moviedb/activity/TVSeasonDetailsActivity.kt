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
package com.wirelessalien.android.moviedb.activity

import android.icu.text.NumberFormat
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ActivityTvSeasonDetailsBinding
import com.wirelessalien.android.moviedb.fragment.SeasonDetailsFragment.Companion.newInstance
import com.wirelessalien.android.moviedb.helper.CrashHelper
import com.wirelessalien.android.moviedb.helper.ThemeHelper
import org.json.JSONObject
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.wirelessalien.android.moviedb.helper.ConfigHelper.getConfigValue
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import android.view.View

class TVSeasonDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTvSeasonDetailsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyAmoledTheme(this)

        super.onCreate(savedInstanceState)
        binding = ActivityTvSeasonDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CrashHelper.setDefaultUncaughtExceptionHandler(applicationContext)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val tvShowId = intent.getIntExtra("tvShowId", -1)
        val seasonNumber = intent.getIntExtra("seasonNumber", 1)
        val numSeasons = intent.getIntExtra("numSeasons", 1)
        val showName = intent.getStringExtra("tvShowName")
        val traktId = intent.getIntExtra("traktId", -1)
        val tmdbObjectString = intent.getStringExtra("tmdbObject")
        val tmdbObject = JSONObject(tmdbObjectString?:"{}")
        
        val isGroup = intent.getBooleanExtra("isGroup", false)
        val groupId = intent.getStringExtra("groupId")
        val groupName = intent.getStringExtra("groupName")

        if (isGroup && groupId != null) {
            supportActionBar?.title = groupName
            
            binding.shimmerFrameLayout.visibility = View.VISIBLE
            binding.shimmerFrameLayout.startShimmer()
            
            lifecycleScope.launch {
                val groupDetailsJson = fetchGroupDetails(groupId)
                
                binding.shimmerFrameLayout.stopShimmer()
                binding.shimmerFrameLayout.visibility = View.GONE
                
                if (groupDetailsJson != null) {
                    val groupsArray = groupDetailsJson.optJSONArray("groups") ?: JSONArray()
                    val numGroups = groupsArray.length()
                    
                    binding.viewPager.adapter = object : FragmentStateAdapter(this@TVSeasonDetailsActivity) {
                        override fun createFragment(position: Int): Fragment {
                            val fragment = newInstance(tvShowId, position, showName, traktId, tmdbObject)
                            fragment.arguments?.putBoolean("isGroup", true)
                            fragment.arguments?.putString("groupEpisodesJson", groupsArray.optJSONObject(position)?.optJSONArray("episodes")?.toString() ?: "[]")
                            return fragment
                        }

                        override fun getItemCount(): Int {
                            return numGroups
                        }
                    }
                    
                    TabLayoutMediator(
                        binding.tabLayout, binding.viewPager
                    ) { tab: TabLayout.Tab, position: Int ->
                        tab.text = groupsArray.optJSONObject(position)?.optString("name", "Group " + (position + 1))
                    }.attach()
                }
            }
        } else {
            binding.viewPager.adapter = object : FragmentStateAdapter(this) {
                override fun createFragment(position: Int): Fragment {
                    return newInstance(tvShowId, position, showName, traktId, tmdbObject)
                }
    
                override fun getItemCount(): Int {
                    return numSeasons + 1
                }
            }
            binding.viewPager.setCurrentItem(seasonNumber, false)
            TabLayoutMediator(
                binding.tabLayout, binding.viewPager
            ) { tab: TabLayout.Tab, position: Int ->
                val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
                tab.text = if (position == 0) {
                    getString(R.string.specials)
                } else {
                    getString(R.string.season) + " " + numberFormat.format(position)
                }
            }.attach()
        }
    }

    fun getBinding(): ActivityTvSeasonDetailsBinding {
        return binding
    }

    private suspend fun fetchGroupDetails(groupId: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = getConfigValue(applicationContext, "api_key")
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.themoviedb.org/3/tv/episode_group/$groupId?api_key=$apiKey")
                    .build()
                val response = client.newCall(request).execute()
                val bodyString = response.body?.string()
                if (response.isSuccessful && !bodyString.isNullOrEmpty()) {
                    return@withContext JSONObject(bodyString)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
