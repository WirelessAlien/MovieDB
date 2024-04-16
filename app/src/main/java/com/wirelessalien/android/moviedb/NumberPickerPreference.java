package com.wirelessalien.android.moviedb;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

public class NumberPickerPreference extends DialogPreference {

    // Allowed range
    private static final int MAX_VALUE = 9;
    private static final int MIN_VALUE = 1;

    private int value;

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public int getValue() {
        return this.value;
    }

    public void setValue(int value) {
        this.value = value;
        persistInt(this.value);
    }
}