
## NWBilling
Đây là library NWBilling dùng cho In App Purchase Android (sử dụng Google Play Billing version 6.0.0 trở lên)

--- 
## Requirement
- Google Play Billing version 6.0.0 trở lên 
- Android SDK : 23 trở lên 
- Ngôn ngữ Kotlin

---

## Implement 

1. Ở file `settings.gradle` thêm dòng bên dưới vào `repositories` của `pluginManagement` và `dependencyResolutionManagement` : 

```kotlin
maven { url 'https://jitpack.io' }
```

2. Ở file `gradle.properties` thêm dòng bên dưới : 

```kotlin
android.enableJetifier=true
```

3. Thêm google billing(version 6.0.0 trở lên) và gson (nếu chưa có) vào build.gradle  

```kotlin
dependencies {
    implementation 'com.github.volythat:nwbilling:2.0.0'
    implementation 'com.android.billingclient:billing-ktx:6.1.0'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

4. Thêm quyền billing cho ứng dụng bên trong AndroidManifest.xml

```kotlin
<uses-permission android:name="com.android.vending.BILLING" />
```

---
## Khởi tạo và kết nối : 
Ở màn Splash  :

```kotlin
private fun setUpBilling(){
    NWBilling.listener = object : NWBillingInterface {
        override fun onConnected() {
            // đã kết nối xong với google play -> lấy thông tin products
            LogD("Splash","splashConnectBillingSuccess")
        }

        override fun onConnectFailed() {
            LogD("Splash","onConnectFailed")
            // không kết nối được với billing google play : có fun NWBilling.reConnect() để gọi lại 
            showAlertRetryConnectBilling()
        }

        override fun onServiceDisconnected() {
            LogD("Splash","onServiceDisconnected")
            // Google service disconnect : google tự gọi lại 
        }

        override fun onLoadPurchased(purchases: List<NWPurchase>) {
            purchases.forEach { purchase ->
                logDebug("detail id = ${purchase.productId} -- price = ${purchase.orderId}")
            }
        }
    }
    NWBilling.setUp(context = `<Application context>`,ids = `List<NWProduct>`, isDebug = true)
}
```
- Biến isDebug : để show log purchase , khi release nên để về false 
- Tất cả interface đều optional, ở màn Splash thì chỉ cần 3 interface trên 
- Hàm NWBilling.setUp chỉ gọi 1 lần ở Splash 

--- 
- Cách lấy productDetails trực tiếp : 

```kotlin
val details = NWBilling.details.productDetails
```

- Cách lấy products đã purchased trực tiếp :

```kotlin
val details = NWBilling.purchased.purchases
```

---
## Lấy thông tin products và thanh toán :

- Đã gộp cả in app và subs

```kotlin 
private fun getInfoIAP(){
        NWBilling.listener = object : NWBillingInterface {
            override fun onConnected() {
                // đã kết nối xong với google play -> trong trường hợp retry 
                //NWBilling.getInfo() 
            }

            override fun onConnectFailed() {
                // không kết nối được với billing google play
                showAlertRetry()
            }

            override fun onLoadedInfo(allDetails: List<NWProductDetails>) {
                //thông tin các product  sẽ trả về ở đây
                
            }


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
        }
        NWBilling.getInfo() // func lấy thông tin iap 
    }
```

---

## Mua hàng

```kotlin
fun buy(){
    val product = NWProduct("<id>",ProductType.SUBS)
    NWBilling.buy(this,product)
    //=> kết quả sẽ trả về : onLoadPurchased
}

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
    val obj = NWBilling.purchased.convertPurchaseJsonToObject(it)
    Log.e(TAG, "onLoadPurchased: obj.productId = ${obj?.productId}")
}
```

Cách lấy histories purchase (fun suspend): 

```kotlin 
    val list = NWBilling.fetchHistory() //hàm gộp cả subs lẫn inapp 
    val subs = NWBilling.getSubscriptionHistory() // chỉ lấy history subs 
    val inapp = NWBilling.getInAppHistory() // chỉ lấy history in app
```

---

## Chú ý : 
- Mới chỉ test trên Nokia (android 11) 
- Nếu có bug gì vui lòng thông báo cho author 

---
## Author 

- trungnk@smartmove.com.vn
