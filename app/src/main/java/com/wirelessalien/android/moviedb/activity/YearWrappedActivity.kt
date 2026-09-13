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
import android.widget.TextView
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
        
        binding.btnShare.setOnClickListener {
            shareCard()
        }
        
        binding.btnSave.setOnClickListener {
            saveCard()
        }
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

    private fun getBitmapFromView(): Bitmap? {
        val recyclerView = binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        val layoutManager = recyclerView?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
        val slideView = layoutManager?.findViewByPosition(currentSlideIndex) ?: return null
        
        val slideTitle = slideView.findViewById<android.widget.TextView>(R.id.slideTitle)
        val slideDesc = slideView.findViewById<android.widget.TextView>(R.id.slideDesc)
        val contentContainer = slideView.findViewById<View>(R.id.slideContentContainer)
        
        val titleWidth = slideTitle.width
        val titleHeight = slideTitle.height
        val descWidth = slideDesc.width
        val descHeight = slideDesc.height
        val contentWidth = contentContainer.width
        val contentHeight = contentContainer.height
        
        val margin = 48
        val paddingBetween = 24
        
        val isSummary = currentSlideIndex == 5
        
        // App logo and name for branding (only for non-summary pages)
        val appLogoDrawable = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_launcher)
        val appLogoSize = if (!isSummary && appLogoDrawable != null) 72 else 0
        val appName = getString(R.string.app_name)
        val paint = android.graphics.Paint().apply {
            val typedArray = theme.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorOnSurface))
            color = typedArray.getColor(0, android.graphics.Color.BLACK)
            typedArray.recycle()
            textSize = 40f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val appNameBounds = android.graphics.Rect()
        if (!isSummary) {
            paint.getTextBounds(appName, 0, appName.length, appNameBounds)
        }
        val brandingHeight = if (!isSummary) appLogoSize + paddingBetween / 2 + appNameBounds.height() else 0
        val brandingPadding = if (!isSummary) paddingBetween * 2 else 0
        
        val totalWidth = slideView.width + margin * 2
        
        val totalHeight = if (isSummary) {
            margin + contentHeight + margin
        } else {
            margin + brandingHeight + brandingPadding + titleHeight + paddingBetween + descHeight + paddingBetween + contentHeight + margin
        }
        
        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        canvas.drawColor(typedValue.data)
        
        if (isSummary) {
            // Draw only the content block for the summary page
            canvas.save()
            val contentXOffset = (totalWidth - contentWidth) / 2f
            canvas.translate(contentXOffset, margin.toFloat())
            contentContainer.draw(canvas)
            canvas.restore()
        } else {
            var currentY = margin.toFloat()
            
            // Draw Branding
            if (appLogoDrawable != null) {
                canvas.save()
                val logoXOffset = (totalWidth - appLogoSize) / 2f
                appLogoDrawable.setBounds(0, 0, appLogoSize, appLogoSize)
                canvas.translate(logoXOffset, currentY)
                val path = android.graphics.Path().apply {
                    addRoundRect(
                        android.graphics.RectF(0f, 0f, appLogoSize.toFloat(), appLogoSize.toFloat()),
                        16f,
                        16f,
                        android.graphics.Path.Direction.CW
                    )
                }
                canvas.clipPath(path)
                appLogoDrawable.draw(canvas)
                canvas.restore()
                currentY += appLogoSize + paddingBetween / 2f
                
                canvas.drawText(appName, (totalWidth - appNameBounds.width()) / 2f, currentY + appNameBounds.height(), paint)
                currentY += appNameBounds.height() + brandingPadding
            }
            
            // Draw Title
            canvas.save()
            val titleXOffset = (totalWidth - titleWidth) / 2f
            canvas.translate(titleXOffset, currentY)
            slideTitle.draw(canvas)
            canvas.restore()
            currentY += titleHeight + paddingBetween
            
            // Draw Description
            canvas.save()
            val descXOffset = (totalWidth - descWidth) / 2f
            canvas.translate(descXOffset, currentY)
            slideDesc.draw(canvas)
            canvas.restore()
            currentY += descHeight + paddingBetween
            
            // Draw Content Container
            canvas.save()
            val contentXOffset = (totalWidth - contentWidth) / 2f
            canvas.translate(contentXOffset, currentY)
            contentContainer.draw(canvas)
            canvas.restore()
        }
        
        return bitmap
    }

    private fun getCurrentSlideView(): View? {
        val recyclerView = binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        val layoutManager = recyclerView?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
        return layoutManager?.findViewByPosition(currentSlideIndex)
    }

    private fun shareCard() {
        val bitmap = getBitmapFromView() ?: return
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

    private fun saveCard() {
        val bitmap = getBitmapFromView() ?: return
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
