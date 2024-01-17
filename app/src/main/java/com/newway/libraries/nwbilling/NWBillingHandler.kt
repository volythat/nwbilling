package com.newway.libraries.nwbilling

import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.google.gson.Gson
import com.newway.libraries.nwbilling.model.NWPurchase

class NWBillingHandler {
    var purchases : ArrayList<NWPurchase> = arrayListOf()
    var isLoadedInApp : Boolean = false
    var isLoadedSubs : Boolean = false

    fun addPurchases(list: List<Purchase>,isSub:Boolean){
        list.forEach { purchase ->
            handlePurchase(purchase)
            val obj = convertPurchaseJsonToObject(purchase = purchase)
            if (obj == null){
                addBillingPurchase(purchase = NWPurchase.from(productId = "",purchase = purchase))
            }else{
                obj.purchase = purchase
                addBillingPurchase(purchase = obj)
            }
        }
        if (isSub){
            isLoadedSubs = true
        }else{
            isLoadedInApp = true
        }
    }
    fun addAllPurchased(list:List<Purchase>){
        list.forEach { purchase ->
            handlePurchase(purchase)
            val obj = convertPurchaseJsonToObject(purchase = purchase)
            if (obj == null){
                addBillingPurchase(purchase = NWPurchase.from(productId = "",purchase = purchase))
            }else{
                obj.purchase = purchase
                addBillingPurchase(purchase = obj)
            }
        }
        isLoadedSubs = true
        isLoadedInApp = true
    }

    private fun addBillingPurchase(purchase:NWPurchase){
        try {
            val filter = purchases.filter { it.orderId == purchase.orderId }
            if (filter.isEmpty()) {
                purchases.add(purchase)
            }
        }catch (e:NullPointerException){
            Log.e("NWDetails","addBillingPurchase NullPointerException error = ${e.localizedMessage}")
        }catch (e:Exception){
            Log.e("NWDetails","addBillingPurchase error = ${e.localizedMessage}")
        }
    }

    fun convertPurchaseJsonToObject(purchase:Purchase) : NWPurchase? {
        val gson = Gson()
        return try {
            gson.fromJson(purchase.originalJson, NWPurchase::class.java)
        }catch (e:Exception){
            NWBilling.logDebug("convertPurchaseJsonToObject: error = ${e.localizedMessage}")
            null
        }
    }
    //handle purchase : dành cho iap non-consumable và subscriptions
    fun handlePurchase(purchase: Purchase){
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED){
            NWBilling.logDebug("handlePurchase = ${purchase.originalJson}")
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken)
                NWBilling.billingClient?.acknowledgePurchase(acknowledgePurchaseParams.build()
                ) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        NWBilling.logDebug("onAcknowledgePurchaseResponse: OK")
                        // If purchase was a consumable product (a product you want the user to be able to buy again)
                        handlePurchaseForConsumable(purchase)
                    } else {
                        NWBilling.logDebug("onAcknowledgePurchaseResponse: Failed")
                    }
                }
            }else{
                NWBilling.logDebug("onAcknowledgePurchaseResponse: purchase already isAcknowledged")
                handlePurchaseForConsumable(purchase)
            }
        }else{
            NWBilling.logDebug("handlePurchase: purchase.purchaseState = ${purchase.purchaseState}")
        }
    }

    // chỉ dành cho iap consumable

    fun handlePurchaseForConsumable(purchase: Purchase) {
        convertPurchaseJsonToObject(purchase)?.let { pur ->
            NWBilling.allProducts.firstOrNull { it.id == pur.productId }?.let { prod ->
                if (prod.isConsumable){
                    handlePurchaseConsumable(purchase)
                }
            }
        }
    }
    fun handlePurchaseConsumable(purchase: Purchase) {
        NWBilling.logDebug("handlePurchaseConsumable = ${purchase.originalJson}")

        // Verify the purchase.
        // Ensure entitlement was not already granted for this purchaseToken.
        // Grant entitlement to the user.
        val consumeParams =
            ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        NWBilling.billingClient?.consumeAsync(consumeParams){ result, token ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK){
                NWBilling.logDebug("handlePurchaseConsumable: OK")
            }else{
                NWBilling.logDebug("handlePurchaseConsumable: not ok (code = ${result.responseCode})")
            }
        }
    }
}