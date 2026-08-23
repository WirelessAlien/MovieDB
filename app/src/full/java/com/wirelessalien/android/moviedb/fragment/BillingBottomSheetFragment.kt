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

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.android.billingclient.api.ProductDetails
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.PurchaseStatus
import com.wirelessalien.android.moviedb.databinding.FragmentBillingBottomSheetBinding
import com.wirelessalien.android.moviedb.helper.BillingHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.edit

class BillingBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentBillingBottomSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var billingHelper: BillingHelper
    private var oneTimeProductDetails: ProductDetails? = null
    private var subscriptionProductDetails: ProductDetails? = null

    var onPurchaseSuccess: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        // Make it not cancellable by touching outside
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
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

        binding.btnBuyLifetime.setOnClickListener {
            oneTimeProductDetails?.let { billingHelper.launchBillingFlow(requireActivity(), it) }
        }

        binding.btnSubscribe.setOnClickListener {
            subscriptionProductDetails?.let { billingHelper.launchBillingFlow(requireActivity(), it) }
        }

        binding.btnContinueAds.setOnClickListener {
            binding.btnContinueAds.text = getString(R.string.please_wait)
            binding.btnContinueAds.isEnabled = false
            val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            preferences.edit { putBoolean("user_is_free_user", true) }
            onPurchaseSuccess?.invoke()
            parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
                putBoolean(RESULT_KEY, true)
            })
            dismissAllowingStateLoss()
        }

        binding.btnRefresh.setOnClickListener {
            binding.billingProgressBar.visibility = View.VISIBLE
            billingHelper.checkPurchases { status ->
                handleCheckPurchaseResult(status)
            }
        }

        setupBillingHelper()
    }

    private fun setupBillingHelper() {
        billingHelper = BillingHelper(requireContext(), lifecycleScope) { status, errorMessage ->
            lifecycleScope.launch(Dispatchers.Main) {
                when (status) {
                    PurchaseStatus.PURCHASED -> {
                        handleValidPurchase()
                    }
                    PurchaseStatus.PENDING -> {
                        binding.description.text = getString(R.string.payment_pending)
                        binding.btnBuyLifetime.isEnabled = false
                        binding.btnSubscribe.isEnabled = false
                    }
                    PurchaseStatus.ERROR -> {
                        handleError(errorMessage)
                    }
                    PurchaseStatus.NOT_PURCHASED -> {
                        if (errorMessage != null) {
                            if (errorMessage == getString(R.string.purchase_canceled)) {
                                Toast.makeText(requireContext(), R.string.purchase_canceled, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(requireContext(), getString(R.string.error2, errorMessage), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        billingHelper.startConnection {
            billingHelper.checkPurchases { status ->
                handleCheckPurchaseResult(status)
            }
        }
    }

    private fun handleCheckPurchaseResult(status: PurchaseStatus) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.billingProgressBar.visibility = View.GONE
            when (status) {
                PurchaseStatus.PURCHASED -> {
                    handleValidPurchase()
                }
                PurchaseStatus.PENDING -> {
                    binding.description.text = getString(R.string.payment_pending)
                    binding.btnBuyLifetime.isEnabled = false
                    binding.btnSubscribe.isEnabled = false
                }
                PurchaseStatus.ERROR -> {
                    handleError(null)
                }
                PurchaseStatus.NOT_PURCHASED -> {
                    billingHelper.queryProducts(
                        onOneTimeProductLoaded = { productDetails ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                oneTimeProductDetails = productDetails
                                updateUIForOneTimeProduct(productDetails)
                            }
                        },
                        onSubscriptionProductLoaded = { productDetails ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                subscriptionProductDetails = productDetails
                                updateUIForSubscriptionProduct(productDetails)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun updateUIForOneTimeProduct(productDetails: ProductDetails) {
        val formattedPrice = productDetails.oneTimePurchaseOfferDetails?.formattedPrice ?: "Unavailable"
        binding.lifetimeTitle.text = getString(R.string.lifetime_patron2)
        binding.btnBuyLifetime.text = getString(R.string.lifetime_patron, formattedPrice)
        binding.btnBuyLifetime.isEnabled = true
    }

    private fun updateUIForSubscriptionProduct(productDetails: ProductDetails) {
        val offerDetails = productDetails.subscriptionOfferDetails?.firstOrNull()
        val pricingPhases = offerDetails?.pricingPhases?.pricingPhaseList
        val formattedPrice = pricingPhases?.firstOrNull()?.formattedPrice ?: "Unavailable"

        binding.subscriptionTitle.text = getString(R.string.supporter_tier_monthly2)
        binding.btnSubscribe.text = getString(R.string.supporter_tier_monthly, formattedPrice)
        binding.btnSubscribe.isEnabled = true
    }

    private fun handleValidPurchase() {
        lifecycleScope.launch(Dispatchers.Main) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            preferences.edit { putBoolean("user_has_active_purchase", true)
            putBoolean("user_is_subscribed", true) }

            Toast.makeText(requireContext(), R.string.thank_you_for_your_support, Toast.LENGTH_LONG).show()
            onPurchaseSuccess?.invoke()
            parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
                putBoolean(RESULT_KEY, true)
            })
            dismissAllowingStateLoss()
        }
    }

    private fun handleError(errorMessage: String?) {
        // Fail Secure: Do not dismiss the dialog. Show error message.
        // The user is blocked from entering the app if they don't have a cached purchase.
        val message = errorMessage ?: getString(R.string.error_loading_data)
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        binding.btnRefresh.visibility = View.VISIBLE
    }

    companion object {
        const val TAG = "BillingBottomSheetFragment"
        const val REQUEST_KEY = "billing_request_key"
        const val RESULT_KEY = "billing_result_key"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::billingHelper.isInitialized) {
            billingHelper.endConnection()
        }
        _binding = null
    }
}