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

package com.wirelessalien.android.moviedb.trakt

import android.content.Context
import android.util.Log
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class TraktSync(private val accessToken: String, applicationContext: Context) {
    private val client = com.wirelessalien.android.moviedb.NetworkClient.client
    private val baseUrl = "https://api.trakt.tv"
    private val clientId = ConfigHelper.getConfigValue(applicationContext, "client_id")

    suspend fun postSuspend(endpoint: String, jsonBody: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = jsonBody.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$baseUrl/$endpoint")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", clientId ?: "")
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 1L
                    Log.w("TraktSync", "Rate limited (429) on $endpoint. Retry-After: $retryAfter s")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("TraktSync", "postSuspend failed on $endpoint: ${e.message}")
            false
        }
    }

    fun post(endpoint: String, jsonBody: JSONObject, callback: Callback) {
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = jsonBody.toString().toRequestBody(mediaType)
        Log.d("TraktSync", "Request: $jsonBody")
        Log.d("TraktSync", "url: $baseUrl/$endpoint")
        val request = Request.Builder()
            .url("$baseUrl/$endpoint")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", clientId ?: "")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback.onFailure(call, e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.code == 429) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 1L
                    Log.w("TraktSync", "Rate limited (429) on $endpoint. Retry-After: $retryAfter s")
                }
                callback.onResponse(call, response)
            }
        })
    }

    fun put(endpoint: String, jsonBody: JSONObject, callback: Callback) {
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = jsonBody.toString().toRequestBody(mediaType)
        Log.d("TraktSync", "Request: $jsonBody")
        Log.d("TraktSync", "url: $baseUrl/$endpoint")
        val request = Request.Builder()
            .url("$baseUrl/$endpoint")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", clientId ?: "")
            .put(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback.onFailure(call, e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.code == 429) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 1L
                    Log.w("TraktSync", "Rate limited (429) on $endpoint. Retry-After: $retryAfter s")
                }
                callback.onResponse(call, response)
            }
        })
    }

    fun delete(endpoint: String, callback: Callback) {
        val request = Request.Builder()
            .url("$baseUrl/$endpoint")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", clientId ?: "")
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback.onFailure(call, e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.code == 429) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 1L
                    Log.w("TraktSync", "Rate limited (429) on $endpoint. Retry-After: $retryAfter s")
                }
                callback.onResponse(call, response)
            }
        })
    }
}
