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
package com.wirelessalien.android.moviedb.tmdb.account

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class GetAccountDetails(private val context: Context?, private val callback: AccountDataCallback?) {
    private val accountId: String?
    private var accountIdInt = 0
    private val accessToken: String?
    private val client: OkHttpClient

    interface AccountDataCallback {
        fun onAccountDataReceived(
            accountId: Int,
            name: String?,
            username: String?,
            avatarPath: String?,
            gravatar: String?
        )
    }

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context!!)
        accountId = preferences.getString("account_id", "")
        accessToken = preferences.getString("access_token", "")
        client = OkHttpClient()
    }

    suspend fun fetchAccountDetails() {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.themoviedb.org/3/account/$accountId")
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(response.body()!!.string())
                    accountIdInt = jsonResponse.getInt("id")
                    val name = jsonResponse.getString("name")
                    val username = jsonResponse.getString("username")

                    // avatar path - object tmdb
                    val avatar = jsonResponse.getJSONObject("avatar")
                    val tmdb = avatar.getJSONObject("tmdb")
                    val avatarPath = tmdb.getString("avatar_path")
                    val gravatar = avatar.getJSONObject("gravatar").getString("hash")
                    callback?.onAccountDataReceived(accountIdInt, name, username, avatarPath, gravatar)
                    (context as Activity).runOnUiThread {
                        if (accountId != null) {
                            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                            val myEdit = preferences.edit()
                            myEdit.putInt("accountIdInt", accountIdInt)
                            myEdit.apply()
                        } else {
                            Log.e("GetAccountDetailsCoroutine", "Failed to get account id")
                        }
                    }
                } else {
                    Log.e("GetAccountDetailsCoroutine", "Failed to get account id")
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }
}