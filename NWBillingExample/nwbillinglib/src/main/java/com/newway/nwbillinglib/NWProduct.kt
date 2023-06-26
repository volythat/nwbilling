package com.newway.purchase

import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product

class NWProduct(
    val id : String,
    val type : String
) {

    fun toQueryProduct() : Product {
        return QueryProductDetailsParams.Product.newBuilder()
            .setProductId(id)
            .setProductType(type)
            .build()
    }
}