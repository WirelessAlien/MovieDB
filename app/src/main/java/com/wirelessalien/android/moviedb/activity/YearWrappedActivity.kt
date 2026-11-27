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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.WrappedPagerAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityYearWrappedBinding
import com.wirelessalien.android.moviedb.helper.WrappedHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import kotlin.math.abs

class YearWrappedActivity : AppCompatActivity() {

    companion object {
        private const val PREF_WRAPPED_PROFILE_URI = "wrapped_profile_image_uri"
    }

    private lateinit var binding: ActivityYearWrappedBinding
    private lateinit var adapter: WrappedPagerAdapter

    private val slideCount = 6
    private var currentYear: Int = 2026

    private var currentSlideIndex = 0
    private var progressAnimator: ValueAnimator? = null
    private val progressDuration = 5000L
    private var isTouchPaused = false
    private var isManuallyPaused = false

    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f

    var selectedImageUri: Uri? = null
    private var activeProfileImageView: ImageView? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            selectedImageUri = uri
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putString(PREF_WRAPPED_PROFILE_URI, uri.toString())
                .apply()
            activeProfileImageView?.setImageURI(uri)
            if (::adapter.isInitialized) {
                adapter.updateImageUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYearWrappedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentYear = intent.getIntExtra("year", 2026)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val savedUriStr = prefs.getString(PREF_WRAPPED_PROFILE_URI, null)
        if (!savedUriStr.isNullOrBlank()) {
            val parsedUri = Uri.parse(savedUriStr)
            val isReadable = try {
                contentResolver.openInputStream(parsedUri)?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
            if (isReadable) {
                selectedImageUri = parsedUri
            } else {
                prefs.edit().remove(PREF_WRAPPED_PROFILE_URI).apply()
            }
        }

        binding.closeButton.setOnClickListener { finish() }
        binding.btnPausePlay.setOnClickListener { toggleManualPause() }
        binding.btnReplay.setOnClickListener {
            isManuallyPaused = false
            updatePausePlayButton()
            binding.viewPager.setCurrentItem(0, true)
        }

        setupProgressBars()
        setupViewPager()
    }

    fun launchImagePicker(imageView: ImageView) {
        activeProfileImageView = imageView
        pickImageLauncher.launch(arrayOf("image/*"))
    }

    private fun setupViewPager() {
        val wrappedHelper = WrappedHelper(this)
        val data = wrappedHelper.calculateWrappedData(currentYear)

        if (data == null) {
            Toast.makeText(this, getString(R.string.wrapped_no_data, currentYear), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        adapter = WrappedPagerAdapter(
            data = data,
            onPickImageClicked = this::launchImagePicker,
            currentImageUri = selectedImageUri,
            onUserInteracted = {
                if (!isManuallyPaused) {
                    isManuallyPaused = true
                    progressAnimator?.pause()
                    updatePausePlayButton()
                }
            }
        )
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = true

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentSlideIndex = position
                binding.btnReplay.visibility = if (position == slideCount - 1) View.VISIBLE else View.GONE
                startProgressAnimation(position)
                binding.viewPager.post {
                    getCurrentSlideView()?.let { slideView ->
                        adapter.playSlideAnimations(slideView, position)
                    }
                }
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
                progressDrawable = ContextCompat.getDrawable(this@YearWrappedActivity, R.drawable.wrapped_story_progress)
                isClickable = true
                setOnClickListener {
                    binding.viewPager.setCurrentItem(i, true)
                }
            }
            binding.progressContainer.addView(progressBar)
        }
    }

    private fun toggleManualPause() {
        isManuallyPaused = !isManuallyPaused
        if (isManuallyPaused) {
            progressAnimator?.pause()
        } else if (!isTouchPaused) {
            progressAnimator?.resume()
        }
        updatePausePlayButton()
    }

    private fun updatePausePlayButton() {
        val paused = isManuallyPaused
        binding.btnPausePlay.setImageResource(if (paused) R.drawable.ic_play_arrow else R.drawable.ic_pause)
        binding.btnPausePlay.contentDescription = getString(if (paused) R.string.wrapped_play else R.string.wrapped_pause)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downTime = event.eventTime
                downX = event.x
                downY = event.y
                pauseForTouch()
            }
            MotionEvent.ACTION_UP -> {
                resumeFromTouch()
                val duration = event.eventTime - downTime
                val deltaX = abs(event.x - downX)
                val deltaY = abs(event.y - downY)

                if (duration < 250 && deltaX < 25 && deltaY < 25 && !isTouchOnInteractiveView(event)) {
                    val focusedView = currentFocus
                    if (focusedView is EditText) {
                        focusedView.clearFocus()
                        return super.dispatchTouchEvent(event)
                    }

                    val screenWidth = resources.displayMetrics.widthPixels
                    val touchX = event.rawX

                    if (touchX < screenWidth * 0.3f) {
                        if (currentSlideIndex > 0) {
                            binding.viewPager.currentItem = currentSlideIndex - 1
                        }
                    } else {
                        if (currentSlideIndex < slideCount - 1) {
                            binding.viewPager.currentItem = currentSlideIndex + 1
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                resumeFromTouch()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun isTouchOnInteractiveView(event: MotionEvent): Boolean {
        return findInteractiveChild(binding.root, event.rawX, event.rawY)
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

        if (view.id == R.id.closeButton ||
            view.id == R.id.btnPausePlay ||
            view.id == R.id.btnReplay ||
            view.id == R.id.progressContainer
        ) {
            return true
        }

        if (view is Button || view is EditText || view.id == R.id.ivProfileImage || view.id == R.id.flProfileImageContainer || view.isClickable) {
            return true
        }

        if (view is ViewGroup) {
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
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (currentPb.progress == 100 && !isTouchPaused && !isManuallyPaused && position < slideCount - 1) {
                        binding.viewPager.currentItem = position + 1
                    }
                }
            })
            start()
            if (isManuallyPaused || isTouchPaused) {
                pause()
            }
        }
    }

    private fun pauseForTouch() {
        isTouchPaused = true
        progressAnimator?.pause()
        binding.headerBranding.animate().alpha(0.5f).setDuration(120L).start()
    }

    private fun resumeFromTouch() {
        isTouchPaused = false
        if (!isManuallyPaused) {
            progressAnimator?.resume()
        }
        binding.headerBranding.animate().alpha(1f).setDuration(120L).start()
    }

    private fun getBitmapFromView(): Bitmap? {
        val slideView = getCurrentSlideView() ?: return null

        val slideTitle = slideView.findViewById<TextView>(R.id.slideTitle)
        val slideDesc = slideView.findViewById<TextView>(R.id.slideDesc)
        val contentContainer = slideView.findViewById<View>(R.id.slideContentContainer)

        val etDisplayName = slideView.findViewById<EditText>(R.id.etDisplayName)
        val ivProfileEditBadge = slideView.findViewById<View>(R.id.ivProfileEditBadge)
        val savedHint = etDisplayName?.hint
        val savedBadgeVisibility = ivProfileEditBadge?.visibility ?: View.GONE
        val savedCompoundDrawables = etDisplayName?.compoundDrawablesRelative?.clone()

        val isSummary = currentSlideIndex == slideCount - 1
        if (isSummary) {
            etDisplayName?.hint = ""
            etDisplayName?.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            ivProfileEditBadge?.visibility = View.GONE
        }

        val titleWidth = slideTitle.width
        val titleHeight = slideTitle.height
        val descWidth = slideDesc.width
        val descHeight = slideDesc.height
        val contentWidth = contentContainer.width
        val contentHeight = contentContainer.height

        val margin = 48
        val paddingBetween = 24

        val appLogoDrawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher)
        val appLogoSize = if (!isSummary && appLogoDrawable != null) 72 else 0
        val appName = getString(R.string.app_name)
        val paint = Paint().apply {
            val typedArray = theme.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorOnSurface))
            color = typedArray.getColor(0, Color.BLACK)
            typedArray.recycle()
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val appNameBounds = Rect()
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

        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        canvas.drawColor(typedValue.data)

        if (isSummary) {
            canvas.save()
            val contentXOffset = (totalWidth - contentWidth) / 2f
            canvas.translate(contentXOffset, margin.toFloat())
            contentContainer.draw(canvas)
            canvas.restore()
        } else {
            var currentY = margin.toFloat()

            if (appLogoDrawable != null) {
                canvas.save()
                val logoXOffset = (totalWidth - appLogoSize) / 2f
                appLogoDrawable.setBounds(0, 0, appLogoSize, appLogoSize)
                canvas.translate(logoXOffset, currentY)
                val path = Path().apply {
                    addRoundRect(
                        RectF(0f, 0f, appLogoSize.toFloat(), appLogoSize.toFloat()),
                        16f,
                        16f,
                        Path.Direction.CW
                    )
                }
                canvas.clipPath(path)
                appLogoDrawable.draw(canvas)
                canvas.restore()
                currentY += appLogoSize + paddingBetween / 2f

                canvas.drawText(appName, (totalWidth - appNameBounds.width()) / 2f, currentY + appNameBounds.height(), paint)
                currentY += appNameBounds.height() + brandingPadding
            }

            canvas.save()
            val titleXOffset = (totalWidth - titleWidth) / 2f
            canvas.translate(titleXOffset, currentY)
            slideTitle.draw(canvas)
            canvas.restore()
            currentY += titleHeight + paddingBetween

            canvas.save()
            val descXOffset = (totalWidth - descWidth) / 2f
            canvas.translate(descXOffset, currentY)
            slideDesc.draw(canvas)
            canvas.restore()
            currentY += descHeight + paddingBetween

            canvas.save()
            val contentXOffset = (totalWidth - contentWidth) / 2f
            canvas.translate(contentXOffset, currentY)
            contentContainer.draw(canvas)
            canvas.restore()
        }

        if (isSummary) {
            etDisplayName?.hint = savedHint
            if (savedCompoundDrawables != null) {
                etDisplayName.setCompoundDrawablesRelative(
                    savedCompoundDrawables[0],
                    savedCompoundDrawables[1],
                    savedCompoundDrawables[2],
                    savedCompoundDrawables[3]
                )
            }
            ivProfileEditBadge?.visibility = savedBadgeVisibility
        }

        return bitmap
    }

    private fun getCurrentSlideView(): View? {
        val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView
        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
        return layoutManager?.findViewByPosition(currentSlideIndex)
    }

    private fun shareCard() {
        val bitmap = getBitmapFromView() ?: return
        binding.btnShare.isEnabled = false
        binding.btnShare.text = getString(R.string.wrapped_sharing)

        lifecycleScope.launch {
            val contentUri = withContext(Dispatchers.IO) {
                try {
                    val cachePath = File(cacheDir, "images")
                    cachePath.mkdirs()
                    val file = File(cachePath, "showcase_wrapped_share.png")
                    FileOutputStream(file).use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    FileProvider.getUriForFile(this@YearWrappedActivity, "${packageName}.provider", file)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                } finally {
                    bitmap.recycle()
                }
            }

            binding.btnShare.isEnabled = true
            binding.btnShare.text = getString(R.string.wrapped_share)

            if (contentUri != null) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setDataAndType(contentUri, "image/png")
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.wrapped_share_text, currentYear))
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.wrapped_share_chooser)))
            } else {
                Toast.makeText(this@YearWrappedActivity, getString(R.string.wrapped_share_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCard() {
        val bitmap = getBitmapFromView() ?: return
        binding.btnSave.isEnabled = false
        binding.btnSave.text = getString(R.string.wrapped_saving)
        val filename = "ShowCase_${currentYear}_Wrapped_${Date().time}.png"

        lifecycleScope.launch {
            val savedUri = withContext(Dispatchers.IO) {
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
                    }
                    uri
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                } finally {
                    bitmap.recycle()
                }
            }

            binding.btnSave.isEnabled = true
            binding.btnSave.text = getString(R.string.wrapped_save)

            if (savedUri != null) {
                Snackbar.make(binding.root, getString(R.string.wrapped_save_success), Snackbar.LENGTH_LONG)
                    .setAction(R.string.wrapped_view) {
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(savedUri, "image/png")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            startActivity(viewIntent)
                        } catch (_: Exception) {
                        }
                    }
                    .show()
            } else {
                Toast.makeText(this@YearWrappedActivity, getString(R.string.wrapped_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        progressAnimator?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (!isManuallyPaused && !isTouchPaused) {
            progressAnimator?.resume()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressAnimator?.cancel()
    }
}
