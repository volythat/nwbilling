
## NWBilling
Đây là library NWBilling dùng cho In App Purchase Android (sử dụng Google Play Billing version 6.0.0)

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
    implementation 'com.github.volythat:nwbilling:1.0.11'
    implementation 'com.android.billingclient:billing-ktx:6.0.1'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

4. Thêm quyền billing cho ứng dụng bên trong AndroidManifest.xml

```kotlin
<uses-permission android:name="com.android.vending.BILLING" />
```

---
## Khởi tạo và kết nối : 
Tạo 1 biến billing ở Activity :

```kotlin
var nwBilling : NWBilling? = null
```

Khởi tạo và gắn listener :
```kotlin
private fun setUpInApp() {
    //list products cần lấy
    val products = listOf(
        NWProduct("sub id", ProductType.SUBS),
        NWProduct("sub id 2", ProductType.SUBS),
        NWProduct("inapp id", ProductType.INAPP)
    )
    nwBilling = NWBilling(this,products)
    nwBilling?.listener = object : NWBillingInterface {
        override fun onConnected() {
            // đã kết nối xong với google play -> lấy thông tin products hoặc lấy thông tin đã purchase 
            
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
```

Sau khi gọi funtion này sẽ chạy vào `onConnected` hoặc `onConnectFailed` . Nếu connect thành công thì mới gọi các funtion lấy thông tin iap hoặc thông tin purchase.

--- 

## Destroy : 

Hủy đi khi không dùng nữa 

```kotlin
override fun onDestroy() {
    super.onDestroy()
    nwBilling?.destroy()
}
```

---
## Lấy thông tin products : 

Chỉ gọi được khi billing đã khởi tạo và kết nôi thành công : 

```kotlin
nwBilling?.getInfo()
```

Thông tin products được trả về interface : 

1. Các product consumable và non-consumable(lifetime)
```kotlin 
fun onLoadedProductsInfo(
        billingResult: BillingResult,
        products:List<NWProductDetails>
    )
```

2. Các product subscription :
```kotlin
fun onLoadedSubscriptionInfo(
                billingResult: BillingResult,
                products:List<NWProductDetails>
            )
```

---
## Lấy thông tin các product đã mua :

Chỉ gọi được khi billing đã khởi tạo và kết nôi thành công : 

```kotlin
 // các hàm lấy thông tin các product đã mua (gọi khi cần check xem user đã mua iap nào chưa)
    nwBilling?.asyncInApp()
    nwBilling?.asyncSubscription()
```

Thông tin được trả về interface :

```kotlin
fun onLoadPurchased(
    billingResult: BillingResult,
    purchases: List<Purchase>,
    type: String
)
```

---

## Mua hàng

```kotlin
fun buy(){
    val product = NWProduct("<id>",ProductType.SUBS)
    nwBilling?.buy(this,product)
    //=> kết quả sẽ trả về : onLoadPurchased
}

```
Kết quả sẽ được trả về interface : 

```kotlin
override fun onPurchasedFailed(billingResult: BillingResult, product: NWProduct?) {
    Log.e(TAG, "onPurchasedFailed: id = ${product?.id}")
}

override fun onPurchasedSuccess(
    billingResult: BillingResult,
    purchase: Purchase?,
    product: NWProduct,
    productDetail: NWProductDetails?
) {
    Log.e(TAG, "onPurchasedSuccess: ${productDetail?.priceValue()}")
}
```


Cách lấy productId từ `Purchase` sau khi mua : 

```kotlin 
purchases.forEach {
    Log.e(TAG, "onLoadPurchased purchases.forEach: ${it.originalJson}")
    val obj = billing?.convertPurchaseJsonToObject(it)
    Log.e(TAG, "onLoadPurchased: obj.productId = ${obj?.productId}")
}
```

Cách lấy histories purchase (fun suspend): 

```kotlin 
    val list = nwBilling?.fetchHistory() //hàm gộp cả subs lẫn inapp 
    val subs = nwBilling?.getSubscriptionHistory() // chỉ lấy history subs 
    val inapp = nwBilling?.getInAppHistory() // chỉ lấy history in app
```

---

## Chú ý : 
- Mới chỉ test trên Nokia (android 11) 
- Nếu có bug gì vui lòng thông báo cho author 
- Library chưa hỗ trợ iap consumable 

---
## Author 

- trungnk@smartmove.com.vn
