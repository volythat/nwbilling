package com.newway.libraries.nwbilling.model


class NWPublisher(
    val acknowledgementState : Int,
    val autoResumeTimeMillis: String?,
    val cancelReason: String?,
    val countryCode: String,
    val developerPayload: String,
    val emailAddress: String?,
    val familyName: String?,
    val givenName: String?,
    val externalAccountId : String?,
    val kind: String,
    val linkedPurchaseToken:String?,
    val startTimeMillis: String,
    val expiryTimeMillis: String,
    val autoRenewing: Boolean,
    val priceAmountMicros: String,
    val priceCurrencyCode: String,
    val paymentState: Int,
    val freeTrialPeriod: String,
    val obfuscatedExternalAccountId: String?,
    val obfuscatedExternalProfileId: String?,
    val orderId: String?,
    val profileId: String?,
    val profileName: String?,
    val promotionCode: String?,
    val promotionType: String?,
    val purchaseType: String?,
    val userCancellationTimeMillis: String?
) {
}
