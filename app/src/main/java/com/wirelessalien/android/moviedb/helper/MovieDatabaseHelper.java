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

package com.wirelessalien.android.moviedb.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.data.EpisodeDbDetails;
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * This class provides some (basic) database functionality.
 */
public class MovieDatabaseHelper extends SQLiteOpenHelper {

    public static final String TABLE_MOVIES = "movies";
    public static final String TABLE_EPISODES = "episodes";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_MOVIES_ID = "movie_id";
    public static final String COLUMN_RATING = "rating";
    public static final String COLUMN_PERSONAL_RATING = "personal_rating";
    public static final String COLUMN_IMAGE = "image";
    public static final String COLUMN_ICON = "icon";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_SUMMARY = "summary";
    public static final String COLUMN_GENRES = "genres";
    public static final String COLUMN_GENRES_IDS = "genres_ids";
    public static final String COLUMN_RELEASE_DATE = "release_date";
    public static final String COLUMN_PERSONAL_START_DATE = "personal_start_date";
    public static final String COLUMN_PERSONAL_FINISH_DATE = "personal_finish_date";
    public static final String COLUMN_PERSONAL_REWATCHED = "personal_rewatched";
    public static final String COLUMN_PERSONAL_EPISODES = "personal_episodes";
    public static final String COLUMN_EPISODE_NUMBER = "episode_number";
    public static final String COLUMN_SEASON_NUMBER = "season_number";
    public static final String COLUMN_EPISODE_WATCH_DATE = "episode_watch_date";
    public static final String COLUMN_EPISODE_RATING = "episode_rating";
    public static final String COLUMN_CATEGORIES = "watched";
    public static final String COLUMN_MOVIE = "movie";
    public static final int CATEGORY_WATCHING = 2;
    public static final int CATEGORY_PLAN_TO_WATCH = 0;
    public static final int CATEGORY_WATCHED = 1;
    public static final int CATEGORY_ON_HOLD = 3;
    public static final int CATEGORY_DROPPED = 4;
    public static final String DATABASE_NAME = "movies.db";
    private static final String DATABASE_FILE_NAME = "movies";
    private static final String DATABASE_FILE_EXT = ".db";
    private static final int DATABASE_VERSION = 13;


    // Initialize the database object.

    /**
     * Initialises the database object.
     *
     * @param context the context passed on to the super.
     */
    public MovieDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Returns the database name.
     *
     * @return the database name.
     */
    public static String getDatabaseFileName() {
        return DATABASE_NAME;
    }

    /**
     * Converts the show table in the database to a JSON string.
     *
     * @param database the database to get the data from.
     * @return a string in JSON format containing all the show data.
     */
    private JSONArray getEpisodesForMovie(int movieId, SQLiteDatabase database) {
        String selectQuery = "SELECT * FROM " + TABLE_EPISODES + " WHERE " + COLUMN_MOVIES_ID + " = " + movieId;
        Cursor cursor = database.rawQuery(selectQuery, null);

        // Convert the database to JSON
        JSONArray episodesSet = new JSONArray();

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            int totalColumn = cursor.getColumnCount();
            JSONObject rowObject = new JSONObject();

            for (int i = 0; i < totalColumn; i++) {
                if (cursor.getColumnName(i) != null) {
                    try {
                        if (cursor.getString(i) != null) {
                            rowObject.put(cursor.getColumnName(i), cursor.getString(i));
                        } else {
                            rowObject.put(cursor.getColumnName(i), "");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            episodesSet.put(rowObject);
            cursor.moveToNext();
        }
        cursor.close();

        return episodesSet;
    }

    private String getJSONExportString(SQLiteDatabase database) {
        String selectQuery = "SELECT * FROM " + TABLE_MOVIES;
        Cursor cursor = database.rawQuery(selectQuery, null);

        // Convert the database to JSON
        JSONArray databaseSet = new JSONArray();

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            int totalColumn = cursor.getColumnCount();
            JSONObject rowObject = new JSONObject();

            for (int i = 0; i < totalColumn; i++) {
                if (cursor.getColumnName(i) != null) {
                    try {
                        if (cursor.getString(i) != null) {
                            rowObject.put(cursor.getColumnName(i), cursor.getString(i));
                        } else {
                            rowObject.put(cursor.getColumnName(i), "");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Add episodes for this movie
            int movieId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MOVIES_ID));
            try {
                rowObject.put("episodes", getEpisodesForMovie(movieId, database));
            } catch (JSONException e) {
                throw new RuntimeException( e );
            }

            databaseSet.put(rowObject);
            cursor.moveToNext();
        }
        cursor.close();

        // Convert databaseSet to string and put in file
        return databaseSet.toString();
    }

    /**
     * Writes the database in the chosen format to the downloads directory.
     *
     * @param context the context needed for the dialogs and toasts.
     */
    public void exportDatabase(final Context context, final DocumentFile outputDirectory) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);

        // Inflate the custom layout
        LayoutInflater inflater = LayoutInflater.from(context);
        View customView = inflater.inflate(R.layout.export_dialog, null);

        final RadioButton jsonRadioButton = customView.findViewById(R.id.radio_json);
        final RadioButton dbRadioButton = customView.findViewById(R.id.radio_db);

        builder.setView(customView); // Set the custom layout
        builder.setTitle(context.getResources().getString(R.string.choose_export_file))
                .setPositiveButton("Export", (dialogInterface, i) -> {
                    if (outputDirectory != null) {
                        File data = Environment.getDataDirectory();
                        String currentDBPath = "/data/" + context.getPackageName() + "/databases/" + DATABASE_NAME;
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yy-kk-mm", Locale.US);

                        if (jsonRadioButton.isChecked()) {
                            // Convert databaseSet to string and put in file
                            String fileContent = getJSONExportString(getReadableDatabase());
                            String fileExtension = ".json";

                            // Write to file
                            String fileName = DATABASE_FILE_NAME + simpleDateFormat.format(new Date()) + fileExtension;
                            try {
                                DocumentFile file = outputDirectory.createFile("application/json", fileName);
                                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(file.getUri(), "w");
                                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(pfd.getFileDescriptor()));
                                bufferedOutputStream.write(fileContent.getBytes());
                                bufferedOutputStream.flush();
                                bufferedOutputStream.close();
                                Toast.makeText(context, context.getResources().getString(R.string.write_to_external_storage_as) + fileName, Toast.LENGTH_SHORT).show();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else if (dbRadioButton.isChecked()) {
                            // Write the .db file to selected directory
                            String fileExtension = ".db";
                            String exportDBPath = DATABASE_FILE_NAME + simpleDateFormat.format(new Date()) + fileExtension;
                            try {
                                File currentDB = new File(data, currentDBPath);
                                DocumentFile exportDB = outputDirectory.createFile("application/octet-stream", exportDBPath);
                                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(exportDB.getUri(), "w");
                                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(pfd.getFileDescriptor()));

                                FileChannel fileChannel = new FileInputStream(currentDB).getChannel();
                                ByteBuffer buffer = ByteBuffer.allocate((int) fileChannel.size());
                                fileChannel.read(buffer);
                                buffer.flip();
                                byte[] byteArray = new byte[buffer.remaining()];
                                buffer.get(byteArray);
                                bufferedOutputStream.write(byteArray);

                                bufferedOutputStream.flush();
                                bufferedOutputStream.close();
                                Toast.makeText(context, context.getResources().getString(R.string.write_to_external_storage_as) + exportDBPath, Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        // Show error message if no directory is selected
                        Toast.makeText(context, "Please select a directory", Toast.LENGTH_SHORT).show();
                    }
                } )
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.cancel() );

        builder.show();
    }



    /**
     * Displays a dialog with possible files to import and imports the chosen file.
     * The current database will be dropped in that case.
     *
     * @param context the context needed for the dialog.
     */
    public void importDatabase(final Context context, final AdapterDataChangedListener listener) {
        // Ask the user which file to import
        String downloadPath = context.getCacheDir().getPath();
        File directory = new File(downloadPath);
        File[] files = directory.listFiles( pathname -> {
            // Only show database
            String name = pathname.getName();
            return name.endsWith(".db");
        } );

        final ArrayAdapter<String> fileAdapter = new ArrayAdapter<>
                (context, android.R.layout.select_dialog_singlechoice);
        for (File file : files) {
            fileAdapter.add(file.getName());
        }

        MaterialAlertDialogBuilder fileDialog = new MaterialAlertDialogBuilder(context);
        fileDialog.setTitle(R.string.choose_file);

        fileDialog.setNegativeButton(R.string.import_cancel, (dialog, which) -> dialog.dismiss() );

        // Show the files that can be imported.
        fileDialog.setAdapter(fileAdapter, (dialog, which) -> {
            File path = new File( context.getCacheDir().getPath() );

            try {
                String exportDBPath = fileAdapter.getItem(which);
                if (exportDBPath == null) {
                    throw new NullPointerException();
                } else if (fileAdapter.getItem(which).endsWith(".db")) {

                    CompletableFuture.runAsync(() -> {
                        try {
                            // Import the file selected in the dialog.
                            File data = Environment.getDataDirectory();
                            String currentDBPath = "/data/" + context.getPackageName() + "/databases/" + MovieDatabaseHelper.DATABASE_NAME;
                            File currentDB = new File(data, currentDBPath);
                            File importDB = new File(path, exportDBPath);

                            FileChannel src = new FileInputStream(importDB).getChannel();
                            FileChannel dst = new FileOutputStream(currentDB).getChannel();
                            dst.transferFrom(src, 0, src.size());
                            src.close();
                            dst.close();

                            Handler mainHandler = new Handler( Looper.getMainLooper());

                            mainHandler.post( () -> {
                                Toast.makeText(context, R.string.database_import_successful, Toast.LENGTH_SHORT).show();
                            } );
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            } catch (NullPointerException npe) {
                npe.printStackTrace();
                Toast.makeText(context, context.getResources().getString
                        (R.string.file_not_found_exception), Toast.LENGTH_SHORT).show();
            }
            listener.onAdapterDataChangedListener();
        } );

        fileDialog.show();
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        // Create the database with the database creation statement.
        String DATABASE_CREATE = "CREATE TABLE IF NOT EXISTS " +
                TABLE_MOVIES + "(" + COLUMN_ID + " integer primary key autoincrement, " +
                COLUMN_MOVIES_ID + " integer not null, " + COLUMN_RATING +
                " integer not null, " + COLUMN_PERSONAL_RATING + " integer, " +
                COLUMN_IMAGE + " text not null, " + COLUMN_ICON + " text not null, " +
                COLUMN_TITLE + " text not null, " + COLUMN_SUMMARY + " text not null, " +
                COLUMN_GENRES + " text not null, " + COLUMN_GENRES_IDS
                + " text not null, " + COLUMN_RELEASE_DATE + " text, " + COLUMN_PERSONAL_START_DATE + " text, " +
                COLUMN_PERSONAL_FINISH_DATE + " text, " + COLUMN_PERSONAL_REWATCHED +
                " integer, " + COLUMN_CATEGORIES + " integer not null, " + COLUMN_MOVIE +
                " integer not null, " + COLUMN_PERSONAL_EPISODES + " integer);";
        database.execSQL(DATABASE_CREATE);

        String CREATE_EPISODES_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_EPISODES + "(" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_MOVIES_ID + " INTEGER NOT NULL, " +
                COLUMN_SEASON_NUMBER + " INTEGER NOT NULL, " +
                COLUMN_EPISODE_NUMBER + " INTEGER NOT NULL, " +
                COLUMN_EPISODE_RATING + " INTEGER, " +
                COLUMN_EPISODE_WATCH_DATE + " TEXT);";
        database.execSQL(CREATE_EPISODES_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        // Update the database when the version has changed.
        Log.w(MovieDatabaseHelper.class.getName(), "Upgrading database from version "
                + oldVersion + " to " + newVersion +
                ", database will be temporarily exported to a JSON string and imported after the upgrade.");

        if (oldVersion < 12) {
            // Create a new table named episodes
            String CREATE_EPISODES_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_EPISODES + "(" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_MOVIES_ID + " INTEGER NOT NULL, " +
                    COLUMN_SEASON_NUMBER + " INTEGER NOT NULL, " +
                    COLUMN_EPISODE_NUMBER + " INTEGER NOT NULL);";
            database.execSQL(CREATE_EPISODES_TABLE);
        }

        //alter episode table to add COLUMN_EPISODE_RATING and COLUMN_EPISODE_WATCH_DATE
        if (oldVersion < 13) {
            String ALTER_EPISODES_TABLE = "ALTER TABLE " + TABLE_EPISODES + " ADD COLUMN " + COLUMN_EPISODE_RATING + " INTEGER;";
            database.execSQL(ALTER_EPISODES_TABLE);
            ALTER_EPISODES_TABLE = "ALTER TABLE " + TABLE_EPISODES + " ADD COLUMN " + COLUMN_EPISODE_WATCH_DATE + " TEXT;";
            database.execSQL(ALTER_EPISODES_TABLE);
        }

        onCreate(database);
    }

    public void addOrUpdateEpisode(int movieId, int seasonNumber, int episodeNumber, @Nullable Integer rating, @Nullable String watchDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MOVIES_ID, movieId);
        values.put(COLUMN_SEASON_NUMBER, seasonNumber);
        values.put(COLUMN_EPISODE_NUMBER, episodeNumber);
        // Only add rating and watch date if they are not null
        if (rating != null) {
            values.put(COLUMN_EPISODE_RATING, rating);
        }
        if (watchDate != null) {
            values.put(COLUMN_EPISODE_WATCH_DATE, watchDate);
        }

        // Check if the episode already exists
        boolean exists = isEpisodeInDatabase(movieId, seasonNumber, Collections.singletonList(episodeNumber));
        if (exists) {
            // Update existing episode
            String whereClause = COLUMN_MOVIES_ID + "=? AND " + COLUMN_SEASON_NUMBER + "=? AND " + COLUMN_EPISODE_NUMBER + "=?";
            String[] whereArgs = new String[]{String.valueOf(movieId), String.valueOf(seasonNumber), String.valueOf(episodeNumber)};
            db.update(TABLE_EPISODES, values, whereClause, whereArgs);
        } else {
            // Insert new episode
            db.insert(TABLE_EPISODES, null, values);
        }
    }

    public EpisodeDbDetails getEpisodeDetails(int movieId, int seasonNumber, int episodeNumber) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_EPISODE_RATING, COLUMN_EPISODE_WATCH_DATE};
        String selection = COLUMN_MOVIES_ID + " = ? AND " + COLUMN_SEASON_NUMBER + " = ? AND " + COLUMN_EPISODE_NUMBER + " = ?";
        String[] selectionArgs = {String.valueOf(movieId), String.valueOf(seasonNumber), String.valueOf(episodeNumber)};
        Cursor cursor = db.query(TABLE_EPISODES, columns, selection, selectionArgs, null, null, null);

        EpisodeDbDetails details = null;
        if (cursor.moveToFirst()) {
            Integer rating = cursor.isNull(0) ? null : cursor.getInt(0);
            String watchDate = cursor.getString(1);
            details = new EpisodeDbDetails(rating, watchDate);
        }
        cursor.close();
        return details;
    }

    public void addEpisodeNumber(int movieId, int seasonNumber, List<Integer> episodeNumbers) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        for (Integer episodeNumber : episodeNumbers) {
            values.put(COLUMN_MOVIES_ID, movieId);
            values.put(COLUMN_SEASON_NUMBER, seasonNumber);
            values.put(COLUMN_EPISODE_NUMBER, episodeNumber);
            db.insert(TABLE_EPISODES, null, values);
        }
    }

    public void removeEpisodeNumber(int movieId, int seasonNumber, List<Integer> episodeNumbers) {
        SQLiteDatabase db = this.getWritableDatabase();

        for (Integer episodeNumber : episodeNumbers) {
            db.delete(TABLE_EPISODES, COLUMN_MOVIES_ID + " = ? AND " + COLUMN_SEASON_NUMBER + " = ? AND " + COLUMN_EPISODE_NUMBER + " = ?",
                    new String[]{String.valueOf(movieId), String.valueOf(seasonNumber), String.valueOf(episodeNumber)});
        }
    }

    public boolean isEpisodeInDatabase(int movieId, int seasonNumber, List<Integer> episodeNumbers) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COLUMN_MOVIES_ID + " = ? AND " + COLUMN_SEASON_NUMBER + " = ? AND " + COLUMN_EPISODE_NUMBER + " = ?";
        boolean exists = false;

        for (Integer episodeNumber : episodeNumbers) {
            Cursor cursor = db.query(TABLE_EPISODES, null, selection,
                    new String[]{String.valueOf(movieId), String.valueOf(seasonNumber), String.valueOf(episodeNumber)},
                    null, null, null);

            if (cursor.getCount() > 0) {
                exists = true;
            }
            cursor.close();
        }

        return exists;
    }

    public int getSeenEpisodesCount(int movieId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String countQuery = "SELECT * FROM " + TABLE_EPISODES + " WHERE " + COLUMN_MOVIES_ID + " = " + movieId;
        Cursor cursor = db.rawQuery(countQuery, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }
}
