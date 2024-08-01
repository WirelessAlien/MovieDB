/*
 *     This file is part of Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     Movie DB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movie DB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movie DB.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.fragment.PersonFragment;

import java.util.List;

public class PersonActivity extends BaseActivity {

    private MenuItem mSearchAction;
    private boolean isSearchOpened = false;
    private final static String LIVE_SEARCH_PREFERENCE = "key_live_search";
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState == null) {
            PersonFragment personFragment = PersonFragment.newInstance();
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.fragment_container, personFragment, "PersonFragment");
            transaction.commit();
        }
    }

    @Override
    public void onBackPressed() {
        // When search is opened and the user presses back,
        // execute a custom action (removing search query or stop searching)
        if (isSearchOpened) {
            handleMenuSearch();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Search action
        if (id == R.id.action_search) {
            handleMenuSearch();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mSearchAction = menu.findItem(R.id.action_search);
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * Handles input from the search bar and icon.
     */
    private void handleMenuSearch() {
        final boolean liveSearch = preferences.getBoolean(LIVE_SEARCH_PREFERENCE, true);

        SearchView searchView = (SearchView) mSearchAction.getActionView();

        if (isSearchOpened) {
            if (searchView != null && searchView.getQuery().toString().equals( "" )) {
                searchView.setIconified( true );
                mSearchAction.collapseActionView();
                isSearchOpened = false;
                cancelSearchInFragment();
            }
        } else {
            mSearchAction.expandActionView();
            isSearchOpened = true;

            if (searchView != null) {
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        searchInFragment(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (liveSearch) {
                            searchInFragment(newText);
                        }
                        return true;
                    }
                });
            }
        }
    }

    private void searchInFragment(String query) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        List<Fragment> fragmentList = fragmentManager.getFragments();
        for (Fragment fragment : fragmentList) {
            PersonFragment personFragment = (PersonFragment) fragment;
            personFragment.search(query);
        }
    }
    /**
     * Cancel the searching process in the fragment.
     */
    private void cancelSearchInFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        List<Fragment> fragmentList = fragmentManager.getFragments();
        for (Fragment fragment : fragmentList) {
            PersonFragment personFragment = (PersonFragment) fragment;
            personFragment.cancelSearch();
        }

    }
}
