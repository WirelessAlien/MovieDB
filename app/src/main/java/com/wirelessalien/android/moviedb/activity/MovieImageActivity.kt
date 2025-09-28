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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.MovieImageAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityMovieImageBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.CrashHelper
import com.wirelessalien.android.moviedb.tmdb.GetMovieImage

class MovieImageActivity : AppCompatActivity() {
    private var movieId = 0
    private var type: String? = null
    private lateinit var popupWindow: PopupWindow
    private lateinit var binding: ActivityMovieImageBinding
    private lateinit var getMovieImage: GetMovieImage
    private lateinit var adapter: MovieImageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovieImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        CrashHelper.setDefaultUncaughtExceptionHandler(applicationContext)
        checkAndRequestPermission()
    }

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_STORAGE
                )
            } else {
                // Permission already granted, proceed with the activity
                initializeActivity()
            }
        } else {
            // For Android 11 and above, proceed with the activity
            initializeActivity()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with the activity
                initializeActivity()
            } else {
                // Permission denied, show dialog and then close the activity
                showPermissionDeniedDialog()
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.permission_required))
            .setMessage(getString(R.string.permission_required_image))
            .setPositiveButton(getString(R.string.ok)) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun initializeActivity() {
        movieId = intent.getIntExtra("movieId", 0)
        type = if (intent.getBooleanExtra("isMovie", true)) "movie" else "tv"
        popupWindow = PopupWindow(this)
        val layoutManager = FlexboxLayoutManager(this)
        layoutManager.flexWrap = FlexWrap.WRAP
        layoutManager.flexDirection = FlexDirection.ROW
        layoutManager.alignItems = AlignItems.STRETCH
        layoutManager.justifyContent = JustifyContent.SPACE_EVENLY
        binding.movieimageRv.layoutManager = layoutManager

        adapter = MovieImageAdapter(this, emptyList(), "backdrops")
        binding.movieimageRv.adapter = adapter

        val apiKey = ConfigHelper.getConfigValue(this, "api_read_access_token")
        getMovieImage = GetMovieImage(movieId, type!!, apiKey)

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.backdrops))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.posters))

        val imageType = intent.getStringExtra("image_type")
        val initialTab = if (imageType == "poster") 1 else 0
        binding.tabLayout.getTabAt(initialTab)?.select()
        fetchImagesForTab(initialTab)

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                fetchImagesForTab(tab?.position ?: 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })


        // Handle back button press
        OnBackPressedDispatcher().addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (popupWindow.isShowing) {
                    popupWindow.dismiss()
                } else {
                    finish()
                }
            }
        })
    }

    private fun fetchImagesForTab(position: Int) {
        val imageType = if (position == 0) "backdrops" else "posters"
        adapter.updateData(emptyList(), imageType)
        getMovieImage.fetchMovieImages(imageType) { movieImages ->
            adapter.updateData(movieImages, imageType)
        }
    }

    companion object {
        private const val REQUEST_WRITE_STORAGE = 112
    }
}

