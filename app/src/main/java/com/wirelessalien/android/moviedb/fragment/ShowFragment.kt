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
package com.wirelessalien.android.moviedb.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.preference.PreferenceManager
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.BaseActivity
import com.wirelessalien.android.moviedb.activity.FilterActivity
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter
import com.wirelessalien.android.moviedb.adapter.ShowPagingAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityMainBinding
import com.wirelessalien.android.moviedb.databinding.FragmentShowBinding
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.pagingSource.ShowPagingSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

class ShowFragment : BaseFragment() {

    private var apiKey: String? = null
    private var mListType: String? = null
    private var filterParameter = ""
    private var filterChanged = false

    private lateinit var pagingAdapter: ShowPagingAdapter
    private lateinit var binding: FragmentShowBinding
    private lateinit var activityBinding: ActivityMainBinding



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        apiKey = ConfigHelper.getConfigValue(requireContext().applicationContext, "api_key")
        mListType = arguments?.getString(ARG_LIST_TYPE) ?: SectionsPagerAdapter.MOVIE
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        mShowArrayList = ArrayList()
        mShowGenreList = HashMap()
        pagingAdapter = ShowPagingAdapter(
            mShowGenreList,
            preferences.getBoolean(SHOWS_LIST_PREFERENCE, false),
            false
        )

        // Set filterParameter based on mListType
        filterParameter = if (mListType == SectionsPagerAdapter.MOVIE) {
            "sort_by=popularity.desc&" + BaseActivity.getRegionParameter(requireContext())
        } else {
            "sort_by=popularity.desc&" + BaseActivity.getRegionParameter2(requireContext()) + "&with_watch_monetization_types=flatrate|free|ads|rent|buy"
        }

        // Use persistent filtering if it is enabled.
        if (preferences.getBoolean(PERSISTENT_FILTERING_PREFERENCE, false)) {
            filterShows()
        } else {
            loadInitialData()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentShowBinding.inflate(inflater, container, false)
        val fragmentView = binding.root
        activityBinding = (activity as MainActivity).getBinding()
        showPagingList(fragmentView)
        activityBinding.fab.setImageResource(R.drawable.ic_filter_list)
        activityBinding.fab.isEnabled = true
        activityBinding.fab.setOnClickListener {
            filterRequestLauncher.launch(Intent())
        }

        fetchGenreList(mListType?: SectionsPagerAdapter.MOVIE)

        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_search, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_search -> {
                        activityBinding.searchView.show()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner)
    }

    override fun onResume() {
        super.onResume()
        activityBinding.fab.visibility = View.VISIBLE
        activityBinding.fab2.visibility = View.GONE
        activityBinding.fab.setImageResource(R.drawable.ic_filter_list)
        activityBinding.fab.isEnabled = true
        activityBinding.fab.setOnClickListener {
            filterRequestLauncher.launch(Intent())
        }
        if (preferences.getBoolean("key_show_continue_watching", true) &&
            (preferences.getString("sync_provider", "local") == "local" || preferences.getBoolean("force_local_sync", false))) {
            activityBinding.upnextChip.visibility = View.VISIBLE
        } else {
            activityBinding.upnextChip.visibility = View.GONE
        }
        requireActivity().invalidateOptionsMenu()
    }

    private var filterRequestContract: ActivityResultContract<Intent, Boolean> =
        object : ActivityResultContract<Intent, Boolean>() {
            override fun createIntent(context: Context, input: Intent): Intent {
                return Intent(context, FilterActivity::class.java).putExtra("mode", mListType)
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
                return resultCode == Activity.RESULT_OK
            }
        }

    private var filterRequestLauncher = registerForActivityResult(
        filterRequestContract
    ) { result: Boolean ->
        if (result) {
            filterShows()
        }
    }

    /**
     * Filters the list of shows based on the preferences set in FilterActivity.
     */
    private fun filterShows() {
        val sharedPreferences = requireActivity().getSharedPreferences(
            FilterActivity.FILTER_PREFERENCES,
            Context.MODE_PRIVATE
        )

        val calendar: android.icu.util.Calendar = android.icu.util.Calendar.getInstance()
        calendar.time = Date()
        calendar.add(android.icu.util.Calendar.DAY_OF_YEAR, 500)
        val dateAfterYear = calendar.time
        val dateFormat = android.icu.text.SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

        var sortPreference: String?
        if (sharedPreferences.getString(FilterActivity.FILTER_SORT, null)
                .also { sortPreference = it } != null
        ) {
            filterParameter = when (sortPreference) {
                "best_rated" ->
                    if (mListType == SectionsPagerAdapter.MOVIE) {
                        BaseActivity.getRegionParameter(requireContext()) + "&sort_by=vote_average.desc"
                    } else {
                        "sort_by=vote_average.desc&" + BaseActivity.getRegionParameter2(requireContext()) + "&with_watch_monetization_types=flatrate|free|ads|rent|buy"
                    }
                "release_date" ->
                    if (mListType == SectionsPagerAdapter.MOVIE) {
                        BaseActivity.getRegionParameter(requireContext()) + "&primary_release_date.lte=" + dateFormat.format(dateAfterYear) + "&sort_by=primary_release_date.desc"
                    } else {
                        "sort_by=first_air_date.desc&" + BaseActivity.getRegionParameter2(requireContext()) + "&with_watch_monetization_types=flatrate|free|ads|rent|buy"
                    }
                "alphabetic_order" ->
                    if (mListType == SectionsPagerAdapter.MOVIE) {
                        "sort_by=title.desc"
                    } else {
                        "sort_by=name.desc"
                    }
                else ->
                    if (mListType == SectionsPagerAdapter.MOVIE) {
                        "sort_by=popularity.desc&" + BaseActivity.getRegionParameter(requireContext())
                    } else {
                        "sort_by=popularity.desc&" + BaseActivity.getRegionParameter2(requireContext()) + "&with_watch_monetization_types=flatrate|free|ads|rent|buy"
                    }
            }
        }

        // Add the dates as constraints to the new API call.
        var datePreference: String?
        if (sharedPreferences.getString(FilterActivity.FILTER_DATES, null)
                .also { datePreference = it } != null
        ) {
            when (datePreference) {
                "in_theater" -> {
                    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val today = simpleDateFormat.format(Date())
                    val calendar1 = GregorianCalendar.getInstance()
                    calendar1.time = Date()
                    calendar1.add(Calendar.DAY_OF_YEAR, -31)
                    val monthAgo = simpleDateFormat.format(calendar.time)
                    filterParameter += ("&primary_release_date.gte=" + monthAgo
                            + "&primary_release_date.lte=" + today)
                }

                "between_dates" -> {
                    sharedPreferences.getString(FilterActivity.FILTER_START_DATE, null)?.let { startDate ->
                        filterParameter += "&primary_release_date.gte=$startDate"
                    }
                    sharedPreferences.getString(FilterActivity.FILTER_END_DATE, null)?.let { endDate ->
                        filterParameter += "&primary_release_date.lte=$endDate"
                    }
                }

                else -> {}
            }
        }

        // Add the genres to be included as constraints to the API call.
        val withGenres = FilterActivity.convertStringToArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_WITH_GENRES,
                null
            ), ", "
        )
        if (withGenres != null && withGenres.isNotEmpty()) {
            filterParameter += "&with_genres="
            for (i in withGenres.indices) {
                filterParameter += withGenres[i]
                if (i + 1 != withGenres.size) {
                    filterParameter += ","
                }
            }
        }

        // Add the genres to be excluded as constraints to the API call.
        val withoutGenres = FilterActivity.convertStringToArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_WITHOUT_GENRES,
                null
            ), ", "
        )
        if (withoutGenres != null && withoutGenres.isNotEmpty()) {
            filterParameter += "&without_genres="
            for (i in withoutGenres.indices) {
                filterParameter += withoutGenres[i]
                if (i + 1 != withoutGenres.size) {
                    filterParameter += ","
                }
            }
        }

        // Add keyword-IDs as the constraints to the API call.
        var withKeywords: String
        if (sharedPreferences.getString(FilterActivity.FILTER_WITH_KEYWORDS, "")
                .also { withKeywords = it!! } != ""
        ) {
            filterParameter += "&with_keywords=$withKeywords"
        }
        var withoutKeywords: String
        if (sharedPreferences.getString(FilterActivity.FILTER_WITHOUT_KEYWORDS, "")
                .also { withoutKeywords = it!! } != ""
        ) {
            filterParameter += "&without_keywords=$withoutKeywords"
        }
        filterChanged = true
        loadInitialData()
    }

    /**
     * Loads the initial data using Paging 3.
     */
    private fun loadInitialData() {
        lifecycleScope.launch {
            Pager(PagingConfig(pageSize = 20)) {
                ShowPagingSource(mListType, filterParameter, apiKey, requireContext())
            }.flow.collectLatest { pagingData ->
                pagingAdapter.submitData(pagingData)
            }
        }

        pagingAdapter.addLoadStateListener { loadState ->
            when (loadState.source.refresh) {
                is LoadState.Loading -> {
                    binding.showRecyclerView.visibility = View.GONE
                    binding.shimmerFrameLayout.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.startShimmer()
                }

                is LoadState.NotLoading -> {
                    binding.showRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                }

                is LoadState.Error -> {
                    binding.showRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                    binding.shimmerFrameLayout.visibility = View.GONE
                    val errorMessage = (loadState.source.refresh as LoadState.Error).error.message
                    Toast.makeText(requireContext(), getString(R.string.error_loading_data) + ": " + errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun showPagingList(fragmentView: View) {
        super.showPagingList(fragmentView)
        mShowView.adapter = pagingAdapter
    }

    companion object {
        const val ARG_LIST_TYPE = "arg_list_type"
        fun newInstance(listType: String?): ShowFragment {
            val fragment = ShowFragment()
            val args = Bundle()
            args.putString(ARG_LIST_TYPE, listType)
            fragment.arguments = args
            return fragment
        }
    }
}