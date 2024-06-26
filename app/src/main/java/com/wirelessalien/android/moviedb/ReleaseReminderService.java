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

package com.wirelessalien.android.moviedb;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.icu.text.SimpleDateFormat;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.wirelessalien.android.moviedb.activity.MainActivity;
import com.wirelessalien.android.moviedb.helper.EpisodeReminderDatabaseHelper;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


public class ReleaseReminderService extends Worker {

    private static final int notificationIdMovie = 1;
    private static final int notificationIdEpisode = 2;
    private final static String NOTIFICATION_PREFERENCES = "key_get_notified_for_saved";
    public ReleaseReminderService(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        MovieDatabaseHelper databaseHelper = new MovieDatabaseHelper( getApplicationContext() );
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery( "SELECT * FROM " + MovieDatabaseHelper.TABLE_MOVIES, null );

        SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd", Locale.US );
        String currentDate = sdf.format( new Date() );

        while (cursor.moveToNext()) {
            String title = cursor.getString( cursor.getColumnIndexOrThrow( MovieDatabaseHelper.COLUMN_TITLE ) );
            String releaseDate = cursor.getString( cursor.getColumnIndexOrThrow( MovieDatabaseHelper.COLUMN_RELEASE_DATE ) );
            if (releaseDate != null && releaseDate.equals( currentDate )) {
                createNotification( title );
            }
        }
        cursor.close();

        EpisodeReminderDatabaseHelper episodeDatabaseHelper = new EpisodeReminderDatabaseHelper(getApplicationContext());
        SQLiteDatabase dbEpisode = episodeDatabaseHelper.getReadableDatabase();

        Cursor cursorEpisode = dbEpisode.rawQuery("SELECT * FROM " + EpisodeReminderDatabaseHelper.TABLE_EPISODE_REMINDERS, null);

        while (cursorEpisode.moveToNext()) {
            String tvShowName = cursorEpisode.getString(cursorEpisode.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_TV_SHOW_NAME));
            String episodeName = cursorEpisode.getString(cursorEpisode.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_NAME));
            String episodeNumber = cursorEpisode.getString(cursorEpisode.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_EPISODE_NUMBER));

            String airDate = cursorEpisode.getString(cursorEpisode.getColumnIndexOrThrow(EpisodeReminderDatabaseHelper.COLUMN_DATE));
            if (airDate != null && airDate.equals(currentDate)) {
                createEpisodeNotification(tvShowName, episodeName, episodeNumber);
            }
        }
        cursorEpisode.close();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ReleaseReminderService.class)
                .setInitialDelay(24, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueue(workRequest);

        return Result.success();
    }

    private void createNotification(String title) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean shouldNotify = sharedPreferences.getBoolean(NOTIFICATION_PREFERENCES, true);

        if (shouldNotify) {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.putExtra("tab_index", 2);
            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "released_movies")
                    .setSmallIcon( R.drawable.icon )
                    .setContentTitle(title)
                    .setContentText(getApplicationContext().getString(R.string.movie_released_today, title))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

            if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            notificationManager.notify(notificationIdMovie, builder.build());
        }
    }

    private void createEpisodeNotification(String tvShowName, String episodeName, String episodeNumber) {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra("tab_index", 2);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "episode_reminders")
                .setSmallIcon( R.drawable.icon )
                .setContentTitle(tvShowName)
                .setContentText(getApplicationContext().getString(R.string.episode_airing_today, episodeNumber, episodeName))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationManager.notify(notificationIdEpisode, builder.build());
    }



}