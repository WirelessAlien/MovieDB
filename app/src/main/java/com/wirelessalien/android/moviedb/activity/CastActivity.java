/*
 * Copyright (c) 2018.
 *
 * This file is part of MovieDB.
 *
 * MovieDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MovieDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MovieDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.helper.ConfigHelper;
import com.wirelessalien.android.moviedb.view.NotifyingScrollView;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.SimilarMovieBaseAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

/**
 * This class displays information about person objects.
 */
public class CastActivity extends BaseActivity {

    private final static String COLLAPSE_VIEW = "collapseView";
    private final static String CAST_MOVIE_VIEW_PREFERENCE = "CastActivity.castMovieView";
    private final static String CREW_MOVIE_VIEW_PREFERENCE = "CastActivity.crewMovieView";
    private RecyclerView castMovieView;
    private SimilarMovieBaseAdapter castMovieAdapter;
    private ArrayList<JSONObject> castMovieArrayList;
    private RecyclerView crewMovieView;
    private SimilarMovieBaseAdapter crewMovieAdapter;
    private ArrayList<JSONObject> crewMovieArrayList;
    private MaterialToolbar toolbar;
    // Change transparency when scrolling.
    private final NotifyingScrollView.OnScrollChangedListener
            mOnScrollChangedListener = new NotifyingScrollView
            .OnScrollChangedListener() {
        public void onScrollChanged(int t) {
            final int headerHeight = findViewById( R.id.actorImage).getHeight() -
                    toolbar.getHeight();
            final float ratio = (float) Math.min(Math.max(t, 0), headerHeight) / headerHeight;
            final int newAlpha = (int) (ratio * 255);
            toolbar.setBackgroundColor( Color.argb(newAlpha, 0, 0, 0));


        }
    };

    private int actorId;
    private TextView actorBiography;

	/*
    * This class provides an overview for actors.
	* The data is send via an Intent that comes from the DetailActivity
	* DetailActivity in his turn gets the data from the CastBaseAdapter
	*/
    private Activity mActivity;
    private SharedPreferences collapseViewPreferences;

    // Indicate whether the network items have loaded.
    private boolean mActorMoviesLoaded = false;
    private boolean mActorDetailsLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the proper layout.
        setContentView(R.layout.activity_cast);
        setNavigationDrawer();
        setBackButtons();

        toolbar = findViewById(R.id.toolbar);

        // Make the transparency dependent on how far the user scrolled down.
        NotifyingScrollView notifyingScrollView = findViewById(R.id.castScrollView);
        notifyingScrollView.setOnScrollChangedListener(mOnScrollChangedListener);

        // Create a variable with the application context that can be used
        // when data is retrieved.
        mActivity = this;

        // RecyclerView to display the shows that the person was part of the cast in.
        castMovieView = findViewById(R.id.castMovieRecyclerView);
        castMovieView.setHasFixedSize(true); // Improves performance (if size is static)

        // RecyclerView to display the movies that the person was part of the crew in.
        crewMovieView = findViewById(R.id.crewMovieRecyclerView);
        crewMovieView.setHasFixedSize(true);

        LinearLayoutManager castLinearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        castMovieView.setLayoutManager(castLinearLayoutManager);

        // RecyclerView to display the shows that the person was part of the crew in.
        crewMovieView = findViewById(R.id.crewMovieRecyclerView);
        crewMovieView.setHasFixedSize(true); // Improves performance (if size is static)

        LinearLayoutManager crewLinearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        crewMovieView.setLayoutManager(crewLinearLayoutManager);

        // Make the views invisible if the user collapsed the view.
        collapseViewPreferences = getApplicationContext()
                .getSharedPreferences(COLLAPSE_VIEW, Context.MODE_PRIVATE);

        if (collapseViewPreferences.getBoolean(CAST_MOVIE_VIEW_PREFERENCE, false)) {
            castMovieView.setVisibility(View.GONE);
        }

        if (collapseViewPreferences.getBoolean(CREW_MOVIE_VIEW_PREFERENCE, false)) {
            crewMovieView.setVisibility(View.GONE);
        }

        // Get the actorObject from the intent that contains the necessary
        // data to display the right person and related RecyclerViews.
        // Send the JSONObject to setActorData() so all the data
        // will be displayed on the screen.
        Intent intent = getIntent();
        try {
            setActorData(new JSONObject(intent.getStringExtra(("actorObject"))));

            // Set the adapter with the (still) empty ArrayList.
            castMovieArrayList = new ArrayList<>();
            castMovieAdapter = new SimilarMovieBaseAdapter(castMovieArrayList,
                    getApplicationContext());
            castMovieView.setAdapter(castMovieAdapter);

            // Set the adapter with the (still) empty ArrayList.
            crewMovieArrayList = new ArrayList<>();
            crewMovieAdapter = new SimilarMovieBaseAdapter(crewMovieArrayList,
                    getApplicationContext());
            crewMovieView.setAdapter(crewMovieAdapter);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        checkNetwork();

        // Check for clicks to collapse/expand the views.
        TextView castMovieTitle = findViewById(R.id.castMovieTitle);
        TextView crewMovieTitle = findViewById(R.id.crewMovieTitle);

        // Set a listener to change the visibility when the TextView is clicked.
        setTitleClickListener(castMovieTitle, castMovieView, CAST_MOVIE_VIEW_PREFERENCE);
        setTitleClickListener(crewMovieTitle, crewMovieView, CREW_MOVIE_VIEW_PREFERENCE);
    }

    @Override
    void doNetworkWork() {
        // Get the shows the person was a part of.
        if (!mActorMoviesLoaded) {
            new ActorMovieList().start();
        }

        // Load person details
        if (!mActorDetailsLoaded) {
            new ActorDetailsThread().start();
        }
    }

    /**
     * Sets an OnClickListener on the title above the RecyclerView.
     * collapseViewEditor needs to be initialised.
     *
     * @param title      the title that the OnClickListener will be set on.
     * @param view       the view that will be expanded/collapsed.
     * @param preference the preferences that needs to be edited to remember the choice.
     */
    private void setTitleClickListener(final TextView title, final RecyclerView view, final String preference) {
        title.setOnClickListener( v -> {
            SharedPreferences.Editor collapseViewEditor = collapseViewPreferences.edit();

            if (collapseViewPreferences.getBoolean(preference, false)) {
                // The view needs to expand.
                view.setVisibility(View.VISIBLE);

                // The preference needs to change.
                collapseViewEditor.putBoolean(preference, false);
            } else {
                // The view needs to collapse.
                view.setVisibility(View.GONE);

                // The preference needs to change.
                collapseViewEditor.putBoolean(preference, true);
            }
            // Set the changes.
            collapseViewEditor.apply();
        } );
    }

    /**
     * Sets the data gotten from the actorObject to the appropriate views.
     *
     * @param actorObject the JSONObject that it takes the data from.
     */
    private void setActorData(JSONObject actorObject) {

        // Get the views from the layout
        ImageView actorImage = findViewById(R.id.actorImage);
        TextView actorName = findViewById(R.id.actorName);
        ImageView actorIcon = findViewById(R.id.actorIcon);
        TextView actorPlaceOfBirth = findViewById(R.id.actorPlaceOfBirth);
        TextView actorBirthday = findViewById(R.id.actorBirthday);
        actorBiography = findViewById(R.id.actorBiography);

        // Check if actorObject values differ from the current values,
        // if they do, use the actorObject values (as they are probably
        // more recent).
        try {
            // Set the actorId
            if (actorObject.has("id")) {
                actorId = Integer.parseInt(actorObject.getString("id"));
            }

            // Due to the difficulty of comparing images (or rather,
            // this can be a slow process) the id of the image is
            // saved as class variable for easy comparison.
            if (actorObject.has("profile_path")) {

                // Load the images into the appropriate view.
                if (actorImage.getDrawable() == null) {
                    Picasso.get().load("https://image.tmdb.org/t/p/h632" +
                            actorObject.getString("profile_path"))
                            .into(actorImage);
                }

                if (actorIcon.getDrawable() == null) {
                    Picasso.get().load("https://image.tmdb.org/t/p/w154" +
                            actorObject.getString("profile_path"))
                            .into(actorIcon);
                }

                // Set the old imageId to the new one.
                String actorImageId = actorObject.getString( "profile_path" );
            }

            // If the name is different in the new dataset, change it.
            if (actorObject.has("name") && !actorObject.getString("name").equals(actorName.getText().toString())) {
                actorName.setText(actorObject.getString("name"));
            }

            // If the place of birth is different in the new dataset, change it.
            if (actorObject.has("place_of_birth") && !actorObject.getString("place_of_birth").equals(actorPlaceOfBirth
                    .getText().toString())) {
                actorPlaceOfBirth.setText(getString(R.string.place_of_birth) + actorObject.getString("place_of_birth"));
            }

            // If the birthday is different in the new dataset, change it.
            if (actorObject.has("birthday") && !actorObject.getString("birthday").equals(actorBirthday
                    .getText().toString())) {
                actorBirthday.setText(getString(R.string.birthday) + actorObject.getString("birthday"));
            }

            // If the biography is different in the new dataset, change it.
            if (actorObject.has("biography") &&
                    !actorObject.getString("biography").equals(
                            actorBiography.getText().toString())) {
                actorBiography.setText(actorObject.getString("biography"));
            }
            if (actorObject.getString("biography").equals("")) {
                new ActorDetailsThread("true").start();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * AsyncTask that retrieves the shows that the person is credited
     * for from the API.
     */
    private class ActorMovieList extends Thread {

        private final String API_KEY = ConfigHelper.getConfigValue(getApplicationContext(), "api_key");
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void run() {
            String response = doInBackground();
            handler.post(() -> onPostExecute(response));
        }

        private String doInBackground() {
            String line;
            StringBuilder stringBuilder = new StringBuilder();

            // Load the webpage with the person's shows.
            try {
                URL url = new URL("https://api.themoviedb.org/3/person/" +
                        actorId + "/combined_credits?api_key=" + API_KEY + getLanguageParameter(getApplicationContext()));
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
                // Break the JSON dataset down and add the JSONObjects to the array.
                try {
                    JSONObject reader = new JSONObject(response);

                    // Add the cast roles to the movieView
                    if (reader.getJSONArray("cast").length() <= 0) {
                        // This person has no roles as cast, do not show the
                        // cast related views.
                        TextView textView = mActivity.findViewById(R.id.castMovieTitle);
                        View view = mActivity.findViewById(R.id.secondDivider);

                        textView.setVisibility(View.GONE);
                        view.setVisibility(View.GONE);
                        castMovieView.setVisibility(View.GONE);
                    } else {
                        JSONArray castMovieArray = reader.getJSONArray("cast");
                        for (int i = 0; i < castMovieArray.length(); i++) {
                            JSONObject actorMovies = castMovieArray.getJSONObject(i);
                            castMovieArrayList.add(actorMovies);
                        }

                        // Set a new adapter so the RecyclerView
                        // shows the new items.
                        castMovieAdapter = new SimilarMovieBaseAdapter(
                                castMovieArrayList, getApplicationContext());
                        castMovieView.setAdapter(castMovieAdapter);
                    }

                    // Add the crew roles to the crewMovieView
                    if (reader.getJSONArray("crew").length() <= 0) {
                        // This person has no roles as crew, do not show the
                        // crew related views.
                        TextView textView = mActivity.findViewById(R.id.crewMovieTitle);
                        View view = mActivity.findViewById(R.id.thirdDivider);

                        textView.setVisibility(View.GONE);
                        view.setVisibility(View.GONE);
                        crewMovieView.setVisibility(View.GONE);
                    } else {
                        JSONArray crewMovieArray = reader.getJSONArray("crew");
                        for (int i = 0; i < crewMovieArray.length(); i++) {
                            JSONObject crewMovies = crewMovieArray.getJSONObject(i);

                            // TODO: Build a lightweight duplicate checker
                            // (any heavy ones will cause the application to crash).

                            crewMovieArrayList.add(crewMovies);
                        }

                        // Set a new adapter so the RecyclerView
                        // shows the new items.
                        crewMovieAdapter = new SimilarMovieBaseAdapter(
                                crewMovieArrayList, getApplicationContext());
                        crewMovieView.setAdapter(crewMovieAdapter);
                        mActorMoviesLoaded = true;
                    }
                } catch (JSONException je) {
                    je.printStackTrace();
                }
            }
        }
    }

    /**
     * AsyncTask that retrieves the details of the person from the API.
     */
    private class ActorDetailsThread extends Thread {

        private final String API_KEY = ConfigHelper.getConfigValue(getApplicationContext(), "api_key");
        private boolean missingOverview;
        private final Handler handler = new Handler( Looper.getMainLooper());

        private final String param;

        // Constructor accepting a parameter
        public ActorDetailsThread(String param) {
            this.param = param;
        }
        public ActorDetailsThread() {
            this.param = null;
        }
        @Override
        public void run() {
            String response = doInBackground();
            handler.post(() -> onPostExecute(response));
        }

        private String doInBackground() {
            String line;
            StringBuilder stringBuilder = new StringBuilder();

            // Load the webpage with the person's details.
            try {
                URL url;
                if (missingOverview) {
                    url = new URL("https://api.themoviedb.org/3/person/" +
                            actorId + "?api_key=" + API_KEY);
                } else {
                    url = new URL("https://api.themoviedb.org/3/person/" +
                            actorId + "?api_key=" + API_KEY + getLanguageParameter(getApplicationContext()));
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
                // Send all the actor data to setActorData.
                try {
                    JSONObject actorData = new JSONObject(response);
                    if (missingOverview && actorData.has("biography") && !actorData.getString("biography").equals("")) {
                        actorBiography.setText(actorData.getString("biography"));
                    } else if (missingOverview && (actorData.optString("biography") == null
                            || (actorData.has("biography") && actorData.get("biography").equals("")))) {
                        actorBiography.setText(getString(R.string.no_biography)
                                + actorData.getString("name") + ".");
                    } else {
                        setActorData(actorData);
                    }
                    mActorDetailsLoaded = true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
