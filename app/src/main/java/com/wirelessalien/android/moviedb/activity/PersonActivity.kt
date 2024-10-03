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
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.fragment.PersonFragment
import com.wirelessalien.android.moviedb.fragment.PersonFragment.Companion.newInstance
import com.wirelessalien.android.moviedb.helper.PeopleDatabaseHelper
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener

class PersonActivity : BaseActivity() , AdapterDataChangedListener {
    private var mSearchAction: MenuItem? = null
    private var isSearchOpened = false
    private lateinit var preferences: SharedPreferences
    private val REQUEST_CODE_ASK_PERMISSIONS_EXPORT = 123
    private val REQUEST_CODE_ASK_PERMISSIONS_IMPORT = 124
    private var exportDirectoryUri: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.title_people)

        exportDirectoryUri = preferences.getString("db_export_directory", null)

        if (savedInstanceState == null) {
            val personFragment = newInstance()
            val transaction = supportFragmentManager.beginTransaction()
            transaction.add(R.id.fragment_container, personFragment, "PersonFragment")
            transaction.commit()
        }
        OnBackPressedDispatcher().addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearchOpened) {
                    handleMenuSearch()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        val inflater = menuInflater
        inflater.inflate(R.menu.options_menu, menu)
        inflater.inflate(R.menu.database_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        // Search action
        if (id == R.id.action_search) {
            handleMenuSearch()
            return true
        }

        if (id == R.id.action_export)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        REQUEST_CODE_ASK_PERMISSIONS_EXPORT
                    )
                } else {
                    //call export function from PeopleDatabaseHelper
                    val peopleDatabaseHelper = PeopleDatabaseHelper(this)
                    peopleDatabaseHelper.exportDatabase(this, exportDirectoryUri)
                }
            } else {
                //call export function from PeopleDatabaseHelper
                val peopleDatabaseHelper = PeopleDatabaseHelper(this)
                peopleDatabaseHelper.exportDatabase(this, exportDirectoryUri)
            }

        if (id == R.id.action_import)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        REQUEST_CODE_ASK_PERMISSIONS_IMPORT
                    )
                } else {
                    val intent = Intent(this, ImportActivity::class.java)
                    startActivity(intent)

                }
            } else {
                val intent = Intent(this, ImportActivity::class.java)
                startActivity(intent)

            }

        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        mSearchAction = menu.findItem(R.id.action_search)
        return super.onPrepareOptionsMenu(menu)
    }

    /**
     * Handles input from the search bar and icon.
     */
    private fun handleMenuSearch() {
        val liveSearch = preferences.getBoolean(LIVE_SEARCH_PREFERENCE, true)
        val searchView = mSearchAction!!.actionView as SearchView?
        if (isSearchOpened) {
            if (searchView != null && searchView.query.toString() == "") {
                searchView.isIconified = true
                mSearchAction!!.collapseActionView()
                isSearchOpened = false
                cancelSearchInFragment()
            }
        } else {
            mSearchAction!!.expandActionView()
            isSearchOpened = true
            searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    searchInFragment(query)
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    if (liveSearch) {
                        searchInFragment(newText)
                    }
                    return true
                }
            })
        }
    }

    private fun searchInFragment(query: String) {
        val fragmentManager = supportFragmentManager
        val fragmentList = fragmentManager.fragments
        for (fragment in fragmentList) {
            val personFragment = fragment as PersonFragment
            personFragment.search(query)
        }
    }

    /**
     * Cancel the searching process in the fragment.
     */
    private fun cancelSearchInFragment() {
        val fragmentManager = supportFragmentManager
        val fragmentList = fragmentManager.fragments
        for (fragment in fragmentList) {
            val personFragment = fragment as PersonFragment
            personFragment.cancelSearch()
        }
    }

    override fun onAdapterDataChangedListener() {
        // Do nothing
    }


    companion object {
        private const val LIVE_SEARCH_PREFERENCE = "key_live_search"
    }
}
