package com.newway.libraries.nwbilling

import android.util.Log
import com.android.billingclient.api.BillingClient.ProductType
import com.newway.libraries.nwbilling.model.NWProduct
import com.newway.libraries.nwbilling.model.NWProductDetails

class NWDetails {
    var productDetails : ArrayList<NWProductDetails> = arrayListOf()
    var isLoadedInApp : Boolean = false
    var isLoadedSubs : Boolean = false

    fun reset(){
        isLoadedSubs = false
        isLoadedInApp = false
    }
    fun getProducts():List<NWProductDetails> {
        return productDetails.filter {it.type == ProductType.INAPP}
    }
    fun getSubscriptions():List<NWProductDetails> {
        return productDetails.filter {it.type == ProductType.SUBS}
    }
    fun addDetails(products:ArrayList<NWProductDetails>,isSubs:Boolean){
        val result = productDetails.apply { addAll(products) }.distinctBy { it.id }
        productDetails = ArrayList(result)
        if (isSubs){
            isLoadedSubs = true
        }else{
            isLoadedInApp = true
        }
    }

    fun getProductDetail(product: NWProduct) : NWProductDetails?{
        try {
            return if (productDetails.size > 0) {
                return productDetails.firstOrNull { it.id == product.id }
            } else {
                null
            }
        }catch (e:NullPointerException){
            Log.e("NWDetails","can't get product detail error = ${e.localizedMessage}")
            return null
        }catch (e:Exception){
            Log.e("NWDetails","can't get product detail error = ${e.localizedMessage}")
            return null
        }
    }
}