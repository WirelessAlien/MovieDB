package com.wirelessalien.android.moviedb.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;

public class ExportActivity extends AppCompatActivity {

   private static final int REQUEST_CODE_SELECT_DIRECTORY = 1;

    private DocumentFile outputDirectory;
    private Context context;



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
                String fullPath = outputDirectory.getUri().getPath();
                String displayedPath = fullPath.replace("/tree/primary", "");

                // Update the TextView with the directory path
//                String directoryText = getString(R.string.directory_path, displayedPath);
//                binding.directoryTextView.setText(directoryText);

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
        setContentView( R.layout.activity_export);

        context = this; // Initialize the context object

        Button selectDirButton = findViewById(R.id.select_directory_button);
        selectDirButton.setOnClickListener(v -> {
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

        Button exportButton = findViewById(R.id.export_button);
        exportButton.setOnClickListener(v -> {
            MovieDatabaseHelper databaseHelper = new MovieDatabaseHelper(getApplicationContext());
            databaseHelper.exportDatabase(context, outputDirectory);
        });
    }
}
