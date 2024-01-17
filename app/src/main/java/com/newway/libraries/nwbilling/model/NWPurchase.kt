package com.newway.libraries.nwbilling.model

import com.android.billingclient.api.Purchase

class NWPurchase(
    val orderId: String?,
    val productId : String,
    val packageName : String,
    val purchaseTime : Long,
    val purchaseState : Int,
    val purchaseToken : String,
    val quantity : Int,
    val autoRenewing : Boolean,
    val acknowledged : Boolean,
    var purchase: Purchase? = null
) {
    companion object {
        fun from(productId: String,purchase: Purchase): NWPurchase {
            return NWPurchase(
                orderId = purchase.orderId,
                productId = productId,
                packageName = purchase.packageName,
                purchaseTime = purchase.purchaseTime,
                purchaseState = purchase.purchaseState,
                purchaseToken = purchase.purchaseToken,
                quantity = purchase.quantity,
                autoRenewing = purchase.isAutoRenewing,
                acknowledged = purchase.isAcknowledged,
                purchase = purchase
            )
        }
    }


}

//{"orderId":"GPA.3321-7442-1576-36737",
//    "packageName":"ow.document.scanner.pdf",
//    "productId":"ow.document.scanner.pdf.weekly",
//    "purchaseTime":1686282380393,
//    "purchaseState":0,
//    "purchaseToken":"DQijTmoYxC7rmeqpz4SIxE",
//    "quantity":1,
//    "autoRenewing":true,
//    "acknowledged":false}