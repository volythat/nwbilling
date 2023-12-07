package com.newway.libraries.nwbilling

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchaseHistory
import com.google.gson.Gson
import com.newway.libraries.nwbilling.model.NWProduct
import com.newway.libraries.nwbilling.model.NWProductDetails
import com.newway.libraries.nwbilling.model.NWPurchase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object NWBilling {
    var billingClient: BillingClient? = null
    private var isDebug : Boolean = false
    var listener: NWBillingInterface? = null

    var allProducts: List<NWProduct> = listOf()
    var buyingProduct : NWProduct? = null
    var details = NWDetails()
    var purchased = NWBillingHandler()

//    fun destroy(){
//        logDebug("NWBilling destroy")
//        billingClient?.endConnection()
//        listener = null
//        billingClient = null
//        resetData()
//    }
    fun logDebug(value:String){
        if (isDebug) {
            Log.e("NWBilling", value)
        }
    }
    private fun resetData(){
        buyingProduct = null
        details = NWDetails()
    }

    //
    fun setUp(context: Context,ids:List<NWProduct>,isDebug:Boolean = false){
        this.isDebug = isDebug
        allProducts = ids
        resetData()
        if (billingClient == null || billingClient?.isReady == false) {
            logDebug("init billing")
            billingClient = BillingClient.newBuilder(context).enablePendingPurchases()
                .setListener { result, listPurchases ->
                    logDebug("buy done: responseCode = ${result.responseCode} -- buying id = ${buyingProduct?.id}")
                    if (result.responseCode == BillingResponseCode.OK && buyingProduct != null) {
                        var pc : Purchase? = null
                        val detail = details.getProductDetail(buyingProduct!!)
                        listPurchases?.forEach { purchase ->
                            logDebug("startServiceConnection: purchase = ${purchase.originalJson}")
                            val convert = purchased.convertPurchaseJsonToObject(purchase)
                            if (convert?.productId.equals(buyingProduct?.id)){
                                pc = purchase
                            }
                            purchased.handlePurchase(purchase)
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            listener?.onPurchasedSuccess(result, pc, buyingProduct!!,detail)
                        },1000)
                    }else {
                        logDebug("startServiceConnection failed: ${result.responseCode} -- mes = ${result.debugMessage}")
                        listPurchases?.forEach { purchase ->
                            logDebug("startServiceConnection failed: purchase = ${purchase.originalJson}")
                            purchased.handlePurchase(purchase)
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            listener?.onPurchasedFailed(result, buyingProduct)
                        },1000)
                    }
                }.build()

            startConnect()
        }else{
            logDebug("inited billing")
            startConnect()
        }
    }
    //connect

    private fun startConnect(){
        logDebug("startConnect")
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    buyingProduct = null
                    logDebug("startServiceConnection: true")
                    listener?.onConnected()
                    getInfo()
                    asyncPurchased()
                } else {
                    buyingProduct = null
                    logDebug("onBillingSetupFinished responseCode = ${billingResult.responseCode}")
                    listener?.onConnectFailed()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                buyingProduct = null
                logDebug("onBillingServiceDisconnected")
                listener?.onServiceDisconnected()
            }
        })
    }
    fun reConnect(){
        startConnect()
    }

    // async : lấy các purchase đã mua
    fun asyncPurchased(){
        asyncSubscription()
        asyncInApp()
    }
    private fun asyncSubscription(){
        logDebug("asyncSubscription")
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.SUBS)
        billingClient?.queryPurchasesAsync(params.build()
        ) { result, purchases ->
            buyingProduct = null
            if (result.responseCode == BillingResponseCode.OK) {
                logDebug("asyncSubscription : OK - purchases.size = ${purchases.size}")
                purchased.addPurchases(purchases,true)
            }
            handleListPurchased()
        }
    }
    private fun asyncInApp(){
        logDebug("asyncInApp")
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.INAPP)

        billingClient?.queryPurchasesAsync(params.build()
        ) { result, purchases ->
            buyingProduct = null
            if (result.responseCode == BillingResponseCode.OK) {
                logDebug("asyncInApp : OK - purchases.size = ${purchases.size}")
                purchased.addPurchases(purchases,false)
            }

            handleListPurchased()
        }
    }

    private fun handleListPurchased(){
        if (purchased.isLoadedSubs && purchased.isLoadedInApp){
            listener?.onLoadPurchased(purchases = purchased.purchases)
        }
    }

    // Info : lấy thông tin product
    fun getInfo(){
        if (allProducts.size == details.productDetails.size){
            listener?.onLoadedInfo(details.productDetails)
        }else {
            val subs = allProducts.filter { it.type == ProductType.SUBS }
            if (subs.isNotEmpty()) {
                getSubscriptionInfo(subs)
            } else {
                details.isLoadedSubs = true
            }
            val inApp = allProducts.filter { it.type == ProductType.INAPP }
            if (inApp.isNotEmpty()) {
                getProductInfo(inApp)
            } else {
                details.isLoadedInApp = true
            }
        }
    }

    private fun getSubscriptionInfo(ids:List<NWProduct>){
        if (billingClient?.isReady == true) {
            logDebug( "getSubscriptionInfo: ")
            val items = ids.map { it.toQueryProduct() }
            val queryProductDetailsParams =
                QueryProductDetailsParams.newBuilder()
                    .setProductList(items)
                    .build()

            billingClient?.queryProductDetailsAsync(queryProductDetailsParams) { billingResult,
                                                                                 productDetailsList ->
                // check billingResult
                logDebug("getSubscriptionInfo billingResult : code = ${billingResult.responseCode}")
                // process returned productDetailsList
                val arTemp = ArrayList<NWProductDetails>()
                productDetailsList.forEach {productDetails ->
                    if (productDetails.productId != null) {
                        val item = NWProductDetails(
                            id = productDetails.productId,
                            type = ProductType.SUBS,
                            productDetails = productDetails
                        )
                        productDetails.subscriptionOfferDetails?.forEach { offer ->
                            item.priceToken = offer.offerToken
                            if (offer.pricingPhases.pricingPhaseList.size == 1) {
                                offer.pricingPhases.pricingPhaseList.first()?.let { first ->
                                    item.currencyCode = first.priceCurrencyCode
                                    item.formatPrice = first.formattedPrice
                                    item.priceMicros = first.priceAmountMicros
                                }
                            } else if (offer.pricingPhases.pricingPhaseList.size > 1) {
                                val first = offer.pricingPhases.pricingPhaseList[0]
                                val two = offer.pricingPhases.pricingPhaseList[1]
                                item.currencyCode = two.priceCurrencyCode
                                item.formatPrice = two.formattedPrice
                                item.priceMicros = two.priceAmountMicros
                                item.isTrial = first.priceAmountMicros == 0L
                            } else {
                                // nothing
                            }
                        }
                        arTemp.add(item)
                    }
                }
                details.addDetails(arTemp, isSubs = true)

                Handler(Looper.getMainLooper()).postDelayed({
                    handleListProductDetails()
                },200)
            }
        }
    }

    private fun getProductInfo(ids:List<NWProduct>){
        if (billingClient?.isReady == true) {
            logDebug("getProductInfo: ")
            val items = ids.map { it.toQueryProduct() }
            val queryProductDetailsParams =
                QueryProductDetailsParams.newBuilder()
                    .setProductList(items)
                    .build()

            billingClient?.queryProductDetailsAsync(queryProductDetailsParams) { billingResult,
                                                                                 productDetailsList ->
                // check billingResult
                logDebug("getProductInfo billingResult : code = ${billingResult.responseCode}")
                // process returned productDetailsList
                val arTemp = ArrayList<NWProductDetails>()
                productDetailsList.forEach { productDetails ->
                    if (productDetails.productId != null) {
                        val item = NWProductDetails(
                            id = productDetails.productId,
                            type = ProductType.INAPP,
                            productDetails = productDetails
                        )
                        productDetails.oneTimePurchaseOfferDetails?.let { offer ->
                            item.currencyCode = offer.priceCurrencyCode
                            item.formatPrice = offer.formattedPrice
                            item.priceMicros = offer.priceAmountMicros
                        }
                        arTemp.add(item)
                    }
                }
                details.addDetails(arTemp, isSubs = false)

                Handler(Looper.getMainLooper()).postDelayed({
                    handleListProductDetails()
                },200)
            }
        }
    }

    private fun handleListProductDetails(){
        if (details.isLoadedSubs && details.isLoadedInApp){
            listener?.onLoadedInfo(details.productDetails)
        }
    }

    //Buy : mua hàng
    fun buy(activity:Activity,product: NWProduct){
        if (activity.isFinishing || activity.isDestroyed) return

        if (details.productDetails.size > 0){
            val detail = details.getProductDetail(product)
            if (detail != null && billingClient?.isReady == true ) {
                buyingProduct = product
                logDebug("buy: id = ${product.id}")
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                        .setProductDetails(detail.productDetails)
                        // to get an offer token, call ProductDetails.subscriptionOfferDetails()
                        // for a list of offers that are available to the user
                        .setOfferToken(detail.priceToken)
                        .build()
                )

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()

                // Launch the billing flow
                val billingResult = billingClient?.launchBillingFlow(activity, billingFlowParams)
                if (billingResult?.responseCode == BillingResponseCode.OK) {
                    logDebug("buy: show popup purchase OK",)
                } else {
                    logDebug("buy: show popup purchase failed = ${billingResult?.debugMessage}")
                }
            }else{
                buyingProduct = null
                logDebug("buy: isConnected = ${billingClient?.isReady}")
                logDebug("buy: can't find id : ${product.id} ")
            }
        }else{
            logDebug("productDetails.size == 0")
        }
    }
}