package com.newway.libraries.nwbilling.model

import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product

class NWProduct(
    val id : String,
    val type : String,
    val offerId : String = "",
    val isConsumable : Boolean = false
) {

    fun toQueryProduct() : Product {
        return QueryProductDetailsParams.Product.newBuilder()
            .setProductId(id)
            .setProductType(type)
            .build()
    }
}