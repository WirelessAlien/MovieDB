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
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.databinding.ActivityDetailBinding;
import com.wirelessalien.android.moviedb.tmdb.account.AddRatingThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.AddToFavouritesThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.AddToWatchlistThreadTMDb;
import com.wirelessalien.android.moviedb.helper.ConfigHelper;
import com.wirelessalien.android.moviedb.tmdb.account.DeleteRatingThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountStateThreadTMDb;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.view.NotifyingScrollView;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.data.TVSeason;
import com.wirelessalien.android.moviedb.tmdb.TVSeasonThread;
import com.wirelessalien.android.moviedb.adapter.CastBaseAdapter;
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter;
import com.wirelessalien.android.moviedb.adapter.SimilarMovieBaseAdapter;
import com.wirelessalien.android.moviedb.adapter.TVSeasonAdapter;
import com.wirelessalien.android.moviedb.fragment.ListBottomSheetDialogFragment;
import com.wirelessalien.android.moviedb.fragment.ListFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * This class provides all the details about the shows.
 * It also manages personal show data.
 */
public class DetailActivity extends BaseActivity {

    private final static String CAST_VIEW_PREFERENCE = "key_show_cast";
    private final static String CREW_VIEW_PREFERENCE = "key_show_crew";
    private final static String RECOMMENDATION_VIEW_PREFERENCE = "key_show_similar_movies";
    private final static String SHOW_SAVE_DIALOG_PREFERENCE = "key_show_save_dialog";
    private String API_KEY;
    private RecyclerView castView;
    private RecyclerView crewView;
    private CastBaseAdapter castAdapter;
    private CastBaseAdapter crewAdapter;
    private ArrayList<JSONObject> castArrayList;
    private ArrayList<JSONObject> crewArrayList;
    private RecyclerView similarMovieView;
    private SimilarMovieBaseAdapter similarMovieAdapter;
    private ArrayList<JSONObject> similarMovieArrayList;
    private MaterialToolbar toolbar;
    private String sessionId;
    private String accountId;
    private SQLiteDatabase database;
    private MovieDatabaseHelper databaseHelper;
    private int movieId;
    private String voteAverage;
    private Integer totalEpisodes;
    private boolean isMovie = true;
    private ImageView movieImage;
    private TextView movieTitle;
    private ImageView moviePoster;
    private TextView movieGenres;
    private TextView movieStartDate;
    private TextView movieFinishDate;
    private TextView movieRewatched;
    private TextView movieEpisodes;
    private TextView imdbLink;
    private RatingBar movieRating;
    private TextView movieDescription;
    private Context context = this;
    private JSONObject jMovieObject;
    private String genres;
    private Date startDate;
    private Date finishDate;
    private Activity mActivity;
    private boolean added = false;
    private SpannableString showTitle;
    private AlphaForegroundColorSpan alphaForegroundColorSpan;
    private ActivityDetailBinding binding;
    private final NotifyingScrollView.OnScrollChangedListener
            mOnScrollChangedListener = new NotifyingScrollView
            .OnScrollChangedListener() {
        public void onScrollChanged(int t) {
            final int headerHeight = binding.movieImage.getHeight() -
                    toolbar.getHeight();
            final float ratio = (float) Math.min(Math.max(t, 0),
                    headerHeight) / headerHeight;
            final int newAlpha = (int) (ratio * 255);
            toolbar.setBackgroundColor(Color.argb(newAlpha, 0, 0, 0));

            // 256 because otherwise it'll become invisible when newAlpha is 255.
            alphaForegroundColorSpan.setAlpha(256 - newAlpha);
            showTitle.setSpan(alphaForegroundColorSpan, 0, showTitle.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (showTitle != null) {
                getSupportActionBar().setTitle(showTitle);
            }
        }
    };
    private SharedPreferences preferences;

    // Indicate whether network items have loaded.
    private boolean mMovieDetailsLoaded = false;
    private boolean mSimilarMoviesLoaded = false;
    private boolean mCastAndCrewLoaded = false;
    private boolean mExternalDataLoaded = false;

    /**
     * Returns the category number when supplied with
     * the category string array index.
     *
     * @param index the position in the string array.
     * @return the category number of the specified position.
     */
    private static int getCategoryNumber(int index) {
        return switch (index) {
            case 0 -> MovieDatabaseHelper.CATEGORY_WATCHING;
            case 1 -> MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH;
            case 2 -> MovieDatabaseHelper.CATEGORY_WATCHED;
            case 3 -> MovieDatabaseHelper.CATEGORY_ON_HOLD;
            case 4 -> MovieDatabaseHelper.CATEGORY_DROPPED;
            default -> MovieDatabaseHelper.CATEGORY_WATCHING;
        };
    }

    /**
     * Returns the category number when supplied with
     * the category string.
     *
     * @param category the name of the category in snake case style.
     * @return the category number of the specified category string.
     */
    public static int getCategoryNumber(String category) {
        return switch (category) {
            case "watching" -> MovieDatabaseHelper.CATEGORY_WATCHING;
            case "plan_to_watch" -> MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH;
            case "watched" -> MovieDatabaseHelper.CATEGORY_WATCHED;
            case "on_hold" -> MovieDatabaseHelper.CATEGORY_ON_HOLD;
            case "dropped" -> MovieDatabaseHelper.CATEGORY_DROPPED;
            default -> MovieDatabaseHelper.CATEGORY_WATCHING;
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetailBinding.inflate(getLayoutInflater());

        API_KEY = ConfigHelper.getConfigValue(getApplicationContext(), "api_key");

        setContentView(binding.getRoot());

        setNavigationDrawer();
        setBackButtons();

        toolbar = binding.toolbar;

        // Make the transparency dependent on how far the user scrolled down.
        NotifyingScrollView notifyingScrollView = binding.scrollView;
        notifyingScrollView.setOnScrollChangedListener(mOnScrollChangedListener);

        // Create a variable with the application context that can be used
        // when data is retrieved.
        mActivity = this;
        movieId = 0;
        context = this;

        // RecyclerView to display the cast of the show.
        castView = binding.castRecyclerView;
        castView.setHasFixedSize(true); // Improves performance (if size is static)

        LinearLayoutManager castLinearLayoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false);
        castView.setLayoutManager(castLinearLayoutManager);

        // RecyclerView to display the crew of the show.
        crewView = binding.crewRecyclerView;
        crewView.setHasFixedSize(true); // Improves performance (if size is static)

        LinearLayoutManager crewLinearLayoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false);
        crewView.setLayoutManager(crewLinearLayoutManager);

        // RecyclerView to display similar shows to this one.
        similarMovieView = binding.movieRecyclerView;
        similarMovieView.setHasFixedSize(true); // Improves performance (if size is static)
        LinearLayoutManager movieLinearLayoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false);
        similarMovieView.setLayoutManager(movieLinearLayoutManager);

        // Make the views invisible if the user collapsed the view.
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (!preferences.getBoolean(CAST_VIEW_PREFERENCE, false)) {
            castView.setVisibility(View.GONE);

            TextView castTitle = binding.castTitle;
            castTitle.setVisibility(View.GONE);

            View castDivider = binding.secondDivider;
            castDivider.setVisibility(View.GONE);
        }

        if (!preferences.getBoolean(CREW_VIEW_PREFERENCE, false)) {
            crewView.setVisibility(View.GONE);

            TextView crewTitle = binding.crewTitle;
            crewTitle.setVisibility(View.GONE);

            View crewDivider = binding.thirdDivider;
            crewDivider.setVisibility(View.GONE);
        }

        if (!preferences.getBoolean(RECOMMENDATION_VIEW_PREFERENCE, false)) {
            similarMovieView.setVisibility(View.GONE);

            TextView similarMovieTitle = binding.similarMovieTitle;
            similarMovieTitle.setVisibility(View.GONE);

            View similarMoviesDivider = binding.fourthDivider;
            similarMoviesDivider.setVisibility(View.GONE);
        }
        // Get the views from the layout.
        movieImage = binding.movieImage;
        movieTitle = binding.movieTitle;
        moviePoster = binding.moviePoster;
        movieGenres = binding.movieGenres;
        movieStartDate = binding.movieStartDate;
        movieFinishDate = binding.movieFinishDate;
        movieRewatched = binding.movieRewatched;
        movieEpisodes = binding.movieEpisodes;
        movieRating = binding.movieRating;
        movieDescription = binding.movieDescription;
        imdbLink = binding.imdbLink;

        ImageButton addToListBtn = binding.addToList;
        ImageButton addToWatchlistButton = binding.watchListButton;
        ImageButton addToFavouritesButton = binding.favouriteButton;
        ImageButton rateButton = binding.ratingBtn;

        sessionId = preferences.getString("access_token", null);
        accountId = preferences.getString("account_id", null);

        // Get the movieObject from the intent that contains the necessary
        // data to display the right movie and related RecyclerViews.
        // Send the JSONObject to setMovieData() so all the data
        // will be displayed on the screen.
        Intent intent = getIntent();
        isMovie = intent.getBooleanExtra("isMovie", true);
        try {
            setMovieData(new JSONObject(intent.getStringExtra("movieObject")));
            jMovieObject = new JSONObject(intent.getStringExtra("movieObject"));

            // Set the adapter with the (still) empty ArrayList.
            castArrayList = new ArrayList<>();
            castAdapter = new CastBaseAdapter(castArrayList, getApplicationContext());
            castView.setAdapter(castAdapter);

            // Set the adapter with the (still) empty ArrayList.
            crewArrayList = new ArrayList<>();
            crewAdapter = new CastBaseAdapter(crewArrayList, getApplicationContext());
            crewView.setAdapter(crewAdapter);

            // Set the adapter with the (still) empty ArrayList.
            similarMovieArrayList = new ArrayList<>();
            similarMovieAdapter = new SimilarMovieBaseAdapter(similarMovieArrayList,
                    getApplicationContext());
            similarMovieView.setAdapter(similarMovieAdapter);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        ProgressBar progressBar = binding.progressBar;
        progressBar.setVisibility(View.VISIBLE);

        CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
            if (accountId != null && sessionId != null) {
                String typeCheck = isMovie ? "movie" : "tv";
                GetAccountStateThreadTMDb getAccountStateThread = new GetAccountStateThreadTMDb(movieId, typeCheck, mActivity);
                getAccountStateThread.start();
                try {
                    getAccountStateThread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException("Thread interrupted", e);
                }
                final boolean isInWatchlist = getAccountStateThread.isInWatchlist();
                final boolean isFavourite = getAccountStateThread.isInFavourites();
                double ratingValue = getAccountStateThread.getRating();
                runOnUiThread(() -> {
                    if (isInWatchlist) {
                        addToWatchlistButton.setImageResource(R.drawable.ic_bookmark);
                    } else {
                        addToWatchlistButton.setImageResource(R.drawable.ic_bookmark_border);
                    }
                    if (isFavourite) {
                        addToFavouritesButton.setImageResource(R.drawable.ic_favorite);
                    } else {
                        addToFavouritesButton.setImageResource(R.drawable.ic_favorite_border);
                    }

                    if (ratingValue != 0) {
                        rateButton.setImageResource(R.drawable.ic_thumb_up);
                    } else {
                        rateButton.setImageResource(R.drawable.ic_thumb_up_border);
                    }
                });
            } else {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Failed to retrieve account details", Toast.LENGTH_SHORT).show());
            }
        }).exceptionally(ex -> {
            Log.e("DetailActivity", "Error in CompletableFuture", ex);
            return null;
        }).thenRun(() -> runOnUiThread(() -> progressBar.setVisibility(View.GONE)));

        CompletableFuture<List<TVSeason>> future2 = null;

        if (!isMovie) {
            future2 = CompletableFuture.supplyAsync(() -> {
                TVSeasonThread tvSeasonThread = new TVSeasonThread(movieId, getApplicationContext());
                tvSeasonThread.start();
                try {
                    tvSeasonThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return tvSeasonThread.getSeasons();
            });
        }

        if (future2 != null) {
            future2.thenAcceptBoth(future1, (seasons, voidResult) -> runOnUiThread(() -> {
                RecyclerView recyclerView = binding.seasonRecyclerview;
                TVSeasonAdapter adapter = new TVSeasonAdapter(DetailActivity.this, seasons);
                recyclerView.setLayoutManager(new LinearLayoutManager(DetailActivity.this, LinearLayoutManager.HORIZONTAL, false));
                recyclerView.setAdapter(adapter);
            }) );
        }

        addToWatchlistButton.setOnClickListener( v -> new Thread( () -> {
            if (accountId != null) {
                boolean add = true;
                boolean remove = false;
                String typeCheck = isMovie ? "movie" : "tv";
                GetAccountStateThreadTMDb checkWatchlistThread = new GetAccountStateThreadTMDb(movieId, typeCheck, mActivity);
                checkWatchlistThread.start();
                try {
                    checkWatchlistThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final boolean isInWatchlist = checkWatchlistThread.isInWatchlist();
                runOnUiThread( () -> {
                    if (isInWatchlist) {
                        addToWatchlistButton.setImageResource( R.drawable.ic_bookmark_border );
                        new AddToWatchlistThreadTMDb(movieId, typeCheck, remove, mActivity).start();
                    } else {
                        addToWatchlistButton.setImageResource( R.drawable.ic_bookmark );
                        new AddToWatchlistThreadTMDb(movieId, typeCheck, add, mActivity).start();
                    }
                } );
            } else {
                runOnUiThread( () -> Toast.makeText(getApplicationContext(), "Failed to retrieve account id", Toast.LENGTH_SHORT).show() );
            }
        } ).start() );

        addToListBtn.setOnClickListener( v -> {
            String typeCheck = isMovie ? "movie" : "tv";
            ListBottomSheetDialogFragment listBottomSheetDialogFragment = new ListBottomSheetDialogFragment(movieId , typeCheck, mActivity);
            listBottomSheetDialogFragment.show(getSupportFragmentManager(), listBottomSheetDialogFragment.getTag());
        } );

        addToFavouritesButton.setOnClickListener( v -> new Thread( () -> {
            if (accountId != null) {
                boolean add = true;
                boolean remove = false;
                String typeCheck = isMovie ? "movie" : "tv";
                GetAccountStateThreadTMDb checkFavouritesThread = new GetAccountStateThreadTMDb(movieId, typeCheck, mActivity);
                checkFavouritesThread.start();
                try {
                    checkFavouritesThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final boolean isInFavourites = checkFavouritesThread.isInFavourites();
                runOnUiThread( () -> {
                    if (isInFavourites) {
                        addToFavouritesButton.setImageResource( R.drawable.ic_favorite_border );
                        new AddToFavouritesThreadTMDb(movieId, typeCheck, remove, mActivity).start();
                    } else {
                        addToFavouritesButton.setImageResource( R.drawable.ic_favorite );
                        new AddToFavouritesThreadTMDb(movieId, typeCheck, add, mActivity).start();
                    }
                } );

            } else {
                runOnUiThread( () -> Toast.makeText(getApplicationContext(), "Failed to retrieve account id. Try login again.", Toast.LENGTH_SHORT).show() );
            }
        } ).start() );

        rateButton.setOnClickListener(v -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mActivity);
            LayoutInflater inflater = getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.rating_dialog, null);
            builder.setView(dialogView);
            final AlertDialog dialog = builder.create();
            dialog.show();

            RatingBar ratingBar = dialogView.findViewById(R.id.ratingBar);
            Button submitButton = dialogView.findViewById(R.id.btnSubmit);
            Button cancelButton = dialogView.findViewById(R.id.btnCancel);
            Button deleteButton = dialogView.findViewById(R.id.btnDelete);

            progressBar.setVisibility(View.VISIBLE);

            CompletableFuture.runAsync(() -> {
                GetAccountStateThreadTMDb getAccountStateThread = new GetAccountStateThreadTMDb(movieId, isMovie ? "movie" : "tv", mActivity);
                getAccountStateThread.start();
                try {
                    getAccountStateThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                double previousRating = getAccountStateThread.getRating();
                runOnUiThread(() -> ratingBar.setRating((float) previousRating / 2));
            }).thenRun(() -> {
                submitButton.setOnClickListener(v1 -> CompletableFuture.runAsync(() -> {
                    String type = isMovie ? "movie" : "tv";
                    double rating = ratingBar.getRating() * 2;
                    new AddRatingThreadTMDb(movieId, rating, type, mActivity).start();
                    runOnUiThread( dialog::dismiss );
                }) );

                deleteButton.setOnClickListener(v12 -> CompletableFuture.runAsync(() -> {
                    String type = isMovie ? "movie" : "tv";
                    new DeleteRatingThreadTMDb(movieId, type, mActivity).start();
                    runOnUiThread( dialog::dismiss );
                }) );

                cancelButton.setOnClickListener(v12 -> dialog.dismiss());
            }).thenRun(() -> progressBar.setVisibility(View.GONE));
        });

        checkNetwork();
    }

    @Override
    void doNetworkWork() {
        // Get the cast and crew for the CastListAdapter and get the movies for the MovieListAdapter.
        if (!mCastAndCrewLoaded) {
            new CastListThread().start();
        }

        if (!mSimilarMoviesLoaded) {
            startSimilarMovieList();
        }

        // Load movie details.
        if (!mMovieDetailsLoaded) {
            MovieDetailsThread movieDetailsThread = new MovieDetailsThread();
            movieDetailsThread.start();
        }

        if (!mExternalDataLoaded) {
            GetExternalIdsThread externalDataThread = new GetExternalIdsThread(context);
            externalDataThread.start();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.save_menu, menu);

        databaseHelper = new MovieDatabaseHelper(getApplicationContext());
        database = databaseHelper.getWritableDatabase();
        databaseHelper.onCreate(database);

        // Check if the show is already in the database.
        Cursor cursor = database.rawQuery("SELECT * FROM " +
                MovieDatabaseHelper.TABLE_MOVIES +
                " WHERE " + MovieDatabaseHelper.COLUMN_MOVIES_ID +
                "=" + movieId + " LIMIT 1", null);

        // If the show is in the database, display a filled in star as icon.
        if (cursor.getCount() > 0) {
            // A record has been found
            MenuItem item = menu.findItem(R.id.action_save);
            item.setIcon(R.drawable.ic_star);
            added = true;
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Add or remove the show from the database.
        if (id == R.id.action_save) {
            databaseHelper = new MovieDatabaseHelper(getApplicationContext());
            database = databaseHelper.getWritableDatabase();
            databaseHelper.onCreate(database);
            if (added) {
                // Remove the show from the database.
                database.delete(MovieDatabaseHelper.TABLE_MOVIES,
                        MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null);
                added = false;
                item.setIcon(R.drawable.ic_star_border);
                ListFragment.databaseUpdate();
            } else {
                final ContentValues showValues = new ContentValues();

                // Only show the dialog is the user specified so in the preferences.
                if (preferences.getBoolean(SHOW_SAVE_DIALOG_PREFERENCE, false)) {

                    // Ask in which category the show should be placed.
                    final MaterialAlertDialogBuilder categoriesDialog = new MaterialAlertDialogBuilder(this);
                    categoriesDialog.setTitle(getString(R.string.category_picker));
                    categoriesDialog.setItems(R.array.categories, (dialog, which) -> {
                        showValues.put(MovieDatabaseHelper.COLUMN_CATEGORIES, getCategoryNumber(which));

                        addMovieToDatabase(showValues, item);
                    } );

                    categoriesDialog.show();
                } else {
                    // If no dialog is shown, add the show to the default category.
                    showValues.put(MovieDatabaseHelper.COLUMN_CATEGORIES, MovieDatabaseHelper.CATEGORY_WATCHING);
                    addMovieToDatabase(showValues, item);
                }
            }
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Adds all necessary values to the ContentValues
     * and then inserts it into the database.
     *
     * @param showValues the ContentValuese with the already specified values.
     * @param item       the menu item that will be replaced with a filled in star.
     */
    private void addMovieToDatabase(ContentValues showValues, MenuItem item) {
        // Add the show to the database.
        try {
            // Put the necessary values into ContentValues object.
            showValues.put(MovieDatabaseHelper.COLUMN_MOVIES_ID,
                    Integer.parseInt(jMovieObject.getString("id")));
            showValues.put(MovieDatabaseHelper.COLUMN_IMAGE,
                    jMovieObject.getString("backdrop_path"));
            showValues.put(MovieDatabaseHelper.COLUMN_ICON,
                    jMovieObject.getString("poster_path"));
            String title = (isMovie) ? "title" : "name";
            showValues.put(MovieDatabaseHelper.COLUMN_TITLE, jMovieObject.getString(title));
            showValues.put(MovieDatabaseHelper.COLUMN_SUMMARY, jMovieObject.getString("overview"));
            showValues.put(MovieDatabaseHelper.COLUMN_GENRES, genres);
            showValues.put(MovieDatabaseHelper.COLUMN_GENRES_IDS,
                    jMovieObject.getString("genre_ids"));
            showValues.put(MovieDatabaseHelper.COLUMN_MOVIE, isMovie);
            showValues.put(MovieDatabaseHelper.COLUMN_RATING,
                    jMovieObject.getString("vote_average"));
            String releaseDate = (isMovie) ? "release_date" : "first_air_date";
            showValues.put(MovieDatabaseHelper.COLUMN_RELEASE_DATE,
                    jMovieObject.getString(releaseDate));

            // Insert the show into the database.
            database.insert(MovieDatabaseHelper.TABLE_MOVIES, null, showValues);

            // Inform the user of the addition to the database
            // and change the boolean in order to change the MenuItem's behaviour.
            added = true;
            item.setIcon(R.drawable.ic_star);
            if (isMovie) {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.movie_added), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.series_added), Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException je) {
            je.printStackTrace();
            Toast.makeText(this, getResources().getString(R.string.show_added_error),
                    Toast.LENGTH_SHORT).show();
        }

        // Update the ListFragment to include the newly added show.
        ListFragment.databaseUpdate();
    }

    @Override
    public void onBackPressed() {
        LinearLayout editShowDetails = binding.editShowDetails;

        if (editShowDetails.getVisibility() != View.GONE) {
            // Clear the focus (in case it has the focus)
            // so the content will be saved when the user leaves.
            Spinner categoriesView = binding.categories;
            EditText timesWatched = binding.timesWatched;
            EditText episodesSeen = binding.episodesSeen;
            EditText showRating = binding.showRating;

            categoriesView.clearFocus();
            timesWatched.clearFocus();
            episodesSeen.clearFocus();
            showRating.clearFocus();
        }

        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * Checks if the current data is different than the data from the
     * specified JSONObject. If that's the case, replace the data with the
     * data from the specified JSONObject.
     *
     * @param movieObject JSONObject with the information about the show.
     */
    private void setMovieData(JSONObject movieObject) {
        // Check if movieObject values differ from the current values,
        // if they do, use the movieObject values.
        try {
            // Set the movieId
            movieId = Integer.parseInt(movieObject.getString("id"));

            // Due to the difficulty of comparing images (or rather,
            // this can be a really slow process) the id of the image is
            // saved as class variable for easy comparison.
            if (movieObject.has("backdrop_path") && movieImage.getDrawable() == null) {
                Picasso.get().load("https://image.tmdb.org/t/p/w780" +
                        movieObject.getString("backdrop_path"))
                        .into(movieImage);

                Animation animation = AnimationUtils.loadAnimation(
                        getApplicationContext(), R.anim.fade_in);
                movieImage.startAnimation(animation);

                // Set the old imageId to the new one.
                String movieImageId = movieObject.getString( "backdrop_path" );
            }

            // Same goes for the movie poster of course.
            if (movieObject.has("poster_path") && moviePoster.getDrawable() == null) {
                Picasso.get().load("https://image.tmdb.org/t/p/w500" +
                        movieObject.getString("poster_path"))
                        .into(moviePoster);

                // Set the old posterId to the new one.
                String moviePosterId = movieObject.getString( "poster_path" );
            }

            // Check if it is a movie or a TV series.
            String title = (movieObject.has("title")) ? "title" : "name";

            if (movieObject.has(title) &&
                    !movieObject.getString(title).equals(movieTitle
                            .getText().toString())) {
                movieTitle.setText(movieObject.getString(title));

                // Initialise global variables (will become visible when scrolling down).
                showTitle = new SpannableString(movieObject.getString(title));
                alphaForegroundColorSpan = new AlphaForegroundColorSpan();
            }

            // The rating also uses a class variable for the same reason
            // as the image.
            databaseHelper = new MovieDatabaseHelper(getApplicationContext());
            database = databaseHelper.getWritableDatabase();

            databaseHelper.onCreate(database);

            // Retrieve and present saved data of the show.
            Cursor cursor = database.rawQuery("SELECT * FROM " +
                    MovieDatabaseHelper.TABLE_MOVIES +
                    " WHERE " + MovieDatabaseHelper.COLUMN_MOVIES_ID +
                    "=" + movieId + " LIMIT 1", null);
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                // Set the rating to the personal rating of the user.
                movieRating.setRating(cursor.getFloat(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_PERSONAL_RATING)) / 2);

                // If the database has a start date, use it, otherwise print unknown.
                if (!cursor.isNull(cursor.getColumnIndexOrThrow
                        (MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE))
                        && !cursor.getString(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)).equals("")) {
                    String startDateString = cursor.getString(
                            cursor.getColumnIndexOrThrow(MovieDatabaseHelper
                                    .COLUMN_PERSONAL_START_DATE));
                    startDate = parseDateString(startDateString, null, null);
                    movieStartDate.setText(getString(R.string.start_date)
                            + parseDateToString(startDate, null, null));
                } else {
                    movieStartDate.setText(getString(R.string.start_date_unknown));
                }

                // If the database has a finish date, use it, otherwise print unknown.
                if (!cursor.isNull(cursor.getColumnIndexOrThrow
                        (MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE))
                        && !cursor.getString(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)).equals("")) {
                    String finishDateString = cursor.getString(
                            cursor.getColumnIndexOrThrow(MovieDatabaseHelper
                                    .COLUMN_PERSONAL_FINISH_DATE));

                    finishDate = parseDateString(finishDateString, null, null);
                    movieFinishDate.setText(getString(R.string.finish_date)
                            + parseDateToString(finishDate, null, null));
                } else {
                    movieFinishDate.setText(getString(R.string.finish_date_unknown));
                }

                // If the database has a rewatched count, use it, otherwise it is 0.
                int watched = 0;
                if (!cursor.isNull(cursor.getColumnIndexOrThrow
                        (MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED))
                        && !cursor.getString(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED)).equals("")) {
                    watched = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper
                            .COLUMN_PERSONAL_REWATCHED));
                } else if (cursor.getInt(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_CATEGORIES)) == 1) {
                    watched = 1;
                }
                movieRewatched.setText(getString(R.string.times_watched) + watched);

                // If the database has an episodes seen count, use it, otherwise it is 0.
                // Only make "episodes seen" visible for TV shows.
                if (!isMovie) {
                    String episodeCount = "0";

                    // Get the total amount of episodes (of all seasons).
                    if (movieObject.has("seasons")) {
                        JSONArray seasonArray = movieObject.getJSONArray("seasons");
                        if (seasonArray != null) {
                            if (seasonArray.length() > 1) {
                                totalEpisodes = 0;
                                // If there are more seasons, season 0 mostly refers to Specials.
                                // Specials (and other extras) don't count as episodes.
                                // (which is also the reason that number_of_episodes is not used)
                                for (int i = 1; i < seasonArray.length(); i++) {
                                    if (seasonArray.get(i) != null) {
                                        totalEpisodes += ((JSONObject) seasonArray.get(i)).getInt("episode_count");
                                    }
                                }
                            } else {
                                totalEpisodes = ((JSONObject) seasonArray.get(0)).getInt("episode_count");
                            }
                        }
                    }

                    // If there is a personal episode count available,
                    // set the episodeCount equal to it. If the show is marked
                    // as "watched" then that implies that all episodes have
                    // been seen and therefore episodeCount is equal to totalEpisodes.
                    if (!cursor.isNull(cursor.getColumnIndexOrThrow
                            (MovieDatabaseHelper.COLUMN_PERSONAL_EPISODES))
                            && !cursor.getString(cursor.getColumnIndexOrThrow(
                            MovieDatabaseHelper.COLUMN_PERSONAL_EPISODES)).equals("")) {
                        episodeCount = String.valueOf(cursor.getInt(
                                cursor.getColumnIndexOrThrow(MovieDatabaseHelper
                                        .COLUMN_PERSONAL_EPISODES)));
                    } else if (cursor.getInt(cursor.getColumnIndexOrThrow(
                            MovieDatabaseHelper.COLUMN_CATEGORIES)) == 1) {
                        if (totalEpisodes != null) {
                            episodeCount = String.valueOf(totalEpisodes);
                        } else {
                            episodeCount = "?";
                        }
                    }
                    movieEpisodes.setText(getString(R.string.episodes_seen) + episodeCount + "/"
                            + totalEpisodes);

                    // Make the row visible once the correct values are set.
                    TableRow episodesSeenRow = (TableRow) binding.episodesSeen.getParent();
                    episodesSeenRow.setVisibility(View.VISIBLE);
                    movieEpisodes.setVisibility(View.VISIBLE);
                }

                // Make all the views visible (if the show is in the database).
                movieStartDate.setVisibility(View.VISIBLE);
                movieFinishDate.setVisibility(View.VISIBLE);
                movieRewatched.setVisibility(View.VISIBLE);

                // Make it possible to change the values.
                ImageView editIcon = binding.editIcon;
                editIcon.setVisibility(View.VISIBLE);
            } else if (movieObject.has("vote_average") &&
                    !movieObject.getString("vote_average").equals(voteAverage)) {
                // Set the avarage (non-personal) rating (if it isn't the same).
                movieRating.setRating(Float.parseFloat(movieObject
                        .getString("vote_average")) / 2);
            }

            // If the overview (summary) is different in the new dataset, change it.
            if (movieObject.has("overview") &&
                    !movieObject.getString("overview").equals(movieDescription
                            .getText().toString()) && !movieObject.getString("overview")
                    .equals("") && !movieObject.getString("overview").equals("null")) {
                movieDescription.setText(movieObject.getString("overview"));
                if (movieObject.getString("overview").equals("")) {
                    MovieDetailsThread movieDetailsThread = new MovieDetailsThread("true");
                    movieDetailsThread.start();
                }
            }

            // Set the genres
            if (movieObject.has("genre_ids")) {
                // This works a bit different,
                // the ids will be converted to genres first after that
                // the new text with genres will be compared to the old one.

                // Remove the [ and ] from the String
                String genreIds = movieObject.getString("genre_ids")
                        .substring(1, movieObject.getString("genre_ids")
                                .length() - 1);

                // Split the String with the ids and set them into an array.
                String[] genreArray = genreIds.split(",");

                // Get the sharedPreferences
                SharedPreferences sharedPreferences = getApplicationContext()
                        .getSharedPreferences("GenreList", Context.MODE_PRIVATE);

                // Add all the genres in one String.
                StringBuilder genreNames = new StringBuilder();
                for (String aGenreArray : genreArray) {
                    genreNames.append(", ").append(sharedPreferences
                            .getString(aGenreArray, aGenreArray));
                }

                // Remove the first ", " from the String and set the text.
                movieGenres.setText(genreNames.substring(2));
                genres = genreNames.substring(2);
            }

            cursor.close();
        } catch (JSONException | ParseException e) {
            e.printStackTrace();
        }
    }

    public class GetExternalIdsThread extends Thread {

        private final Handler handler;
        private final Context context;

        public GetExternalIdsThread(Context context) {

            this.context = context;
            this.handler = new Handler(Looper.getMainLooper());
        }

        @Override
        public void run() {
            try {
                String movie = (isMovie) ? SectionsPagerAdapter.MOVIE : SectionsPagerAdapter.TV;
                URL url = new URL("https://api.themoviedb.org/3/" + movie + "/" + movieId + "/external_ids?api_key=" + API_KEY);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                reader.close();
                String response = stringBuilder.toString();

                handler.post(() -> onPostExecute(response));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void onPostExecute(String response) {
            try {
                JSONObject jsonObject = new JSONObject(response);
                String imdbId = jsonObject.getString("imdb_id");
                imdbLink = binding.imdbLink;
                RelativeLayout imdbLayout = findViewById(R.id.imdbLayout);
                //if imdbId is not available, set the text to "IMDB (not available)"
                if (imdbId.equals("null")) {
                    imdbLink.setText("IMDB (not available)");
                } else {
                    //if imdbId is available, set the text to "IMDB" and set an onClickListener to open the IMDB page
                    imdbLink.setText("IMDB");
                    imdbLayout.setOnClickListener(v -> {
                        String url = "https://www.imdb.com/title/" + imdbId;
                        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                        CustomTabsIntent customTabsIntent = builder.build();
                        customTabsIntent.launchUrl(context, Uri.parse(url));
                    });
                }
                mExternalDataLoaded = true;

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }



    /**
     * Makes the showDetails layout invisible and the editShowDetails visible
     * (or the other way around).
     */
    public void editDetails(View view) {
        final LinearLayout showDetails, editShowDetails;
        showDetails = binding.showDetails;
        editShowDetails = binding.editShowDetails;
        ImageView editIcon = binding.editIcon;

        final EditText episodesSeenView = binding.episodesSeen;
        final EditText timesWatchedView = binding.timesWatched;
        final EditText showRating = binding.showRating;

        if (editShowDetails.getVisibility() == View.GONE) {
            fadeOutAndHideAnimation(showDetails);
            showDetails.getAnimation().setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    fadeInAndShowAnimation(editShowDetails);
                    updateEditShowDetails();
                    showDetails.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationEnd(Animation animation) {

                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });

            editIcon.setImageResource(R.drawable.ic_check);

            // Listen for changes to the categories.
            Spinner categoriesView = binding.categories;
            categoriesView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    // Save the category to the database
                    ContentValues showValues = new ContentValues();
                    database = databaseHelper.getWritableDatabase();
                    Cursor cursor = database.rawQuery("SELECT * FROM " +
                            MovieDatabaseHelper.TABLE_MOVIES +
                            " WHERE " + MovieDatabaseHelper.COLUMN_MOVIES_ID +
                            "=" + movieId + " LIMIT 1", null);
                    cursor.moveToFirst();

                    // Check if the show is already watched and if the user changed the category.
                    if (getCategoryNumber(position) == 1
                            && cursor.getInt(cursor.getColumnIndexOrThrow(
                            MovieDatabaseHelper.COLUMN_CATEGORIES))
                            != getCategoryNumber(position)) {
                        if (totalEpisodes != null) {
                            showValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_EPISODES,
                                    totalEpisodes);
                            episodesSeenView.setText(totalEpisodes.toString());
                        }

                        // If the user hasn't set their own watched value, automatically set it.
                        int timesWatchedCount = cursor.getInt(cursor.getColumnIndexOrThrow(
                                MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED));
                        if (timesWatchedCount == 0) {
                            showValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED,
                                    1);
                            timesWatchedView.setText("1");
                        }
                    }

                    showValues.put(MovieDatabaseHelper.COLUMN_CATEGORIES,
                            getCategoryNumber(position));
                    database.update(MovieDatabaseHelper.TABLE_MOVIES, showValues,
                            MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null);
                    database.close();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // Do nothing
                }
            });

            // Listen to changes to the EditText.
            timesWatchedView.setOnFocusChangeListener( (v, hasFocus) -> {
                if (!hasFocus && !timesWatchedView.getText().toString().isEmpty()) {
                    // Save the number to the database
                    ContentValues showValues = new ContentValues();
                    int timesWatched = Integer.parseInt(timesWatchedView.getText().toString());

                    showValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED,
                            timesWatched);

                    database = databaseHelper.getWritableDatabase();
                    databaseHelper.onCreate(database);
                    database.update(MovieDatabaseHelper.TABLE_MOVIES, showValues,
                            MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null);

                    database.close();

                    // Update the view
                    movieRewatched.setText(getString(R.string.change_watched_times) + timesWatched );
                }
            } );

            // Listen to changes to the EpisodesSeen EditText.
            episodesSeenView.setOnFocusChangeListener( (v, hasFocus) -> {
                if (!hasFocus && !episodesSeenView.getText().toString().isEmpty()) {
                    // Save the number to the database
                    ContentValues showValues = new ContentValues();
                    int episodesSeen = Integer.parseInt(episodesSeenView.getText().toString());
                    showValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_EPISODES,
                            episodesSeen);

                    database = databaseHelper.getWritableDatabase();
                    databaseHelper.onCreate(database);
                    database.update(MovieDatabaseHelper.TABLE_MOVIES, showValues,
                            MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null);

                    database.close();

                    // Update the view
                    String movieEpisodesString = getString(R.string.episodes_seen)
                            + episodesSeen;
                    if (totalEpisodes != null) {
                        movieEpisodesString += "/" + totalEpisodes;
                    }
                    movieEpisodes.setText(movieEpisodesString);
                }
            } );

            // Listen to changes to the ShowRating EditText.
            showRating.setOnFocusChangeListener( (v, hasFocus) -> {
                if (!hasFocus && !showRating.getText().toString().isEmpty()) {
                    // Save the number to the database
                    ContentValues showValues = new ContentValues();
                    int rating = Integer.parseInt(showRating.getText().toString());

                    // Do not allow ratings outside of the range.
                    if (rating > 10) {
                        rating = 10;
                    } else if (rating < 0) {
                        rating = 0;
                    }
                    showValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_RATING, rating);

                    database = databaseHelper.getWritableDatabase();
                    databaseHelper.onCreate(database);
                    database.update(MovieDatabaseHelper.TABLE_MOVIES, showValues,
                            MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null);

                    database.close();

                    // Update the view
                    movieRating.setRating((float) rating / 2);
                }
            } );
        } else {
            fadeOutAndHideAnimation(editShowDetails);
            editShowDetails.getAnimation().setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    fadeInAndShowAnimation(showDetails);
                    editShowDetails.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationEnd(Animation animation) {

                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });

            editIcon.setImageResource( R.drawable.ic_edit );
        }
    }

    private void updateEditShowDetails() {
        database = databaseHelper.getWritableDatabase();
        databaseHelper.onCreate(database);
        Cursor cursor = database.rawQuery("SELECT * FROM " +
                MovieDatabaseHelper.TABLE_MOVIES +
                " WHERE " + MovieDatabaseHelper.COLUMN_MOVIES_ID +
                "=" + movieId + " LIMIT 1", null);

        if (cursor.getCount() <= 0) {
            // No record has been found, the show hasn't been added or an error occurred.
            cursor.close();
        } else {
            cursor.moveToFirst();

            Spinner categories = binding.categories;
            Button startDateButton = binding.startDateButton;
            Button endDateButton = binding.endDateButton;
            EditText timesWatched = binding.timesWatched;
            EditText episodesSeen = binding.episodesSeen;
            EditText showRating = binding.showRating;

            // Set the right category.
            switch (cursor.getInt( cursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_CATEGORIES ) )) {
                case MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH -> categories.setSelection( 1 );
                case MovieDatabaseHelper.CATEGORY_WATCHED -> categories.setSelection( 2 );
                case MovieDatabaseHelper.CATEGORY_ON_HOLD -> categories.setSelection( 3 );
                case MovieDatabaseHelper.CATEGORY_DROPPED -> categories.setSelection( 4 );
                default -> {
                }
                // The "Watching" category will be displayed.
            }

            if (!cursor.isNull(cursor.getColumnIndexOrThrow
                    (MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE))
                    && !cursor.getString(cursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)).equals("")) {
                String startDateString = cursor.getString(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE));
                try {
                    startDate = parseDateString(startDateString, null, null);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                startDateButton.setText(parseDateToString(startDate, null, null));
            } else {
                startDateButton.setText("Not set");
            }

            if (!cursor.isNull(cursor.getColumnIndexOrThrow
                    (MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE))
                    && !cursor.getString(cursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)).equals("")) {
                String finishDateString = cursor.getString(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE));
                try {
                    finishDate = parseDateString(finishDateString, null, null);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                endDateButton.setText(parseDateToString(finishDate, null, null));
            } else {
                endDateButton.setText("Not set");
            }

            if (!cursor.isNull(cursor.getColumnIndexOrThrow
                    (MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED))
                    && !cursor.getString(cursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED)).equals("")) {
                timesWatched.setText(cursor.getString(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED
                )));
            } else {
                if (cursor.getInt(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_CATEGORIES)) == 1) {
                    timesWatched.setText("1");
                } else {
                    timesWatched.setText("0");
                }
            }

            if (!cursor.isNull(cursor.getColumnIndexOrThrow
                    (MovieDatabaseHelper.COLUMN_PERSONAL_EPISODES))
                    && !cursor.getString(cursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_PERSONAL_EPISODES)).equals("")) {
                episodesSeen.setText(cursor.getString(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_PERSONAL_EPISODES
                )));
            } else {
                if (cursor.getInt(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_CATEGORIES)) == 1
                        && totalEpisodes != null) {
                    episodesSeen.setText(totalEpisodes.toString());
                } else {
                    episodesSeen.setText("0");
                }
            }

            if (!cursor.isNull(cursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_PERSONAL_RATING))
                    && !cursor.getString(cursor.getColumnIndexOrThrow(
                    MovieDatabaseHelper.COLUMN_PERSONAL_RATING)).equals("")) {
                showRating.setText(cursor.getString(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_PERSONAL_RATING
                )));
            }
        }
        ListFragment.databaseUpdate();
    }

    public void selectDate(final View view) {
        final MaterialAlertDialogBuilder dateDialog = new MaterialAlertDialogBuilder(this);

        LayoutInflater inflater = getLayoutInflater();
        // Suppress the warning because DialogView is supposed to have a null root view
        // because the parent is not known at the inflation time.
        @SuppressLint("InflateParams") final View dialogView = inflater.inflate(R.layout.date_change_dialog, null);
        dateDialog.setView(dialogView);
        dateDialog.setTitle("Select a date:");

        final DatePicker datePicker = dialogView.findViewById(R.id.movieDatePicker);

        // Set the date in the date picker to the previous selected date.
        Date date = (view.getTag().equals("start_date")) ? startDate : finishDate;
        if (date != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            datePicker.updateDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        }

        dateDialog.setPositiveButton("Save", (dialog, which) -> {
            // Get the date from the DatePicker.
            Calendar calendar = Calendar.getInstance();
            calendar.set(datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth());
            String dateFormat = parseDateToString(calendar.getTime(), null, null);

            // Save the date to the database and update the view
            ContentValues movieValues = new ContentValues();

            if (view.getTag().equals("start_date")) {
                movieValues.put(MovieDatabaseHelper
                        .COLUMN_PERSONAL_START_DATE, dateFormat);

                Button button = findViewById(R.id.startDateButton);
                button.setText(dateFormat);
                movieStartDate.setText(getString(R.string.change_start_date_2) +
                        dateFormat);
                startDate = calendar.getTime();

            } else {
                movieValues.put(MovieDatabaseHelper
                        .COLUMN_PERSONAL_FINISH_DATE, dateFormat);

                Button button = findViewById(R.id.endDateButton);
                button.setText(dateFormat);
                movieFinishDate.setText(getString(R.string.change_finish_date_2)
                        + dateFormat);
                finishDate = calendar.getTime();
            }

            database = databaseHelper.getWritableDatabase();
            databaseHelper.onCreate(database);
            database.update(MovieDatabaseHelper.TABLE_MOVIES, movieValues,
                    MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null);
            dialog.dismiss();
        } );

        dateDialog.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss() );

        dateDialog.show();
    }

    private void fadeOutAndHideAnimation(final View view) {
        Animation fadeOut = new AlphaAnimation(1, 0);
        fadeOut.setInterpolator(new AccelerateInterpolator());
        fadeOut.setDuration(100);

        view.startAnimation(fadeOut);
    }

    private void fadeInAndShowAnimation(final View view) {
        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setInterpolator(new AccelerateInterpolator());
        fadeIn.setDuration(100);

        fadeIn.setAnimationListener(new Animation.AnimationListener() {
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.VISIBLE);
            }

            public void onAnimationRepeat(Animation animation) {
            }

            public void onAnimationStart(Animation animation) {
            }
        });

        view.startAnimation(fadeIn);
    }

    private void hideEmptyRecyclerView(RecyclerView recyclerView, View titleView) {
        if (recyclerView.getAdapter().getItemCount() == 0) {
            recyclerView.setVisibility(View.GONE);
            titleView.setVisibility(View.GONE);
        }
    }

    /**
     * Parses a string to a date.
     * @param date the string containing only a date.
     * @return the date parsed from the String.
     * @throws ParseException thrown when parsing the date string failed.
     */
    private static Date parseDateString(String date, String format, Locale locale) throws ParseException {
        if (format == null) {
            format = "dd-MM-yyyy";
        }

        if (locale == null) {
            locale = Locale.US;
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format, locale);
            return simpleDateFormat.parse(date);
        } catch (ParseException pe) {
            // Try parsing an old format.
            // Note: normally this shouldn't be hardcoded, but right now I think it is more
            // important to get all the dates to be consistent.
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd MMM yy", locale);
            return simpleDateFormat.parse(date);
        }
    }

    /**
     * Parses a date to a string.
     * @param date the date to be parsed.
     * @param format the format of the resulting string, nullable.
     * @param locale the date locale, nullable.
     * @return a string containing the date in the given format.
     */
    private static String parseDateToString(Date date, String format, Locale locale) {
        if (format == null) {
            format = "dd-MM-yyyy";
        }

        if (locale == null) {
            locale = Locale.US;
        }

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format, locale);
        return simpleDateFormat.format(date);
    }

    // Load the list of actors.
    private class CastListThread extends Thread {

        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void run() {
            String line;
            StringBuilder stringBuilder = new StringBuilder();

            String movie = (isMovie) ? SectionsPagerAdapter.MOVIE : SectionsPagerAdapter.TV;

            // Load the list with actors that played in the movie.
            try {
                URL url = new URL("https://api.themoviedb.org/3/" + movie + "/" +
                        movieId + "/credits?api_key=" + API_KEY +
                        getLanguageParameter(getApplicationContext()));
                URLConnection urlConnection = url.openConnection();

                try {
                    BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(
                                    urlConnection.getInputStream()));

                    // Create one long string of the webpage.
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }

                    // Close connection and return the data from the webpage.
                    bufferedReader.close();
                    String response = stringBuilder.toString();
                    handler.post(() -> onPostExecute(response));
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        private void onPostExecute(String response) {
            if (response != null && !response.isEmpty()) {
                // Set all the actors in a list and send that to the adapter.
                try {
                    JSONObject reader = new JSONObject(response);

                    // Add the cast to the castView
                    if (reader.getJSONArray("cast").length() <= 0) {
                        // This movie has no available cast,
                        // do not show the cast related views.
                        TextView textView = mActivity.findViewById(R.id.castTitle);
                        View view = mActivity.findViewById(R.id.secondDivider);

                        textView.setVisibility(View.GONE);
                        view.setVisibility(View.GONE);
                        castView.setVisibility(View.GONE);
                    } else {
                        JSONArray castArray = reader.getJSONArray("cast");

                        for (int i = 0; i < castArray.length(); i++) {
                            JSONObject castData = castArray.getJSONObject(i);
                            castArrayList.add(castData);
                        }
                        castAdapter = new CastBaseAdapter(castArrayList,
                                getApplicationContext());

                        castView.setAdapter(castAdapter);
                    }

                    // Add the crew to the crewView
                    if (reader.getJSONArray("crew").length() <= 0) {
                        // This movie has no available cast,
                        // do not show the cast related views.
                        TextView textView = mActivity.findViewById(R.id.crewTitle);
                        View view = mActivity.findViewById(R.id.thirdDivider);

                        textView.setVisibility(View.GONE);
                        view.setVisibility(View.GONE);
                        crewView.setVisibility(View.GONE);
                    } else {
                        JSONArray crewArray = reader.getJSONArray("crew");

                        for (int i = 0; i < crewArray.length(); i++) {
                            JSONObject crewData = crewArray.getJSONObject(i);

                            // Before adding the JSONObject to the Array,
                            // "camouflage" it as an actor so it can use
                            // the CastBaseAdapter
                            crewData.put("character", crewData.getString("job"));

                            crewArrayList.add(crewData);
                        }

                        crewAdapter = new CastBaseAdapter(crewArrayList,
                                getApplicationContext());

                        crewView.setAdapter(crewAdapter);
                    }

                    mCastAndCrewLoaded = true;
                } catch (JSONException je) {
                    je.printStackTrace();
                }
            }

            hideEmptyRecyclerView(castView, binding.castTitle);
            hideEmptyRecyclerView(crewView, binding.crewTitle);
        }
    }

    // Load a list with similar movies.
    private final Handler handler = new Handler( Looper.getMainLooper());

    public void startSimilarMovieList() {
        new Thread( () -> {
            String response = doInBackground();
            handler.post( () -> onPostExecute(response) );
        } ).start();
    }

    private String doInBackground() {
        String line;
        StringBuilder stringBuilder = new StringBuilder();

        String movie = (isMovie) ? SectionsPagerAdapter.MOVIE : SectionsPagerAdapter.TV;

        // Load the webpage with the list of similar movies.
        try {
            URL url = new URL("https://api.themoviedb.org/3/" + movie + "/" +
                    movieId + "/recommendations?api_key=" +
                    API_KEY + getLanguageParameter(getApplicationContext()));
            URLConnection urlConnection = url.openConnection();

            try {
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(urlConnection.getInputStream()));

                // Create one long string of the webpage.
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }

                // Close connection and return the data from the webpage.
                bufferedReader.close();
                return stringBuilder.toString();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        // Loading the dataset failed, return null.
        return null;
    }

    private void onPostExecute(String response) {
        if (response != null && !response.isEmpty()) {
            // Set all the similar movies in a list and send that to the adapter.
            try {
                JSONObject reader = new JSONObject(response);
                JSONArray similarMovieArray = reader.getJSONArray("results");
                for (int i = 0; i < similarMovieArray.length(); i++) {
                    JSONObject movieData = similarMovieArray.getJSONObject(i);
                    similarMovieArrayList.add(movieData);
                }
                similarMovieAdapter = new SimilarMovieBaseAdapter(
                        similarMovieArrayList, getApplicationContext());

                similarMovieView.setAdapter(similarMovieAdapter);
                mSimilarMoviesLoaded = false;
            } catch (JSONException je) {
                je.printStackTrace();
            }
        }

        hideEmptyRecyclerView(similarMovieView, binding.similarMovieTitle);
    }

    // Load the movie details.
    public class MovieDetailsThread extends Thread {
        private boolean missingOverview;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final String[] params;

        public MovieDetailsThread(String... params) {
            this.params = params;
        }

        @Override
        public void run() {
            if (params.length > 0) {
                missingOverview = params[0].equalsIgnoreCase("true");
            }

            String line;
            StringBuilder stringBuilder = new StringBuilder();

            // Load the movie webpage (and check for updates/corrections).
            try {
                String type = (isMovie) ? SectionsPagerAdapter.MOVIE : SectionsPagerAdapter.TV;
                URL url;
                if (missingOverview) {
                    url = new URL("https://api.themoviedb.org/3/" + type +
                            "/" + movieId + "?api_key=" + API_KEY);
                } else {
                    url = new URL("https://api.themoviedb.org/3/" + type +
                            "/" + movieId + "?api_key=" + API_KEY
                            + getLanguageParameter(getApplicationContext()));
                }
                URLConnection urlConnection = url.openConnection();

                try {
                    BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(
                                    urlConnection.getInputStream()));

                    // Create one long string of the webpage.
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }

                    // Close connection and return the data from the webpage.
                    bufferedReader.close();
                    String response = stringBuilder.toString();
                    handler.post(() -> onPostExecute(response));
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        private void onPostExecute(String response) {
            if (response != null && !response.isEmpty()) {
                // Send the dataset to setMovieData to change the data where needed.
                try {
                    JSONObject movieData = new JSONObject(response);
                    // If the translation is missing, only change the overview.
                    // Doing this with setMovieData would change everything to English.
                    // To prevent that, we use this if-statement.
                    if (missingOverview) {
                        movieDescription.setText(movieData.getString("overview"));
                    } else {
                        setMovieData(movieData);
                    }
                    mMovieDetailsLoaded = true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @SuppressLint("ParcelCreator")
    /* Credits to: Flavien Laurent (http://flavienlaurent.com/blog/2013/11/20/making-your-action-bar-not-boring/) */
    public static class AlphaForegroundColorSpan extends ForegroundColorSpan {
        private float mAlpha;

        AlphaForegroundColorSpan() {
            super(0xffffffff);
        }

        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeFloat(mAlpha);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setColor(getAlphaColor());
        }

        void setAlpha(float alpha) {
            mAlpha = alpha;
        }

        private int getAlphaColor() {
            int foregroundColor = getForegroundColor();
            return Color.argb((int) (mAlpha * 255), Color.red(foregroundColor), Color.green(foregroundColor), Color.blue(foregroundColor));
        }
    }
}
