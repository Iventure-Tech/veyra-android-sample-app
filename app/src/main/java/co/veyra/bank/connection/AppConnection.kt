package co.veyra.bank.connection

import co.veyra.bank.BuildConfig
import co.veyra.common.providers.VeyraProvider
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The provider this app passes to `initialize` — one for both SDKs — chosen in the untracked
 * `veyra.properties` (see `veyra.properties.example`). Which kind is a
 * decision this app makes, so there is no default: an unset or unknown mode stops the app at
 * startup, naming what to set.
 */
object AppConnection {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** The provider for both SDKs. Built per call; the SDK re-binds it on every initialise. */
    fun provider(): VeyraProvider = from(
        mode = BuildConfig.VEYRA_CONNECTION_MODE,
        clientId = BuildConfig.VEYRA_CLIENT_ID,
        clientSecret = BuildConfig.VEYRA_CLIENT_SECRET,
        bankBackendBaseUrl = BuildConfig.BANK_BACKEND_BASE_URL,
        bankSession = { BuildConfig.BANK_SESSION_TOKEN },
        http = http,
    )

    internal fun from(
        mode: String,
        clientId: String,
        clientSecret: String,
        bankBackendBaseUrl: String,
        bankSession: () -> String?,
        http: OkHttpClient,
    ): VeyraProvider = when (mode) {
        "directWithClientSecret" ->
            ClientSecretCredentials(clientId, clientSecret)

        "directWithAssertion" -> BankBackendAssertionProvider(clientId, required(bankBackendBaseUrl), bankSession, http)

        "viaAppBackend" -> BankBackendRelay(required(bankBackendBaseUrl), bankSession, http)

        else -> error(
            "veyra.connection.mode is not set (got \"$mode\"). Copy veyra.properties.example to " +
                "veyra.properties in the project root and choose directWithClientSecret, " +
                "directWithAssertion or viaAppBackend.",
        )
    }

    private fun required(baseUrl: String): String =
        baseUrl.trimEnd('/').ifBlank { error("veyra.bankBackendBaseUrl must be set in veyra.properties for this mode") }
}

/**
 * The deprecated client-secret provider — **for testing only**, e.g. against UAT before your bank
 * backend can sign assertions. A secret inside an app can be extracted: ship a
 * [BankBackendAssertionProvider] or [BankBackendRelay] instead.
 */
@Suppress("DEPRECATION")
class ClientSecretCredentials(
    override val clientId: String,
    override val clientSecret: String,
) : co.veyra.common.providers.VeyraClientSecretProvider
