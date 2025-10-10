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

import android.content.SharedPreferences
import com.wirelessalien.android.moviedb.adapter.ShowPagingAdapter

abstract class TogglableFragment : BaseFragment() {

    protected var mListType: String? = null
    protected lateinit var pagingAdapter: ShowPagingAdapter

    override fun onResume() {
        super.onResume()
        updateAndRefreshListType()
    }

    private fun updateAndRefreshListType() {
        val newType = if (preferences.getBoolean(DEFAULT_MEDIA_TYPE, false)) "tv" else "movie"
        if (newType != mListType) {
            setType(newType)
        }
    }

    fun setType(type: String) {
        mListType = type
        if (this::pagingAdapter.isInitialized) {
            pagingAdapter.refresh()
        }
    }

    companion object {
        const val DEFAULT_MEDIA_TYPE = "key_default_media_type"
    }
}