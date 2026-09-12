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

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.viewpager2.widget.ViewPager2
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.WrappedPagerAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityYearWrappedBinding
import java.io.File
import java.io.FileOutputStream
import java.util.Date

class YearWrappedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityYearWrappedBinding
    
    private val slideCount = 6
    private var currentYear: Int = 2026
    
    private var currentSlideIndex = 0
    private var progressAnimator: ValueAnimator? = null
    private val progressDuration = 5000L
    private var isPaused = false

    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f

    var selectedImageUri: Uri? = null
    private var activeProfileImageView: ImageView? = null

    val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            activeProfileImageView?.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYearWrappedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentYear = intent.getIntExtra("year", 2026)

        binding.closeButton.setOnClickListener { finish() }

        setupViewPager()
        setupProgressBars()
    }

    fun launchImagePicker(imageView: ImageView) {
        activeProfileImageView = imageView
        pickImageLauncher.launch("image/*")
    }

    private fun setupViewPager() {
        val wrappedHelper = com.wirelessalien.android.moviedb.helper.WrappedHelper(this)
        val data = wrappedHelper.calculateWrappedData(currentYear)
        
        if (data == null) {
            Toast.makeText(this, getString(R.string.wrapped_no_data, currentYear), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val adapter = WrappedPagerAdapter(
            data,
            this::shareCard,
            this::saveCard,
            this::launchImagePicker,
            selectedImageUri
        )
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false
        
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentSlideIndex = position
                startProgressAnimation(position)
            }
        })
    }

    private fun setupProgressBars() {
        binding.progressContainer.weightSum = slideCount.toFloat()
        for (i in 0 until slideCount) {
            val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(4, 0, 4, 0)
                }
                max = 100
                progress = 0
            }
            binding.progressContainer.addView(progressBar)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downTime = event.eventTime
                downX = event.x
                downY = event.y
                pauseProgress()
            }
            MotionEvent.ACTION_UP -> {
                resumeProgress()
                val duration = event.eventTime - downTime
                val deltaX = Math.abs(event.x - downX)
                val deltaY = Math.abs(event.y - downY)

                if (duration < 300 && deltaX < 30 && deltaY < 30) {
                    val focusedView = currentFocus
                    if (focusedView is EditText) {
                        focusedView.clearFocus()
                    }

                    val screenWidth = resources.displayMetrics.widthPixels
                    val touchX = event.rawX
                    
                    if (touchX < screenWidth * 0.3f) {
                        if (currentSlideIndex > 0) {
                            binding.viewPager.currentItem = currentSlideIndex - 1
                        }
                    } else if (touchX > screenWidth * 0.3f && !isTouchOnInteractiveView(event)) {
                        if (currentSlideIndex < slideCount - 1) {
                            binding.viewPager.currentItem = currentSlideIndex + 1
                        } else {
                            finish()
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                resumeProgress()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun isTouchOnInteractiveView(event: MotionEvent): Boolean {
        val root = binding.root
        return findInteractiveChild(root, event.rawX, event.rawY)
    }

    private fun findInteractiveChild(view: View, rawX: Float, rawY: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false

        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val right = left + view.width
        val bottom = top + view.height

        val isInside = rawX >= left && rawX <= right && rawY >= top && rawY <= bottom

        if (!isInside) return false

        if (view.id == R.id.closeButton) return true

        if (view is android.widget.Button || view is EditText || view.id == R.id.ivProfileImage || view.isClickable) {
            return true
        }

        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                if (findInteractiveChild(view.getChildAt(i), rawX, rawY)) {
                    return true
                }
            }
        }

        return false
    }

    private fun startProgressAnimation(position: Int) {
        progressAnimator?.cancel()
        
        for (i in 0 until slideCount) {
            val pb = binding.progressContainer.getChildAt(i) as ProgressBar
            if (i < position) pb.progress = 100
            else if (i > position) pb.progress = 0
        }

        val currentPb = binding.progressContainer.getChildAt(position) as ProgressBar
        currentPb.progress = 0

        if (position == slideCount - 1) {
            currentPb.progress = 100
            return
        }

        progressAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = progressDuration
            addUpdateListener { animator ->
                currentPb.progress = animator.animatedValue as Int
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (currentPb.progress == 100 && !isPaused && position < slideCount - 1) {
                        binding.viewPager.currentItem = position + 1
                    }
                }
            })
            start()
        }
    }

    private fun pauseProgress() {
        isPaused = true
        progressAnimator?.pause()
    }

    private fun resumeProgress() {
        isPaused = false
        progressAnimator?.resume()
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun shareCard(view: View) {
        val bitmap = getBitmapFromView(view)
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "showcase_wrapped_share.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri: Uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, "image/png")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, getString(R.string.wrapped_share_text, currentYear))
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.wrapped_share_chooser)))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.wrapped_share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCard(view: View) {
        val bitmap = getBitmapFromView(view)
        val filename = "ShowCase_${currentYear}_Wrapped_${Date().time}.png"
        
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ShowCase")
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                Toast.makeText(this, getString(R.string.wrapped_save_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.wrapped_save_failed), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.wrapped_save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressAnimator?.cancel()
    }
}
