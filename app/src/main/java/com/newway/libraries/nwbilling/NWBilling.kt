package com.newway.libraries.nwbilling

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        listener = null
        billingClient = null
    }
    fun logDebug(value:String){
        if (isDebug) {
            Log.e("NWBilling", value)
        }
    }
    private fun resetData(){
        purchased = null
        buyingProduct = null
        allProducts = listOf()
        details = NWDetails()
        details.reset()
    }
    //setup

    //
    fun setUp(ids:List<NWProduct> = listOf(), isDebug:Boolean = false){
        resetData()
        this.isDebug = isDebug
        allProducts = ids
        if (billingClient == null || billingClient?.isReady == false) {
            logDebug("init billing")

            billingClient = BillingClient.newBuilder(context).enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
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

            startConnect(withIds = ids)
        }else{
            logDebug("inited billing")
            startConnect(withIds = ids)
        }
    }
    //connect

    private fun startConnect(withIds:List<NWProduct>){
        allProducts = withIds
        logDebug("startConnect")
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    buyingProduct = null
                    logDebug("startServiceConnection: true")
                    listener?.onConnected()
                    if (allProducts.isNotEmpty()) {
                        getInfo()
                    }
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
        startConnect(withIds = allProducts)
    }

    // async : lấy các purchase đã mua

    fun asyncPurchased(){
        purchased = NWBillingHandler(this)
        CoroutineScope(Dispatchers.Default).launch {
            val all = fetchAllPurchased()
            purchased?.addAllPurchased(all)
            withContext(Dispatchers.Main){
                handleListPurchased()
            }
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
        if (allProducts.isNotEmpty()){
            CoroutineScope(Dispatchers.Default).launch {
                val all = fetchAllInfo(allProducts)
                details.addAll(all)
                withContext(Dispatchers.Main){
                    handleListProductDetails()
                }
            }
        }
    }
    suspend fun fetchAllInfo(ids:List<NWProduct>): List<NWProductDetails> {
        return coroutineScope {
            val idSubs = ids.filter {it.type == ProductType.SUBS}
            val idInApps = ids.filter {it.type == ProductType.INAPP}
            val subs = if (idSubs.isEmpty()) null else async {
                val items = idSubs.map { it.toQueryProduct() }
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(items)
                    .build()
                billingClient?.queryProductDetails(params)
            }
            val inapp = if (idInApps.isEmpty()) null else async {
                val items = idInApps.map { it.toQueryProduct() }
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(items)
                    .build()
                billingClient?.queryProductDetails(params)
            }

            // Wait for both deferred results to complete
            val rsSubs = subs?.await()
            val rsInapp = inapp?.await()

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
                    item.priceToken = detail.subscriptionOfferDetails?.get(0)?.offerToken ?: ""
                    detail.subscriptionOfferDetails?.forEach { offer ->
                        logDebug("basePlanId = ${offer.basePlanId} -- offerId = ${offer.offerId} ")
                        if (item.priceToken.isEmpty()) {
                            item.priceToken = offer.offerToken
                        }
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

                    if (product.basePlanId.isNotEmpty()) {
                        // lấy productDetails có basePlanId này ra
                        val filter = detail.productDetails.subscriptionOfferDetails?.filter { it.basePlanId == product.basePlanId }
                        if (filter == null){
                            // không có product detail nào có base plan id này cả
                            Toast.makeText(activity,"Base plan is wrong!", Toast.LENGTH_SHORT).show()
                            return
                        }else{
                            // có base plan id này => kiểm tra xem có offer không
                            if (product.offerId.isNotEmpty()){
                                //có dùng offer id
                                val filterOffer = filter.firstOrNull() { it.offerId == product.offerId }
                                if (filterOffer != null) {
                                    logDebug("buy: offerId = ${filterOffer.offerId} - plan = ${filterOffer.basePlanId}")
                                    builder.setOfferToken(filterOffer.offerToken)
                                }else{
                                    logDebug("can't find offer id => buy base plan")
                                    val filterBase = filter.firstOrNull() { it.offerId == null }
                                    if (filterBase != null){
                                        logDebug("buy: base plan = ${filterBase.basePlanId} - offer = ${filterBase.offerId}")
                                        builder.setOfferToken(filterBase.offerToken)
                                    }else{
                                        logDebug("buy: can't find base plan ")
                                        Toast.makeText(activity,"Base plan is wrong!", Toast.LENGTH_SHORT).show()
                                        return
                                    }
                                }
                            }else{
                                // mua bằng base plan này
                                val filterOffer = filter.firstOrNull() { it.offerId == null }
                                if (filterOffer != null){
                                    logDebug("buy: base plan = ${filterOffer.basePlanId} - offer = ${filterOffer.offerId}")
                                    builder.setOfferToken(filterOffer.offerToken)
                                }else{
                                    logDebug("buy: can't find offer ")
                                    Toast.makeText(activity,"Base plan is wrong!", Toast.LENGTH_SHORT).show()
                                    return
                                }
                            }

                        }
                    }else{
                        Toast.makeText(activity,"Base plan is empty!", Toast.LENGTH_SHORT).show()
                        return
                    }
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