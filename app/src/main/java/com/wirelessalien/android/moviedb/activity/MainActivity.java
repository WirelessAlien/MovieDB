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

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.ReleaseReminderService;
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter;
import com.wirelessalien.android.moviedb.fragment.BaseFragment;
import com.wirelessalien.android.moviedb.fragment.ListFragment;
import com.wirelessalien.android.moviedb.fragment.LoginFragment;
import com.wirelessalien.android.moviedb.fragment.PersonFragment;
import com.wirelessalien.android.moviedb.fragment.ShowFragment;
import com.wirelessalien.android.moviedb.helper.ConfigHelper;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener;
import com.wirelessalien.android.moviedb.tmdb.account.GetAccessToken;

import java.io.File;
import java.util.List;

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
    private String api_read_access_token;
    private static final int REQUEST_CODE = 101;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.activity_main);

        // Set the default preference values.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        api_read_access_token = ConfigHelper.getConfigValue(this, "api_read_access_token");

        mViewPager = findViewById(R.id.container);
        mSectionsPagerAdapter = new SectionsPagerAdapter(this, this);

        ViewPager2 mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

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

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);

        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.nav_movie -> {
                    mViewPager.setCurrentItem( mSectionsPagerAdapter.getCorrectedPosition( 0 ) );
                    return true;
                }
                case R.id.nav_series -> {
                    mViewPager.setCurrentItem( mSectionsPagerAdapter.getCorrectedPosition( 1 ) );
                    return true;
                }
                case R.id.nav_saved -> {
                    mViewPager.setCurrentItem( mSectionsPagerAdapter.getCorrectedPosition( 2 ) );
                    return true;
                }
                case R.id.nav_person -> {
                    mViewPager.setCurrentItem( mSectionsPagerAdapter.getCorrectedPosition( 3 ) );
                    return true;
                }
            }
            return false;
        });

        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                switch (mSectionsPagerAdapter.getCorrectedPosition( position )) {
                    case 0 -> bottomNavigationView.setSelectedItemId( R.id.nav_movie );
                    case 1 -> bottomNavigationView.setSelectedItemId( R.id.nav_series );
                    case 2 -> bottomNavigationView.setSelectedItemId( R.id.nav_saved );
                    case 3 -> bottomNavigationView.setSelectedItemId( R.id.nav_person );
                }
            }
        });


        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                new MaterialAlertDialogBuilder( this )
                        .setTitle("Permission needed")
                        .setMessage("This permission is needed to show notifications.")
                        .setPositiveButton("ok", (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE))
                        .setNegativeButton("cancel", (dialog, which) -> dialog.dismiss())
                        .create().show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE);
            }
            return;
        }



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Released Movies";
            String description = "Get notified when a movie is released.";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("released_movies", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Episode Reminders";
            String description = "Get notified when an episode is aired.";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("episode_reminders", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }


        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReleaseReminderService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 7);
        calendar.set(Calendar.MINUTE, 30);
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pendingIntent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri uri = intent.getData();
        if (uri != null) {
            if (uri.toString().startsWith("com.wirelessalien.android.moviedb://callback")) {
                String requestToken = preferences.getString("request_token", null);
                if (requestToken != null) {
                    Handler handler = new Handler( Looper.getMainLooper());
                    GetAccessToken getAccessToken = new GetAccessToken(api_read_access_token, requestToken, this, handler);
                    getAccessToken.start();
                }
            }
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

        if (id == R.id.action_login) {
            LoginFragment loginFragment = new LoginFragment();
            loginFragment.show(getSupportFragmentManager(), "login");
        }

        if (id == R.id.action_list) {
            Intent intent = new Intent(getApplicationContext(), MyListsActivity.class);
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
