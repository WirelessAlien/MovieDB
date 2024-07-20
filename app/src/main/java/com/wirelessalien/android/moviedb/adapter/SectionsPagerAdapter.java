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

package com.wirelessalien.android.moviedb.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseArray;

import androidx.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.fragment.AccountDataFragment;
import com.wirelessalien.android.moviedb.fragment.ListFragment;
import com.wirelessalien.android.moviedb.fragment.PersonFragment;
import com.wirelessalien.android.moviedb.fragment.ShowFragment;

import java.util.ArrayList;


public class SectionsPagerAdapter extends FragmentStateAdapter {
    public final static String HIDE_MOVIES_PREFERENCE = "key_hide_movies_tab";
    public final static String HIDE_SERIES_PREFERENCE = "key_hide_series_tab";
    public final static String HIDE_SAVED_PREFERENCE = "key_hide_saved_tab";
    public final static String HIDE_ACCOUNT_DATA_PREFERENCE = "key_hide_account_data_tab";
    public final static String HIDE_PERSON_PREFERENCE = "key_hide_person_tab";

    public final static String MOVIE = "movie";
    public final static String TV = "tv";

    private static int NUM_ITEMS = 5;
    private final SharedPreferences preferences;
    public final String movieTabTitle;
    public final String seriesTabTitle;
    public final String savedTabTitle;
    public final String personTabTitle;
    public final String accountDataTabTitle;
    private final SparseArray<Fragment> fragmentList = new SparseArray<>();


    /**
     * Determines the (amount of) Pages to be shown.
     *
     * @param fa      given to super.
     * @param context context to retrieve the preferences.
     */
    public SectionsPagerAdapter(FragmentActivity fa, Context context) {
        super(fa);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Instantiate NUM_ITEMS again to prevent decreasing further than intended when
        // recreating SectionPagerAdapter but keeping the old NUM_ITEMS value.
        NUM_ITEMS = 5;

        if (preferences.getBoolean(HIDE_MOVIES_PREFERENCE, false)) {
            NUM_ITEMS--;
        }

        if (preferences.getBoolean(HIDE_SERIES_PREFERENCE, false)) {
            NUM_ITEMS--;
        }

        if (preferences.getBoolean(HIDE_SAVED_PREFERENCE, false)) {
            NUM_ITEMS--;
        }

        if (preferences.getBoolean( HIDE_ACCOUNT_DATA_PREFERENCE, false )) {
            NUM_ITEMS--;
        }

        if (preferences.getBoolean(HIDE_PERSON_PREFERENCE, false)) {
            NUM_ITEMS--;
        }

        movieTabTitle = context.getResources().getString( R.string.movie_tab_title);
        seriesTabTitle = context.getResources().getString(R.string.series_tab_title);
        savedTabTitle = context.getResources().getString(R.string.saved_tab_title);
        accountDataTabTitle = context.getResources().getString(R.string.account_data_tab_title);
        personTabTitle = context.getResources().getString(R.string.person_tab_title);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment fragment = switch (getCorrectedPosition( position )) {
            case 0 -> ShowFragment.newInstance( MOVIE );
            case 1 -> ShowFragment.newInstance( TV );
            case 2 -> ListFragment.newInstance();
            case 3 -> AccountDataFragment.newInstance();
            case 4 -> PersonFragment.newInstance();
            default -> ShowFragment.newInstance( MOVIE );
        };
        fragmentList.put(position, fragment);
        return fragment;
    }

    public Fragment getFragment(int position) {
        return fragmentList.get(position);
    }

    @Override
    public int getItemCount() {
        return NUM_ITEMS;
    }

    /**
     * Returns position based on the Pages that are visible.
     *
     * @param position the position in the tab list.
     * @return the position of the case that the switch goes to.
     */
    public int getCorrectedPosition(int position) {
        ArrayList<Integer> visibleTabs = new ArrayList<>();

        if (!preferences.getBoolean(HIDE_MOVIES_PREFERENCE, false)) {
            visibleTabs.add(0); // Movie tab index
        }
        if (!preferences.getBoolean(HIDE_SERIES_PREFERENCE, false)) {
            visibleTabs.add(1); // Series tab index
        }
        if (!preferences.getBoolean(HIDE_SAVED_PREFERENCE, false)) {
            visibleTabs.add(2); // Saved tab index
        }
        if (!preferences.getBoolean(HIDE_ACCOUNT_DATA_PREFERENCE, false)) {
            visibleTabs.add(3); // Account Data tab index
        }
        if (!preferences.getBoolean(HIDE_PERSON_PREFERENCE, false)) {
            visibleTabs.add(4); // Person tab index
        }

        // Return the corrected position if it exists, otherwise return -1 indicating the tab should not be displayed
        return position < visibleTabs.size() ? visibleTabs.get(position) : -1;
    }
}
