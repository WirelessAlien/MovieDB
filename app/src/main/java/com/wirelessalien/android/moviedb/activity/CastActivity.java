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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.DateFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter;
import com.wirelessalien.android.moviedb.databinding.ActivityCastBinding;
import com.wirelessalien.android.moviedb.helper.ConfigHelper;
import com.wirelessalien.android.moviedb.helper.PeopleDatabaseHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This class displays information about person objects.
 */
public class CastActivity extends BaseActivity {

    private final static String COLLAPSE_VIEW = "collapseView";
    private final static String CAST_MOVIE_VIEW_PREFERENCE = "CastActivity.castMovieView";
    private final static String CREW_MOVIE_VIEW_PREFERENCE = "CastActivity.crewMovieView";
    private final static String DYNAMIC_COLOR_DETAILS_ACTIVITY = "dynamic_color_details_activity";
    private Context context;
    private JSONObject actorObject;
    private JSONObject actorData;
    private ShowBaseAdapter castMovieAdapter;
    private ArrayList<JSONObject> castMovieArrayList;
    private ShowBaseAdapter crewMovieAdapter;
    private ArrayList<JSONObject> crewMovieArrayList;
    private ActivityCastBinding binding;
    private int actorId;
    private Target target;

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
    private int darkMutedColor;
    private int lightMutedColor;

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCastBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setNavigationDrawer();
        setBackButtons();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences( this );
        context = this;

        // Create a variable with the application context that can be used
        // when data is retrieved.
        mActivity = this;
        actorData = new JSONObject();

        // RecyclerView to display the shows that the person was part of the cast in.
        binding.castMovieRecyclerView.setHasFixedSize(true); // Improves performance (if size is static)

        // RecyclerView to display the movies that the person was part of the crew in.
        binding.crewMovieRecyclerView.setHasFixedSize(true);

        LinearLayoutManager castLinearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.castMovieRecyclerView.setLayoutManager(castLinearLayoutManager);

        // RecyclerView to display the shows that the person was part of the crew in.;
        binding.crewMovieRecyclerView.setHasFixedSize(true); // Improves performance (if size is static)

        LinearLayoutManager crewLinearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.crewMovieRecyclerView.setLayoutManager(crewLinearLayoutManager);

        // Make the views invisible if the user collapsed the view.
        collapseViewPreferences = getApplicationContext()
                .getSharedPreferences(COLLAPSE_VIEW, Context.MODE_PRIVATE);

        if (collapseViewPreferences.getBoolean(CAST_MOVIE_VIEW_PREFERENCE, false)) {
            binding.castMovieRecyclerView.setVisibility(View.GONE);
        }

        if (collapseViewPreferences.getBoolean(CREW_MOVIE_VIEW_PREFERENCE, false)) {
            binding.crewMovieRecyclerView.setVisibility(View.GONE);
        }

        // Get the actorObject from the intent that contains the necessary
        // data to display the right person and related RecyclerViews.
        // Send the JSONObject to setActorData() so all the data
        // will be displayed on the screen.
        Intent intent = getIntent();
        try {
            setActorData(new JSONObject(intent.getStringExtra(("actorObject"))));
            actorObject = new JSONObject(intent.getStringExtra("actorObject"));

            // Set the adapter with the (still) empty ArrayList.
            castMovieArrayList = new ArrayList<>();
            castMovieAdapter = new ShowBaseAdapter(castMovieArrayList, null, ShowBaseAdapter.MView.RECOMMENDATIONS, false);
            binding.castMovieRecyclerView.setAdapter(castMovieAdapter);

            // Set the adapter with the (still) empty ArrayList.
            crewMovieArrayList = new ArrayList<>();
            crewMovieAdapter = new ShowBaseAdapter(crewMovieArrayList, null, ShowBaseAdapter.MView.RECOMMENDATIONS, false);
            binding.crewMovieRecyclerView.setAdapter(crewMovieAdapter);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        checkNetwork();


        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        boolean isDarkTheme = uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES;

        int color;
        if (isDarkTheme) {
            color = Color.BLACK;
        } else {
            color = Color.WHITE;
        }

        // Set a listener to change the visibility when the TextView is clicked.
        setTitleClickListener(binding.castMovieTitle, binding.castMovieRecyclerView, CAST_MOVIE_VIEW_PREFERENCE);
        setTitleClickListener(binding.crewMovieTitle, binding.crewMovieRecyclerView, CREW_MOVIE_VIEW_PREFERENCE);

        if (preferences.getBoolean( DYNAMIC_COLOR_DETAILS_ACTIVITY, false )) {
            if (actorObject.has("profile_path") && binding.actorImage.getDrawable() == null) {
                String imageUrl;
                try {
                    imageUrl = "https://image.tmdb.org/t/p/h632" + actorObject.getString( "profile_path" );
                } catch (JSONException e) {
                    throw new RuntimeException( e );
                }
                // Set the loaded bitmap to your ImageView before generating the Palette
                target = new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        // Set the loaded bitmap to your ImageView before generating the Palette
                        binding.actorImage.setImageBitmap( bitmap );

                        Palette.from( bitmap ).generate( palette -> {

                            darkMutedColor = palette.getDarkMutedColor(palette.getMutedColor(Color.TRANSPARENT));
                            lightMutedColor = palette.getLightMutedColor(palette.getMutedColor(Color.TRANSPARENT));

                            GradientDrawable gradientDrawable;
                            if (isDarkTheme) {
                                gradientDrawable = new GradientDrawable(
                                        GradientDrawable.Orientation.TL_BR,
                                        new int[]{darkMutedColor, color}
                                );
                            } else {
                                gradientDrawable = new GradientDrawable(
                                        GradientDrawable.Orientation.TL_BR,
                                        new int[]{lightMutedColor, color}
                                );
                            }

                            binding.getRoot().setBackground( gradientDrawable );
                            binding.appBarLayout.setBackgroundColor( Color.TRANSPARENT );
                        } );

                        Animation animation = AnimationUtils.loadAnimation(
                                getApplicationContext(), R.anim.fade_in );
                        binding.actorImage.startAnimation( animation );
                    }

                    @Override
                    public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                        binding.actorImage.setBackgroundColor( ContextCompat.getColor( context, R.color.md_theme_surface ) );
                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {
                        binding.actorImage.setBackgroundColor( ContextCompat.getColor( context, R.color.md_theme_surface ) );
                    }
                };
                Picasso.get().load( imageUrl ).into( target );
            }
        } else {
            if (actorObject.has("profile_path")) {

                if (binding.actorImage.getDrawable() == null) {
                    try {
                        Picasso.get().load("https://image.tmdb.org/t/p/h632" +
                                        actorObject.getString("profile_path"))
                                .into(binding.actorImage);
                    } catch (JSONException e) {
                        throw new RuntimeException( e );
                    }
                }
            }
        }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.save_menu, menu);

        MenuItem saveItem = menu.findItem(R.id.action_save);

        PeopleDatabaseHelper dbHelper = new PeopleDatabaseHelper(context);

        if (dbHelper.personExists(actorId)) {
            saveItem.setIcon(R.drawable.ic_star);
        } else {
            saveItem.setIcon(R.drawable.ic_star_border);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_save) {
            PeopleDatabaseHelper dbHelper = new PeopleDatabaseHelper(context);

            int actorId = actorData.optInt("id");

            if (dbHelper.personExists(actorId)) {
                dbHelper.deleteById( actorId );
                item.setIcon(R.drawable.ic_star_border);
            } else {
                // If the person does not exist in the database, insert the person
                String name = actorData.optString("name");
                String birthday = actorData.optString("birthday");
                String deathday = actorData.optString("deathday");
                String biography = actorData.optString("biography");
                String placeOfBirth = actorData.optString("place_of_birth");
                double popularity = actorData.optDouble("popularity");
                String profilePath = actorData.optString("profile_path");
                String imdbId = actorData.optString("imdb_id");
                String homepage = actorData.optString("homepage");

                dbHelper.insert(actorId, name, birthday, deathday, biography, placeOfBirth, popularity, profilePath, imdbId, homepage);
                item.setIcon(R.drawable.ic_star);
            }
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * Sets the data gotten from the actorObject to the appropriate views.
     *
     * @param actorObject the JSONObject that it takes the data from.
     */
    private void setActorData(JSONObject actorObject) {

        // Check if actorObject values differ from the current values,
        // if they do, use the actorObject values (as they are probably
        // more recent).
        try {
            // Set the actorId
            if (actorObject.has("id")) {
                actorId = Integer.parseInt(actorObject.getString("id"));
            }

            // If the name is different in the new dataset, change it.
            if (actorObject.has("name") && !actorObject.getString("name").equals(binding.actorName.getText().toString())) {
                binding.actorName.setText(actorObject.getString("name"));
            }

            // If the place of birth is different in the new dataset, change it.
            if (actorObject.has("place_of_birth") && !actorObject.getString("place_of_birth").equals(binding.actorPlaceOfBirth
                    .getText().toString())) {
                binding.actorPlaceOfBirth.setText(getString(R.string.place_of_birth) + actorObject.getString("place_of_birth"));
            }

            // If the birthday is different in the new dataset, change it.
            DateFormat localFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault());
            if (actorObject.has("birthday") && !actorObject.getString("birthday").equals(binding.actorBirthday
                    .getText().toString())) {
                Date birthday = sdf.parse(actorObject.getString("birthday"));
                String birthdayString = localFormat.format(birthday);
                if (actorObject.isNull("deathday")) {
                    Date currentDate = new Date();
                    long currentAge = Math.floorDiv(TimeUnit.DAYS.convert(Math.abs(currentDate.getTime() - birthday.getTime()), TimeUnit.MILLISECONDS), 365);
                    binding.actorBirthday.setText(getString(R.string.birthday) + birthdayString + " (" + currentAge + " " + getString(R.string.years) + ")");
                } else {
                    binding.actorBirthday.setText(getString(R.string.birthday) + birthdayString);
                }
            }

            // If the deathday is different in the new dataset, change it.
            if (actorObject.has("deathday") && !actorObject.getString("deathday").equals(binding.actorDeathday
                    .getText().toString()) && !(actorObject.isNull("deathday"))) {
                Date birthday = sdf.parse(actorObject.getString("birthday"));
                Date deathday = sdf.parse(actorObject.getString("deathday"));
                String deathdayString = localFormat.format(deathday);
                long ageAtDeath = Math.floorDiv(TimeUnit.DAYS.convert(Math.abs(deathday.getTime() - birthday.getTime()), TimeUnit.MILLISECONDS), 365);
                binding.actorDeathday.setText(getString(R.string.deathday) + deathdayString + " (" + ageAtDeath + " " + getString(R.string.years) + ")");
            }

            // If the biography is different in the new dataset, change it.
            if (actorObject.has("biography") &&
                    !actorObject.getString("biography").equals(
                            binding.actorBiography.getText().toString())) {
                binding.actorBiography.setText(actorObject.getString("biography"));
            }
            if (actorObject.getString("biography").equals("")) {
                new ActorDetailsThread("true").start();
            }
        } catch (JSONException | ParseException e) {
            e.printStackTrace();
        }
    }

    /**
     * Thread that retrieves the shows that the person is credited
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
                        binding.castMovieRecyclerView.setVisibility(View.GONE);
                    } else {
                        JSONArray castMovieArray = reader.getJSONArray("cast");
                        for (int i = 0; i < castMovieArray.length(); i++) {
                            JSONObject actorMovies = castMovieArray.getJSONObject(i);
                            castMovieArrayList.add(actorMovies);
                        }

                        // Set a new adapter so the RecyclerView
                        // shows the new items.
                        castMovieAdapter = new ShowBaseAdapter(castMovieArrayList, null, ShowBaseAdapter.MView.RECOMMENDATIONS, false);
                        binding.castMovieRecyclerView.setAdapter(castMovieAdapter);
                    }

                    // Add the crew roles to the crewMovieView
                    if (reader.getJSONArray("crew").length() <= 0) {
                        // This person has no roles as crew, do not show the
                        // crew related views.
                        TextView textView = mActivity.findViewById(R.id.crewMovieTitle);
                        View view = mActivity.findViewById(R.id.thirdDivider);

                        textView.setVisibility(View.GONE);
                        view.setVisibility(View.GONE);
                        binding.crewMovieRecyclerView.setVisibility(View.GONE);
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
                        crewMovieAdapter = new ShowBaseAdapter(crewMovieArrayList, null, ShowBaseAdapter.MView.RECOMMENDATIONS, false);
                        binding.crewMovieRecyclerView.setAdapter(crewMovieAdapter);
                        mActorMoviesLoaded = true;
                    }
                } catch (JSONException je) {
                    je.printStackTrace();
                }
            }
        }
    }

    /**
     * Thread that retrieves the details of the person from the API.
     */
    private class ActorDetailsThread extends Thread {

        private final String API_KEY = ConfigHelper.getConfigValue(getApplicationContext(), "api_key");
        private final Handler handler = new Handler(Looper.getMainLooper());

        // Constructor accepting a parameter
        public ActorDetailsThread(String param) {
        }
        public ActorDetailsThread() {
        }

        @Override
        public void run() {
            OkHttpClient client = new OkHttpClient();

            String baseUrl = "https://api.themoviedb.org/3/person/" + actorId + "?api_key=" + API_KEY;
            String urlWithLanguage = baseUrl + getLanguageParameter(getApplicationContext());

            try {
                // First request with language parameter
                JSONObject actorData = fetchActorDetails(client, urlWithLanguage);

                // Check if biography is empty
                if (actorData.getString("biography").isEmpty()) {
                    // Second request without language parameter
                    actorData = fetchActorDetails(client, baseUrl);
                }

                JSONObject finalActorData = actorData;
                handler.post(() -> onPostExecute(finalActorData));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private JSONObject fetchActorDetails(OkHttpClient client, String url) throws IOException, JSONException {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                String responseBody = response.body().string();
                return new JSONObject(responseBody);
            }
        }

        private void onPostExecute(JSONObject actorData) {
            if (actorData != null) {
                if (actorData.isNull("deathday")) {
                    binding.actorDeathday.setVisibility(View.GONE);
                }
                setActorData(actorData);
            }
        }
    }
}
