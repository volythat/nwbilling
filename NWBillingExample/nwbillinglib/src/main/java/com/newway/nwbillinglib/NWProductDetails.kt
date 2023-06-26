package com.newway.purchase

import com.android.billingclient.api.ProductDetails

class NWProductDetails(
    val id : String,
    val type : String,
    var productDetails : ProductDetails,
    var currencyCode : String = "USD",
    var formatPrice : String = "",
    var priceMicros : Long = 0,
    var isTrial : Boolean = false,
    var priceToken : String = ""
    ) {
}