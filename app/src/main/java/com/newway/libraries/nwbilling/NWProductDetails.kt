package com.newway.libraries.nwbilling

import com.android.billingclient.api.ProductDetails

class NWProductDetails(
    val id : String = "",
    val type : String = "",
    var productDetails : ProductDetails,
    var currencyCode : String = "USD",
    var formatPrice : String = "",
    var priceMicros : Long = 0,
    var isTrial : Boolean = false,
    var priceToken : String = ""
    ) {
    fun priceValue() : Double {
        return (priceMicros / 1000000.0)
    }
}