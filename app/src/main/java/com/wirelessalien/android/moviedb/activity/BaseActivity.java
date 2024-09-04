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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.wirelessalien.android.moviedb.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

/**
 * This class contains some basic functionality that would
 * otherwise be duplicated in multiple activities.
 */
@SuppressLint("Registered")
public class BaseActivity extends AppCompatActivity {

    private final static String API_LANGUAGE_PREFERENCE = "key_api_language";
    private ConnectivityReceiver connectivityReceiver;
    private IntentFilter intentFilter;

    /**
     * Returns the language that is used by the phone.
     * Usage: this is only meant to be used at the end of the API url.
     * Otherwise an ampersand needs to be added manually at the end
     * and the possibility that an empty string can be returned
     * (which will interfere with the manual ampersand) must be
     * taken into account.
     */
    public static String getLanguageParameter(Context context) {
        String languageParameter = "&language=";

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String userPickedLanguage = preferences.getString(API_LANGUAGE_PREFERENCE, null);
        if (userPickedLanguage != null && !userPickedLanguage.isEmpty()) {
            return languageParameter + userPickedLanguage;
        }
        return languageParameter + "en-US";
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            StringWriter crashLog = new StringWriter();
            PrintWriter printWriter = new PrintWriter(crashLog);
            throwable.printStackTrace(printWriter);

            String osVersion = android.os.Build.VERSION.RELEASE;
            String appVersion = "";
            try {
                appVersion = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            printWriter.write("\nDevice OS Version: " + osVersion);
            printWriter.write("\nApp Version: " + appVersion);
            printWriter.close();

            try {
                String fileName = "Crash_Log.txt";
                File targetFile = new File(getApplicationContext().getFilesDir(), fileName);
                FileOutputStream fileOutputStream = new FileOutputStream(targetFile, true);
                fileOutputStream.write((crashLog + "\n").getBytes());
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    /**
     * Creates the toolbar.
     */
    void setNavigationDrawer() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    /**
     * Creates a home button in the toolbar.
     */
    void setBackButtons() {
        // Add back button to the activity.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }
    }

    /**
     * Checks if a network is available.
     * If/Once a network connection is established, it calls doNetworkWork().
     */
    public void checkNetwork() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        connectivityManager.registerNetworkCallback(builder.build(),
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        doNetworkWork();
                    }
                });

        // Check if there is an Internet connection, if not tell the user.
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnectedOrConnecting()) {
            Toast.makeText(getApplicationContext(), getApplicationContext().getResources()
                    .getString( R.string.no_internet_connection), Toast.LENGTH_SHORT).show();
        } else {
            doNetworkWork();
        }
    }

    void doNetworkWork() {}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Back button
        if (id == android.R.id.home) {
            this.finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public class ConnectivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectivityManager
                    = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE );
            NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetInfo != null && activeNetInfo.isConnectedOrConnecting()) {
                doNetworkWork();
            }
        }
    }
}
