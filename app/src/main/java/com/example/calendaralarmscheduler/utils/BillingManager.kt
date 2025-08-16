package com.example.calendaralarmscheduler.utils

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages Google Play Billing for premium features ($2 one-time purchase).
 * 
 * Premium Feature: Event details in alarm notifications
 * - Free users see "Calendar Event" 
 * - Premium users see actual event titles and descriptions
 * 
 * Handles purchase flow, state persistence, error recovery, and debug support.
 */
class BillingManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) : PurchasesUpdatedListener {
    
    private var onPurchaseStateChanged: (Boolean) -> Unit = {}
    private var onPurchaseError: (String) -> Unit = {}
    
    fun setCallbacks(
        onStateChanged: (Boolean) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        onPurchaseStateChanged = onStateChanged
        onPurchaseError = onError
    }
    
    companion object {
        private const val PREMIUM_SKU = "event_details_premium"
    }
    
    private var billingClient: BillingClient? = null
    private var isServiceConnected = false
    
    init {
        setupBillingClient()
    }
    
    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases() // Required for Google Play Billing
            .build()
        
        connectToBillingService()
    }
    
    private fun connectToBillingService() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isServiceConnected = true
                    Logger.d("BillingManager", "Billing client connected successfully")
                    queryPurchases()
                } else {
                    Logger.e("BillingManager", "Failed to connect to billing service: ${billingResult.debugMessage}")
                    // Retry connection after a delay for certain errors
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE) {
                        Logger.i("BillingManager", "Will retry connection - service temporarily unavailable")
                    }
                }
            }
            
            override fun onBillingServiceDisconnected() {
                isServiceConnected = false
                Logger.w("BillingManager", "Billing service disconnected - will reconnect when needed")
            }
        })
    }
    
    fun isPremiumPurchased(): Boolean {
        if (!isServiceConnected) {
            Logger.w("BillingManager", "Billing service not connected, using cached state")
            return settingsRepository.isPremiumPurchased()
        }
        
        val purchasesResult = billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPremium = purchases.any { purchase ->
                    purchase.products.contains(PREMIUM_SKU) && 
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                onPurchaseStateChanged(hasPremium)
            }
        }
        
        // For immediate response, return cached state from settings
        return settingsRepository.isPremiumPurchased()
    }
    
    private fun queryPurchases() {
        if (!isServiceConnected) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val purchasesResult = billingClient?.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
                
                purchasesResult?.let { (billingResult, purchases) ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val hasPremium = purchases.any { purchase ->
                            purchase.products.contains(PREMIUM_SKU) && 
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        }
                        
                        // Cache result in settings
                        settingsRepository.setPremiumPurchased(hasPremium)
                        
                        withContext(Dispatchers.Main) {
                            onPurchaseStateChanged(hasPremium)
                        }
                        
                        Logger.d("BillingManager", "Premium status: $hasPremium")
                    }
                }
            } catch (e: Exception) {
                Logger.e("BillingManager", "Error querying purchases", e)
            }
        }
    }
    
    fun launchPurchaseFlow(activity: Activity) {
        if (!isServiceConnected) {
            Logger.e("BillingManager", "Cannot launch purchase flow - billing service not connected")
            // Try to reconnect
            connectToBillingService()
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val productDetailsParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PREMIUM_SKU)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                        )
                    )
                    .build()
                
                val productDetailsResult = billingClient?.queryProductDetails(productDetailsParams)
                
                productDetailsResult?.let { (billingResult, productDetailsList) ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !productDetailsList.isNullOrEmpty()) {
                        val productDetails = productDetailsList[0]
                        
                        val billingFlowParams = BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(
                                listOf(
                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(productDetails)
                                        .build()
                                )
                            )
                            .build()
                        
                        withContext(Dispatchers.Main) {
                            billingClient?.launchBillingFlow(activity, billingFlowParams)
                        }
                    } else {
                        val errorMsg = when (billingResult.responseCode) {
                            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Product not available - needs Google Play Console setup"
                            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "In-app purchases not supported on this device"
                            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Google Play Store unavailable"
                            else -> "Product setup required: ${billingResult.debugMessage}"
                        }
                        Logger.e("BillingManager", "Failed to get product details: $errorMsg")
                        withContext(Dispatchers.Main) {
                            onPurchaseError(errorMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e("BillingManager", "Error launching purchase flow", e)
            }
        }
    }
    
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.products.contains(PREMIUM_SKU)) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Logger.d("BillingManager", "User canceled the purchase")
                onPurchaseStateChanged(settingsRepository.isPremiumPurchased()) // Keep current state
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Logger.i("BillingManager", "Item already owned, updating local state")
                settingsRepository.setPremiumPurchased(true)
                onPurchaseStateChanged(true)
            }
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                Logger.e("BillingManager", "Google Play services unavailable")
                onPurchaseError("Google Play services unavailable. Please try again later.")
                onPurchaseStateChanged(settingsRepository.isPremiumPurchased()) // Keep current state
            }
            BillingClient.BillingResponseCode.NETWORK_ERROR -> {
                Logger.e("BillingManager", "Network error during purchase")
                onPurchaseError("Network error. Please check your connection and try again.")
                onPurchaseStateChanged(settingsRepository.isPremiumPurchased()) // Keep current state  
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                Logger.e("BillingManager", "Billing unavailable")
                onPurchaseError("In-app purchases are not available on this device.")
                onPurchaseStateChanged(settingsRepository.isPremiumPurchased()) // Keep current state
            }
            else -> {
                Logger.e("BillingManager", "Purchase failed: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                onPurchaseError("Purchase failed. Please try again.")
                onPurchaseStateChanged(settingsRepository.isPremiumPurchased()) // Keep current state
            }
        }
    }
    
    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                // Verify purchase authenticity (basic check)
                if (purchase.purchaseToken.isEmpty()) {
                    Logger.e("BillingManager", "Invalid purchase - empty token")
                    return
                }
                
                // Acknowledge the purchase if it hasn't been acknowledged yet
                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    
                    billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Logger.d("BillingManager", "Purchase acknowledged successfully")
                        } else {
                            Logger.e("BillingManager", "Failed to acknowledge purchase: ${billingResult.debugMessage}")
                        }
                    }
                }
                
                // Update cached state
                settingsRepository.setPremiumPurchased(true)
                onPurchaseStateChanged(true)
                Logger.i("BillingManager", "Premium purchase successful!")
            }
            Purchase.PurchaseState.PENDING -> {
                Logger.i("BillingManager", "Purchase is pending - waiting for user confirmation")
                // Don't update local state yet, wait for purchase to complete
                onPurchaseStateChanged(settingsRepository.isPremiumPurchased())
            }
            else -> {
                Logger.w("BillingManager", "Purchase in unknown state: ${purchase.purchaseState}")
                onPurchaseStateChanged(settingsRepository.isPremiumPurchased())
            }
        }
    }
    
    fun restorePurchases() {
        if (!isServiceConnected) {
            Logger.w("BillingManager", "Cannot restore purchases - billing service not connected")
            connectToBillingService()
            return
        }
        
        Logger.i("BillingManager", "Restoring purchases...")
        queryPurchases()
    }
    
    fun disconnect() {
        billingClient?.endConnection()
        isServiceConnected = false
        Logger.d("BillingManager", "Billing client disconnected")
    }
}