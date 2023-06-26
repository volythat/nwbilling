package com.newway.nwbillinglib

class NWPurchase(
    val orderId: String?,
    val productId : String,
    val packageName : String,
    val purchaseTime : Long,
    val purchaseState : Int,
    val purchaseToken : String,
    val quantity : Int,
    val autoRenewing : Boolean,
    val acknowledged : Boolean
) {
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