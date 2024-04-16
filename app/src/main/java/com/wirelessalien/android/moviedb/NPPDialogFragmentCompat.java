package com.wirelessalien.android.moviedb;


import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class NPPDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    private static final int MAX_VALUE = 9;
    private static final int MIN_VALUE = 1;
    private static final boolean WRAP_SELECTOR_WHEEL = true;

    private NumberPicker picker;

    public static NPPDialogFragmentCompat newInstance(String key) {
        final NPPDialogFragmentCompat fragment = new NPPDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        picker = new NumberPicker(getActivity());
        // Configures the NumberPicker.
        picker.setMinValue(MIN_VALUE);
        picker.setMaxValue(MAX_VALUE);
        picker.setWrapSelectorWheel(WRAP_SELECTOR_WHEEL);

        // Block descendants of NumberPicker from receiving focus.
        picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        // Get the preference and set the value of the NumberPicker to the value of the preference.
        NumberPickerPreference preference = (NumberPickerPreference) getPreference();
        picker.setValue(preference.getValue());

        builder.setView(picker);

        // Add positive and negative buttons
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            // User clicked OK, so save the result somewhere or return them.
            int newValue = picker.getValue();
            if (preference.callChangeListener(newValue)) {
                preference.setValue(newValue);
            }
        });

        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            // User cancelled the dialog, do nothing and close the dialog
        });

        return builder.create();
    }

    @Override
    protected void onBindDialogView(@NonNull View view) {
        super.onBindDialogView(view);
        NumberPickerPreference preference = (NumberPickerPreference) getPreference();
        picker.setValue(preference.getValue());
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            int newValue = picker.getValue();
            NumberPickerPreference preference = (NumberPickerPreference) getPreference();
            if (preference.callChangeListener(newValue)) {
                preference.setValue(newValue);
            }
        }
    }
}