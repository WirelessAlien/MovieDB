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

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.ReleaseReminderService;
import com.wirelessalien.android.moviedb.data.ListData;
import com.wirelessalien.android.moviedb.fragment.AccountDataFragment;
import com.wirelessalien.android.moviedb.fragment.BaseFragment;
import com.wirelessalien.android.moviedb.fragment.HomeFragment;
import com.wirelessalien.android.moviedb.fragment.ListFragment;
import com.wirelessalien.android.moviedb.fragment.LoginFragment;
import com.wirelessalien.android.moviedb.fragment.PersonFragment;
import com.wirelessalien.android.moviedb.fragment.ShowFragment;
import com.wirelessalien.android.moviedb.helper.ConfigHelper;
import com.wirelessalien.android.moviedb.helper.ListDatabaseHelper;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener;
import com.wirelessalien.android.moviedb.tmdb.account.FetchListThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.GetAccessToken;
import com.wirelessalien.android.moviedb.tmdb.account.ListDetailsThreadTMDb;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends BaseActivity {

    @SuppressWarnings("OctalInteger")
    private final static int SETTINGS_REQUEST_CODE = 0001;
    public final static int RESULT_SETTINGS_PAGER_CHANGED = 1001;
    private final static int REQUEST_CODE_ASK_PERMISSIONS_EXPORT = 123;
    private final static int REQUEST_CODE_ASK_PERMISSIONS_IMPORT = 124;
    private final static String LIVE_SEARCH_PREFERENCE = "key_live_search";
    public final static String HIDE_MOVIES_PREFERENCE = "key_hide_movies_tab";
    public final static String HIDE_SERIES_PREFERENCE = "key_hide_series_tab";
    public final static String HIDE_SAVED_PREFERENCE = "key_hide_saved_tab";
    public final static String HIDE_ACCOUNT_PREFERENCE = "key_hide_account_tab";
    public final static String MOVIE = "movie";
    public final static String TV = "tv";

    BottomNavigationView bottomNavigationView;
    // Variables used for searching
    private MenuItem mSearchAction;
    private boolean isSearchOpened = false;
    private SharedPreferences preferences;
    public AdapterDataChangedListener mAdapterDataChangedListener;
    private String api_read_access_token;
    private static final int REQUEST_CODE = 101;
    private Context context;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;


    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen( this );
        super.onCreate(savedInstanceState);
        setContentView( R.layout.activity_main);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        context = this;

        String fileName = "Crash_Log.txt";
        File crashLogFile = new File(getFilesDir(), fileName);
        if (crashLogFile.exists()) {
            StringBuilder crashLog = new StringBuilder();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(crashLogFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    crashLog.append(line);
                    crashLog.append('\n');
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Crash Log")
                    .setMessage(crashLog.toString())
                    .setPositiveButton("Copy", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Movie DB Crash Log", crashLog.toString());
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(MainActivity.this, R.string.crash_log_copied, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Close", null)
                    .show();
            crashLogFile.delete();
        }

        // Set the default preference values.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        api_read_access_token = ConfigHelper.getConfigValue(this, "api_read_access_token");
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment selectedFragment = null;
            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_movie) {
                selectedFragment = ShowFragment.newInstance(MOVIE);
            } else if (itemId == R.id.nav_series) {
                selectedFragment = ShowFragment.newInstance(TV);
            } else if (itemId == R.id.nav_saved) {
                selectedFragment = ListFragment.newInstance();
            } else if (itemId == R.id.nav_account) {
                selectedFragment = new AccountDataFragment();
            }
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction().replace(R.id.container, selectedFragment).commit();
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new HomeFragment()).commit();
        }

        Menu menu = bottomNavigationView.getMenu();
        menu.findItem(R.id.nav_movie).setVisible(!preferences.getBoolean(HIDE_MOVIES_PREFERENCE, false));
        menu.findItem(R.id.nav_series).setVisible(!preferences.getBoolean(HIDE_SERIES_PREFERENCE, false));
        menu.findItem(R.id.nav_saved).setVisible(!preferences.getBoolean(HIDE_SAVED_PREFERENCE, false));
        menu.findItem(R.id.nav_account).setVisible(!preferences.getBoolean(HIDE_ACCOUNT_PREFERENCE, false));

        prefListener = (prefs, key) -> {
            if (key.equals(HIDE_MOVIES_PREFERENCE) ||
                    key.equals(HIDE_SERIES_PREFERENCE) ||
                    key.equals(HIDE_SAVED_PREFERENCE) ||
                    key.equals(HIDE_ACCOUNT_PREFERENCE)) {
                Menu menu1 = bottomNavigationView.getMenu();
                menu1.findItem(R.id.nav_movie).setVisible(!preferences.getBoolean(HIDE_MOVIES_PREFERENCE, false));
                menu1.findItem(R.id.nav_series).setVisible(!preferences.getBoolean(HIDE_SERIES_PREFERENCE, false));
                menu1.findItem(R.id.nav_saved).setVisible(!preferences.getBoolean(HIDE_SAVED_PREFERENCE, false));
                menu1.findItem(R.id.nav_account).setVisible(!preferences.getBoolean(HIDE_ACCOUNT_PREFERENCE, false));
            }
        };

// Register the listener
        preferences.registerOnSharedPreferenceChangeListener(prefListener);

        FloatingActionButton fab = findViewById( R.id.fab );
        bottomNavigationView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
           int bottomNavHeight = bottomNavigationView.getHeight();
            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) fab.getLayoutParams();
            params.bottomMargin = bottomNavHeight + 16;
            fab.setLayoutParams(params);
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSearchOpened) {
                    handleMenuSearch();
                } else {
                    finish();
                }
            }
        });

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                new MaterialAlertDialogBuilder( this )
                        .setTitle( R.string.permission_required)
                        .setMessage( R.string.permission_required_description)
                        .setPositiveButton( R.string.ok, (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE))
                        .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                        .create().show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE);
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString( R.string.released_movies);
            String description = getString( R.string.notification_for_movie_released);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("released_movies", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString( R.string.aired_episodes);
            String description = getString( R.string.notification_for_episode_air);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("episode_reminders", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ReleaseReminderService.class)
                .setInitialDelay(24, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(this).enqueue(workRequest);

        Intent nIntent = getIntent();
        if (nIntent != null && nIntent.hasExtra("tab_index")) {
            int tabIndex = nIntent.getIntExtra("tab_index", 0);
            bottomNavigationView.setSelectedItemId(tabIndex);
        }

        String access_token = preferences.getString("access_token", null);
        boolean hasRunOnce = preferences.getBoolean("hasRunOnce", false);

        if (!hasRunOnce && access_token != null && !access_token.equals("")) {
            ListDatabaseHelper listDatabaseHelper = new ListDatabaseHelper(MainActivity.this);
            SQLiteDatabase db = listDatabaseHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT * FROM " + ListDatabaseHelper.TABLE_LISTS, null);
            if (cursor.getCount() > 0) {
                new Handler(Looper.getMainLooper());

                AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                        .setView(R.layout.dialog_progress)
                        .setCancelable(false)
                        .create();
                progressDialog.show();

                FetchListThreadTMDb fetchListThreadTMDb = new FetchListThreadTMDb(MainActivity.this, listData -> {
                    for (ListData data : listData) {
                        listDatabaseHelper.addList(data.getId(), data.getName());

                        ListDetailsThreadTMDb listDetailsThreadTMDb = new ListDetailsThreadTMDb(data.getId(), MainActivity.this, listDetailsData -> {

                            for (JSONObject item : listDetailsData) {
                                try {
                                    int movieId = item.getInt("id");
                                    String mediaType = item.getString("media_type");

                                    listDatabaseHelper.addListDetails(data.getId(), data.getName(), movieId, mediaType);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    progressDialog.dismiss();
                                    Toast.makeText(MainActivity.this, R.string.error_occurred_in_list_data, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        listDetailsThreadTMDb.start();
                    }
                    progressDialog.dismiss();
                });
                fetchListThreadTMDb.fetchLists();
            }
            cursor.close();
            preferences.edit().putBoolean("hasRunOnce", true).apply();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri uri = intent.getData();
        if (uri != null) {
            if (uri.toString().startsWith("com.wirelessalien.android.moviedb://callback")) {
                String requestToken = preferences.getString("request_token", null);
                if (requestToken != null) {
                    Handler handler = new Handler(Looper.getMainLooper());

                    AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                            .setView(R.layout.dialog_progress)
                            .setCancelable(false)
                            .create();
                    progressDialog.show();

                    GetAccessToken getAccessToken = new GetAccessToken(api_read_access_token, requestToken, this, handler, accessToken -> {
                        FetchListThreadTMDb fetchListThreadTMDb = new FetchListThreadTMDb(MainActivity.this, listData -> {
                            ListDatabaseHelper listDatabaseHelper = new ListDatabaseHelper(MainActivity.this);
                            for (ListData data : listData) {
                                listDatabaseHelper.addList(data.getId(), data.getName());

                                ListDetailsThreadTMDb listDetailsThreadTMDb = new ListDetailsThreadTMDb(data.getId(), MainActivity.this, listDetailsData -> {

                                    for (JSONObject item : listDetailsData) {
                                        try {
                                            int movieId = item.getInt("id");
                                            String mediaType = item.getString("media_type");

                                            listDatabaseHelper.addListDetails(data.getId(), data.getName(), movieId, mediaType);
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                            progressDialog.dismiss();
                                            Toast.makeText(MainActivity.this, R.string.error_occurred_in_list_data, Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                                listDetailsThreadTMDb.start();
                            }
                            progressDialog.dismiss();
                        });
                        fetchListThreadTMDb.fetchLists();
                    });
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
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the listener
        preferences.unregisterOnSharedPreferenceChangeListener(prefListener);
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

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment mCurrentFragment = fragmentManager.findFragmentById(R.id.container);

        if (mCurrentFragment != null) {
            mCurrentFragment.onActivityResult(requestCode, resultCode, data);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mSearchAction = menu.findItem(R.id.action_search);
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
        // This is a hack
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment mCurrentFragment = fragmentManager.findFragmentById(R.id.container);

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
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment mCurrentFragment = fragmentManager.findFragmentById(R.id.container);

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
