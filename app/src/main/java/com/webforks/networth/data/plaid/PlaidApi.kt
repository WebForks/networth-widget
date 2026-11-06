package com.webforks.networth.data.plaid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

enum class PlaidEnvironment(val baseUrl: String, val label: String, val shortLabel: String) {
    SANDBOX("https://sandbox.plaid.com", "Sandbox (test data)", "Sandbox"),
    PRODUCTION("https://production.plaid.com", "Production (real accounts)", "Production"),
}

/** What kind of connection the user is adding; decides which Plaid product we ask for. */
enum class LinkKind { BANK, INVESTMENT }

data class PlaidCredentials(
    val clientId: String,
    val secret: String,
    val environment: PlaidEnvironment,
)

/**
 * Thin client for the Plaid REST endpoints the app needs.
 *
 * This calls Plaid directly from the phone using your own client id and secret. That is
 * fine for a single-user app on your own device; never ship it to other people this way.
 */
class PlaidApi(private val credentials: (PlaidEnvironment) -> PlaidCredentials?) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * Creates a token that opens Plaid Link. Pass [accessToken] to open Link in "update mode",
     * which re-authenticates an existing connection instead of adding a new one.
     */
    suspend fun createLinkToken(
        env: PlaidEnvironment,
        kind: LinkKind,
        packageName: String,
        accessToken: String? = null,
    ): LinkTokenResponse = post(env, "/link/token/create") {
        put("client_name", "Net Worth")
        put("language", "en")
        putJsonArray("country_codes") { add("US") }
        putJsonObject("user") { put("client_user_id", "owner") }
        put("android_package_name", packageName)
        if (accessToken != null) {
            put("access_token", accessToken)
        } else {
            putJsonArray("products") {
                add(
                    when (kind) {
                        LinkKind.BANK -> "transactions"
                        LinkKind.INVESTMENT -> "investments"
                    },
                )
            }
            if (kind == LinkKind.BANK) {
                // Only the minimum history; the app reads balances, not transactions.
                putJsonObject("transactions") { put("days_requested", 30) }
            }
        }
    }

    suspend fun exchangePublicToken(env: PlaidEnvironment, publicToken: String): ExchangeResponse =
        post(env, "/item/public_token/exchange") { put("public_token", publicToken) }

    /** Cached balances for every account on a connection. Cheap; used on every refresh. */
    suspend fun getAccounts(env: PlaidEnvironment, accessToken: String): AccountsResponse =
        post(env, "/accounts/get") { put("access_token", accessToken) }

    /** Positions (funds, stocks, cash) plus account balances for an investment connection. */
    suspend fun getHoldings(env: PlaidEnvironment, accessToken: String): HoldingsResponse =
        post(env, "/investments/holdings/get") { put("access_token", accessToken) }

    suspend fun removeItem(env: PlaidEnvironment, accessToken: String) {
        post<JsonObject>(env, "/item/remove") { put("access_token", accessToken) }
    }

    private suspend inline fun <reified T> post(
        env: PlaidEnvironment,
        path: String,
        crossinline body: JsonObjectBuilder.() -> Unit,
    ): T = withContext(Dispatchers.IO) {
        val creds = credentials(env) ?: throw IllegalStateException(
            "Add your Plaid client ID and ${env.shortLabel} secret in Settings first.",
        )
        val payload = buildJsonObject {
            put("client_id", creds.clientId)
            put("secret", creds.secret)
            body()
        }
        val request = Request.Builder()
            .url(creds.environment.baseUrl + path)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val err = runCatching { json.decodeFromString<PlaidError>(text) }.getOrNull()
                throw PlaidApiException(response.code, err)
            }
            json.decodeFromString<T>(text)
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
