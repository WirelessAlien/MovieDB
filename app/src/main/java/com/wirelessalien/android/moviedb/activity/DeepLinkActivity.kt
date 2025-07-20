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

import android.content.Intent
import android.icu.text.DateFormat
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.DialogMediaDetailsBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

class DeepLinkActivity : AppCompatActivity() {

    private lateinit var dialog: androidx.appcompat.app.AlertDialog
    private lateinit var binding: DialogMediaDetailsBinding
    private var mediaId: String? = null
    private var mediaType: String? = null
    private var imdbId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DialogMediaDetailsBinding.inflate(LayoutInflater.from(this))
        dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        binding.cancelButton.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        binding.refreshButton.setOnClickListener {
            if (mediaId != null && mediaType != null) {
                fetchDetails(mediaId!!, mediaType!!)
            } else if (imdbId != null) {
                fetchImdbDetails(imdbId!!)
            }
        }

        val intent = intent
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_VIEW == action) {
            val data: Uri? = intent.data
            if (data != null) {
                handleUri(data)
            }
        } else if (Intent.ACTION_SEND == action && "text/plain" == type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                handleSharedText(sharedText)
            }
        }
    }

    private fun handleUri(data: Uri) {
        val path = data.path
        if (path != null) {
            val segments = path.split("/").filter { it.isNotEmpty() }
            if (segments.size >= 2) {
                mediaType = segments[0]
                mediaId = segments[1].split("-")[0]
                if (mediaType == "movie" || mediaType == "tv") {
                    fetchDetails(mediaId!!, mediaType!!)
                } else if (segments[0] == "title") {
                    imdbId = segments[1]
                    fetchImdbDetails(imdbId!!)
                }
            }
        }
    }

    private fun handleSharedText(sharedText: String) {
        val tmdbPattern = Pattern.compile("https://www.themoviedb.org/(movie|tv)/(\\d+)")
        val imdbPattern = Pattern.compile("https://(www|m).imdb.com/title/(tt\\d+)")

        val tmdbMatcher = tmdbPattern.matcher(sharedText)
        val imdbMatcher = imdbPattern.matcher(sharedText)

        if (tmdbMatcher.find()) {
            mediaType = tmdbMatcher.group(1)
            mediaId = tmdbMatcher.group(2)
            if (mediaType != null && mediaId != null) {
                fetchDetails(mediaId!!, mediaType!!)
            }
        } else if (imdbMatcher.find()) {
            imdbId = imdbMatcher.group(2)
            if (imdbId != null) {
                fetchImdbDetails(imdbId!!)
            }
        } else {
            val intent = Intent(this, ExternalSearchActivity::class.java)
            intent.action = Intent.ACTION_SEND
            intent.putExtra(Intent.EXTRA_TEXT, sharedText)
            intent.type = "text/plain"
            startActivity(intent)
            finish()
        }
    }


    private fun fetchDetails(mediaId: String, mediaType: String) {
        showLoading()
        CoroutineScope(Dispatchers.IO).launch {
            val movieObject = fetchFromTmdb(mediaId, mediaType)
            withContext(Dispatchers.Main) {
                if (movieObject != null) {
                    showDialog(movieObject, mediaType == "movie")
                } else {
                    showError()
                }
            }
        }
    }

    private fun fetchImdbDetails(imdbId: String) {
        showLoading()
        CoroutineScope(Dispatchers.IO).launch {
            val movieObject = fetchMovieDetailsByExternalId(imdbId)
            withContext(Dispatchers.Main) {
                if (movieObject != null) {
                    val isMovie = movieObject.has("title")
                    showDialog(movieObject, isMovie)
                } else {
                    showError()
                }
            }
        }
    }


    private fun fetchFromTmdb(mediaId: String, mediaType: String): JSONObject? {
        val client = OkHttpClient()
        val apiReadAccessToken = ConfigHelper.getConfigValue(this, "api_read_access_token")
        val url = "https://api.themoviedb.org/3/$mediaType/$mediaId"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("accept", "application/json")
            .addHeader("Authorization", "Bearer $apiReadAccessToken")
            .build()
        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                JSONObject(response.body?.string().toString())
            } else {
                null
            }
        } catch (e: IOException) {
            null
        }
    }

    private suspend fun fetchMovieDetailsByExternalId(externalId: String): JSONObject? {
        val apiReadAccessToken = ConfigHelper.getConfigValue(this, "api_read_access_token")
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val url = "https://api.themoviedb.org/3/find/$externalId?external_source=imdb_id"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer $apiReadAccessToken")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("NetworkError", "TMDB API request failed: ${response.code} - ${response.message}")
                        return@withContext null
                    }

                    val responseBody = response.body?.string() ?: run {
                        Log.e("NetworkError", "Empty response body from TMDB API")
                        return@withContext null
                    }

                    val jsonObject = JSONObject(responseBody)
                    val movieResults = jsonObject.getJSONArray("movie_results")
                    if (movieResults.length() > 0) {
                        return@withContext movieResults.getJSONObject(0)
                    }

                    val tvResults = jsonObject.getJSONArray("tv_results")
                    if (tvResults.length() > 0) {
                        return@withContext tvResults.getJSONObject(0)
                    }

                    return@withContext null
                }
            } catch (e: SocketTimeoutException) {
                Log.e("NetworkError", "TMDB API request timed out: ${e.message}")
                null
            } catch (e: UnknownHostException) {
                Log.e("NetworkError", "Network unavailable for TMDB API: ${e.message}")
                null
            } catch (e: IOException) {
                Log.e("NetworkError", "Network IO error with TMDB API: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e("NetworkError", "Unexpected error with TMDB API: ${e.message}")
                null
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.detailsContainer.visibility = View.GONE
        dialog.show()
    }

    private fun showError() {
        Toast.makeText(this@DeepLinkActivity, getString(R.string.error_loading_data), Toast.LENGTH_SHORT).show()
        dialog.dismiss()
        finish()
    }


    private fun showDialog(movieObject: JSONObject, isMovie: Boolean) {
        binding.progressBar.visibility = View.GONE
        binding.detailsContainer.visibility = View.VISIBLE

        val title = if (isMovie) movieObject.optString("title") else movieObject.optString("name")
        val description = movieObject.optString("overview")
        val posterPath = movieObject.optString("poster_path")
        val releaseDate = if (isMovie) movieObject.optString("release_date") else movieObject.optString("first_air_date")
        val rating = movieObject.optDouble("vote_average").toFloat()

        val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val parsedDate = apiDateFormat.parse(releaseDate)
        val formattedDate = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault()).format(parsedDate)

        binding.title.text = title
        binding.description.text = description
        binding.date.text = formattedDate
        binding.rating.rating = rating / 2

        Picasso.get().load("https://image.tmdb.org/t/p/w500$posterPath").into(binding.image)

        binding.openDetailsButton.setOnClickListener {
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra("movieObject", movieObject.toString())
            intent.putExtra("isMovie", isMovie)
            startActivity(intent)
            finish()
        }
    }
}

