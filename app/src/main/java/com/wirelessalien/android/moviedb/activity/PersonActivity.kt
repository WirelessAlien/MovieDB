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
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.adapter.PersonPagingAdapter
import com.wirelessalien.android.moviedb.databinding.ActivityPersonBinding
import com.wirelessalien.android.moviedb.fragment.BaseFragment
import com.wirelessalien.android.moviedb.fragment.PersonFragment.Companion.newInstance
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import com.wirelessalien.android.moviedb.helper.PeopleDatabaseHelper
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener
import com.wirelessalien.android.moviedb.pagingSource.SearchPersonPagingSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PersonActivity : BaseActivity() , AdapterDataChangedListener {
    private var mSearchAction: MenuItem? = null
    private lateinit var binding: ActivityPersonBinding
    private lateinit var preferences: SharedPreferences
    private val REQUEST_CODE_ASK_PERMISSIONS_EXPORT = 123
    private val REQUEST_CODE_ASK_PERMISSIONS_IMPORT = 124
    private var exportDirectoryUri: String? = null
    private lateinit var mSearchPersonAdapter: PersonPagingAdapter
    private lateinit var mShowGridLayoutManager: GridLayoutManager
    private var apiReadAccessToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        apiReadAccessToken = ConfigHelper.getConfigValue(applicationContext, "api_read_access_token")
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = getString(R.string.title_people)

        exportDirectoryUri = preferences.getString("db_export_directory", null)

        if (savedInstanceState == null) {
            val personFragment = newInstance()
            val transaction = supportFragmentManager.beginTransaction()
            transaction.add(R.id.fragment_container, personFragment, "PersonFragment")
            transaction.commit()
        }

        mSearchPersonAdapter = PersonPagingAdapter()

        val mShowGridView = GridLayoutManager(this, preferences.getInt(BaseFragment.GRID_SIZE_PREFERENCE, 3))
        binding.searchResultsRecyclerView.layoutManager = mShowGridView
        mShowGridLayoutManager = mShowGridView

        binding.searchResultsRecyclerView.adapter = mSearchPersonAdapter

        setupSearchView()
        addMenuProvider()

        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                binding.searchView.hide()
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)

        binding.searchView.addTransitionListener { _, _, newState ->
            if (newState === com.google.android.material.search.SearchView.TransitionState.SHOWING) {
                callback.isEnabled = true
            } else if (newState === com.google.android.material.search.SearchView.TransitionState.HIDING) {
                callback.isEnabled = false
                binding.searchResultsRecyclerView.adapter = null
            }
        }
    }

    private fun setupSearchView() {
        val liveSearch = preferences.getBoolean(LIVE_SEARCH_PREFERENCE, false)

        if (liveSearch) {
            binding.searchView.editText.addTextChangedListener(object : TextWatcher {
                private val handler = Handler(Looper.getMainLooper())
                private var workRunnable: Runnable? = null
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (workRunnable != null) {
                        handler.removeCallbacks(workRunnable!!)
                    }
                    workRunnable = Runnable {
                        val query = s.toString()
                        personSearch(query)
                    }
                    handler.postDelayed(workRunnable!!, 500)
                }

                override fun afterTextChanged(s: Editable) {}
            })
        } else {
            binding.searchView.editText.setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    val query = v.text.toString()
                    personSearch(query)
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else {
                    false
                }
            }
        }
    }

    fun personSearch(query: String?) {
        if (query.isNullOrEmpty()) {
            return
        }

        binding.searchResultsRecyclerView.adapter = mSearchPersonAdapter

        lifecycleScope.launch {
            Pager(PagingConfig(pageSize = 20)) {
                SearchPersonPagingSource(apiReadAccessToken?: "", query, this@PersonActivity)
            }.flow.collectLatest { pagingData ->
                mSearchPersonAdapter.submitData(pagingData)
            }
        }

        mSearchPersonAdapter.addLoadStateListener { loadState ->
            when (loadState.source.refresh) {
                is LoadState.Loading -> {
                    binding.searchResultsRecyclerView.visibility = View.GONE
                    binding.shimmerFrameLayout.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.startShimmer()
                }

                is LoadState.NotLoading -> {
                    binding.searchResultsRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                }

                is LoadState.Error -> {
                    binding.searchResultsRecyclerView.visibility = View.VISIBLE
                    binding.shimmerFrameLayout.visibility = View.GONE
                    binding.shimmerFrameLayout.stopShimmer()
                    Toast.makeText(
                        this,
                        getString(R.string.error_loading_data),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun addMenuProvider() {
        val menuHost: MenuHost = this
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
                menuInflater.inflate(R.menu.database_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                val id = menuItem.itemId

                if (id == R.id.action_export)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(
                                this@PersonActivity,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            ActivityCompat.requestPermissions(
                                this@PersonActivity,
                                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                REQUEST_CODE_ASK_PERMISSIONS_EXPORT
                            )
                        } else {
                            //call export function from PeopleDatabaseHelper
                            val peopleDatabaseHelper = PeopleDatabaseHelper(this@PersonActivity)
                            peopleDatabaseHelper.exportDatabase(this@PersonActivity, exportDirectoryUri)
                        }
                    } else {
                        //call export function from PeopleDatabaseHelper
                        val peopleDatabaseHelper = PeopleDatabaseHelper(this@PersonActivity)
                        peopleDatabaseHelper.exportDatabase(this@PersonActivity, exportDirectoryUri)
                    }

                if (id == R.id.action_import)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(
                                this@PersonActivity,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            ActivityCompat.requestPermissions(
                                this@PersonActivity,
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                REQUEST_CODE_ASK_PERMISSIONS_IMPORT
                            )
                        } else {
                            val intent = Intent(this@PersonActivity, ImportActivity::class.java)
                            startActivity(intent)

                        }
                    } else {
                        val intent = Intent(this@PersonActivity, ImportActivity::class.java)
                        startActivity(intent)

                    }

                return false
            }

            override fun onPrepareMenu(menu: Menu) {
                mSearchAction = menu.findItem(R.id.action_search)
                mSearchAction?.isVisible = false
                //hide account menu item
                val accountMenuItem = menu.findItem(R.id.action_login)
                accountMenuItem.isVisible = false
            }
        }, this, Lifecycle.State.RESUMED)
    }

    override fun onAdapterDataChangedListener() {
        // Do nothing
    }

    fun getBinding(): ActivityPersonBinding {
        return binding
    }

    companion object {
        private const val LIVE_SEARCH_PREFERENCE = "key_live_search"
    }
}
