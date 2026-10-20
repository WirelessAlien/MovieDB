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
import com.wirelessalien.android.moviedb.BuildConfig
import com.wirelessalien.android.moviedb.NetworkClient
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.RateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

class SimklSync(private val accessToken: String, private val context: Context) {

    private val client = NetworkClient.client
    private val baseUrl = "https://api.simkl.com"
    private val clientId = ConfigHelper.getConfigValue(context.applicationContext, "simkl_client_id") ?: ""

    fun buildSimklUrl(endpoint: String): HttpUrl {
        val cleanEndpoint = endpoint.removePrefix("/")
        val rawUrl = "$baseUrl/$cleanEndpoint"
        val parsed = rawUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid Simkl URL: $rawUrl")
        return parsed.newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("app-name", "showcase")
            .addQueryParameter("app-version", BuildConfig.VERSION_NAME)
            .build()
    }

    private fun applyHeaders(builder: Request.Builder): Request.Builder {
        builder
            .addHeader("User-Agent", "ShowCase/${BuildConfig.VERSION_NAME}")
            .addHeader("Content-Type", "application/json")
            .addHeader("simkl-api-key", clientId)
        if (accessToken.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer $accessToken")
        }
        return builder
    }

    fun post(endpoint: String, jsonBody: JSONObject, callback: Callback) {
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = jsonBody.toString().toRequestBody(mediaType)
        val url = buildSimklUrl(endpoint)
        Log.d("SimklSync", "POST Request: $jsonBody")
        Log.d("SimklSync", "POST URL: $url")

        val request = applyHeaders(Request.Builder().url(url))
            .post(body)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            postRateLimiter.acquire()
            executeWithRetryAsync(request, callback)
        }
    }

    fun get(endpoint: String, callback: Callback) {
        val url = buildSimklUrl(endpoint)
        Log.d("SimklSync", "GET URL: $url")

        val request = applyHeaders(Request.Builder().url(url))
            .get()
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            getRateLimiter.acquire()
            executeWithRetryAsync(request, callback)
        }
    }

    suspend fun executeGetSync(endpoint: String): Response {
        val url = buildSimklUrl(endpoint)
        val request = applyHeaders(Request.Builder().url(url))
            .get()
            .build()
        getRateLimiter.acquire()
        return executeWithRetryBlocking(request)
    }

    suspend fun executePostSync(endpoint: String, jsonBody: JSONObject): Response {
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = jsonBody.toString().toRequestBody(mediaType)
        val url = buildSimklUrl(endpoint)
        val request = applyHeaders(Request.Builder().url(url))
            .post(body)
            .build()
        postRateLimiter.acquire()
        return executeWithRetryBlocking(request)
    }

    private suspend fun executeWithRetryBlocking(request: Request): Response {
        var attempt = 0
        var waitTimeMs = 1000L
        var lastException: IOException? = null

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                val call = client.newCall(request)
                val response = call.execute()
                if (response.code in TRANSIENT_ERROR_CODES && attempt < MAX_RETRY_ATTEMPTS - 1) {
                    Log.w("SimklSync", "Transient HTTP ${response.code}, retrying in ${waitTimeMs}ms (attempt ${attempt + 1})")
                    response.close()
                    delay(waitTimeMs)
                    waitTimeMs = min(waitTimeMs * 2, MAX_BACKOFF_MS)
                    attempt++
                    continue
                }
                return response
            } catch (e: IOException) {
                lastException = e
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    Log.w("SimklSync", "Network error: ${e.message}, retrying in ${waitTimeMs}ms (attempt ${attempt + 1})")
                    delay(waitTimeMs)
                    waitTimeMs = min(waitTimeMs * 2, MAX_BACKOFF_MS)
                    attempt++
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: IOException("Simkl request failed after $MAX_RETRY_ATTEMPTS attempts")
    }

    private fun executeWithRetryAsync(
        request: Request,
        callback: Callback,
        attempt: Int = 0,
        waitTimeMs: Long = 1000L
    ) {
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(waitTimeMs)
                        executeWithRetryAsync(
                            request,
                            callback,
                            attempt + 1,
                            min(waitTimeMs * 2, MAX_BACKOFF_MS)
                        )
                    }
                } else {
                    callback.onFailure(call, e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code in TRANSIENT_ERROR_CODES && attempt < MAX_RETRY_ATTEMPTS - 1) {
                    response.close()
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(waitTimeMs)
                        executeWithRetryAsync(
                            request,
                            callback,
                            attempt + 1,
                            min(waitTimeMs * 2, MAX_BACKOFF_MS)
                        )
                    }
                } else {
                    callback.onResponse(call, response)
                }
            }
        })
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val MAX_BACKOFF_MS = 60_000L
        private val TRANSIENT_ERROR_CODES = setOf(429, 500, 502, 503)

        // Simkl Rate Limits: Max 10 GET/sec and 1 POST/sec per client_id and user token
        val getRateLimiter = RateLimiter(10, 1, TimeUnit.SECONDS)
        val postRateLimiter = RateLimiter(1, 1, TimeUnit.SECONDS)
    }
}
