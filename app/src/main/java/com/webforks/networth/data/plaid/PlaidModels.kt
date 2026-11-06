package com.webforks.networth.data.plaid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Only the fields this app uses are modelled; the JSON parser ignores the rest.

@Serializable
data class LinkTokenResponse(
    @SerialName("link_token") val linkToken: String,
    val expiration: String? = null,
)

@Serializable
data class ExchangeResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("item_id") val itemId: String,
)

@Serializable
data class PlaidBalances(
    val current: Double? = null,
    val available: Double? = null,
    @SerialName("iso_currency_code") val isoCurrencyCode: String? = null,
)

@Serializable
data class PlaidAccount(
    @SerialName("account_id") val accountId: String,
    val name: String,
    @SerialName("official_name") val officialName: String? = null,
    val mask: String? = null,
    val type: String,
    val subtype: String? = null,
    val balances: PlaidBalances,
)

@Serializable
data class PlaidError(
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("display_message") val displayMessage: String? = null,
)

@Serializable
data class PlaidItem(
    @SerialName("item_id") val itemId: String,
    @SerialName("institution_id") val institutionId: String? = null,
    val error: PlaidError? = null,
    @SerialName("consent_expiration_time") val consentExpirationTime: String? = null,
)

@Serializable
data class AccountsResponse(
    val accounts: List<PlaidAccount>,
    val item: PlaidItem,
)

@Serializable
data class PlaidHolding(
    @SerialName("account_id") val accountId: String,
    @SerialName("security_id") val securityId: String,
    val quantity: Double = 0.0,
    @SerialName("institution_price") val institutionPrice: Double? = null,
    @SerialName("institution_price_as_of") val institutionPriceAsOf: String? = null,
    @SerialName("institution_value") val institutionValue: Double? = null,
    @SerialName("cost_basis") val costBasis: Double? = null,
)

@Serializable
data class PlaidSecurity(
    @SerialName("security_id") val securityId: String,
    val name: String? = null,
    @SerialName("ticker_symbol") val tickerSymbol: String? = null,
    val type: String? = null,
    @SerialName("close_price") val closePrice: Double? = null,
    @SerialName("is_cash_equivalent") val isCashEquivalent: Boolean? = null,
)

@Serializable
data class HoldingsResponse(
    val accounts: List<PlaidAccount>,
    val holdings: List<PlaidHolding>,
    val securities: List<PlaidSecurity>,
    val item: PlaidItem,
)

class PlaidApiException(
    val httpCode: Int,
    val error: PlaidError?,
) : Exception(error?.displayMessage ?: error?.errorMessage ?: "Plaid request failed ($httpCode)") {
    /** The bank needs the user to sign in again (password change, MFA, consent expiry). */
    val needsReauth: Boolean
        get() = error?.errorCode in setOf(
            "ITEM_LOGIN_REQUIRED",
            "PENDING_EXPIRATION",
            "PENDING_DISCONNECT",
            "ACCESS_NOT_GRANTED",
            "INSUFFICIENT_CREDENTIALS",
        )
}
