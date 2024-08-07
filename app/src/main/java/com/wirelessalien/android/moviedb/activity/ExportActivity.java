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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.databinding.ActivityExportBinding;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ExportActivity extends AppCompatActivity {

   private static final int REQUEST_CODE_SELECT_DIRECTORY = 1;

    private DocumentFile outputDirectory;
    private Context context;
    private ActivityExportBinding binding;




    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_DIRECTORY && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                // Persist the URI permission across device reboots
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                outputDirectory = DocumentFile.fromTreeUri(this, uri);
                String fullPath = null;
                if (outputDirectory != null) {
                    fullPath = outputDirectory.getUri().getPath();
                }
                String displayedPath = null;
                if (fullPath != null) {
                    displayedPath = fullPath.replace("/tree/primary", "");
                }

                // Update the TextView with the directory path
                String directoryText = getString(R.string.directory_path, displayedPath);
                binding.selectedDirectoryText.setText(directoryText);

                // Save the output directory URI in SharedPreferences
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("outputDirectoryUri", uri.toString());
                editor.apply();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        context = this;

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

        binding.selectDirectoryButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, REQUEST_CODE_SELECT_DIRECTORY);
        });

        //get the output directory from the shared preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String outputDirectoryUri = sharedPreferences.getString("outputDirectoryUri", null);
        if (outputDirectoryUri != null) {
            Uri uri = Uri.parse(outputDirectoryUri);
            outputDirectory = DocumentFile.fromTreeUri(this, uri);
        }

        binding.exportButton.setOnClickListener(v -> {
            MovieDatabaseHelper databaseHelper = new MovieDatabaseHelper(getApplicationContext());
            databaseHelper.exportDatabase(context, outputDirectory);
        });
    }
}
