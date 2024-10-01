/*
 *     This file is part of "ShowCase" formerly Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     ShowCase is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ShowCase is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with "ShowCase".  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.wirelessalien.android.moviedb.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/**
 * This class handles all the different sorting and filtering on the shows.
 */

public class FilterActivity extends AppCompatActivity {

    public static final String FILTER_PREFERENCES = "filter_preferences";
    public static final String FILTER_SORT = "filter_sort";
    public static final String FILTER_CATEGORIES = "filter_categories";
    public static final String FILTER_SHOW_MOVIE = "filter_show_movie";
    public static final String FILTER_DATES = "filter_dates";
    public static final String FILTER_START_DATE = "filter_start_date";
    public static final String FILTER_END_DATE = "filter_end_date";
    public static final String FILTER_WITH_GENRES = "filter_with_genres";
    public static final String FILTER_WITHOUT_GENRES = "filter_without_genres";
    public static final String FILTER_WITH_KEYWORDS = "filter_with_keywords";
    public static final String FILTER_WITHOUT_KEYWORDS = "filter_without_keywords";
    private ArrayList<Integer> withGenres = new ArrayList<>();
    private ArrayList<Integer> withoutGenres = new ArrayList<>();

    /**
     * Uses Integer.parseInt on all characters in the string (except for splitArg).
     *
     * @param array    the string with integers.
     * @param splitArg the character that separates the integers.
     * @return an ArrayList with integers.
     */
    public static ArrayList<Integer> convertStringToIntegerArrayList(String array, String splitArg) {
        ArrayList<Integer> idArrayList = new ArrayList<>();
        if (array != null) {
            String arrayList = array.substring(1, array.length() - 1);
            if (!arrayList.equals("")) {
                String[] ids = arrayList.split(splitArg);
                for (String id : ids) {
                    idArrayList.add(Integer.parseInt(id));
                }
            }
        }

        // An empty ArrayList will be returned if the String is null.
        return idArrayList;
    }

    /**
     * Splits the string into smaller strings and stores them in an ArrayList based on the splitArg.
     *
     * @param array    the string containing the substrings that needs to be split.
     * @param splitArg the character that separates the substrings.
     * @return an ArrayList with strings.
     */
    public static ArrayList<String> convertStringToArrayList(String array, String splitArg) {
        ArrayList<String> arrayList = new ArrayList<>();
        if (array != null) {
            array = array.substring(1, array.length() - 1);
            if (!array.equals("")) {
                String[] items = array.split(splitArg);
                Collections.addAll(arrayList, items);
            }
        }

        // null will be returned if the string did not contain any items.
        if (arrayList.size() == 0) {
            return null;
        }

        return arrayList;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter);

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
        Intent intent = getIntent();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveFilterPreferences();
                setResult(RESULT_OK);
                finish();
            }
        });

        // Show different types of sorting depending on the activity that the user came from.
        if (intent.getBooleanExtra("categories", false)) {
            LinearLayout categoriesLayout = findViewById(R.id.categoriesLayout);
            categoriesLayout.setVisibility(View.VISIBLE);
        }

        if (!intent.getBooleanExtra("most_popular", true)) {
            RadioButton radioButton = findViewById( R.id.most_popular);
            radioButton.setText(getString(R.string.default_sort));
        }

        if (intent.getBooleanExtra("startDate", false)) {
            RadioButton radioButton = findViewById( R.id.startDate);
            radioButton.setVisibility(View.VISIBLE);
        }

        if (intent.getBooleanExtra("finishDate", false)) {
            RadioButton radioButton = findViewById( R.id.finishDate);
            radioButton.setVisibility(View.VISIBLE);
        }

        if (!intent.getBooleanExtra("dates", true)) {
            RelativeLayout dateViewLayout = findViewById(R.id.dateViewLayout);
            dateViewLayout.setVisibility(View.GONE);
        }


        if (!intent.getBooleanExtra("keywords", true)) {
            View separator = findViewById(R.id.genreViewSeparator);
            RelativeLayout advancedTitle = findViewById(R.id.advancedTitle);

            separator.setVisibility(View.GONE);
            advancedTitle.setVisibility(View.GONE);
        }

        SharedPreferences sharedPreferences = getSharedPreferences("GenreList", Context.MODE_PRIVATE);
        String mode = intent.getStringExtra("mode");

        try {
            JSONArray genreArray = null;
            if (mode != null) {
                String genreList = sharedPreferences.getString(mode + "GenreJSONArrayList", null);
                if (genreList != null) {
                    genreArray = new JSONArray(genreList);
                }
            } else {
                String movieGenreList = sharedPreferences.getString("movieGenreJSONArrayList", null);
                String tvGenreList = sharedPreferences.getString("tvGenreJSONArrayList", null);
                if (movieGenreList != null && tvGenreList != null) {
                    genreArray = concatJSONArray(
                            new JSONArray(movieGenreList),
                            new JSONArray(tvGenreList));
                }
            }

            if (genreArray != null) {
                ChipGroup chipGroup = findViewById(R.id.genreButtons);
                for (int i = 0; i < genreArray.length(); i++) {
                    JSONObject genre = genreArray.getJSONObject(i);

                    Chip chip = new Chip(this);
                    chip.setText(genre.getString("name"));
                    chip.setId(Integer.parseInt(genre.getString("id")));
                    chip.setCheckable(true);
                    chip.setOnClickListener(v -> {
                        Chip genreChip = (Chip) v;
                        int chipId = genreChip.getId();

                        if (withGenres.contains(chipId)) {
                            withGenres.remove((Integer) chipId);
                            withoutGenres.add(chipId);

                            genreChip.setChipBackgroundColorResource(R.color.colorRed);
                            genreChip.setCloseIconResource(R.drawable.ic_close);
                        } else if (withoutGenres.contains(chipId)) {
                            withoutGenres.remove((Integer) chipId);

                            genreChip.setChipBackgroundColorResource(android.R.color.transparent);
                            genreChip.setCloseIconVisible(false);
                        } else {
                            withGenres.add(chipId);

                            genreChip.setChipBackgroundColorResource(R.color.md_theme_primary);
                            genreChip.setCloseIconResource(R.drawable.ic_check);
                        }
                    });
                    chipGroup.addView(chip);
                }
            }
        } catch (JSONException je) {
            je.printStackTrace();
        }

        // Add back button to the activity.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        // Retrieve preferences from the last time.
        retrieveFilterPreferences();
    }

    /**
     * Concatenates multiple JSONArrays.
     * @param arrays the arrays to concatenate.
     * @return the concatenated array.
     * @throws JSONException if there is a problem retrieving an item from the JSONArray.
     */
    private JSONArray concatJSONArray(JSONArray... arrays) throws JSONException {
        JSONArray concatenatedArray = new JSONArray();
        boolean firstArray = false;
        for (JSONArray array : arrays) {
            for (int i = 0; i < array.length(); i++) {
                // Remove duplicates (assuming the arrays themselves contain no duplicates).
                if (!firstArray && !isDuplicate(concatenatedArray, array.get(i))) {
                    concatenatedArray.put(array.get(i));
                }
            }
            firstArray = true;
        }

        return concatenatedArray;
    }

    /**
     * Checks if the item is already in the array.
     * @param array the array to be checked.
     * @param item the item that is a potential duplicate.
     * @return whether or not the item is already in the array.
     * @throws JSONException if there is a problem retrieving an item from the JSONArray.
     */
    private boolean isDuplicate(JSONArray array, Object item) throws JSONException {
        if (item instanceof String) {
            for(int i = 0; i < array.length(); i++) {
                if (array.getString(i).equals(item)) {
                    return true;
                }
            }
        } else {
            for (int i = 0; i < array.length(); i++) {
                if (array.get(i) == item) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            saveFilterPreferences();
            setResult( RESULT_OK );
            this.finish();
            return true;
        }
        return super.onOptionsItemSelected( item );
    }

    /**
     * Replace all current filter preferences with the new ones (which can be equal to the old value).
     */
    private void saveFilterPreferences() {
        SharedPreferences sharedPreferences
                = getSharedPreferences(FILTER_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = sharedPreferences.edit();

        // Save all settings
        prefsEditor.putString(FILTER_WITH_GENRES, withGenres.toString());

        prefsEditor.putString(FILTER_WITHOUT_GENRES, withoutGenres.toString());

        prefsEditor.putString(FILTER_SORT, getSelectedRadioButton(
                findViewById(R.id.sortSelection)
        ));

        prefsEditor.putString(FILTER_CATEGORIES, getSelectedCheckBoxes(
                findViewById(R.id.watchingCheckBox),
                findViewById(R.id.watchedCheckBox),
                findViewById(R.id.plannedToWatchCheckBox),
                findViewById(R.id.onHoldCheckBox),
                findViewById(R.id.droppedCheckBox)).toString());

        prefsEditor.putString( FILTER_SHOW_MOVIE, getSelectedCheckBoxes(
                findViewById( R.id.movieCheckBox),
                findViewById( R.id.tvCheckBox)).toString());

        prefsEditor.putString(FILTER_DATES, getSelectedCheckBox(
                findViewById(R.id.theaterCheckBox),
                findViewById(R.id.twoDatesCheckBox)
        ));

        EditText withKeywords = findViewById(R.id.withKeywords);
        EditText withoutKeywords = findViewById(R.id.withoutKeywords);

        prefsEditor.putString(FILTER_WITH_KEYWORDS, withKeywords.getText().toString());
        prefsEditor.putString(FILTER_WITHOUT_KEYWORDS, withoutKeywords.getText().toString());


        prefsEditor.apply();
    }

    /**
     * Only one of the checkboxes may be selected.
     *
     * @param checkBoxes the checkboxes that needs to be iterated through.
     * @return the tag of the checkbox that is checked.
     */
    private String getSelectedCheckBox(CheckBox... checkBoxes) {
        for (CheckBox checkBox : checkBoxes) {
            if (checkBox.isChecked()) {
                return checkBox.getTag().toString();
            }
        }

        return null;
    }

    /**
     * Checks which CheckBoxes are selected.
     *
     * @param checkBoxes the CheckBoxes that need to be checked.
     * @return an ArrayList with the tags of all selected CheckBoxes.
     */
    private ArrayList<String> getSelectedCheckBoxes(CheckBox... checkBoxes) {
        ArrayList<String> checkBoxArray = new ArrayList<>();
        for (CheckBox checkBox : checkBoxes) {
            if (checkBox.isChecked()) {
                checkBoxArray.add(checkBox.getTag().toString());
            }
        }

        return checkBoxArray;
    }

    /**
     * Checks which RadioButton is selected.
     *
     * @param radioGroup the group containing all the RadioButtons.
     * @return the RadioButton that is selected.
     */
    private String getSelectedRadioButton(RadioGroup radioGroup) {
        if (radioGroup.getCheckedRadioButtonId() != -1) {
            int id = radioGroup.getCheckedRadioButtonId();
            RadioButton radioButton = radioGroup.findViewById(id);
            return radioButton.getTag().toString();
        } else {
            return null;
        }
    }

    /**
     * Check the RadioButton that has a certain tag.
     *
     * @param tag        the tag that the RadioButton has (if any).
     * @param radioGroup the group of RadioButtons with the designated RadioButton.
     */
    private void selectRadioButtonByTag(String tag, RadioGroup radioGroup) {
        int count = radioGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            View view = radioGroup.getChildAt(i);
            if (view instanceof RadioButton radioButton) {
                if (radioButton.getTag().toString().equals(tag)) {
                    radioGroup.check(radioButton.getId());
                }
            }
        }
    }

    /**
     * Check the CheckBoxes that have certain tags.
     *
     * @param arrayList the list of different tags.
     * @param parent    the parent containing all the designated CheckBoxes.
     */
    private void selectCheckBoxByTag(ArrayList<String> arrayList, ViewGroup parent) {
        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            CheckBox checkBox = (CheckBox) parent.getChildAt(i);
            if (arrayList.contains(checkBox.getTag().toString())) {
                checkBox.setChecked(true);
            }
        }
    }

    /**
     * Retrieve all preferences needed by the filter
     * and load them in their respective UI elements
     */
    @SuppressLint("SetTextI18n")
    private void retrieveFilterPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences(FILTER_PREFERENCES, Context.MODE_PRIVATE);

        withGenres = convertStringToIntegerArrayList(sharedPreferences.getString(FILTER_WITH_GENRES, null), ", ");
        withoutGenres = convertStringToIntegerArrayList(sharedPreferences.getString(FILTER_WITHOUT_GENRES, null), ", ");

        // Select the criterion to sort by.
        String sortTag = sharedPreferences.getString(FILTER_SORT, null);
        if (sortTag != null) {
            selectRadioButtonByTag(sortTag, findViewById(R.id.sortSelection));
        } else {
            // Select the default button.
            selectRadioButtonByTag("most_popular", findViewById(R.id.sortSelection));
        }

        // Select the categories to filter (if any) the local database on.
        String categoriesTags = sharedPreferences.getString(FILTER_CATEGORIES, null);
        if (categoriesTags != null && !categoriesTags.equals("[]")) {
            ArrayList<String> categoryTagArray = convertStringToArrayList(categoriesTags, ", ");
            selectCheckBoxByTag(categoryTagArray, findViewById(R.id.categoryCheckBoxesLayout));
        }

        String showMovieTag = sharedPreferences.getString(FILTER_SHOW_MOVIE, "");
        ArrayList<String> showMovieTagList = new ArrayList<>();

        if (showMovieTag.isEmpty() || showMovieTag.equals("[]") || showMovieTag.contains("movie") && showMovieTag.contains("tv")) {
            showMovieTagList.add("movie");
            showMovieTagList.add("tv");
        } else {
            showMovieTagList = convertStringToArrayList(showMovieTag, ", ");
        }
        selectCheckBoxByTag(showMovieTagList, findViewById(R.id.mediaCheckBoxesLayout));

        // Select the dates that the shows were filtered on last time (if any).
        String dateTag = sharedPreferences.getString(FILTER_DATES, null);
        if (dateTag != null) {
            CheckBox checkBox;
            if (dateTag.equals("in_theater")) {
                checkBox = findViewById(R.id.theaterCheckBox);
            } else {
                checkBox = findViewById(R.id.twoDatesCheckBox);
            }
            checkBox.setChecked(true);
        }

        // Retrieve the starting date (if any).
        String startDate;
        if ((startDate = sharedPreferences.getString(FILTER_START_DATE, null)) != null) {
            Button button = findViewById(R.id.startDateButton);
            // TODO: Set the date in the user locale.
            button.setText(startDate);
        }

        // Retrieve the end date (if any).
        String endDate;
        Button button = findViewById(R.id.endDateButton);
        if ((endDate = sharedPreferences.getString(FILTER_END_DATE, null)) != null) {
            // TODO: Set the date in the user locale.
            button.setText(endDate);
        } else {
            // Set the date to 2000.
            // TODO: Set the date in the user locale.
            button.setText("");
        }

        // If any genres were included last time, select them as included in advance.
        if (withGenres != null) {
            for (int i = 0; i < withGenres.size(); i++) {
                int id = withGenres.get(i);

                Chip genreChip = findViewById(id);
                if (genreChip != null) {
                    Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_check, null);
                    genreChip.setChipIcon(drawable);
                }
            }
        }

        // If any genres were excluded last time, select them as excluded in advance.
        if (withoutGenres != null) {
            for (int i = 0; i < withoutGenres.size(); i++) {
                int id = withoutGenres.get(i);

                Chip genreChip = findViewById(id);
                if (genreChip != null) {
                    Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_close, null);
                    genreChip.setChipIcon(drawable);
                }
            }
        }

        // Load the included keywords in the EditText (if any).
        String withKeywords;
        if ((withKeywords = sharedPreferences.getString(FILTER_WITH_KEYWORDS, null)) != null) {
            EditText withKeywordsView = findViewById(R.id.withKeywords);
            withKeywordsView.setText(withKeywords);
        }

        // Load the excluded keywords in the EditText (if any).
        String withoutKeywords;
        if ((withoutKeywords = sharedPreferences.getString(FILTER_WITHOUT_KEYWORDS, null)) != null) {
            EditText withoutKeywordsView = findViewById(R.id.withoutKeywords);
            withoutKeywordsView.setText(withoutKeywords);
        }
    }

    /**
     * Shows or hides the selection of two dates.
     *
     * @param view the CheckBox view that was selected.
     */
    public void checkBoxSelected(View view) {
        CheckBox theaterCheckBox = findViewById(R.id.theaterCheckBox);
        CheckBox twoDatesCheckBox = findViewById(R.id.twoDatesCheckBox);

        TableLayout tableLayout = findViewById(R.id.dateDetailsLayout);
        if (view.getId() == twoDatesCheckBox.getId()) {
            if (twoDatesCheckBox.isChecked()) {
                // If the other CheckBox is selected, deselect it.
                if (theaterCheckBox.isChecked()) {
                    theaterCheckBox.setChecked(false);
                }

                // Make the Views to choose the date visible.
                tableLayout.setVisibility(View.VISIBLE);
            } else {
                // Make the Views to choose the date invisible.
                tableLayout.setVisibility(View.GONE);
            }
        } else {
            // If the other CheckBox is selected, deselect it.
            if (twoDatesCheckBox.isChecked()) {
                twoDatesCheckBox.setChecked(false);

                // Also make the Views to choose the date invisible.
                tableLayout.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Creates a DatePicker on button press.
     *
     * @param view the button that is being pressed.
     */
    public void selectDate(final View view) {
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker();
        builder.setTitleText("Select a date");

        SharedPreferences sharedPreferences = getSharedPreferences(FILTER_PREFERENCES, Context.MODE_PRIVATE);
        final SharedPreferences.Editor prefsEditor = sharedPreferences.edit();

        // Retrieve the date from SharedPreferences and set it in the DatePicker.
        if (view.getTag().equals("start_date")) {
            String startDate = sharedPreferences.getString(FILTER_START_DATE, null);
            if (startDate != null) {
                builder.setSelection(parseDateToMillis(startDate));
            }
        } else if (view.getTag().equals("end_date")) {
            String endDate = sharedPreferences.getString(FILTER_END_DATE, null);
            if (endDate != null) {
                builder.setSelection(parseDateToMillis(endDate));
            }
        }

        MaterialDatePicker<Long> datePicker = builder.build();
        datePicker.addOnPositiveButtonClickListener(selection -> {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String dateFormat = simpleDateFormat.format(new Date(selection));

            if (view.getTag().equals("start_date")) {
                prefsEditor.putString(FILTER_START_DATE, dateFormat);
                Button button = findViewById(R.id.startDateButton);
                button.setText(dateFormat);
            } else {
                prefsEditor.putString(FILTER_END_DATE, dateFormat);
                Button button = findViewById(R.id.endDateButton);
                button.setText(dateFormat);
            }

            prefsEditor.apply();
        });

        datePicker.show(getSupportFragmentManager(), datePicker.toString());
    }

    private long parseDateToMillis(String date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        try {
            Date parsedDate = simpleDateFormat.parse(date);
            return parsedDate != null ? parsedDate.getTime() : 0;
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Hide the advanced view with an animation.
     */
    public void collapseAdvanced(View view) {
        final RelativeLayout advancedView = findViewById(R.id.advancedView);
        ImageView collapseIcon = findViewById(R.id.collapseIcon);

        if (advancedView.getVisibility() == View.GONE) {
            expandAnimation(advancedView);
            collapseIcon.setImageResource( R.drawable.ic_keyboard_arrow_up );
        } else {
            collapseAnimation(advancedView);
            collapseIcon.setImageResource( R.drawable.ic_keyboard_arrow_down );
        }
    }

    /**
     * The animation that hides the view.
     *
     * @param view the view that is being hidden.
     */
    private void collapseAnimation(final View view) {
        final int initialHeight = view.getMeasuredHeight();

        // Decrease the height of the view until zero over the given time.
        Animation collapse = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    view.setVisibility(View.GONE);
                } else {
                    view.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                    view.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return super.willChangeBounds();
            }
        };

        collapse.setDuration((int) (initialHeight / view.getContext().getResources().getDisplayMetrics().density));
        view.startAnimation(collapse);
    }

    /**
     * The animation that shows the view.
     *
     * @param view the view that is to be shown.
     */
    private void expandAnimation(final View view) {
        view.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int targetHeight = view.getMeasuredHeight();

        view.getLayoutParams().height = 1;
        view.setVisibility(View.VISIBLE);

        // Slowly increases the height until the target height is reached over a given time.
        Animation expand = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    view.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    ScrollView scrollView = findViewById(R.id.scrollView);
                    scrollView.scrollTo(0, scrollView.getHeight());
                } else {
                    view.getLayoutParams().height = (int) (targetHeight * interpolatedTime);
                }

                view.requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return super.willChangeBounds();
            }
        };

        expand.setDuration((int) (targetHeight / view.getContext().getResources().getDisplayMetrics().density));
        view.startAnimation(expand);
    }
}
