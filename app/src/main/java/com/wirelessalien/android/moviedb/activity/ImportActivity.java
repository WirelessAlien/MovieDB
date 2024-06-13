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

import static com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper.DATABASE_NAME;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.util.concurrent.CompletableFuture;

public class ImportActivity extends AppCompatActivity implements AdapterDataChangedListener {

    private Context context;
    private static final int PICK_FILE_REQUEST_CODE = 1;


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data.getData() != null) {
                Uri archiveFileUri = data.getData();
                Log.d("ArchiveFileUri", archiveFileUri.toString());
                Toast.makeText(this, getString(R.string.file_picked_success), Toast.LENGTH_SHORT).show();

                try {
                    InputStream inputStream = getContentResolver().openInputStream( archiveFileUri );
                    File cacheFile = new File(getCacheDir(), getArchiveFileName( archiveFileUri ));
                    FileOutputStream fileOutputStream = new FileOutputStream(cacheFile);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        fileOutputStream.write(buffer, 0, length);
                    }
                    Log.d("CacheFile", cacheFile.getAbsolutePath());
                    fileOutputStream.close();
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }





                // Display the file name from the intent
//                String fileName = getArchiveFileName(archiveFileUri);
//                String selectedFileText = getString(R.string.selected_file_text, fileName);
//                fileNameTextView.setText(selectedFileText);
//                fileNameTextView.setSelected(true);
            } else {
//                Toast.makeText(this, getString(R.string.file_picked_fail), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getArchiveFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query( uri, null, null, null, null )) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString( cursor.getColumnIndexOrThrow( OpenableColumns.DISPLAY_NAME ) );
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.activity_import);

        context = this;

        Thread.setDefaultUncaughtExceptionHandler( (thread, throwable) -> {
            StringWriter crashLog = new StringWriter();
            throwable.printStackTrace(new PrintWriter(crashLog));

            try {
                String fileName = "Crash_Log.txt";
                File targetFile = new File(context.getFilesDir(), fileName);
                FileOutputStream fileOutputStream = new FileOutputStream(targetFile);
                fileOutputStream.write(crashLog.toString().getBytes());
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            android.os.Process.killProcess(android.os.Process.myPid());
        } );

        Button pickFileButton = findViewById(R.id.pick_file_button);
        pickFileButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_FILE_REQUEST_CODE);
        });
        Button importButton = findViewById(R.id.import_button);
        importButton.setOnClickListener(v -> {
            MovieDatabaseHelper databaseHelper = new MovieDatabaseHelper(getApplicationContext());
            databaseHelper.importDatabase(context, this);
        });
    }

    @Override
    public void onAdapterDataChangedListener() {
        // Do nothing
    }
}
