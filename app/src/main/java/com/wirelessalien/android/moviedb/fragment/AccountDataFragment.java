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

package com.wirelessalien.android.moviedb.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.wirelessalien.android.moviedb.R;

public class AccountDataFragment extends BaseFragment {


    public static AccountDataFragment newInstance() {
        return new AccountDataFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate( R.layout.fragment_account_data, container, false);
        TabLayout tabLayout = view.findViewById(R.id.tabs);
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.watchlist)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.favourite)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.rated)));
        tabLayout.addTab( tabLayout.newTab().setText(getString(R.string.lists)));

        // Default to showing the Watchlist tab
        getChildFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new WatchlistFragment())
                .commit();

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Fragment selectedFragment = switch (tab.getPosition()) {
                    case 0 -> new WatchlistFragment();
                    case 1 -> new FavoriteFragment();
                    case 2 -> new RatedListFragment();
                    case 3 -> new MyListsFragment();
                    default -> null;
                };
                if (selectedFragment != null) {
                    getChildFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, selectedFragment)
                            .commit();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Not used
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Not used
            }
        });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuItem item = menu.findItem(R.id.action_search);
        if (item != null) {
            item.setVisible(false);
            item.setEnabled(false);
        }
    }
}
