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
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.DateFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.CastBaseAdapter;
import com.wirelessalien.android.moviedb.adapter.EpisodePagerAdapter;
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter;
import com.wirelessalien.android.moviedb.adapter.SimilarMovieBaseAdapter;
import com.wirelessalien.android.moviedb.databinding.ActivityDetailBinding;
import com.wirelessalien.android.moviedb.fragment.LastEpisodeFragment;
import com.wirelessalien.android.moviedb.fragment.ListBottomSheetDialogFragment;
import com.wirelessalien.android.moviedb.fragment.ListFragment;
import com.wirelessalien.android.moviedb.helper.ConfigHelper;
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper;
import com.wirelessalien.android.moviedb.tmdb.account.AddRatingThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.AddToFavouritesThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.AddToWatchlistThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.DeleteRatingThreadTMDb;
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountStateThreadTMDb;
import com.wirelessalien.android.moviedb.view.NotifyingScrollView;

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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This class provides all the details about the shows.
 * It also manages personal show data.
 */
public class DetailActivity extends BaseActivity {

    private final static String CAST_VIEW_PREFERENCE = "key_show_cast";
    private final static String CREW_VIEW_PREFERENCE = "key_show_crew";
    private final static String RECOMMENDATION_VIEW_PREFERENCE = "key_show_similar_movies";
    private final static String SHOW_SAVE_DIALOG_PREFERENCE = "key_show_save_dialog";
    private final static String DYNAMIC_COLOR_DETAILS_ACTIVITY = "dynamic_color_details_activity";
    private static final String HD_IMAGE_SIZE = "key_hq_images";

    private String API_KEY;
    private String api_read_access_token;
    private CastBaseAdapter castAdapter;
    private CastBaseAdapter crewAdapter;
    private ArrayList<JSONObject> castArrayList;
    private ArrayList<JSONObject> crewArrayList;
    private SimilarMovieBaseAdapter similarMovieAdapter;
    private ViewPager2 episodeViewPager;
    private EpisodePagerAdapter episodePagerAdapter;
    private ArrayList<JSONObject> similarMovieArrayList;
    private String sessionId;
    private String accountId;
    private SQLiteDatabase database;
    private MovieDatabaseHelper databaseHelper;
    private int movieId;
    private JSONArray seasons;
    private Target target;
    private String voteAverage;
    private int numSeason;
    private String showName;
    private Integer totalEpisodes;
    private int seenEpisode;
    private boolean isMovie = true;
    private Context context = this;
    private JSONObject jMovieObject;
    private String genres;
    private Date startDate;
    private Date finishDate;
    private Activity mActivity;
    private final int MAX_RETRY_COUNT = 2;
    private int retryCount = 0;
    private boolean added = false;
    private SpannableString showTitle;
    private Palette palette;
    private int darkMutedColor;
    private int lightMutedColor;
    private ActivityDetailBinding binding;
    private final NotifyingScrollView.OnScrollChangedListener
            mOnScrollChangedListener = new NotifyingScrollView
            .OnScrollChangedListener() {
        int lastScrollY = 0;
        final int scrollThreshold = 50;
        public void onScrollChanged(int t) {
            if (t == 0) {
                getSupportActionBar().setTitle("");
            } else {
                if (showTitle != null && Math.abs(t - lastScrollY) > scrollThreshold) {
                    getSupportActionBar().setTitle(showTitle);
                }
            }
            if (Math.abs(t - lastScrollY) > scrollThreshold) {
                if (t > lastScrollY) {
                    binding.fab.hide();
                    binding.fabSave.hide();
                } else if (t < lastScrollY) {
                    binding.fab.show();
                    binding.fabSave.show();
                }
                lastScrollY = t;
            }
        }
    };
    private SharedPreferences preferences;

    // Indicate whether network items have loaded.
    private boolean mMovieDetailsLoaded = false;
    private boolean mSimilarMoviesLoaded = false;
    private boolean mCastAndCrewLoaded = false;
    private boolean mVideosLoaded = false;

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
        api_read_access_token = ConfigHelper.getConfigValue(getApplicationContext(), "api_read_access_token");

        setContentView(binding.getRoot());

        setNavigationDrawer();
        getSupportActionBar().setTitle("");


        episodeViewPager = findViewById(R.id.episodeViewPager);
        episodePagerAdapter = new EpisodePagerAdapter(this);
        // Make the transparency dependent on how far the user scrolled down.
        binding.scrollView.setOnScrollChangedListener(mOnScrollChangedListener);
        binding.scrollView.scrollTo(0, 0);

        // Create a variable with the application context that can be used
        // when data is retrieved.
        mActivity = this;
        movieId = 0;
        context = this;
        seenEpisode = 0;

        // RecyclerView to display the cast of the show.
        binding.castRecyclerView.setHasFixedSize(true); // Improves performance (if size is static)

        LinearLayoutManager castLinearLayoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false);
        binding.castRecyclerView.setLayoutManager(castLinearLayoutManager);

        // RecyclerView to display the crew of the show.
        binding.crewRecyclerView.setHasFixedSize(true); // Improves performance (if size is static)

        LinearLayoutManager crewLinearLayoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false);
        binding.crewRecyclerView.setLayoutManager(crewLinearLayoutManager);

        // RecyclerView to display similar shows to this one.
        binding.movieRecyclerView.setHasFixedSize(true); // Improves performance (if size is static)
        LinearLayoutManager movieLinearLayoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false);
        binding.movieRecyclerView.setLayoutManager(movieLinearLayoutManager);

        // Make the views invisible if the user collapsed the view.
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (!preferences.getBoolean(CAST_VIEW_PREFERENCE, false)) {
            binding.castRecyclerView.setVisibility(View.GONE);

            binding.castTitle.setVisibility(View.GONE);
            binding.secondDivider.setVisibility(View.GONE);
        }

        if (!preferences.getBoolean(CREW_VIEW_PREFERENCE, false)) {
            binding.crewRecyclerView.setVisibility(View.GONE);

            binding.crewTitle.setVisibility(View.GONE);
            binding.thirdDivider.setVisibility(View.GONE);
        }

        if (!preferences.getBoolean(RECOMMENDATION_VIEW_PREFERENCE, false)) {
            binding.movieRecyclerView.setVisibility(View.GONE);

            TextView similarMovieTitle = binding.similarMovieTitle;
            similarMovieTitle.setVisibility(View.GONE);
        }

        sessionId = preferences.getString("access_token", null);
        accountId = preferences.getString("account_id", null);

        if (sessionId == null || accountId == null) {
            // Disable the buttons
            binding.watchListButton.setEnabled(false);
            binding.favouriteButton.setEnabled(false);
            binding.ratingBtn.setEnabled(false);
            binding.addToList.setEnabled(false);
//            binding.episodeRateBtn.setEnabled(false);
        } else {
            // Enable the buttons
            binding.watchListButton.setEnabled(true);
            binding.favouriteButton.setEnabled(true);
            binding.ratingBtn.setEnabled(true);
            binding.addToList.setEnabled(true);
//            binding.episodeRateBtn.setEnabled(true);
        }

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
            binding.castRecyclerView.setAdapter(castAdapter);

            // Set the adapter with the (still) empty ArrayList.
            crewArrayList = new ArrayList<>();
            crewAdapter = new CastBaseAdapter(crewArrayList, getApplicationContext());
            binding.crewRecyclerView.setAdapter(crewAdapter);

            // Set the adapter with the (still) empty ArrayList.
            similarMovieArrayList = new ArrayList<>();
            similarMovieAdapter = new SimilarMovieBaseAdapter(similarMovieArrayList,
                    getApplicationContext());
            binding.movieRecyclerView.setAdapter(similarMovieAdapter);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        checkNetwork();

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
            binding.fabSave.setImageResource(R.drawable.ic_star);
            added = true;
        }

        ProgressBar progressBar = binding.progressBar;
        progressBar.setVisibility(View.VISIBLE);

        CompletableFuture.runAsync( () -> {
            if (accountId != null && sessionId != null) {
                String typeCheck = isMovie ? "movie" : "tv";
                GetAccountStateThreadTMDb getAccountStateThread = new GetAccountStateThreadTMDb( movieId, typeCheck, mActivity );
                getAccountStateThread.start();
                try {
                    getAccountStateThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final boolean isInWatchlist = getAccountStateThread.isInWatchlist();
                final boolean isFavourite = getAccountStateThread.isInFavourites();
                double ratingValue = getAccountStateThread.getRating();
                runOnUiThread( () -> {
                    if (isInWatchlist) {
                        binding.watchListButton.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_bookmark));
                    } else {
                        binding.watchListButton.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_bookmark_border));
                    }
                    if (isFavourite) {
                        binding.favouriteButton.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_favorite));
                    } else {
                        binding.favouriteButton.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_favorite_border));
                    }

                    if (ratingValue != 0) {
                        binding.ratingBtn.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_thumb_up));
                    } else {
                        binding.ratingBtn.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_thumb_up_border));
                    }
                } );
            } else {
                boolean isToastShown = preferences.getBoolean( "isToastShown", false );

                if (!isToastShown) {
                    runOnUiThread( () -> Toast.makeText( getApplicationContext(), R.string.login_is_required_to_use_tmdb_based_sync, Toast.LENGTH_SHORT ).show() );

                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean( "isToastShown", true );
                    editor.apply();
                }
            }
        } ).exceptionally( ex -> {
            Log.e( "DetailActivity", "Error in CompletableFuture", ex );
            return null;
        } ).thenRun( () -> runOnUiThread( () -> progressBar.setVisibility( View.GONE ) ) );

        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        boolean isDarkTheme = uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES;

        int color;
        if (isDarkTheme) {
            color = Color.BLACK;
        } else {
            color = Color.WHITE;
        }

        if (preferences.getBoolean( DYNAMIC_COLOR_DETAILS_ACTIVITY, false )) {
            if (jMovieObject.has( "backdrop_path" ) && binding.movieImage.getDrawable() == null) {
                boolean loadHDImage = preferences.getBoolean(HD_IMAGE_SIZE, false);
                String imageSize = loadHDImage ? "w1280" : "w780";
                String imageUrl;
                try {
                    imageUrl = "https://image.tmdb.org/t/p/" + imageSize + jMovieObject.getString( "backdrop_path" );
                } catch (JSONException e) {
                    throw new RuntimeException( e );
                }
                // Set the loaded bitmap to your ImageView before generating the Palette
                String finalImageUrl = imageUrl;
                target = new Target() {

                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        // Set the loaded bitmap to your ImageView before generating the Palette
                        binding.movieImage.setImageBitmap(bitmap);

                        palette = Palette.from(bitmap).generate();
                        darkMutedColor = palette.getDarkMutedColor(palette.getMutedColor(Color.TRANSPARENT));
                        lightMutedColor = palette.getLightMutedColor(palette.getMutedColor(Color.TRANSPARENT));

                        GradientDrawable gradientDrawable;
                        int mutedColor;
                        if (isDarkTheme) {
                            gradientDrawable = new GradientDrawable(
                                    GradientDrawable.Orientation.TL_BR,
                                    new int[]{darkMutedColor, color}
                            );
                            mutedColor = darkMutedColor;
                        } else {
                            gradientDrawable = new GradientDrawable(
                                    GradientDrawable.Orientation.TL_BR,
                                    new int[]{lightMutedColor, color}
                            );
                            mutedColor = lightMutedColor;
                        }

                        binding.getRoot().setBackground(gradientDrawable);
                        binding.appBarLayout.setBackgroundColor(Color.TRANSPARENT);
                        ColorStateList colorStateList = ColorStateList.valueOf(mutedColor);

                        if (mutedColor != Color.TRANSPARENT) {
                            binding.fab.setBackgroundTintList(colorStateList);
                            binding.certificationCv.setBackgroundColor( Color.TRANSPARENT );
                            binding.releaseDateCv.setBackgroundColor( Color.TRANSPARENT );
                            binding.runtimeCv.setBackgroundColor( Color.TRANSPARENT );
                            binding.genreCv.setBackgroundColor( Color.TRANSPARENT );
                            binding.ratingCv.setBackgroundColor( Color.TRANSPARENT );
                            binding.allEpisodeBtn.setBackgroundTintList( colorStateList );
                            binding.fabSave.setBackgroundTintList( colorStateList );
                            binding.toolbar.setBackgroundColor(Color.TRANSPARENT );
                            binding.collapsingToolbar.setContentScrimColor( mutedColor );
                        }

                        Animation animation = AnimationUtils.loadAnimation(
                                getApplicationContext(), R.anim.slide_in_right);
                        binding.movieImage.startAnimation(animation);
                    }

                    @Override
                    public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                        if (retryCount < MAX_RETRY_COUNT) {
                            Picasso.get().load( finalImageUrl ).into(this);
                            retryCount++;
                        } else {
                            binding.movieImage.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_surface));
                        }
                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {
                        binding.movieImage.setBackgroundColor( ContextCompat.getColor( context, R.color.md_theme_surface ) );
                    }
                };
                Picasso.get().load( imageUrl ).into( target );
            }
        } else {

            if (jMovieObject.has("backdrop_path") && binding.movieImage.getDrawable() == null) {
                boolean loadHDImage = preferences.getBoolean(HD_IMAGE_SIZE, false);
                String imageSize = loadHDImage ? "w1280" : "w780";
                try {
                    Picasso.get().load("https://image.tmdb.org/t/p/" + imageSize +
                                    jMovieObject.getString("backdrop_path"))
                            .into(binding.movieImage);
                } catch (JSONException e) {
                    throw new RuntimeException( e );
                }

                Animation animation = AnimationUtils.loadAnimation(
                        getApplicationContext(), R.anim.fade_in);
                binding.movieImage.startAnimation(animation);
            }
        }

        binding.watchListButton.setOnClickListener( v -> new Thread( () -> {
            if (accountId != null) {
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
                        binding.watchListButton.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_bookmark_border));
                        new AddToWatchlistThreadTMDb(movieId, typeCheck, false, mActivity).start();
                    } else {
                        binding.watchListButton.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_bookmark));
                        new AddToWatchlistThreadTMDb(movieId, typeCheck, true, mActivity).start();
                    }
                } );
            } else {
                runOnUiThread( () -> Toast.makeText(getApplicationContext(), "Failed to retrieve account id", Toast.LENGTH_SHORT).show() );
            }
        } ).start() );

        binding.addToList.setOnClickListener( v -> {
            String typeCheck = isMovie ? "movie" : "tv";
            ListBottomSheetDialogFragment listBottomSheetDialogFragment = new ListBottomSheetDialogFragment(movieId , typeCheck, mActivity, true);
            listBottomSheetDialogFragment.show(getSupportFragmentManager(), listBottomSheetDialogFragment.getTag());
        } );

        binding.favouriteButton.setOnClickListener( v -> new Thread( () -> {
            if (accountId != null) {
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
                        binding.favouriteButton.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_favorite_border));
                        new AddToFavouritesThreadTMDb(movieId, typeCheck, false, mActivity).start();
                    } else {
                        binding.favouriteButton.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_favorite));
                        new AddToFavouritesThreadTMDb(movieId, typeCheck, true, mActivity).start();
                    }
                } );

            } else {
                runOnUiThread( () -> Toast.makeText(getApplicationContext(), "Failed to retrieve account id. Try login again.", Toast.LENGTH_SHORT).show() );
            }
        } ).start() );

        binding.ratingBtn.setOnClickListener(v -> {
            BottomSheetDialog dialog = new BottomSheetDialog(mActivity);
            LayoutInflater inflater = getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.rating_dialog, null);
            dialog.setContentView(dialogView);
            dialog.show();

            RatingBar ratingBar = dialogView.findViewById(R.id.ratingBar);
            Button submitButton = dialogView.findViewById(R.id.btnSubmit);
            Button cancelButton = dialogView.findViewById(R.id.btnCancel);
            Button deleteButton = dialogView.findViewById(R.id.btnDelete);
            TextView movieTitle = dialogView.findViewById(R.id.tvTitle);
            
            movieTitle.setText( showTitle );

            CompletableFuture.runAsync(() -> {
                GetAccountStateThreadTMDb getAccountStateThread = new GetAccountStateThreadTMDb(movieId, isMovie ? "movie" : "tv", mActivity);
                getAccountStateThread.start();
                try {
                    getAccountStateThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                double previousRating = getAccountStateThread.getRating();
                runOnUiThread(() -> ratingBar.setRating((float) previousRating));
            }).thenRun(() -> {
                submitButton.setOnClickListener(v1 -> CompletableFuture.runAsync(() -> {
                    String type = isMovie ? "movie" : "tv";
                    double rating = ratingBar.getRating();
                    new AddRatingThreadTMDb(movieId, rating, type, mActivity).start();
                    runOnUiThread( dialog::dismiss );
                }));

                deleteButton.setOnClickListener(v12 -> CompletableFuture.runAsync(() -> {
                    String type = isMovie ? "movie" : "tv";
                    new DeleteRatingThreadTMDb(movieId, type, mActivity).start();
                    runOnUiThread( dialog::dismiss );
                }));

                cancelButton.setOnClickListener(v12 -> dialog.dismiss());
            });
        });



        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.intent.setPackage("com.android.chrome");
        CustomTabsClient.bindCustomTabsService(mActivity, "com.android.chrome", new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(@NonNull ComponentName componentName, @NonNull CustomTabsClient customTabsClient) {
                customTabsClient.warmup(0L);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        });

        binding.fab.setOnClickListener(new View.OnClickListener() {
            final String typeCheck = isMovie ? "movie" : "tv";
            @Override
            public void onClick(View view) {
                String tmdbLink = "https://www.themoviedb.org/" + typeCheck + "/" + movieId;
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, tmdbLink);
                startActivity(Intent.createChooser(shareIntent, "Share link using"));
            }
        });

        if (!isMovie) {
            binding.revenueText.setVisibility( View.GONE );
            binding.revenueDataText.setVisibility( View.GONE );
        }
        if (!isMovie) {
            jMovieObject.has( "name" );
            showName = jMovieObject.optString( "name" );
        }
        if (isMovie) {
            binding.episodeViewPager.setVisibility( View.GONE );
            binding.allEpisodeBtn.setVisibility( View.GONE );
            binding.episodeText.setVisibility( View.GONE );
        }

        binding.allEpisodeBtn.setOnClickListener( v -> {
            Intent iiintent = new Intent( getApplicationContext(), TVSeasonDetailsActivity.class );
            iiintent.putExtra( "tvShowId", movieId );
            iiintent.putExtra( "numSeasons", numSeason );
            iiintent.putExtra( "tvShowName", showName);
            startActivity( iiintent );
        } );

        binding.moreImageBtn.setOnClickListener( v -> {
            Intent imageintent = new Intent( getApplicationContext(), MovieImageActivity.class );
            imageintent.putExtra( "movieId", movieId );
            imageintent.putExtra( "isMovie", isMovie );
            startActivity( imageintent );
        } );


        binding.watchProvider.setOnClickListener(v -> {
            final String typeCheck = isMovie ? "movie" : "tv";
            String url = "https://www.themoviedb.org/" + typeCheck  + "/" + movieId + "/watch?locale=" + Locale.getDefault().getCountry();

            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            CustomTabsIntent customTabIntent = builder.build();

            if (customTabIntent.intent.resolveActivity(getPackageManager()) != null) {
                customTabIntent.launchUrl(this, Uri.parse(url));
            } else {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            }
        });

        binding.fabSave.setOnClickListener(v -> {
            if (added) {
                // Remove the show from the database.
                database.delete(MovieDatabaseHelper.TABLE_MOVIES,
                        MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null);
                added = false;
                binding.fabSave.setImageResource(R.drawable.ic_star_border);
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

                        // Add the show to the database
                        addMovieToDatabase(showValues);

                        // If the selected category is CATEGORY_WATCHED, add seasons and episodes to the database
                        if (getCategoryNumber(which) == MovieDatabaseHelper.CATEGORY_WATCHED) {
                            addSeasonsAndEpisodesToDatabase();
                        }
                    });

                    categoriesDialog.show();
                } else {
                    // If no dialog is shown, add the show to the default category.
                    showValues.put(MovieDatabaseHelper.COLUMN_CATEGORIES, MovieDatabaseHelper.CATEGORY_WATCHING);
                    addMovieToDatabase(showValues);
                }
            }
        });
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
            CompletableFuture.runAsync( () -> {
                MovieDetailsThread movieDetailsThread = new MovieDetailsThread();
                movieDetailsThread.start();
            } ).exceptionally( ex -> {
                Log.e( "DetailActivity", "Error in CompletableFuture", ex );
                return null;
            } );
        }

        if (!mVideosLoaded) {
            GetVideosThread getVideosThread = new GetVideosThread();
            getVideosThread.start();
        }
    }


    private void addSeasonsAndEpisodesToDatabase() {
        if (!isMovie && seasons != null) {
            try {
                for (int i = 1; i <= seasons.length(); i++) {
                    JSONObject season = seasons.getJSONObject(i-1);
                    int seasonNumber = season.getInt("season_number");
                    int episodeCount = season.getInt("episode_count");
                    for (int j = 1; j <= episodeCount; j++) {

                        ContentValues values = new ContentValues();
                        values.put(MovieDatabaseHelper.COLUMN_MOVIES_ID, movieId);
                        values.put(MovieDatabaseHelper.COLUMN_SEASON_NUMBER, seasonNumber);
                        values.put(MovieDatabaseHelper.COLUMN_EPISODE_NUMBER, j );
                        long newRowId = database.insert(MovieDatabaseHelper.TABLE_EPISODES, null, values);
                        if (newRowId == -1) {
                            Toast.makeText(this, R.string.error_adding_episode_to_database, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Adds all necessary values to the ContentValues
     * and then inserts it into the database.
     *
     * @param showValues the ContentValuese with the already specified values.
     */
    private void addMovieToDatabase(ContentValues showValues) {
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
            binding.fabSave.setImageResource(R.drawable.ic_star);
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

            binding.categories.clearFocus();
            binding.timesWatched.clearFocus();
            binding.showRating.clearFocus();
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
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false);

        String imageSize = loadHDImage ? "w780" : "w500";

        // Check if movieObject values differ from the current values,
        // if they do, use the movieObject values.
        try {
            // Set the movieId
            movieId = Integer.parseInt(movieObject.getString("id"));

            // Due to the difficulty of comparing images (or rather,
            // this can be a really slow process) the id of the image is
            // saved as class variable for easy comparison.

            if (movieObject.has("poster_path") && binding.moviePoster.getDrawable() == null) {
                Picasso.get().load("https://image.tmdb.org/t/p/" + imageSize +
                        movieObject.getString("poster_path"))
                        .into(binding.moviePoster);

                // Set the old posterId to the new one.
                movieObject.getString( "poster_path" );
            }

            // Check if it is a movie or a TV series.
            String title = (movieObject.has("title")) ? "title" : "name";

            if (movieObject.has(title) &&
                    !movieObject.getString(title).equals(binding.movieTitle
                            .getText().toString())) {
                binding.movieTitle.setText(movieObject.getString(title));

                // Initialise global variables (will become visible when scrolling down).
                showTitle = new SpannableString(movieObject.getString(title));
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
                binding.rating.setText(cursor.getFloat(cursor.getColumnIndexOrThrow(
                        MovieDatabaseHelper.COLUMN_PERSONAL_RATING)) +"/10");

                // If the database has a start date, use it, otherwise print unknown.
                SimpleDateFormat dbDateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

                // Start Date
                if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE))
                        && !cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)).equals("")) {
                    String startDateString = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE));
                    try {
                        Date startDate = dbDateFormat.parse(startDateString);
                        // Use DateFormat.getDateInstance(DateFormat.DEFAULT) for default system date format
                        String formattedStartDate = DateFormat.getDateInstance(DateFormat.DEFAULT).format(startDate);
                        binding.movieStartDate.setText(getString(R.string.start_date) + formattedStartDate);
                    } catch (ParseException e) {
                        e.printStackTrace();
                        binding.movieStartDate.setText(getString(R.string.start_date_unknown));
                    }
                } else {
                    binding.movieStartDate.setText(getString(R.string.start_date_unknown));
                }

                // Finish Date
                if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE))
                        && !cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)).equals("")) {
                    String finishDateString = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE));
                    try {
                        Date finishDate = dbDateFormat.parse(finishDateString);
                        // Use DateFormat.getDateInstance(DateFormat.DEFAULT) for default system date format
                        String formattedFinishDate = DateFormat.getDateInstance(DateFormat.DEFAULT).format(finishDate);
                        binding.movieFinishDate.setText(getString(R.string.finish_date) + formattedFinishDate);
                    } catch (ParseException e) {
                        e.printStackTrace();
                        binding.movieFinishDate.setText(getString(R.string.finish_date_unknown));
                    }
                } else {
                    binding.movieFinishDate.setText(getString(R.string.finish_date_unknown));
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
                binding.movieRewatched.setText(getString(R.string.times_watched) + watched);

                if (!isMovie) {
                    String episodeCount = "0";

                    // Get the total amount of episodes.
                    if (movieObject.has("number_of_episodes")) {
                        totalEpisodes = movieObject.getInt("number_of_episodes");
                    } else if (movieObject.has("seasons")) {
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

                    if (!isMovie) {
                        seenEpisode = databaseHelper.getSeenEpisodesCount(movieId);
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
                            episodeCount = String.valueOf(seenEpisode);
                        }
                    } else {
                        episodeCount = String.valueOf(seenEpisode);
                    }

                    binding.movieEpisodes.setText(getString(R.string.episodes_seen) + episodeCount + "/"
                            + totalEpisodes);

                    binding.movieEpisodes.setVisibility(View.VISIBLE);
                }

                // Make all the views visible (if the show is in the database).
                binding.movieStartDate.setVisibility(View.VISIBLE);
                binding.movieFinishDate.setVisibility(View.VISIBLE);
                binding.movieRewatched.setVisibility(View.VISIBLE);

                // Make it possible to change the values.
                binding.editIcon.setVisibility(View.VISIBLE);
            } else if (movieObject.has("vote_average") &&
                !movieObject.getString("vote_average").equals(voteAverage)) {
            float voteAverage = Float.parseFloat(movieObject.getString("vote_average"));
            binding.rating.setText(String.format(Locale.getDefault(), "%.2f/10", voteAverage));
        }

            // If the overview (summary) is different in the new dataset, change it.
            if (movieObject.has("overview") &&
                    !movieObject.getString("overview").equals(binding.movieDescription
                            .getText().toString()) && !movieObject.getString("overview")
                    .equals("") && !movieObject.getString("overview").equals("null")) {
                binding.movieDescription.setText(movieObject.getString("overview"));
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
                binding.genreText.setText( genreNames.substring(2) );
                genres = genreNames.substring(2);
            }

            if (!isMovie) {
                JSONObject lastEpisode = movieObject.optJSONObject("last_episode_to_air");
                JSONObject nextEpisode = movieObject.optJSONObject("next_episode_to_air");

                // Clear the adapter's fragments before adding new ones or it duplicates them.
                episodePagerAdapter.clearFragments();

                if (lastEpisode != null) {
                    episodePagerAdapter.addFragment(LastEpisodeFragment.newInstance(lastEpisode, "Latest Episode"), 0);
                }

                if (nextEpisode != null) {
                    episodePagerAdapter.addFragment(LastEpisodeFragment.newInstance(nextEpisode, "Next Episode"), 1);
                }

                episodeViewPager.setAdapter(episodePagerAdapter);
            }

            if (movieObject.has("tagline")) {
                String tagline = movieObject.getString("tagline");
                if (!tagline.equals(binding.tagline.getText().toString())) {
                    binding.tagline.setText(tagline);
                    binding.tagline.setVisibility(View.VISIBLE);
                }
            } else {
                binding.tagline.setVisibility(View.GONE);
            }

            if (!isMovie) {
                if (movieObject.has("first_air_date")) {
                    String firstAirDateStr = movieObject.getString("first_air_date");
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        Date firstAirDate = sdf.parse(firstAirDateStr);
                        if (firstAirDate != null) {
                            DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault());
                            String formattedDate = dateFormat.format(firstAirDate);
                            if (!formattedDate.equals(binding.releaseDate.getText().toString())) {
                                binding.releaseDate.setText(formattedDate);
                            }
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (isMovie) {
                if (movieObject.has("runtime") && !movieObject.getString("runtime").equals(binding.runtime.getText().toString())) {
                    int totalMinutes = Integer.parseInt(movieObject.getString("runtime"));
                    int hours = totalMinutes / 60;
                    int minutes = totalMinutes % 60;
                    String formattedRuntime = String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
                    binding.runtime.setText(formattedRuntime);
                }
            } else {
                if (movieObject.has("episode_run_time")) {
                    JSONArray episodeRuntimes = movieObject.getJSONArray("episode_run_time");
                    if (episodeRuntimes.length() > 0) {
                        int episodeRuntimeMinutes = episodeRuntimes.getInt(0);
                        int hours = episodeRuntimeMinutes / 60;
                        int minutes = episodeRuntimeMinutes % 60;
                        String formattedRuntime = String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
                        binding.runtime.setText(formattedRuntime);
                    } else {
                        binding.runtime.setText(R.string.unknown);
                    }
                } else {
                    binding.runtime.setText(R.string.unknown);
                }
            }

            if (movieObject.has("status") && !movieObject.getString("status").equals(binding.statusDataText.getText().toString())) {
                binding.statusDataText.setText(movieObject.getString("status"));
            }

            if (movieObject.has("production_countries")) {
                JSONArray productionCountries = movieObject.getJSONArray("production_countries");
                StringBuilder countries = new StringBuilder();
                for (int i = 0; i < productionCountries.length(); i++) {
                    JSONObject country = productionCountries.getJSONObject(i);
                    countries.append(country.getString("name"));
                    if (i < productionCountries.length() - 1) {
                        countries.append(", ");
                    }
                }
                binding.countryDataText.setText(countries.toString());
            }


            String formattedRevenue;
            if (movieObject.has("revenue")) {
                long revenue = Long.parseLong(movieObject.getString("revenue"));
                formattedRevenue = formatCurrency(revenue);
            } else {
                formattedRevenue = getString(R.string.unknown);
            }

            String formattedBudget;
            if (movieObject.has("budget")) {
                long budget = Long.parseLong(movieObject.getString("budget"));
                formattedBudget = formatCurrency(budget);
            } else {
                formattedBudget = getString(R.string.unknown);
            }

            String combinedText = formattedBudget + " / " + formattedRevenue;

            binding.revenueDataText.setText(combinedText);

            cursor.close();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String formatCurrency(long value) {
        if (value >= 1_000_000_000) {
            return String.format(Locale.US, "$%.1fB", value / 1_000_000_000.0);
        } else if (value >= 1_000_000) {
            return String.format(Locale.US, "$%.1fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format(Locale.US, "$%.1fK", value / 1_000.0);
        } else {
            return String.format(Locale.US, "$%d", value);
        }
    }


    public class GetVideosThread extends Thread {

        private final Handler handler;

        public GetVideosThread() {
            this.handler = new Handler(Looper.getMainLooper());
        }

        @Override
        public void run() {
            try {
                String type = (isMovie) ? SectionsPagerAdapter.MOVIE : SectionsPagerAdapter.TV;

                URL url = new URL("https://api.themoviedb.org/3/" + type + "/" + movieId + "/videos?api_key=" + API_KEY);
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
                JSONArray results = jsonObject.getJSONArray("results");
                for (int i = 0; i < results.length(); i++) {
                    JSONObject video = results.getJSONObject(i);
                    String type = video.getString("type");
                    String site = video.getString("site");
                    if (type.equals("Trailer")) {
                        String key = video.getString("key");
                        String url;
                        if (site.equals("YouTube")) {
                            url = "https://www.youtube.com/watch?v=" + key;
                        } else {
                            url = "https://www." + site.toLowerCase() + ".com/watch?v=" + key;
                        }

                        binding.trailer.setOnClickListener(v -> {
                            if (url == null || url.isEmpty()) {
                                Toast.makeText(context, "No trailer available", Toast.LENGTH_SHORT).show();
                            } else if (url.contains("youtube")) {
                                // Extract the video key from the URL if it's a YouTube video
                                String videoKey = url.substring(url.lastIndexOf("=") + 1);
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + videoKey));
                                if (intent.resolveActivity(context.getPackageManager()) != null) {
                                    context.startActivity(intent);
                                } else {
                                    // YouTube app is not installed, open the video in a custom Chrome tab
                                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                                    CustomTabsIntent customTabsIntent = builder.build();
                                    customTabsIntent.launchUrl(context, Uri.parse(url));
                                }
                            } else {
                                // If it's not a YouTube video, open it in a custom Chrome tab
                                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                                CustomTabsIntent customTabsIntent = builder.build();
                                customTabsIntent.launchUrl(context, Uri.parse(url));
                            }
                        });
                    }
                }
                mVideosLoaded = true;
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

        final TextInputEditText timesWatchedView = binding.timesWatched;
        final TextInputEditText showRating = binding.showRating;

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

            binding.editIcon.setIcon( ContextCompat.getDrawable( this, R.drawable.ic_check ));
            binding.editIcon.setText( R.string.done );

            // Listen for changes to the categories.
            MaterialAutoCompleteTextView categoriesView = binding.categories;
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                    R.array.categories, android.R.layout.simple_dropdown_item_1line);
            categoriesView.setAdapter(adapter);

// Disable text input
            categoriesView.setInputType( InputType.TYPE_NULL);
            categoriesView.setKeyListener(null);

// Show dropdown on click
            categoriesView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    categoriesView.showDropDown();
                }
            });

            categoriesView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    // Save the category to the database
                    ContentValues showValues = new ContentValues();
                    database = databaseHelper.getWritableDatabase();
                    Cursor cursor = database.rawQuery("SELECT * FROM " +
                            MovieDatabaseHelper.TABLE_MOVIES +
                            " WHERE " + MovieDatabaseHelper.COLUMN_MOVIES_ID +
                            "=" + movieId + " LIMIT 1", null);
                    cursor.moveToFirst();

                    // Check if the show is already watched and if the user changed the category.
                    if (getCategoryNumber(position) == 1 && cursor.getInt(cursor.getColumnIndexOrThrow(
                            MovieDatabaseHelper.COLUMN_CATEGORIES))
                            != getCategoryNumber(position)) {

                        // If the user hasn't set their own watched value, automatically set it.
                        int timesWatchedCount = cursor.getInt(cursor.getColumnIndexOrThrow(
                                MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED));
                        if (timesWatchedCount == 0) {
                            showValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED,
                                    1);
                            timesWatchedView.setText("1");
                        }

                        // Fetch seasons data and add to database if category is changed to "watched"
                        MovieDetailsThread movieDetailsThread = new MovieDetailsThread();
                        movieDetailsThread.start();
                        try {
                            movieDetailsThread.join();
                            addSeasonsAndEpisodesToDatabase();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    showValues.put(MovieDatabaseHelper.COLUMN_CATEGORIES,
                            getCategoryNumber(position));
                    database.update(MovieDatabaseHelper.TABLE_MOVIES, showValues,
                            MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null);
                    database.close();
                }
            });

            // Listen to changes to the EditText.
            timesWatchedView.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && !timesWatchedView.getText().toString().isEmpty()) {
                    // Save the number to the database
                    ContentValues showValues = new ContentValues();
                    int timesWatched = Integer.parseInt(timesWatchedView.getText().toString());

                    showValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED, timesWatched);

                    database = databaseHelper.getWritableDatabase();
                    databaseHelper.onCreate(database);
                    database.update(MovieDatabaseHelper.TABLE_MOVIES, showValues,
                            MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null);

                    database.close();

                    // Update the view
                    binding.movieRewatched.setText(getString(R.string.change_watched_times) + timesWatched);
                }
            });

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
                    binding.rating.setText((float) rating + "/10");
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

            binding.editIcon.setIcon( ContextCompat.getDrawable( this, R.drawable.ic_edit ));
            binding.editIcon.setText( R.string.edit );
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

            MaterialAutoCompleteTextView categories = binding.categories;
            Button startDateButton = binding.startDateButton;
            Button endDateButton = binding.endDateButton;
            EditText timesWatched = binding.timesWatched;
            EditText showRating = binding.showRating;

            int category = cursor.getInt(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_CATEGORIES));
            switch (category) {
                case MovieDatabaseHelper.CATEGORY_PLAN_TO_WATCH -> categories.setText(categories.getAdapter().getItem(1).toString(), false);
                case MovieDatabaseHelper.CATEGORY_WATCHED -> categories.setText(categories.getAdapter().getItem(2).toString(), false);
                case MovieDatabaseHelper.CATEGORY_ON_HOLD -> categories.setText(categories.getAdapter().getItem(3).toString(), false);
                case MovieDatabaseHelper.CATEGORY_DROPPED -> categories.setText(categories.getAdapter().getItem(4).toString(), false);
                default -> categories.setText(categories.getAdapter().getItem(0).toString(), false);
            }

            if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE))
                    && !cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE)).equals("")) {
                String startDateString = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE));
                try {
                    startDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(startDateString);
                    // Use DateFormat.getDateInstance() for default system date format
                    startDateButton.setText(DateFormat.getDateInstance().format(startDate));
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } else {
                startDateButton.setText(R.string.start_date);
            }

            if (!cursor.isNull(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE))
                    && !cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE)).equals("")) {
                String finishDateString = cursor.getString(cursor.getColumnIndexOrThrow(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE));
                try {
                    finishDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(finishDateString);
                    // Use DateFormat.getDateInstance() for default system date format
                    endDateButton.setText(DateFormat.getDateInstance().format(finishDate));
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } else {
                endDateButton.setText(R.string.finish_date);
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
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker();
        builder.setTitleText("Select a date");

        MaterialDatePicker<Long> datePicker = builder.build();
        datePicker.show(getSupportFragmentManager(), datePicker.toString());

        datePicker.addOnPositiveButtonClickListener(selection -> {
            // Get the date from the MaterialDatePicker.
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selection);
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            String dateFormat = sdf.format(calendar.getTime());

            // Save the date to the database and update the view
            ContentValues movieValues = new ContentValues();

            if (view.getTag().equals("start_date")) {
                movieValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_START_DATE, dateFormat);

                Button button = findViewById(R.id.startDateButton);
                button.setText(dateFormat);

                // Convert the date to DateFormat.DEFAULT
                DateFormat dateFormatDefault = DateFormat.getDateInstance(DateFormat.DEFAULT);
                String formattedDate = dateFormatDefault.format(calendar.getTime());

                binding.movieStartDate.setText(getString(R.string.change_start_date_2) + formattedDate);
                startDate = calendar.getTime();

            } else {
                movieValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_FINISH_DATE, dateFormat);

                Button button = findViewById(R.id.endDateButton);
                button.setText(dateFormat);

                // Convert the date to DateFormat.DEFAULT
                DateFormat dateFormatDefault = DateFormat.getDateInstance(DateFormat.DEFAULT);
                String formattedDate = dateFormatDefault.format(calendar.getTime());

                binding.movieFinishDate.setText(getString(R.string.change_finish_date_2) + formattedDate);
                finishDate = calendar.getTime();
            }

            database = databaseHelper.getWritableDatabase();
            databaseHelper.onCreate(database);
            database.update(MovieDatabaseHelper.TABLE_MOVIES, movieValues, MovieDatabaseHelper.COLUMN_MOVIES_ID + "=" + movieId, null);
        });
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
                        binding.castRecyclerView.setVisibility(View.GONE);
                    } else {
                        JSONArray castArray = reader.getJSONArray("cast");

                        // Clear the castArrayList before adding new data
                        castArrayList.clear();

                        for (int i = 0; i < castArray.length(); i++) {
                            JSONObject castData = castArray.getJSONObject(i);
                            castArrayList.add(castData);
                        }
                        castAdapter = new CastBaseAdapter(castArrayList,
                                getApplicationContext());

                        binding.castRecyclerView.setAdapter(castAdapter);
                    }

                    // Add the crew to the crewView
                    if (reader.getJSONArray("crew").length() <= 0) {
                        // This movie has no available cast,
                        // do not show the cast related views.
                        TextView textView = mActivity.findViewById(R.id.crewTitle);
                        View view = mActivity.findViewById(R.id.thirdDivider);

                        textView.setVisibility(View.GONE);
                        view.setVisibility(View.GONE);
                        binding.crewRecyclerView.setVisibility(View.GONE);
                    } else {
                        JSONArray crewArray = reader.getJSONArray("crew");

                        // Clear the crewArrayList before adding new data
                        crewArrayList.clear();

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

                        binding.crewRecyclerView.setAdapter(crewAdapter);
                    }

                    mCastAndCrewLoaded = true;
                } catch (JSONException je) {
                    je.printStackTrace();
                }
            }

            hideEmptyRecyclerView(binding.castRecyclerView, binding.castTitle);
            hideEmptyRecyclerView(binding.castRecyclerView, binding.crewTitle);
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

                binding.movieRecyclerView.setAdapter(similarMovieAdapter);
                mSimilarMoviesLoaded = false;
            } catch (JSONException je) {
                je.printStackTrace();
            }
        }

        hideEmptyRecyclerView(binding.movieRecyclerView, binding.similarMovieTitle);
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

            OkHttpClient client = new OkHttpClient();

            String type = (isMovie) ? SectionsPagerAdapter.MOVIE : SectionsPagerAdapter.TV;
            String additionalEndpoint = (isMovie) ? "release_dates,external_ids,keywords" : "content_ratings,external_ids,keywords";
            String url;
            if (missingOverview) {
                url = "https://api.themoviedb.org/3/" + type +
                        "/" + movieId + "?append_to_response=" + additionalEndpoint;
            } else {
                url = "https://api.themoviedb.org/3/" + type +
                        "/" + movieId + "?append_to_response=" + additionalEndpoint
                        + getLanguageParameter(getApplicationContext());
            }

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("accept", "application/json")
                    .addHeader("Authorization", "Bearer " + api_read_access_token)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();
                handler.post(() -> onPostExecute(responseBody));
            } catch (IOException e) {
                e.printStackTrace();
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
                        binding.movieDescription.setText(movieData.getString("overview"));
                    } else {
                        setMovieData(movieData);
                    }
                    showKeywords(movieData);

                    if (movieData.has("number_of_seasons")) {
                        numSeason = movieData.getInt("number_of_seasons");
                    }
                    //seasons
                    if(movieData.has("seasons")) {
                        seasons = movieData.getJSONArray("seasons");
                    }

                    if (isMovie) {
                        JSONObject releaseDatesObject = movieData.getJSONObject( "release_dates" );

                        JSONArray resultsArray = releaseDatesObject.getJSONArray( "results" );

                        JSONObject defaultResult = null;

                        for (int i = 0; i < resultsArray.length(); i++) {
                            JSONObject result = resultsArray.getJSONObject( i );
                            String isoCountry = result.getString( "iso_3166_1" );

                            if (isoCountry.equals( Locale.getDefault().getCountry() )) {
                                processReleaseDates( result );
                                return;
                            }

                            if (isoCountry.equals( "US" )) {
                                defaultResult = result;
                            }
                        }

                        if (defaultResult != null) {
                            processReleaseDates( defaultResult );
                        }
                    } else {

                        JSONObject contentRatingsObject = movieData.getJSONObject( "content_ratings" );

                        JSONArray contentRrArray = contentRatingsObject.getJSONArray( "results" );

                        boolean isLocaleRatingFound = false;
                        String usRating = null;

                        for (int i = 0; i < contentRrArray.length(); i++) {
                            JSONObject result = contentRrArray.getJSONObject( i );
                            String isoCountry = result.getString( "iso_3166_1" );
                            String rating = result.getString( "rating" );

                            if (isoCountry.equalsIgnoreCase( Locale.getDefault().getCountry() )) {
                                handler.post( () -> binding.certification.setText( rating + " (" + isoCountry + ")" ) );
                                isLocaleRatingFound = true;
                                break;
                            }

                            if (isoCountry.equalsIgnoreCase( "US" )) {
                                usRating = rating;
                            }
                        }

                        if (!isLocaleRatingFound && usRating != null) {
                            String finalUsRating = usRating;
                            handler.post( () -> binding.certification.setText( finalUsRating + " (US)" ) );
                        }
                    }

                    JSONObject externalIdsObject = movieData.getJSONObject( "external_ids" );

                    String imdbId = externalIdsObject.getString("imdb_id");
                    //if imdbId is not available, set the text to "IMDB (not available)"
                    if (imdbId.equals("null")) {
                        binding.imdbLink.setText( R.string.imdb_not_available);
                    } else {
                        //if imdbId is available, set the text to "IMDB" and set an onClickListener to open the IMDB page
                        binding.imdbLink.setText("IMDB");
                        binding.imdbLink.setOnClickListener(v -> {
                            String url = "https://www.imdb.com/title/" + imdbId;
                            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                            CustomTabsIntent customTabsIntent = builder.build();
                            // Check if there is any package available to handle the CustomTabsIntent
                            if (customTabsIntent.intent.resolveActivity(getPackageManager()) != null) {
                                customTabsIntent.launchUrl(context, Uri.parse(url));
                            } else {
                                // If no package is available to handle the CustomTabsIntent, launch the URL in a web browser
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                if (browserIntent.resolveActivity(getPackageManager()) != null) {
                                    startActivity(browserIntent);
                                } else {
                                    Toast.makeText(context, R.string.no_browser_available, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                    mMovieDetailsLoaded = true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        private void showKeywords(JSONObject movieData) {
            try {
                JSONObject keywordsObject = movieData.getJSONObject("keywords");
                JSONArray keywordsArray = (isMovie) ? keywordsObject.getJSONArray("keywords") : keywordsObject.getJSONArray("results");

                FlexboxLayout keywordsLayout = findViewById(R.id.keywordsLayout);
                keywordsLayout.removeAllViews();

                for (int i = 0; i < keywordsArray.length(); i++) {
                    JSONObject keyword = keywordsArray.getJSONObject(i);
                    String keywordName = keyword.getString("name");

                    MaterialCardView cardView = new MaterialCardView(context);
                    cardView.setRadius(5f);
                    cardView.setCardBackgroundColor( Color.TRANSPARENT );
                    cardView.setStrokeWidth( 2 );
                    cardView.setContentPadding(5, 5, 5, 5);

                    TextView keywordTextView = new TextView(context);
                    keywordTextView.setText(keywordName);
                    keywordTextView.setPadding(8, 4, 8, 4);
                    keywordTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);

                    cardView.addView(keywordTextView);

                    FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(
                            FlexboxLayout.LayoutParams.WRAP_CONTENT,
                            FlexboxLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(8, 8, 8, 8);
                    cardView.setLayoutParams(params);

                    keywordsLayout.addView(cardView);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                FlexboxLayout keywordsLayout = findViewById(R.id.keywordsLayout);
                keywordsLayout.setVisibility( View.GONE );
            }
        }
    }

    private void processReleaseDates(JSONObject result) throws JSONException {
        JSONArray releaseDates = result.getJSONArray("release_dates");
        String date = "Unknown";
        String certification = "Unknown";
        for (int j = 0; j < releaseDates.length(); j++) {
            JSONObject releaseDate = releaseDates.getJSONObject(j);
            if (releaseDate.getString("type").equals("3")) { // Theatrical release
                date = releaseDate.getString("release_date");
                certification = releaseDate.getString("certification");
                break;
            } else if (releaseDate.getString("type").equals("4")) { // Digital release
                date = releaseDate.getString("release_date");
                certification = releaseDate.getString("certification");
            }
        }

        // Parse the date string and format it to only include the date
        if (isMovie) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                Date parsedDate = inputFormat.parse(date);
                if (parsedDate != null) {
                    DateFormat outputFormat = DateFormat.getDateInstance( DateFormat.DEFAULT, Locale.getDefault());
                    String formattedDate = outputFormat.format(parsedDate);
                    binding.releaseDate.setText(formattedDate);
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        if (!certification.isEmpty()) {
            binding.certification.setText( certification );
        } else
            binding.certification.setText( R.string.not_rated );
    }
}
