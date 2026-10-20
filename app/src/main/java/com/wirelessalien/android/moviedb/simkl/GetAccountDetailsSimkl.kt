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

package com.wirelessalien.android.moviedb.simkl

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.wirelessalien.android.moviedb.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class GetAccountDetailsSimkl(
    private val context: Context?,
    private val clientId: String,
    private val callback: AccountDataCallback?
) {
    private val accessToken: String?
    private val client: OkHttpClient

    interface AccountDataCallback {
        fun onAccountDataReceived(
            userId: Int,
            name: String?,
            avatarUrl: String?
        )
    }

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context!!)
        accessToken = preferences.getString("simkl_access_token", "")
        client = com.wirelessalien.android.moviedb.NetworkClient.client
    }

    suspend fun fetchAccountDetails() {
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.simkl.com/users/settings".toHttpUrl().newBuilder()
                    .addQueryParameter("client_id", clientId)
                    .addQueryParameter("app-name", "showcase")
                    .addQueryParameter("app-version", BuildConfig.VERSION_NAME)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "ShowCase/${BuildConfig.VERSION_NAME}")
                    .addHeader("simkl-api-key", clientId)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseBody)
                    val user = jsonResponse.optJSONObject("user")
                    val account = jsonResponse.optJSONObject("account")
                    val name = user?.optString("name", "")
                    val rawAvatar = user?.optString("avatar", "") ?: ""
                    val avatarUrl = when {
                        rawAvatar.isEmpty() || rawAvatar == "null" -> null
                        rawAvatar.startsWith("http") -> rawAvatar
                        else -> "https://simkl.in/avatars/${rawAvatar}_m.jpg"
                    }
                    val userId = account?.optInt("id", -1) ?: -1

                    if (userId != -1 && context != null) {
                        PreferenceManager.getDefaultSharedPreferences(context).edit()
                            .putInt("simkl_user_id", userId)
                            .apply()
                    }

                    callback?.onAccountDataReceived(userId, name, avatarUrl)
                } else {
                    Log.e("GetAccountDetailsSimkl", "Failed to get Simkl account details: ${response.code}")
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }
}
