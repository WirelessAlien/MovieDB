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

package com.wirelessalien.android.moviedb.activity;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.wirelessalien.android.moviedb.fragment.LoginFragment;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.tmdb.account.TMDbAuthThread;
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter;
import com.wirelessalien.android.moviedb.fragment.AccountFragment;
import com.wirelessalien.android.moviedb.fragment.BaseFragment;
import com.wirelessalien.android.moviedb.fragment.ListFragment;
import com.wirelessalien.android.moviedb.fragment.PersonFragment;
import com.wirelessalien.android.moviedb.fragment.ShowFragment;
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/*
 * nnuuneoi's article was a great help by programming the permissions for API 23 and higher.
 * Article: https://inthecheesefactory.com/blog/things-you-need-to-know-about-android-m-permission-developer-edition
 */

public class MainActivity extends BaseActivity {

    @SuppressWarnings("OctalInteger")
    private final static int SETTINGS_REQUEST_CODE = 0001;
    public final static int RESULT_SETTINGS_PAGER_CHANGED = 1001;
    private final static int REQUEST_CODE_ASK_PERMISSIONS_EXPORT = 123;
    private final static int REQUEST_CODE_ASK_PERMISSIONS_IMPORT = 124;
    private final static String LIVE_SEARCH_PREFERENCE = "key_live_search";
    private final static String REWATCHED_FIELD_CHANGE_PREFERENCE = "key_rewatched_field_change";
    private final static String PREVIOUS_APPLICATION_VERSION_PREFERENCE = "key_application_version";

    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager2 mViewPager;
    // Variables used for searching
    private MenuItem mSearchAction;
    private boolean isSearchOpened = false;
    private EditText editSearch;
    private SharedPreferences preferences;
    public AdapterDataChangedListener mAdapterDataChangedListener;

    private SharedPreferences getEncryptedSharedPreferences(Context context) throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        return EncryptedSharedPreferences.create(
                context,
                "encrypted_preferences",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.activity_main);

        // Set the default preference values.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mViewPager = findViewById(R.id.container);
        mSectionsPagerAdapter = new SectionsPagerAdapter(this, this);

        ViewPager2 mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = findViewById(R.id.tabs);
        new TabLayoutMediator(tabLayout, mViewPager, (tab, position) -> {
            switch (mSectionsPagerAdapter.getCorrectedPosition( position )) {
                case 0 -> tab.setText( mSectionsPagerAdapter.movieTabTitle );
                case 1 -> tab.setText( mSectionsPagerAdapter.seriesTabTitle );
                case 2 -> tab.setText( mSectionsPagerAdapter.savedTabTitle );
                case 3 -> tab.setText( mSectionsPagerAdapter.personTabTitle );
            }
        }).attach();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int versionNumber;
        try {
            versionNumber = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            versionNumber = -1;
        }

        // The rewatched field has changed to watched, notify users that make use of the database
        // of this change and also tell them that the value is automatically increased by one.
        if (!preferences.getBoolean(REWATCHED_FIELD_CHANGE_PREFERENCE, false) && preferences.getInt(PREVIOUS_APPLICATION_VERSION_PREFERENCE, versionNumber) < 190) {
            File dbFile = getDatabasePath( MovieDatabaseHelper.getDatabaseFileName());

            // If there is a database.
            if (dbFile.exists()) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
                builder.setMessage(getString(R.string.watched_upgrade_dialog_message))
                        .setTitle(getString(R.string.watched_upgrade_dialog_title));

                builder.setPositiveButton(getString(R.string.watched_upgrade_dialog_positive),
                        (dialog, id) -> {
                            // Don't change the values of shows that haven't been watched and have a
                            // rewatch value of zero.
                            MovieDatabaseHelper databaseHelper
                                    = new MovieDatabaseHelper(getApplicationContext());
                            SQLiteDatabase database = databaseHelper.getWritableDatabase();
                            databaseHelper.onCreate(database);

                            Cursor cursor = database.rawQuery("SELECT * FROM " +
                                    MovieDatabaseHelper.TABLE_MOVIES, null);
                            // Go through all rows in the database.
                            cursor.moveToFirst();
                            while (!cursor.isAfterLast()) {
                                if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED))) {
                                    int rewatchedValue = cursor.getInt(cursor.getColumnIndexOrThrow
                                            (MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED));
                                    // In case of a value of zero, check if the show is watched.
                                    if (rewatchedValue != 0 || cursor.getInt( cursor.getColumnIndexOrThrow(
                                            MovieDatabaseHelper.COLUMN_CATEGORIES ) ) == 1) {
                                        ContentValues watchedValues = new ContentValues();
                                        watchedValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED,
                                                (rewatchedValue + 1));
                                        database.update(MovieDatabaseHelper.TABLE_MOVIES, watchedValues,
                                                MovieDatabaseHelper.COLUMN_MOVIES_ID + "="
                                                        + cursor.getInt(cursor.getColumnIndexOrThrow
                                                        (MovieDatabaseHelper.COLUMN_MOVIES_ID)), null);
                                    }
                                }
                                cursor.moveToNext();
                            }

                            database.close();
                        } );

                builder.setNegativeButton(getString(R.string.watched_upgrade_dialog_negative), (dialog, id) -> {
                    // Don't do anything.
                } );

                builder.show();

                preferences.edit().putBoolean(REWATCHED_FIELD_CHANGE_PREFERENCE, true).apply();
            }
        }

        if (versionNumber != -1) {
            preferences.edit().putInt(PREVIOUS_APPLICATION_VERSION_PREFERENCE, versionNumber).apply();
        }

        try {
            SharedPreferences sharedPreferences = getEncryptedSharedPreferences(MainActivity.this);
            String username = sharedPreferences.getString("username", null);
            String password = sharedPreferences.getString("password", null);

            TMDbAuthThread tmDbAuthThread = new TMDbAuthThread(username, password, this);
            tmDbAuthThread.start();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    void doNetworkWork() {
        // Pass the call to all fragments.
        FragmentManager fragmentManager = getSupportFragmentManager();
        List<Fragment> fragmentList = fragmentManager.getFragments();
        for (Fragment fragment : fragmentList) {
            BaseFragment baseFragment = (BaseFragment) fragment;
            baseFragment.doNetworkWork();
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
        inflater.inflate(R.menu.menu_main, menu);
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivityForResult(intent, SETTINGS_REQUEST_CODE);
            return true;
        }

        if (id == R.id.action_login_test) {
            LoginFragment loginFragment = new LoginFragment();
            loginFragment.show(getSupportFragmentManager(), "login");
        }

        if (id == R.id.action_account) {
            Intent intent = new Intent(getApplicationContext(), AccountFragment.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Fragment mCurrentFragment = mSectionsPagerAdapter.getFragment(mViewPager.getCurrentItem());

        if (mCurrentFragment != null) {
            mCurrentFragment.onActivityResult(requestCode, resultCode, data);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_SETTINGS_PAGER_CHANGED) {
            mSectionsPagerAdapter = new SectionsPagerAdapter(this, this);
            mViewPager.setAdapter(mSectionsPagerAdapter);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mSearchAction = menu.findItem(R.id.action_search);
        MenuItem mFilterAction = menu.findItem(R.id.action_filter);
        return super.onPrepareOptionsMenu(menu);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS_EXPORT -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    MovieDatabaseHelper databaseHelper = new MovieDatabaseHelper( this );
                    databaseHelper.exportDatabase( this, null );
                } // else: permission denied
            }
            case REQUEST_CODE_ASK_PERMISSIONS_IMPORT -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    MovieDatabaseHelper databaseHelper = new MovieDatabaseHelper( this );
                    databaseHelper.importDatabase( this, mAdapterDataChangedListener );
                } // else: permission denied
            }
            default -> super.onRequestPermissionsResult( requestCode, permissions, grantResults );
        }
    }

    /**
     * Handles input from the search bar and icon.
     */
    private void handleMenuSearch() {
        ActionBar action = getSupportActionBar();
        if (action == null) {
            return;
        }

        final boolean liveSearch = preferences.getBoolean(LIVE_SEARCH_PREFERENCE, true);

        if (isSearchOpened) {
            if (editSearch.getText().toString().equals("")) {
                action.setDisplayShowCustomEnabled(true);

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
                }

                mSearchAction.setIcon( ResourcesCompat.getDrawable(getResources(), R.drawable.ic_search, null));

                isSearchOpened = false;

                action.setCustomView(null);
                action.setDisplayShowTitleEnabled(true);

                cancelSearchInFragment();
            } else {
                editSearch.setText("");
            }
        } else {
            action.setDisplayShowCustomEnabled(true);
            action.setCustomView(R.layout.search_bar);
            action.setDisplayShowTitleEnabled(false);

            editSearch = action.getCustomView().findViewById(R.id.editSearch);

            editSearch.setOnEditorActionListener( (view, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    searchInFragment(editSearch.getText().toString());
                    return true;
                }
                return false;
            } );

            editSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (liveSearch) {
                        searchInFragment(editSearch.getText().toString());
                    }
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
            });

            editSearch.requestFocus();

            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editSearch, InputMethodManager.SHOW_IMPLICIT);
            }

            mSearchAction.setIcon( ResourcesCompat.getDrawable(getResources(), R.drawable.ic_close, null));

            isSearchOpened = true;
        }
    }

    private void searchInFragment(String query) {
        // This is a hack
        Fragment mCurrentFragment = mSectionsPagerAdapter.getFragment(mViewPager.getCurrentItem());

        if (mCurrentFragment != null) {
            if (mCurrentFragment instanceof ShowFragment) {
                ((ShowFragment) mCurrentFragment).search(query);
            }

            if (mCurrentFragment instanceof ListFragment) {
                ((ListFragment) mCurrentFragment).search(query);
            }

            if (mCurrentFragment instanceof PersonFragment) {
                ((PersonFragment) mCurrentFragment).search(query);
            }
        } else {
            Log.d("MainActivity", "Current fragment is null");
        }
    }
    /**
     * Cancel the searching process in the fragment.
     */
    private void cancelSearchInFragment() {
        // This is a hack
        Fragment mCurrentFragment = mSectionsPagerAdapter.getFragment(mViewPager.getCurrentItem());

        if (mCurrentFragment != null) {
            if (mCurrentFragment instanceof ShowFragment) {
                ((ShowFragment) mCurrentFragment).cancelSearch();
            }

            if (mCurrentFragment instanceof ListFragment) {
                ((ListFragment) mCurrentFragment).cancelSearch();
            }

            if (mCurrentFragment instanceof PersonFragment) {
                ((PersonFragment) mCurrentFragment).cancelSearch();
            }
        }
    }
}
