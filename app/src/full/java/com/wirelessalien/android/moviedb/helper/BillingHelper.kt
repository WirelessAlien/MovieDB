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

package com.wirelessalien.android.moviedb.helper

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.PurchaseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.core.content.edit

class BillingHelper(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onPurchaseFinished: (PurchaseStatus, String?) -> Unit
) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient
    private var isConnected = false

    companion object {
        const val TAG = "BillingHelper"
        const val ONE_TIME_PRODUCT_ID = "com.wirelessalien.moviedb.adfree.full"
        const val SUBSCRIPTION_PRODUCT_ID = "com.wirelessalien.moviedb.sub.adfree"
        const val GRACE_PERIOD_MILLIS = 24 * 60 * 60 * 1000L // 24 hours

        private const val PREF_FILE = "billing_prefs"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val CACHE_VALIDITY_MILLIS = 12 * 60 * 60 * 1000L // 12 hours
    }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    fun startConnection(onConnected: (Boolean) -> Unit) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isConnected = true
                    onConnected(true)
                } else {
                    isConnected = false
                    onConnected(false)
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnected = false
            }
        })
    }

    fun endConnection() {
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private fun savePremiumStatus(isPremium: Boolean) {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_IS_PREMIUM, isPremium)
                .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
        }
    }

    private fun checkLocalPremiumStatus(): PurchaseStatus {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val isPremium = prefs.getBoolean(KEY_IS_PREMIUM, false)
        val lastCheckTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0)

        if (isPremium && (System.currentTimeMillis() - lastCheckTime < CACHE_VALIDITY_MILLIS)) {
            return PurchaseStatus.PURCHASED
        }
        return PurchaseStatus.ERROR
    }

    fun checkSubscription(onResult: (Boolean) -> Unit) {
        if (!isConnected) {
            startConnection { success ->
                if (success) {
                    checkSubscription(onResult)
                } else {
                    onResult(false)
                }
            }
            return
        }

        val paramsSubs = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(paramsSubs) { resultSubs, purchasesSubs ->
            var isSubscribed = false
            if (resultSubs.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchasesSubs) {
                    if (purchase.products.contains(SUBSCRIPTION_PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        isSubscribed = true
                        break
                    }
                }
            }
            if (isSubscribed) {
                savePremiumStatus(true)
            }
            onResult(isSubscribed)
        }
    }

    suspend fun checkPurchasesSuspend(): PurchaseStatus {
        return suspendCancellableCoroutine { continuation ->
            checkPurchases { status ->
                if (continuation.isActive) {
                    continuation.resume(status)
                }
            }
        }
    }

    fun queryProducts(
        onOneTimeProductLoaded: (ProductDetails) -> Unit,
        onSubscriptionProductLoaded: (ProductDetails) -> Unit
    ) {
        if (!isConnected) return

        coroutineScope.launch {
            val oneTimeParams = QueryProductDetailsParams.newBuilder().setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ONE_TIME_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()

            val oneTimeResult = billingClient.queryProductDetails(oneTimeParams)
            if (oneTimeResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                oneTimeResult.productDetailsList?.firstOrNull()?.let {
                    onOneTimeProductLoaded(it)
                }
            }

            val subParams = QueryProductDetailsParams.newBuilder().setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SUBSCRIPTION_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()

            val subResult = billingClient.queryProductDetails(subParams)
            if (subResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                subResult.productDetailsList?.firstOrNull()?.let {
                    onSubscriptionProductLoaded(it)
                }
            }
        }
    }

    fun checkPurchases(onResult: (PurchaseStatus) -> Unit) {
        if (!isConnected) {
            startConnection { success ->
                if (success) {
                    checkPurchases(onResult)
                } else {
                    onResult(PurchaseStatus.ERROR)
                }
            }
            return
        }

        val paramsInApp = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(paramsInApp) { resultInApp, purchasesInApp ->
            var isPurchased = false
            var isPending = false
            var purchaseToAcknowledge: Purchase? = null

            if (resultInApp.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchasesInApp) {
                    if (purchase.products.contains(ONE_TIME_PRODUCT_ID)) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            isPurchased = true
                            if (!purchase.isAcknowledged) {
                                purchaseToAcknowledge = purchase
                            }
                            break
                        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                            isPending = true
                        }
                    }
                }
            } else if (resultInApp.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ||
                resultInApp.responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ||
                resultInApp.responseCode == BillingClient.BillingResponseCode.ERROR) {
                if (resultInApp.responseCode == BillingClient.BillingResponseCode.ERROR ||
                    resultInApp.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) {
                    if (checkLocalPremiumStatus() == PurchaseStatus.PURCHASED) {
                        onResult(PurchaseStatus.PURCHASED)
                        return@queryPurchasesAsync
                    }
                }
                onResult(PurchaseStatus.ERROR)
                return@queryPurchasesAsync
            }

            if (isPurchased) {
                handlePurchaseAcknowledging(purchaseToAcknowledge) {
                    savePremiumStatus(true)
                    onResult(PurchaseStatus.PURCHASED)
                }
            } else {
                val paramsSubs = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()

                billingClient.queryPurchasesAsync(paramsSubs) { resultSubs, purchasesSubs ->
                    var isSubscribed = false
                    var isSubPending = false
                    var subToAcknowledge: Purchase? = null

                    if (resultSubs.responseCode == BillingClient.BillingResponseCode.OK) {
                        for (purchase in purchasesSubs) {
                            if (purchase.products.contains(SUBSCRIPTION_PRODUCT_ID)) {
                                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                    isSubscribed = true
                                    if (!purchase.isAcknowledged) {
                                        subToAcknowledge = purchase
                                    }
                                    break
                                } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                                    isSubPending = true
                                }
                            }
                        }
                    } else if (resultSubs.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ||
                        resultSubs.responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ||
                        resultSubs.responseCode == BillingClient.BillingResponseCode.ERROR) {
                        if (resultSubs.responseCode == BillingClient.BillingResponseCode.ERROR ||
                            resultSubs.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) {
                            if (checkLocalPremiumStatus() == PurchaseStatus.PURCHASED) {
                                onResult(PurchaseStatus.PURCHASED)
                                return@queryPurchasesAsync
                            }
                        }
                        onResult(PurchaseStatus.ERROR)
                        return@queryPurchasesAsync
                    }

                    if (isSubscribed) {
                        handlePurchaseAcknowledging(subToAcknowledge) {
                            savePremiumStatus(true)
                            onResult(PurchaseStatus.PURCHASED)
                        }
                    } else if (isPending || isSubPending) {
                        onResult(PurchaseStatus.PENDING)
                    } else {
                        savePremiumStatus(false)
                        onResult(PurchaseStatus.NOT_PURCHASED)
                    }
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .apply {
                    if (productDetails.productType == BillingClient.ProductType.SUBS) {
                        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                        if (offerToken != null) {
                            setOfferToken(offerToken)
                        } else {
                            return@apply
                        }
                    }
                }
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            var anyPending = false
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    anyPending = true
                }
                handlePurchase(purchase)
            }
            if (anyPending) {
                onPurchaseFinished(PurchaseStatus.PENDING, null)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            onPurchaseFinished(PurchaseStatus.NOT_PURCHASED, context.getString(R.string.purchase_canceled))
        } else {
            onPurchaseFinished(PurchaseStatus.NOT_PURCHASED, billingResult.debugMessage)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            handlePurchaseAcknowledging(if (!purchase.isAcknowledged) purchase else null) {
                coroutineScope.launch(Dispatchers.Main) {
                    onPurchaseFinished(PurchaseStatus.PURCHASED, null)
                }
            }
        }
    }

    private fun handlePurchaseAcknowledging(
        purchase: Purchase?,
        onComplete: () -> Unit
    ) {
        if (purchase != null) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { _ ->
                onComplete()
            }
        } else {
            onComplete()
        }
    }
}