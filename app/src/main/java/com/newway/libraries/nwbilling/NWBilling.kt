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
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchaseHistory
import com.android.billingclient.api.queryPurchasesAsync
import com.google.gson.Gson
import com.newway.libraries.nwbilling.model.NWProduct
import com.newway.libraries.nwbilling.model.NWProductDetails
import com.newway.libraries.nwbilling.model.NWPurchase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

open class NWBilling(val context: Context) {
    var billingClient: BillingClient? = null
    private var isDebug : Boolean = false
    var listener: NWBillingInterface? = null

    var allProducts: List<NWProduct> = listOf()
    var buyingProduct : NWProduct? = null
    var details = NWDetails()
    var purchased : NWBillingHandler? = null

    fun destroy(){
        logDebug("NWBilling destroy")
        billingClient?.endConnection()
        resetData()
        purchased = null
        listener = null
        billingClient = null
    }
    fun logDebug(value:String){
        if (isDebug) {
            Log.e("NWBilling", value)
        }
    }
    private fun resetData(){
        buyingProduct = null
        allProducts = listOf()
        details = NWDetails()
    }
    //setup

    fun verify(){
        if (billingClient == null){
            billingClient  = BillingClient.newBuilder(context).enablePendingPurchases().build()
            startConnect()
        }
    }
    //
    fun setUp(ids:List<NWProduct>,isDebug:Boolean = false){
        this.isDebug = isDebug
        allProducts = ids
        resetData()
        if (billingClient == null || billingClient?.isReady == false) {
            logDebug("init billing")
            purchased = NWBillingHandler(this)
            billingClient = BillingClient.newBuilder(context).enablePendingPurchases()
                .setListener { result, listPurchases ->
                    logDebug("buy done: responseCode = ${result.responseCode} -- buying id = ${buyingProduct?.id}")
                    if (result.responseCode == BillingResponseCode.OK && buyingProduct != null) {
                        var pc : Purchase? = null
                        val detail = details.getProductDetail(buyingProduct!!)
                        listPurchases?.forEach { purchase ->
                            logDebug("startServiceConnection: purchase = ${purchase.originalJson}")
                            val convert = purchased?.convertPurchaseJsonToObject(purchase)
                            if (convert?.productId.equals(buyingProduct?.id)){
                                pc = purchase
                            }
                            purchased?.handlePurchase(purchase)
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            listener?.onPurchasedSuccess(result, pc, buyingProduct!!,detail)
                        },1000)
                    }else {
                        logDebug("startServiceConnection failed: ${result.responseCode} -- mes = ${result.debugMessage}")
                        listPurchases?.forEach { purchase ->
                            logDebug("startServiceConnection failed: purchase = ${purchase.originalJson}")
                            purchased?.handlePurchase(purchase)
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
//            if (result.responseCode == BillingResponseCode.OK) {
            logDebug("asyncSubscription : OK - purchases.size = ${purchases.size}")
            purchased?.addPurchases(purchases,true)
//            }
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
//            if (result.responseCode == BillingResponseCode.OK) {
            logDebug("asyncInApp : OK - purchases.size = ${purchases.size}")
            purchased?.addPurchases(purchases,false)
//            }

            handleListPurchased()
        }
    }
    suspend fun fetchAllPurchased(): List<Purchase> {
        return coroutineScope {
            val subs = async { billingClient?.queryPurchasesAsync(params = QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()) }
            val inapp = async {
                billingClient?.queryPurchasesAsync(params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build())
            }

            // Wait for both deferred results to complete
            val rsSubs = subs.await()
            val rsInapp = inapp.await()

            // Merge or process the results as needed
            val mergedResult = (rsSubs?.purchasesList ?: listOf()) + (rsInapp?.purchasesList ?: listOf())
            purchased?.addAllPurchased(mergedResult)

            mergedResult
        }
    }


    private fun handleListPurchased(){
        purchased?.let {
            if (it.isLoadedSubs  && it.isLoadedInApp){
                listener?.onLoadPurchased(purchases = it.purchases)
            }
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
    suspend fun fetchAllInfo(ids:List<NWProduct>): List<NWProductDetails> {
        return coroutineScope {
            val idSubs = ids.filter {it.type == ProductType.SUBS}
            val idInApps = ids.filter {it.type == ProductType.INAPP}
            val subs = async {
                val items = idSubs.map { it.toQueryProduct() }
                val params = QueryProductDetailsParams.newBuilder()
                        .setProductList(items)
                        .build()
                billingClient?.queryProductDetails(params)
            }
            val inapp = async {
                val items = idInApps.map { it.toQueryProduct() }
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(items)
                    .build()
                billingClient?.queryProductDetails(params)
            }

            // Wait for both deferred results to complete
            val rsSubs = subs.await()
            val rsInapp = inapp.await()

            // Merge or process the results as needed
            val mergedResult = (rsSubs?.productDetailsList ?: listOf()) + (rsInapp?.productDetailsList ?: listOf())
            val arTemp = ArrayList<NWProductDetails>()
            mergedResult.forEach { detail ->
                val item = NWProductDetails(
                    id = detail.productId,
                    productDetails = detail
                )
                if (detail.subscriptionOfferDetails != null){
                    item.type = ProductType.SUBS
                    detail.subscriptionOfferDetails?.forEach { offer ->
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
                }

                if (detail.oneTimePurchaseOfferDetails != null){
                    item.type = ProductType.INAPP
                    detail.oneTimePurchaseOfferDetails?.let { offer ->
                        item.currencyCode = offer.priceCurrencyCode
                        item.formatPrice = offer.formattedPrice
                        item.priceMicros = offer.priceAmountMicros
                    }
                }
                arTemp.add(item)
            }
            arTemp
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
                val builder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(detail.productDetails)
                if (product.type == ProductType.SUBS){
                    builder.setOfferToken(detail.priceToken)
                }

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(builder.build()))
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

    //history
    suspend fun fetchHistory(): List<PurchaseHistoryRecord> {
        return coroutineScope {
            val subs = async { getSubscriptionHistory() }
            val inapp = async { getInAppHistory() }

            // Wait for both deferred results to complete
            val rsSubs = subs.await()
            val rsInapp = inapp.await()

            // Merge or process the results as needed
            val mergedResult = mergeHistoryResults(rsSubs, rsInapp)

            mergedResult
        }
    }
    private fun mergeHistoryResults(result1: List<PurchaseHistoryRecord>, result2: List<PurchaseHistoryRecord>): List<PurchaseHistoryRecord> {
        // Merge or process the results as needed
        return result1 + result2
    }
    suspend fun getSubscriptionHistory():List<PurchaseHistoryRecord> {
        return if (billingClient?.isReady == true) {
            val params = QueryPurchaseHistoryParams.newBuilder()
                .setProductType(ProductType.SUBS)
            val result = billingClient?.queryPurchaseHistory(params.build())

            result?.purchaseHistoryRecordList ?: listOf()
        } else {
            listOf()
        }
    }
    suspend fun getInAppHistory():List<PurchaseHistoryRecord>{
        return if (billingClient?.isReady == true) {
            val params = QueryPurchaseHistoryParams.newBuilder()
                .setProductType(ProductType.INAPP)
            val result = billingClient?.queryPurchaseHistory(params.build())

            result?.purchaseHistoryRecordList ?: listOf()
        }else{
            listOf()
        }
    }

}