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
package com.wirelessalien.android.moviedb.preference

import android.R
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.preference.PreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class NPPDialogFragmentCompat : PreferenceDialogFragmentCompat() {
    private lateinit var picker: NumberPicker
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        picker = NumberPicker(activity)
        // Configures the NumberPicker.
        picker.minValue = MIN_VALUE
        picker.maxValue = MAX_VALUE
        picker.wrapSelectorWheel = WRAP_SELECTOR_WHEEL

        // Block descendants of NumberPicker from receiving focus.
        picker.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        // Get the preference and set the value of the NumberPicker to the value of the preference.
        val preference = preference as NumberPickerPreference
        picker.value = preference.value
        builder.setView(picker)

        // Add positive and negative buttons
        builder.setPositiveButton(R.string.ok) { dialog: DialogInterface?, which: Int ->
            // User clicked OK, so save the result somewhere or return them.
            val newValue = picker.value
            if (preference.callChangeListener(newValue)) {
                preference.value = newValue
            }
        }
        builder.setNegativeButton(R.string.cancel) { dialog: DialogInterface?, which: Int -> }
        return builder.create()
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        val preference = preference as NumberPickerPreference
        picker.value = preference.value
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val newValue = picker.value
            val preference = preference as NumberPickerPreference
            if (preference.callChangeListener(newValue)) {
                preference.value = newValue
            }
        }
    }

    companion object {
        private const val MAX_VALUE = 9
        private const val MIN_VALUE = 1
        private const val WRAP_SELECTOR_WHEEL = true
        fun newInstance(key: String?): NPPDialogFragmentCompat {
            val fragment = NPPDialogFragmentCompat()
            val b = Bundle(1)
            b.putString(ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }
}