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
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.ActivityCsvImportBinding
import com.wirelessalien.android.moviedb.databinding.ItemCsvHeaderMappingBinding
import com.wirelessalien.android.moviedb.helper.CsvParserUtil
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.service.ImportService

class CsvImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCsvImportBinding
    private var selectedFileUri: Uri? = null
    private val csvHeaders = mutableListOf<String>()
    private val mappingAutoCompleteTextViews = mutableListOf<AutoCompleteTextView>()

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

    private fun updateDefaultTypeRadioGroupVisibility() {
        var isMovieColumnMapped = false
        mappingAutoCompleteTextViews.forEach { autoCompleteTextView ->
            val selectedDbFieldKey = autoCompleteTextView.text.toString()
            val dbColumnConstant = databaseFields[selectedDbFieldKey]
            if (dbColumnConstant == MovieDatabaseHelper.COLUMN_MOVIE && dbColumnConstant != "DO_NOT_IMPORT") {
                isMovieColumnMapped = true
            }
        }

        if (isMovieColumnMapped) {
            binding.radioGroupDefaultType.visibility = View.GONE
        } else {
            binding.radioGroupDefaultType.visibility = View.VISIBLE
            if (binding.radioGroupDefaultType.checkedRadioButtonId == -1) {
                binding.radioButtonMovie.isChecked = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCsvImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.buttonSelectCsv.setOnClickListener {
            openFilePicker()
        }

        binding.buttonStartImport.setOnClickListener {
            startImportProcess()
        }

        binding.editTextDifferentiator.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                if (selectedFileUri != null) {
                    loadCsvHeaders(selectedFileUri!!)
                }
            }
        }


        binding.editTextDifferentiator.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                if (selectedFileUri != null) {
                    loadCsvHeaders(selectedFileUri!!)
                }
                true
            } else {
                false
            }
        }

        binding.editTextDifferentiator.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                if (selectedFileUri != null) {
                    loadCsvHeaders(selectedFileUri!!)
                }
            }
        }

        binding.buttonInfo.setOnClickListener {
            val bottomSheetDialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.bottom_sheet_import_info, null)
            bottomSheetDialog.setContentView(view)
            bottomSheetDialog.show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.no_app_found_to_pick_files), Toast.LENGTH_LONG).show()
        }
    }

    private fun loadCsvHeaders(uri: Uri) {
        csvHeaders.clear()
        mappingAutoCompleteTextViews.clear()
        binding.linearLayoutHeaderMappings.removeAllViews()

        val delimiter = getDelimiterFromInput()

        val headers = CsvParserUtil.readCsvHeaders(this, uri, delimiter)
        if (headers.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.could_not_read_headers_2), Toast.LENGTH_LONG).show()
            binding.textViewNoHeadersMessage.text = getString(R.string.could_not_read_headers)
            binding.textViewNoHeadersMessage.visibility = View.VISIBLE
            binding.buttonStartImport.isEnabled = false
            return
        }

        csvHeaders.addAll(headers)
        populateHeaderMappingUI(headers)
        updateDefaultTypeRadioGroupVisibility()
    }

    private fun populateHeaderMappingUI(csvHeaders: List<String>) {
        val dbFieldKeys = databaseFields.keys.toList()
        val autoCompleteAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, dbFieldKeys)

        csvHeaders.forEach { header ->
            val mappingViewBinding = ItemCsvHeaderMappingBinding.inflate(LayoutInflater.from(this), binding.linearLayoutHeaderMappings, false)

            val textInputLayout = mappingViewBinding.textInputLayoutDbField
            val autoCompleteTextView = mappingViewBinding.autoCompleteDbField

            textInputLayout.hint = header
            autoCompleteTextView.setAdapter(autoCompleteAdapter)

            val bestMatchIndex = findBestMatchForHeader(header)
            if (bestMatchIndex < dbFieldKeys.size) {
                autoCompleteTextView.setText(dbFieldKeys[bestMatchIndex], false)
            }

            mappingAutoCompleteTextViews.add(autoCompleteTextView)
            binding.linearLayoutHeaderMappings.addView(mappingViewBinding.root)

            autoCompleteTextView.setOnItemClickListener { _, _, _, _ ->
                updateDefaultTypeRadioGroupVisibility()
            }
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

    private fun getDelimiterFromInput(): Char {
        val delimiterText = binding.editTextDifferentiator.text.toString()
        return if (delimiterText.length == 1) {
            delimiterText[0]
        } else {
            if (delimiterText.isNotEmpty()) {
                Toast.makeText(this, getString(R.string.delimiter_format), Toast.LENGTH_SHORT).show()
            }
            ',' // Default delimiter
        }
    }


    private fun startImportProcess() {
        if (selectedFileUri == null) {
            Toast.makeText(this,
                getString(R.string.please_select_a_csv_file_first), Toast.LENGTH_SHORT).show()
            return
        }

        if (binding.radioGroupDefaultType.visibility == View.VISIBLE && binding.radioGroupDefaultType.checkedRadioButtonId == -1) {
            Toast.makeText(this, getString(R.string.select_a_default_item_type), Toast.LENGTH_SHORT).show()
            return
        }

        val headerMapping = HashMap<String, String>()
        var hasTmdbIdMapping = false
        var isMovieColumnExplicitlyMapped = false

        csvHeaders.forEachIndexed { index, csvHeader ->
            val selectedDbFieldKey = mappingAutoCompleteTextViews[index].text.toString()
            val dbColumnConstant = databaseFields[selectedDbFieldKey]

            if (dbColumnConstant != null && dbColumnConstant != "DO_NOT_IMPORT") {
                headerMapping[csvHeader] = dbColumnConstant
                if (dbColumnConstant == MovieDatabaseHelper.COLUMN_MOVIES_ID) hasTmdbIdMapping = true
                if (dbColumnConstant == MovieDatabaseHelper.COLUMN_TITLE) hasTitleMapping = true
                if (dbColumnConstant == MovieDatabaseHelper.COLUMN_MOVIE) isMovieColumnExplicitlyMapped = true
            }
        }

        if (headerMapping.isEmpty()) {
            Toast.makeText(this,
                getString(R.string.please_map_at_least_one_csv_column), Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasTmdbIdMapping) {
            Toast.makeText(this, getString(R.string.tmdb_id_or_title_required_import), Toast.LENGTH_LONG).show()
            return
        }

        val delimiter = getDelimiterFromInput()

        val intent = Intent(this, ImportService::class.java).apply {
            action = ImportService.ACTION_START_IMPORT
            putExtra(ImportService.EXTRA_FILE_URI, selectedFileUri)
            putExtra(ImportService.EXTRA_HEADER_MAPPING, headerMapping)
            putExtra(ImportService.EXTRA_DELIMITER, delimiter)

            if (!isMovieColumnExplicitlyMapped && binding.radioGroupDefaultType.visibility == View.VISIBLE) {
                val selectedTypeId = binding.radioGroupDefaultType.checkedRadioButtonId
                if (selectedTypeId != -1) {
                    val defaultIsMovie = if (selectedTypeId == binding.radioButtonMovie.id) 1 else 0
                    putExtra(ImportService.EXTRA_DEFAULT_IS_MOVIE_TYPE, defaultIsMovie)
                }
            }
        }
        startService(intent)

        Toast.makeText(this, getString(R.string.import_process_started), Toast.LENGTH_LONG).show()
        finish()
    }
}