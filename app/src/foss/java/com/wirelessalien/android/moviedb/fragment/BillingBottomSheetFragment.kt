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

package com.wirelessalien.android.moviedb.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.databinding.FragmentBillingBottomSheetBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BillingBottomSheetFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentBillingBottomSheetBinding? = null
    private val binding get() = _binding!!

    var onPurchaseSuccess: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = arguments?.getBoolean("is_cancelable", false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillingBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBuyLifetime.isEnabled = false
        binding.btnSubscribe.isEnabled = false

        binding.btnContinueAds.setOnClickListener {
            binding.btnContinueAds.text = getString(R.string.please_wait)
            binding.btnContinueAds.isEnabled = false
            
            CoroutineScope(Dispatchers.Main).launch {
                val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
                preferences.edit { putBoolean("user_is_free_user", true) }
                onPurchaseSuccess?.invoke()
                parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
                    putBoolean(RESULT_KEY, true)
                })
                dismissAllowingStateLoss()
            }
        }
        
        binding.adsTitle.text = getString(R.string.continue_with_ads)
        binding.btnContinueAds.text = getString(R.string.continue_with_ads)
    }

    companion object {
        const val TAG = "BillingBottomSheetFragment"
        const val REQUEST_KEY = "billing_request_key"
        const val RESULT_KEY = "billing_result_key"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

