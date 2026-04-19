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

package com.wirelessalien.android.moviedb.helper

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object WebDavHelper {
    private const val PREFS_FILENAME = "webdav_prefs"
    const val KEY_WEBDAV_ENABLED = "webdav_enabled"
    const val KEY_WEBDAV_URL = "webdav_url"
    const val KEY_WEBDAV_USERNAME = "webdav_username"
    const val KEY_WEBDAV_PASSWORD = "webdav_password"

    private var sharedPreferences: SharedPreferences? = null

    fun getEncryptedSharedPreferences(context: Context): SharedPreferences {
        if (sharedPreferences == null) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return sharedPreferences!!
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun getClient(): OkHttpClient {
        return httpClient
    }

    fun testConnection(url: String, username: String, password: String): Boolean {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                // Just use GET to test if we can reach the URL
                .get()

            if (username.isNotEmpty() || password.isNotEmpty()) {
                val credential = Credentials.basic(username, password)
                requestBuilder.header("Authorization", credential)
            }

            val response = getClient().newCall(requestBuilder.build()).execute()
            response.close()
            return response.isSuccessful || response.code == 405 || response.code == 404 
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun uploadFile(url: String, username: String, password: String, file: File): Boolean {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .put(file.asRequestBody(null))

            if (username.isNotEmpty() || password.isNotEmpty()) {
                val credential = Credentials.basic(username, password)
                requestBuilder.header("Authorization", credential)
            }

            val response = getClient().newCall(requestBuilder.build()).execute()
            val success = response.isSuccessful
            response.close()
            return success
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun downloadFile(url: String, username: String, password: String, destinationFile: File): Boolean {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            if (username.isNotEmpty() || password.isNotEmpty()) {
                val credential = Credentials.basic(username, password)
                requestBuilder.header("Authorization", credential)
            }

            val response = getClient().newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                response.close()
                return false
            }

            response.body?.byteStream()?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            response.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
