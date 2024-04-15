/*
 * Copyright (c) 2018.
 *
 * This file is part of MovieDB.
 *
 * MovieDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MovieDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MovieDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.adapter;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import android.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.fragment.ListFragment;
import com.wirelessalien.android.moviedb.fragment.PersonFragment;
import com.wirelessalien.android.moviedb.fragment.ShowFragment;


public class SectionsPagerAdapter extends FragmentPagerAdapter {
    public final static String HIDE_MOVIES_PREFERENCE = "key_hide_movies_tab";
    public final static String HIDE_SERIES_PREFERENCE = "key_hide_series_tab";
    public final static String HIDE_SAVED_PREFERENCE = "key_hide_saved_tab";
    public final static String HIDE_PERSON_PREFERENCE = "key_hide_person_tab";

    public final static String MOVIE = "movie";
    public final static String TV = "tv";

    private static int NUM_ITEMS = 4;
    private final SharedPreferences preferences;
    private final String movieTabTitle;
    private final String seriesTabTitle;
    private final String savedTabTitle;
    private final String personTabTitle;

    /**
     * Determines the (amount of) Pages to be shown.
     *
     * @param fm      given to super.
     * @param context context to retrieve the preferences.
     */
    public SectionsPagerAdapter(FragmentManager fm, Context context) {
        super(fm);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Instantiate NUM_ITEMS again to prevent decreasing further than intended when
        // recreating SectionPagerAdapter but keeping the old NUM_ITEMS value.
        NUM_ITEMS = 4;

        if (preferences.getBoolean(HIDE_MOVIES_PREFERENCE, false)) {
            NUM_ITEMS--;
        }

        if (preferences.getBoolean(HIDE_SERIES_PREFERENCE, false)) {
            NUM_ITEMS--;
        }

        if (preferences.getBoolean(HIDE_SAVED_PREFERENCE, false)) {
            NUM_ITEMS--;
        }

        if (preferences.getBoolean(HIDE_PERSON_PREFERENCE, false)) {
            NUM_ITEMS--;
        }

        movieTabTitle = context.getResources().getString( R.string.movie_tab_title);
        seriesTabTitle = context.getResources().getString(R.string.series_tab_title);
        savedTabTitle = context.getResources().getString(R.string.saved_tab_title);
        personTabTitle = context.getResources().getString(R.string.person_tab_title);
    }

    @Override
    public Fragment getItem(int position) {
        // getItem is called to instantiate the fragment for the given page.
        // Return a PlaceholderFragment (defined as a static inner class below).
        return switch (getCorrectedPosition( position )) {
            case 0 -> ShowFragment.newInstance( MOVIE );
            case 1 -> ShowFragment.newInstance( TV );
            case 2 -> ListFragment.newInstance();
            case 3 -> PersonFragment.newInstance();
            default -> null;
        };
    }

    @Override
    public int getCount() {
        return NUM_ITEMS;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return switch (getCorrectedPosition( position )) {
            case 0 -> movieTabTitle;
            case 1 -> seriesTabTitle;
            case 2 -> savedTabTitle;
            case 3 -> personTabTitle;
            default -> null;
        };
    }

    /**
     * Returns position based on the Pages that are visible.
     *
     * @param position the position in the tab list.
     * @return the position of the case that the switch goes to.
     */
    private int getCorrectedPosition(int position) {
        int index = 0;
        index += preferences.getBoolean(HIDE_MOVIES_PREFERENCE, false) ? 8 : 0;
        index += preferences.getBoolean(HIDE_SERIES_PREFERENCE, false) ? 4 : 0;
        index += preferences.getBoolean(HIDE_SAVED_PREFERENCE, false) ? 2 : 0;
        index += preferences.getBoolean(HIDE_PERSON_PREFERENCE, false) ? 1 : 0;

        int[][] corpos = {
                {0, 1, 2, 3},    // MOV SER SAV PER
                {0, 1, 2, -1},   // MOV SER SAV ---
                {0, 1, 3, -1},   // MOV SER PER ---
                {0, 1, -1, -1},  // MOV SER --- ---
                {0, 2, 3, -1},   // MOV SAV PER ---
                {0, 2, -1, -1},  // MOV SAV --- ---
                {0, 3, -1, -1},  // MOV PER --- ---
                {0, -1, -1, -1}, // MOV --- --- ---
                {1, 2, 3, -1},   // SER SAV PER ---
                {1, 2, -1, -1},  // SER SAV --- ---
                {1, 3, -1, -1},  // SER PER --- ---
                {1, -1, -1, -1}, // SER --- --- ---
                {2, 3, -1, -1},  // SAV PER --- ---
                {2, -1, -1, -1}, // SAV --- --- ---
                {3, -1, -1, -1}, // PER --- --- ---
                {-1, -1, -1, -1},// --- --- --- ---
        };
        // magic!
        return corpos[index][position];
    }
}
