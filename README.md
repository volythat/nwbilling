
## NWBilling
Đây là library NWBilling dùng cho In App Purchase Android (sử dụng Google Play Billing version 6.2.0 trở lên)

--- 
## Requirement
- Google Play Billing version 6.2.0 trở lên 
- Android SDK : 23 trở lên 
- Ngôn ngữ Kotlin

---

## Implement 

1. Ở file `settings.gradle` thêm dòng bên dưới vào `repositories` của `pluginManagement` và `dependencyResolutionManagement` : 

```kotlin
maven { url 'https://jitpack.io' }
```

2. Thêm google billing(version 6.2.0 trở lên) và gson (nếu chưa có) vào build.gradle  

```kotlin
dependencies {
    implementation("com.github.volythat:nwbilling:2.2.7")
    implementation("com.android.billingclient:billing-ktx:7.1.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}
```

3. Thêm quyền billing cho ứng dụng bên trong AndroidManifest.xml

```kotlin
<uses-permission android:name="com.android.vending.BILLING" />
```

---
## Khởi tạo và kết nối : 
Ở màn Splash  :

- khởi tạo :

```kotlin
    private var billing : NWBilling? = null 
```

- Setup

```kotlin
private fun setUpBilling(){
    billing = NWBilling(this)
    billing?.listener = object : NWBillingInterface {
        override fun onConnected() {
            // đã kết nối xong với google play -> lấy thông tin products
            LogD("Splash", "splashConnectBillingSuccess")
        }

        override fun onConnectFailed() {
            LogD("Splash", "onConnectFailed")
            // không kết nối được với billing google play => nên show alert retry 
            showAlertRetry()
        }

        override fun onServiceDisconnected() {
            LogD("Splash", "onServiceDisconnected")
            // Google service disconnect
        }

        override fun onLoadPurchased(purchases: List<NWPurchase>) {
            purchases.forEach { purchase ->
                logDebug("onLoadPurchased id = ${purchase.productId} -- price = ${purchase.orderId}")
            }
            // sau khi lấy được thông tin của iap user đã mua thì sẽ được trả về ở đây 
        }
    }
    billing?.setUp(isDebug = BuildConfig.DEBUG)

}
```

- Destroy : nên hủy đi nếu không dùng nữa , thường là ở `onDestroy()` :

```kotlin
override fun onDestroy() {
    super.onDestroy()
    billing?.destroy()
    billing = null
}
```

- Biến isDebug : để show log purchase , khi release nên để về false 
- Tất cả interface đều optional, ở màn Splash thì chỉ cần 4 interface trên 
- Ở splash không cần truyền mảng ids 

--- 
- Cách lấy productDetails trực tiếp : 

```kotlin
val details = billing?.details?.productDetails
```

- Cách lấy products đã purchased trực tiếp :

```kotlin
val details = billing?.purchased?.purchases
```

---
## Lấy thông tin products và thanh toán (trong các màn IAP):

- Đã gộp cả in app và subs

```kotlin 
private fun getInfoIAP(){
    billing = NWBilling(this)
    billing?.listener = object : NWBillingInterface {
        override fun onConnected() {
            // đã kết nối xong với google play -> lấy thông tin products
            LogD("BasePremium","onConnected")

        }

        override fun onConnectFailed() {
            // không kết nối được với billing google play

            LogD("BasePremium","onConnectFailed")
            showAlertRetry()
        }

        override fun onServiceDisconnected() {
            logDebug("onServiceDisconnected")
        }

        override fun onLoadedInfo(allDetails: List<NWProductDetails>) {
            //thông tin các product  sẽ trả về ở đây
            // lấy price từng gói ở đây 
        }

        override fun onPurchasedSuccess(
            billingResult: BillingResult,
            purchase: Purchase?,
            product: NWProduct,
            productDetail: NWProductDetails?
        ) {
            //nếu app có cả sub và inapp thì check bằng biến type 
            if (product.type == BillingClient.ProductType.SUBS) {
                // 
            }else if (product.type == BillingClient.ProductType.INAPP) {
                //
                
            }
            
        }

        override fun onPurchasedFailed(billingResult: BillingResult, product: NWProduct?) {
            super.onPurchasedFailed(billingResult, product)
            // mua không thành công 
        }
    }
    billing?.setUp(ids = <Mảng NWProduct>, isDebug = BuildConfig.DEBUG)
}
```

---

## Mua hàng

- Khởi tạo *NWProduct* gồm id , type là bắt buộc , còn basePlanId, offerId , isConsumable là optional 
- Lưu ý : lifetime không có basePlanId , offerId nên không cần truyền vào 
- 
```kotlin
fun buy(){
    // nếu là sub thì cần truyền thêm basePlanId 
    val product = NWProduct(id = "<id.sub>",type = ProductType.SUBS,basePlanId = "planId")
    // nếu là lifetime thì không cần 
    val product = NWProduct(id = "<id.lifetime>",type = ProductType.INAPP)
    
    billing?.buy(this,product)
    //=> kết quả sẽ trả về : onLoadPurchased
}

```

- Nếu có offerId (chỉ dành cho sub):
```kotlin
fun buy(){
    // nếu có offerId thì thêm vào (có offerId thì bắt buộc phải có basePlanId)
    val product = NWProduct(id = "<id>",type = ProductType.SUBS, basePlanId = "planA", offerId = "offerA")
    
    billing?.buy(this,product)
    //=> kết quả sẽ trả về : onLoadPurchased
}

```

Nếu mua *Consumable* thì thêm biến `isConsumable = true` , mặc định biến này là false 
```kotlin 
    val product = NWProduct(id = "<id>",type = ProductType.INAPP,isConsumable = true)
```


Kết quả sẽ được trả về interface : 

```kotlin
override fun onPurchasedSuccess(
    billingResult: BillingResult,
    purchase: Purchase?,
    product: NWProduct,
    productDetail: NWProductDetails?
) {
    //thanh toán thành công 
}

override fun onPurchasedFailed(billingResult: BillingResult, product: NWProduct?) {
    // Thanh toán lỗi 
}
```


Cách lấy productId từ `Purchase` sau khi mua : 

```kotlin 
purchases.forEach {
    Log.e(TAG, "onLoadPurchased purchases.forEach: ${it.originalJson}")
    val obj = billing?.purchased?.convertPurchaseJsonToObject(it)
    Log.e(TAG, "onLoadPurchased: obj.productId = ${obj?.productId}")
}
```

Cách lấy histories purchase (fun suspend): 

```kotlin 
    val list = billing?.fetchHistory() //hàm gộp cả subs lẫn inapp 
    val subs = billing?.getSubscriptionHistory() // chỉ lấy history subs 
    val inapp = billing?.getInAppHistory() // chỉ lấy history in app
```

## Subscriptions publisher api 

- Gọi khi mua thành công :
```kotlin
    private fun loadPublish(subId:String, token:String, signature:String){
        CoroutineScope(Dispatchers.IO).launch {
            billing?.getSubscriptionPublisher(packageName = "<package name>",
                subscriptionId = subId,
                token = token,
                signature = signature)?.collect { result ->
                withContext(Dispatchers.Main){
                    Log.e("LoadPublish", "currency = ${result?.priceCurrencyCode} - price = ${result?.priceAmountMicros}")
                }
            }
        }
    }
```

---

## Chú ý : 
- Mới chỉ test trên Nokia (android 11) 
- Nếu có bug gì vui lòng thông báo cho author 

---
## Author 

- trungnk@smartmove.com.vn
