package com.newway.libraries.nwbilling

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.newway.libraries.nwbilling.model.NWProduct
import com.newway.libraries.nwbilling.model.NWProductDetails
import com.newway.libraries.nwbilling.model.NWPurchase

interface NWBillingInterface {
    //connect to google billing success
    fun onConnected(){}
    //connect to google billing failed
    fun onConnectFailed(){}

    //Google service disconnected => google auto reload
    fun onServiceDisconnected(){}

    // return all product detail
    fun onLoadedInfo(allDetails:List<NWProductDetails>){}

    //Get all purchases available (type : SUB or INAPP)
    fun onLoadPurchased(purchases: List<NWPurchase>){}
    //purchase a product success
    fun onPurchasedSuccess(billingResult: BillingResult, purchase: Purchase?, product: NWProduct, productDetail: NWProductDetails?){}
    //purchase a product failed
    fun onPurchasedFailed(billingResult: BillingResult, product: NWProduct?){}
}