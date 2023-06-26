package com.example.nwbillingexample

import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.example.nwbillingexample.databinding.ActivityMainBinding
import com.newway.purchase.NWBilling
import com.newway.purchase.NWBillingInterface
import com.newway.purchase.NWProduct
import com.newway.purchase.NWProductDetails

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    var nwBilling : NWBilling? = null
    val TAG = "MainActivity"

    //list products cần lấy
    val products = listOf(
        NWProduct("id subscription", ProductType.SUBS),
        NWProduct("id subscription", ProductType.SUBS),
        NWProduct("id onetime", ProductType.INAPP)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpInApp()

        binding.btnBuy.setOnClickListener {
            buy()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        nwBilling?.destroy()
    }

    //MARK: - FUN
    private fun setUpInApp() {
        nwBilling = NWBilling(this)
        nwBilling?.listener = object : NWBillingInterface {
            override fun onConnected() {
                // đã kết nối xong với google play -> lấy thông tin products
                nwBilling?.getInfo(products)

                // các hàm lấy thông tin các product đã mua (gọi khi cần check xem user đã mua iap nào chưa)
                nwBilling?.asyncInApp()
                nwBilling?.asyncSubscription()
            }

            override fun onConnectFailed() {
                // không kết nối được với billing google play
            }

            override fun onLoadedProductsInfo(
                billingResult: BillingResult,
                products:List<NWProductDetails>
            ) {
                //thông tin các product consumable , non-consumable (lifetime) sẽ trả về ở đây
                products.forEach {product ->
                    Log.e(TAG, "onLoadedProductsInfo: product = ${product.id} - price = ${product.formatPrice}")
                }
            }

            override fun onLoadedSubscriptionInfo(
                billingResult: BillingResult,
                products:List<NWProductDetails>
            ) {
                //thông tin các product subscription sẽ trả về ở đây
                products.forEach {product ->
                    Log.e(TAG, "onLoadedSubscriptionInfo: product = ${product.id} - price = ${product.formatPrice}")
                }
            }

            override fun onLoadPurchased(
                billingResult: BillingResult,
                purchases: List<Purchase>,
                type: String
            ) {
                Log.e(TAG, "onLoadPurchased: size = ${purchases.size}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.forEach {
                        Log.e(TAG, "onLoadPurchased purchases.forEach: ${it.originalJson}")
                    }
                    if (purchases.isNotEmpty()){
                        // Có ít nhất 1 purchase còn available
                        Log.e(TAG, "onLoadPurchased: purchase available")
                    }else{
                        //không tìm thấy purchase nào
                        Log.e(TAG, "onLoadPurchased: không tìm thấy purchase nào cả")
                    }
                }else{
                    // load purchase lỗi => kiểm tra biến billingResult.responseCode xem là lỗi gì
                    Log.e(TAG, "onPurchased: Failed")
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED){
                        Log.e(TAG, "onPurchased: Cancel")
                    }else if (billingResult.responseCode == BillingClient.BillingResponseCode.NETWORK_ERROR){
                        Log.e(TAG, "onPurchased: NETWORK_ERROR")
                    }else if (billingResult.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE){
                        Log.e(TAG, "onPurchased: BILLING_UNAVAILABLE")
                    }

                }

            }
        }
        nwBilling?.startServiceConnection()
    }

    //mua một iap nào đó
    fun buy(){
        val product = NWProduct("id sub",ProductType.SUBS)
        nwBilling?.buy(this,product)
        //=> kết quả sẽ trả về : onLoadPurchased
    }
}