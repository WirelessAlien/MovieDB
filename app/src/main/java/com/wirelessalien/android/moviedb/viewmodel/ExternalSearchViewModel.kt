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

package com.wirelessalien.android.moviedb.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.pagingSource.MultiSearchPagingSource
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class ExternalSearchViewModel : ViewModel() {

    fun search(query: String, context: Context): Flow<PagingData<JSONObject>> {
        val apiReadAccessToken = ConfigHelper.getConfigValue(context, "api_read_access_token")
        return Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = { MultiSearchPagingSource(apiReadAccessToken?: "", query, context) }
        ).flow.cachedIn(viewModelScope)
    }
}
