package com.newway.libraries.nwbilling

import android.app.Activity
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.gson.Gson


interface NWBillingInterface {
    //connect to google billing success
    fun onConnected(){}
    //connect to google billing failed
    fun onConnectFailed(){}
    //Loaded Subscription info
    fun onLoadedSubscriptionInfo(billingResult: BillingResult,products:List<NWProductDetails>){}
    // Loaded Products info (INAPP)
    fun onLoadedProductsInfo(billingResult: BillingResult,products:List<NWProductDetails>){}
    //Get all purchases available (type : SUB or INAPP)
    fun onLoadPurchased(billingResult: BillingResult,purchases: List<Purchase>,type:String){}
    //purchase a product success
    fun onPurchasedSuccess(billingResult: BillingResult, purchase:Purchase?, product: NWProduct, productDetail: NWProductDetails?){}
    //purchase a product failed
    fun onPurchasedFailed(billingResult: BillingResult,product: NWProduct?){}
}

class NWBilling(private val activity: Activity) {
    val TAG = "NWBilling"

    private var billingClient: BillingClient? = null
    var isConnected : Boolean = false
    var listener: NWBillingInterface? = null
    var products : MutableList<NWProductDetails> = mutableListOf()
    private var buyingProduct : NWProduct? = null

    fun destroy(){
        billingClient?.endConnection()
        isConnected = false
        buyingProduct = null
        billingClient = null
        products = mutableListOf()
        listener = null
    }

    //
    private fun addProduct(product: NWProductDetails){
        if (!products.contains(product)){
            products.add(product)
        }
    }
    private fun getProductDetail(product: NWProduct) : NWProductDetails?{
        val detail = products.filter {it.id == product.id}
        return detail.firstOrNull()
    }
    //connect
    fun startServiceConnection(){
        if (billingClient == null) {
            Log.d(TAG, "startServiceConnection: ")
            billingClient = BillingClient.newBuilder(activity).enablePendingPurchases()
                .setListener { result, listPurchases ->

                    if (result.responseCode == BillingResponseCode.OK && buyingProduct != null) {
                        var pc : Purchase? = null
                        val detail = getProductDetail(buyingProduct!!)
                        listPurchases?.forEach { purchase ->
                            val convert = convertPurchaseJsonToObject(purchase)
                            if (convert?.productId.equals(buyingProduct?.id)){
                                pc = purchase
                            }
                            handlePurchase(purchase)
                        }
                        android.os.Handler(Looper.getMainLooper()).postDelayed({
                            listener?.onPurchasedSuccess(result, pc, buyingProduct!!,detail)
                        },1000)
                    }else {
                        listPurchases?.forEach { purchase ->
                            handlePurchase(purchase)
                        }
                        android.os.Handler(Looper.getMainLooper()).postDelayed({
                            listener?.onPurchasedFailed(result, buyingProduct)
                        },1000)
                    }
            }.build()

            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        // The BillingClient is ready. You can query purchases here.
                        buyingProduct = null
                        isConnected = true
                        Log.d(TAG, "startServiceConnection: true")
                        listener?.onConnected()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // Try to restart the connection on the next request to
                    // Google Play by calling the startConnection() method.
                    buyingProduct = null
                    isConnected = false
                    Log.d(TAG, "startServiceConnection: false")
                    listener?.onConnectFailed()
                }
            })
        }
    }

    fun convertPurchaseJsonToObject(purchase:Purchase) : NWPurchase? {
        val gson = Gson()
        return try {
            gson.fromJson(purchase.originalJson, NWPurchase::class.java)
        }catch (e:Exception){
            Log.d(TAG, "convertPurchaseJsonToObject: error = ${e.localizedMessage}")
            null
        }
    }

    //handle purchase : dành cho iap non-consumable và subscriptions
    private fun handlePurchase(purchase: Purchase){
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED){
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken)
                billingClient?.acknowledgePurchase(acknowledgePurchaseParams.build()
                ) { result ->
                    if (result.responseCode == BillingResponseCode.OK) {
                        Log.d(TAG, "onAcknowledgePurchaseResponse: OK")
                    } else {
                        Log.d(TAG, "onAcknowledgePurchaseResponse: Failed")
                    }
                }
            }
        }else{
            Log.d(TAG, "handlePurchase: purchase.purchaseState = ${purchase.purchaseState}")
        }
    }

    // chỉ dành cho iap consumable (chưa support)
//    suspend fun handlePurchaseConsumable(purchase: Purchase) {
//        // Verify the purchase.
//        // Ensure entitlement was not already granted for this purchaseToken.
//        // Grant entitlement to the user.
//        val consumeParams =
//            ConsumeParams.newBuilder()
//                .setPurchaseToken(purchase.purchaseToken)
//                .build()
//        val result = billingClient?.consumePurchase(consumeParams)
//        if (result != null){
//            if (result.billingResult.responseCode == BillingResponseCode.OK){
//                Log.d(TAG, "onAcknowledgePurchaseResponse: OK")
//            }
//        }
//    }

    // async : lấy các purchase đã mua
    fun asyncSubscription(){
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.SUBS)
        billingClient?.queryPurchasesAsync(params.build()
        ) { result, purchases ->
            buyingProduct = null
            if (result.responseCode == BillingResponseCode.OK) {
                purchases.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            listener?.onLoadPurchased(result,purchases,ProductType.SUBS)
        }
    }
    fun asyncInApp(){
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.INAPP)
        billingClient?.queryPurchasesAsync(params.build()
        ) { result, purchases ->
            buyingProduct = null
            if (result.responseCode == BillingResponseCode.OK) {
                purchases.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            listener?.onLoadPurchased(result,purchases,ProductType.INAPP)
        }
    }

    // Info : lấy thông tin product
    fun getInfo(products: List<NWProduct>){
        val subs = products.filter {it.type == ProductType.SUBS}
        if (subs.isNotEmpty()) {
            getSubscriptionInfo(subs)
        }
        val inApp = products.filter {it.type == ProductType.INAPP}
        if (inApp.isNotEmpty()) {
            getProductInfo(inApp)
        }
    }

    private fun getSubscriptionInfo(ids:List<NWProduct>){
        if (isConnected) {
            Log.d(TAG, "getSubscriptionInfo: ")
            val items = ids.map { it.toQueryProduct() }
            val queryProductDetailsParams =
                QueryProductDetailsParams.newBuilder()
                    .setProductList(items)
                    .build()

            billingClient?.queryProductDetailsAsync(queryProductDetailsParams) { billingResult,
                                                                                 productDetailsList ->
                // check billingResult
                // process returned productDetailsList
                val list = mutableListOf<NWProductDetails>()
                productDetailsList.forEach {productDetails ->
                    val item = NWProductDetails(productDetails.productId,ProductType.SUBS,productDetails)
                    productDetails.subscriptionOfferDetails?.forEach {offer ->
                        item.priceToken = offer.offerToken
                        if (offer.pricingPhases.pricingPhaseList.size == 1){
                            offer.pricingPhases.pricingPhaseList.first()?.let {first ->
                                item.currencyCode = first.priceCurrencyCode
                                item.formatPrice = first.formattedPrice
                                item.priceMicros = first.priceAmountMicros
                            }
                        }else if (offer.pricingPhases.pricingPhaseList.size > 1){
                            val first = offer.pricingPhases.pricingPhaseList[0]
                            val two = offer.pricingPhases.pricingPhaseList[1]
                            item.currencyCode = two.priceCurrencyCode
                            item.formatPrice = two.formattedPrice
                            item.priceMicros = two.priceAmountMicros
                            item.isTrial = first.priceAmountMicros == 0L
                        }else{
                            // nothing
                        }
                    }
                    list.add(item)
                    addProduct(item)
                }
                Log.d(TAG, "getSubscriptionInfo: list products size = ${list.size}")
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    listener?.onLoadedSubscriptionInfo(billingResult,list)
                },200)
            }
        }
    }

    private fun getProductInfo(ids:List<NWProduct>){
        if (isConnected) {
            Log.d(TAG, "getOnetimeInfo: ")
            val items = ids.map { it.toQueryProduct() }
            val queryProductDetailsParams =
                QueryProductDetailsParams.newBuilder()
                    .setProductList(items)
                    .build()

            billingClient?.queryProductDetailsAsync(queryProductDetailsParams) { billingResult,
                                                                                 productDetailsList ->
                // check billingResult
                // process returned productDetailsList
                val list = mutableListOf<NWProductDetails>()
                productDetailsList.forEach { productDetails ->
                    val item = NWProductDetails(productDetails.productId, ProductType.INAPP,productDetails)
                    productDetails.oneTimePurchaseOfferDetails?.let { offer ->
                        item.currencyCode = offer.priceCurrencyCode
                        item.formatPrice = offer.formattedPrice
                        item.priceMicros = offer.priceAmountMicros
                    }
                    list.add(item)
                    addProduct(item)
                }
                listener?.onLoadedProductsInfo(billingResult,list)
            }
        }
    }


    //Buy : mua hàng
    fun buy(activity:Activity,product: NWProduct){
        val detail = products.firstOrNull() { it.id == product.id }
        if (detail != null && isConnected ) {
            buyingProduct = product
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
                Log.d(TAG, "buy: show popup purchase OK",)
            } else {
                Log.d(TAG, "buy: show popup purchase failed = ${billingResult?.debugMessage}")
            }
        }else{
            buyingProduct = null
            Log.d(TAG, "buy: isConnected = $isConnected")
            Log.d(TAG, "buy: can't find id : ${product.id} ")
        }
    }
}