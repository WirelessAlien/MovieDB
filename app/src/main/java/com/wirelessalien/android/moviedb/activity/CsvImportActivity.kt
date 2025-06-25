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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.wirelessalien.android.moviedb.databinding.ActivityCsvImportBinding
import com.wirelessalien.android.moviedb.databinding.ItemCsvHeaderMappingBinding
import com.wirelessalien.android.moviedb.helper.CsvParserUtil
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.service.ImportService

class CsvImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCsvImportBinding
    private var selectedFileUri: Uri? = null
    private val csvHeaders = mutableListOf<String>()
    private val mappingSpinners = mutableListOf<Spinner>()

    private val databaseFields = linkedMapOf(
        "Do Not Import" to "DO_NOT_IMPORT",
        "TMDB ID (movie_id)" to MovieDatabaseHelper.COLUMN_MOVIES_ID,
        "Title" to MovieDatabaseHelper.COLUMN_TITLE,
        "Summary/Overview" to MovieDatabaseHelper.COLUMN_SUMMARY,
        "TMDB Rating (0-10)" to MovieDatabaseHelper.COLUMN_RATING,
        "My Rating (0-10)" to MovieDatabaseHelper.COLUMN_PERSONAL_RATING,
        "Release Date (YYYY-MM-DD)" to MovieDatabaseHelper.COLUMN_RELEASE_DATE,
        "Type (1 for Movie, 0 for TV Show)" to MovieDatabaseHelper.COLUMN_MOVIE,
        "Genres (comma separated text)" to MovieDatabaseHelper.COLUMN_GENRES,
        "My Watch Status (0:Plan, 1:Watched, 2:Watching, 3:Hold, 4:Dropped)" to MovieDatabaseHelper.COLUMN_CATEGORIES,
        "Poster URL (poster_path)" to MovieDatabaseHelper.COLUMN_ICON,
        "Backdrop URL (backdrop_path)" to MovieDatabaseHelper.COLUMN_IMAGE,
        "My Review Text" to MovieDatabaseHelper.COLUMN_MOVIE_REVIEW,
        "My Watch Start Date (YYYY-MM-DD)" to MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE,
        "My Watch Finish Date (YYYY-MM-DD)" to MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE,
        "Season Number" to MovieDatabaseHelper.COLUMN_SEASON_NUMBER,
        "Episode Number" to MovieDatabaseHelper.COLUMN_EPISODE_NUMBER,
        "Episode My Rating (0-10)" to MovieDatabaseHelper.COLUMN_EPISODE_RATING,
        "Episode Watch Date (YYYY-MM-DD)" to MovieDatabaseHelper.COLUMN_EPISODE_WATCH_DATE,
        "Episode My Review Text" to MovieDatabaseHelper.COLUMN_EPISODE_REVIEW
    )

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                selectedFileUri = uri
                binding.textViewSelectedFileName.text = uri.pathSegments.lastOrNull() ?: "Selected File"
                loadCsvHeaders(uri)
                binding.buttonStartImport.isEnabled = true
                binding.textViewNoHeadersMessage.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCsvImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Import CSV Data"

        binding.buttonSelectCsv.setOnClickListener {
            openFilePicker()
        }

        binding.buttonStartImport.setOnClickListener {
            startImportProcess()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to pick files.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadCsvHeaders(uri: Uri) {
        csvHeaders.clear()
        mappingSpinners.clear()
        binding.linearLayoutHeaderMappings.removeAllViews()

        val headers = CsvParserUtil.readCsvHeaders(this, uri)
        if (headers.isNullOrEmpty()) {
            Toast.makeText(this, "Could not read headers from CSV.", Toast.LENGTH_SHORT).show()
            binding.textViewNoHeadersMessage.text = "Could not read headers or CSV is empty."
            binding.textViewNoHeadersMessage.visibility = View.VISIBLE
            binding.buttonStartImport.isEnabled = false
            return
        }

        csvHeaders.addAll(headers)
        populateHeaderMappingUI(headers)
    }

    private fun populateHeaderMappingUI(csvHeaders: List<String>) {
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, databaseFields.keys.toList())
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        csvHeaders.forEach { header ->
            val mappingViewBinding = ItemCsvHeaderMappingBinding.inflate(LayoutInflater.from(this), binding.linearLayoutHeaderMappings, false)

            mappingViewBinding.textViewCsvHeader.text = header
            mappingViewBinding.spinnerDbField.adapter = spinnerAdapter

            val bestMatchIndex = findBestMatchForHeader(header)
            mappingViewBinding.spinnerDbField.setSelection(bestMatchIndex)

            mappingSpinners.add(mappingViewBinding.spinnerDbField)
            binding.linearLayoutHeaderMappings.addView(mappingViewBinding.root)
        }
    }

    private fun findBestMatchForHeader(csvHeader: String): Int {
        val normalizedCsvHeader = csvHeader.lowercase().replace("_", "").replace(" ", "")
        var bestMatchIndex = 0
        var highestSimilarity = 0.7

        databaseFields.keys.forEachIndexed { index, dbFieldDisplayName ->
            if (index == 0) return@forEachIndexed

            val normalizedDbField = dbFieldDisplayName.lowercase()
                .replace("_", "")
                .replace(" ", "")
                .substringBefore("(")

            if (normalizedCsvHeader.contains(normalizedDbField) || normalizedDbField.contains(normalizedCsvHeader)) {
                val currentSimilarity = if (normalizedCsvHeader == normalizedDbField) 1.0 else 0.8
                if (currentSimilarity > highestSimilarity) {
                    highestSimilarity = currentSimilarity
                    bestMatchIndex = index
                }
            }
        }
        return bestMatchIndex
    }


    private fun startImportProcess() {
        if (selectedFileUri == null) {
            Toast.makeText(this, "Please select a CSV file first.", Toast.LENGTH_SHORT).show()
            return
        }

        val headerMapping = HashMap<String, String>()
        var hasTmdbIdMapping = false
        var hasTitleMapping = false

        csvHeaders.forEachIndexed { index, csvHeader ->
            val selectedDbFieldKey = mappingSpinners[index].selectedItem as String
            val dbColumnConstant = databaseFields[selectedDbFieldKey]

            if (dbColumnConstant != null && dbColumnConstant != "DO_NOT_IMPORT") {
                headerMapping[csvHeader] = dbColumnConstant
                if (dbColumnConstant == MovieDatabaseHelper.COLUMN_MOVIES_ID) hasTmdbIdMapping = true
                if (dbColumnConstant == MovieDatabaseHelper.COLUMN_TITLE) hasTitleMapping = true
            }
        }

        if (headerMapping.isEmpty()) {
            Toast.makeText(this, "Please map at least one CSV column.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasTmdbIdMapping && !hasTitleMapping) {
            Toast.makeText(this, "Warning: It's highly recommended to map a column to 'TMDB ID' or 'Title' for effective import.", Toast.LENGTH_LONG).show()
        }


        val intent = Intent(this, ImportService::class.java).apply {
            action = ImportService.ACTION_START_IMPORT
            putExtra(ImportService.EXTRA_FILE_URI, selectedFileUri)
            putExtra(ImportService.EXTRA_HEADER_MAPPING, headerMapping)
        }
        startService(intent)

        Toast.makeText(this, "Import process started...", Toast.LENGTH_LONG).show()
        finish()
    }
}